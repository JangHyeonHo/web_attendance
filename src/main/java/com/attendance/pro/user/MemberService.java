package com.attendance.pro.user;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.attendance.pro.user.MemberDtos.MemberScheduleRequest;
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

    /** 개인 기본 근무 스케줄 미지정 시 기본값 */
    static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 0);

    private final UserMapper userMapper;
    private final UserTokenService userTokenService;
    private final MemberInviteService memberInviteService;
    private final PasswordResetRateLimiter rateLimiter;
    private final BillingService billingService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public MemberService(UserMapper userMapper, UserTokenService userTokenService,
            MemberInviteService memberInviteService, PasswordResetRateLimiter rateLimiter,
            BillingService billingService) {
        this.userMapper = userMapper;
        this.userTokenService = userTokenService;
        this.memberInviteService = memberInviteService;
        this.rateLimiter = rateLimiter;
        this.billingService = billingService;
        this.passwordEncoder = new BCryptPasswordEncoder(12); //강도 12(권장)
    }

    /** 멤버 목록(필터 없음) — 자기 테넌트 전체. */
    public List<MemberResponse> list(long tenantId) {
        return list(tenantId, null, null, null);
    }

    /**
     * 멤버 검색 목록(#6) — 이름·이메일·부서 텍스트 + 개인 기본 근무 시간대(workFrom~workTo) 겹침 필터.
     * 대규모 인원에서도 서버에서 걸러 응답하므로 전체 로딩이 불필요하다. 빈 값 파라미터는 무시(전체).
     * PENDING 행은 유효 INVITE 토큰의 만료시각을 동봉한다.
     */
    public List<MemberResponse> list(long tenantId, String q, String workFrom, String workTo) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        LocalTime from = parseTimeOrNull(workFrom);
        LocalTime to = parseTimeOrNull(workTo);
        Map<Long, LocalDateTime> inviteExpiries = userTokenService.findActiveInviteExpiries(tenantId);
        return userMapper.searchByTenant(tenantId, query, from, to).stream()
                .map(user -> MemberResponse.from(user, inviteExpiries.get(user.userId())))
                .toList();
    }

    private LocalTime parseTimeOrNull(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(time.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("WORK_TIME_INVALID", "member.work-time.invalid");
        }
    }

    /**
     * 멤버 초대 등록. 항상 MEMBER/PENDING으로 생성하고 INVITE 메일을 발송한다.
     * 테넌트 내 이메일 중복(활성 행 기준)시 409. workStart/End 미지정은 09:00/18:00.
     */
    public MemberCreateResponse create(long tenantId, MemberCreateRequest request, String inviterName) {
        if (userMapper.existsByEmail(tenantId, request.email())) {
            throw ApiException.conflict("EMAIL_DUPLICATED", "member.email.duplicated");
        }
        LocalTime workStart = parseOrDefault(request.workStart(), DEFAULT_WORK_START);
        LocalTime workEnd = parseOrDefault(request.workEnd(), DEFAULT_WORK_END);
        validateWorkRange(workStart, workEnd);
        //입사일 선택 — 미입력 시 null(매퍼가 CURDATE로 채움). 연차 계산 기준(#11)
        java.time.LocalDate hireDate = (request.hireDate() == null || request.hireDate().isBlank())
                ? null : java.time.LocalDate.parse(request.hireDate());
        UserCreate create = new UserCreate(tenantId, request.email(),
                unusablePasswordHash(), request.name(), request.departCd(),
                workStart, workEnd, Role.MEMBER, UserStatus.PENDING, hireDate,
                request.baseMonthlySalary());
        try {
            userMapper.insert(create);
        } catch (DuplicateKeyException e) {
            //existsByEmail 검사와 INSERT 사이의 동시 등록 레이스 — UNIQUE(email_key) 위반을 같은 409로
            throw ApiException.conflict("EMAIL_DUPLICATED", "member.email.duplicated");
        }
        //메일 발송은 등록 트랜잭션과 분리 — 실패해도 멤버·토큰은 존재(mailSent=false → 재발송 유도)
        InviteOutcome mail = memberInviteService.sendInvite(tenantId, create.getUserId(),
                create.getEmail(), create.getName(), inviterName);
        return new MemberCreateResponse(create.getUserId(), create.getEmail(), create.getName(),
                create.getDepartCd(), Role.MEMBER, UserStatus.PENDING,
                MemberDtos.formatTime(workStart), MemberDtos.formatTime(workEnd),
                mail.mailSent(), mail.expiresAt());
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
     * 개인 기본 근무 스케줄 수정(속성별 PUT — /status·/role 패턴 계승).
     * PENDING(초대 대기) 행에도 허용(입사 전 준비 — CR3-6).
     */
    @Transactional
    public MemberResponse updateSchedule(long tenantId, long userId, MemberScheduleRequest request) {
        requireManageableMember(tenantId, userId);
        LocalTime workStart = LocalTime.parse(request.workStart());
        LocalTime workEnd = LocalTime.parse(request.workEnd());
        validateWorkRange(workStart, workEnd);
        //전 요일 휴무는 거부 — 집계상 매일이 dayOff가 되어 근무 기록이 전부 휴무 표시가 된다
        if (!request.workDays().contains("1")) {
            throw ApiException.badRequest("WORK_DAYS_EMPTY", "member.work-days.empty");
        }
        userMapper.updateWorkSchedule(tenantId, userId, workStart, workEnd, request.workDays());
        return toResponse(tenantId, userMapper.findById(tenantId, userId));
    }

    /**
     * 월 기본급 수정(속성별 PUT — /schedule 패턴 계승). null이면 미입력으로 저장(정산 제외).
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
                DEFAULT_WORK_START, DEFAULT_WORK_END, Role.TENANT_ADMIN, UserStatus.PENDING, null, null);
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

    private LocalTime parseOrDefault(String time, LocalTime defaultValue) {
        return time == null || time.isBlank() ? defaultValue : LocalTime.parse(time);
    }

    /** 교차 검증은 서비스 단일 출처 — 자정 넘김(22:00~06:00) 개인 기본 스케줄은 비지원(교대제는 별도 Phase). */
    private void validateWorkRange(LocalTime workStart, LocalTime workEnd) {
        if (!workStart.isBefore(workEnd)) {
            throw ApiException.badRequest("WORK_TIME_INVALID_RANGE", "member.work-time.invalid-range");
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
