package com.attendance.pro.user;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.auth.PasswordResetRateLimiter;
import com.attendance.pro.billing.BillingService;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.MemberDtos.InviteResponse;
import com.attendance.pro.user.MemberDtos.MemberCreateRequest;
import com.attendance.pro.user.MemberDtos.MemberCreateResponse;
import com.attendance.pro.user.MemberDtos.MemberResponse;
import com.attendance.pro.user.MemberInviteService.InviteOutcome;

/**
 * 테넌트 멤버 관리 서비스(TENANT_ADMIN 초대 등록제 — Phase 3에서 초기 비밀번호 방식 폐지).
 * tenantId는 항상 세션에서 취득해 명시 전달받는다.
 *
 * 등록 = PENDING + 사용 불능 플레이스홀더 해시(BCrypt(SecureRandom 64B)) + INVITE 메일.
 * 메일 발송은 등록 INSERT 뒤(토큰 발급은 UserTokenService의 자체 Tx) — 발송 실패해도 멤버는
 * 생성되며 mailSent=false로 응답한다(재발송이 수습 경로 — INV-06).
 */
@Service
public class MemberService {

    private final UserMapper userMapper;
    private final UserTokenService userTokenService;
    private final MemberInviteService memberInviteService;
    private final PasswordResetRateLimiter rateLimiter;
    private final BillingService billingService;
    private final com.attendance.pro.attendance.ScheduleAdminService scheduleAdminService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public MemberService(UserMapper userMapper, UserTokenService userTokenService,
            MemberInviteService memberInviteService, PasswordResetRateLimiter rateLimiter,
            BillingService billingService,
            com.attendance.pro.attendance.ScheduleAdminService scheduleAdminService) {
        this.userMapper = userMapper;
        this.userTokenService = userTokenService;
        this.memberInviteService = memberInviteService;
        this.rateLimiter = rateLimiter;
        this.billingService = billingService;
        this.scheduleAdminService = scheduleAdminService;
        this.passwordEncoder = new BCryptPasswordEncoder(12); //강도 12(권장)
    }

    /** 멤버 목록(필터 없음) — 자기 테넌트 전체. */
    public List<MemberResponse> list(long tenantId) {
        return list(tenantId, null);
    }

    /**
     * 멤버 검색 목록 — 이름·이메일·부서 텍스트(q). 대규모 인원 대비 서버에서 걸러 응답한다.
     * (특정 날짜·시각 근무자 검색은 실효 스케줄 기반 별도 엔드포인트 /members/working)
     * PENDING 행은 유효 INVITE 토큰의 만료시각을 동봉한다.
     */
    public List<MemberResponse> list(long tenantId, String q) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Map<Long, LocalDateTime> inviteExpiries = userTokenService.findActiveInviteExpiries(tenantId);
        return userMapper.searchByTenant(tenantId, query).stream()
                .map(user -> MemberResponse.from(user, inviteExpiries.get(user.userId())))
                .toList();
    }

    /**
     * 멤버 초대 등록. 항상 MEMBER/PENDING으로 생성하고 INVITE 메일을 발송한다.
     * 테넌트 내 이메일 중복(활성 행 기준)시 409. 근무 스케줄은 등록 후 회사 기본 스케줄이 정기 스케줄로 복제된다.
     */
    public MemberCreateResponse create(long tenantId, MemberCreateRequest request, String inviterName) {
        if (userMapper.existsByEmail(tenantId, request.email())) {
            throw ApiException.conflict("EMAIL_DUPLICATED", "member.email.duplicated");
        }
        //입사일 선택 — 미입력 시 null(매퍼가 CURDATE로 채움). 연차 계산 기준(#11)
        java.time.LocalDate hireDate = (request.hireDate() == null || request.hireDate().isBlank())
                ? null : java.time.LocalDate.parse(request.hireDate());
        UserCreate create = new UserCreate(tenantId, request.email(),
                unusablePasswordHash(), request.name(), request.departCd(),
                Role.MEMBER, UserStatus.PENDING, hireDate, request.baseMonthlySalary());
        try {
            userMapper.insert(create);
        } catch (DuplicateKeyException e) {
            //existsByEmail 검사와 INSERT 사이의 동시 등록 레이스 — UNIQUE(email_key) 위반을 같은 409로
            throw ApiException.conflict("EMAIL_DUPLICATED", "member.email.duplicated");
        }
        //스케줄 단일화: 회사 기본 스케줄을 이 멤버의 정기 스케줄로 자동 생성(템플릿 없으면 no-op)
        scheduleAdminService.initFromTenantDefault(tenantId, create.getUserId());
        //메일 발송은 등록 트랜잭션과 분리 — 실패해도 멤버·토큰은 존재(mailSent=false → 재발송 유도)
        InviteOutcome mail = memberInviteService.sendInvite(tenantId, create.getUserId(),
                create.getEmail(), create.getName(), inviterName);
        return new MemberCreateResponse(create.getUserId(), create.getEmail(), create.getName(),
                create.getDepartCd(), Role.MEMBER, UserStatus.PENDING, mail.mailSent(), mail.expiresAt());
    }

    /**
     * 초대 재발송 — PENDING 대상 한정(ACTIVE/DISABLED는 409).
     * 기존 INVITE 토큰 삭제 + 신규 발급(72h 리셋) — 구 링크는 즉시 무효(오송신 수습 겸용).
     */
    public InviteResponse resendInvite(long tenantId, long userId, String inviterName) {
        User target = requireManageableMember(tenantId, userId);
        if (target.status() != UserStatus.PENDING) {
            throw ApiException.conflict("MEMBER_NOT_PENDING", "member.invite.not-pending");
        }
        rateLimiter.checkInviteResend(tenantId, target.email());
        InviteOutcome mail = memberInviteService.sendInvite(tenantId, userId,
                target.email(), target.name(), inviterName);
        return new InviteResponse(userId, target.email(), mail.mailSent(), mail.expiresAt());
    }

    /**
     * 멤버 소프트 삭제(deleted=TRUE — 출결 기록 보존) + 같은 Tx에서 유효 토큰 전멸.
     * 가드 순서: 자기 자신 400 → 존재 비노출 404 → 마지막 활성 TENANT_ADMIN 409.
     * 활성 세션은 SessionRevalidationInterceptor의 findById null → 즉시 회수(추가 장치 불요).
     */
    @Transactional
    public void delete(long tenantId, long actorUserId, long userId) {
        if (userId == actorUserId) {
            throw ApiException.badRequest("MEMBER_SELF_DELETE", "member.delete.self");
        }
        User target = requireManageableMember(tenantId, userId);
        if (target.role() == Role.TENANT_ADMIN && target.status() == UserStatus.ACTIVE) {
            guardLastTenantAdmin(tenantId);
        }
        userMapper.softDelete(tenantId, userId);
        userTokenService.invalidateAll(tenantId, userId);
        //활성 멤버 삭제는 좌석 감소 → 좌석 변동 기록(감원은 다음 달부터 반영, best-effort)
        billingService.touchSeatUsage(tenantId);
    }

    /**
     * 멤버 상태 변경(ACTIVE/DISABLED — PENDING 지정은 400).
     * 대상이 PENDING이면 400 — 비밀번호 미설정 계정이 ACTIVE가 되는 경로 차단(수습은 재발송/삭제로).
     * 타 테넌트 userId는 404(존재 비노출), 마지막 활성 TENANT_ADMIN 비활성은 409.
     * 정지 시 유효 토큰도 전멸(오송신 잔존 링크 무력화).
     */
    @Transactional
    public MemberResponse updateStatus(long tenantId, long userId, UserStatus status) {
        if (status == UserStatus.PENDING) {
            throw ApiException.badRequest("MEMBER_STATUS_INVALID", "member.status.invalid");
        }
        User target = requireManageableMember(tenantId, userId);
        if (target.status() == UserStatus.PENDING) {
            //ACTIVE 전이는 토큰 경유 단일 경로(INV-07)
            throw ApiException.badRequest("MEMBER_STATUS_INVALID", "member.status.invalid");
        }
        if (status == UserStatus.DISABLED && target.role() == Role.TENANT_ADMIN
                && target.status() == UserStatus.ACTIVE) {
            guardLastTenantAdmin(tenantId);
        }
        userMapper.updateStatus(tenantId, userId, status);
        if (status == UserStatus.DISABLED) {
            userTokenService.invalidateAll(tenantId, userId);
        }
        //활성 좌석 수 변동 → 월중 최대(과금 기준) 갱신(best-effort)
        billingService.touchSeatUsage(tenantId);
        return toResponse(tenantId, userMapper.findById(tenantId, userId));
    }

    /**
     * 멤버 역할 변경(TENANT_ADMIN·HR_ADMIN·MEMBER — SYSTEM_ADMIN 지정은 400).
     * 총관리자를 그 외 역할(인사관리자·멤버)로 바꾸면 총관리자 수가 줄므로, 마지막 활성 총관리자 강등은 409.
     * 호출 경로는 총관리자 전용(RoleInterceptor의 members role 규칙) — 직권 분산.
     */
    @Transactional
    public MemberResponse updateRole(long tenantId, long userId, Role role) {
        if (role == Role.SYSTEM_ADMIN) {
            throw ApiException.badRequest("MEMBER_ROLE_INVALID", "member.role.invalid");
        }
        User target = requireManageableMember(tenantId, userId);
        if (role != Role.TENANT_ADMIN && target.role() == Role.TENANT_ADMIN
                && target.status() == UserStatus.ACTIVE) {
            guardLastTenantAdmin(tenantId);
        }
        userMapper.updateRole(tenantId, userId, role);
        return toResponse(tenantId, userMapper.findById(tenantId, userId));
    }

    /**
     * 월 기본급 수정(속성별 PUT). null이면 미입력으로 저장(정산 제외).
     * PENDING·ACTIVE 무관 허용(입사 전 등록 준비 포함).
     */
    @Transactional
    public MemberResponse updateSalary(long tenantId, long userId, Long baseMonthlySalary) {
        requireManageableMember(tenantId, userId);
        userMapper.updateSalary(tenantId, userId, baseMonthlySalary);
        return toResponse(tenantId, userMapper.findById(tenantId, userId));
    }

    /**
     * 테넌트 생성시 최초 TENANT_ADMIN을 PENDING으로 등록(TenantService용 — UserMapper 접근을 user 패키지에 유지).
     * INVITE 발송은 생성 트랜잭션 커밋 후 호출부(TenantService)가 수행한다.
     */
    public long registerPendingAdmin(long tenantId, String email, String name) {
        UserCreate create = new UserCreate(tenantId, email, unusablePasswordHash(), name, null,
                Role.TENANT_ADMIN, UserStatus.PENDING, null, null);
        userMapper.insert(create);
        return create.getUserId();
    }

    private MemberResponse toResponse(long tenantId, User user) {
        return MemberResponse.from(user,
                userTokenService.findActiveInviteExpiries(tenantId).get(user.userId()));
    }

    /**
     * 조작 대상 멤버 로드. 타 테넌트 userId와 마찬가지로 SYSTEM_ADMIN 계정도 404
     * — V4 이관으로 DEFAULT 테넌트에 운영사 계정과 고객사 관리자가 공존할 수 있는데,
     * TENANT_ADMIN이 운영사 계정을 비활성/강등해 플랫폼 관리자를 잠그는 경로를 차단한다(존재 비노출).
     */
    private User requireManageableMember(long tenantId, long userId) {
        User target = userMapper.findById(tenantId, userId);
        if (target == null || target.role() == Role.SYSTEM_ADMIN) {
            throw ApiException.notFound("MEMBER_NOT_FOUND", "member.not-found");
        }
        return target;
    }

    /**
     * 마지막 활성 TENANT_ADMIN 보호 — 트랜잭션 내 FOR UPDATE 카운트가 1 이하면 409.
     */
    private void guardLastTenantAdmin(long tenantId) {
        if (userMapper.countActiveTenantAdmins(tenantId) <= 1) {
            throw ApiException.conflict("LAST_TENANT_ADMIN", "member.last-admin");
        }
    }


    /**
     * PENDING 계정의 사용 불능 플레이스홀더 해시 — password_hash NOT NULL 불변식 유지.
     * 원문(SecureRandom 48B → Base64 64자, BCrypt 72바이트 한계 내)은 어디에도 보관하지 않으므로
     * 로그인 불가(어차피 status=ACTIVE만 통과).
     */
    private String unusablePasswordHash() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }

}
