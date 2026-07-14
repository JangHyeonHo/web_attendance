package com.attendance.pro.tenant;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.attendance.pro.auth.PasswordResetRateLimiter;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.HolidayService;
import com.attendance.pro.leave.LeaveService;
import com.attendance.pro.tenant.TenantDtos.TenantCreateRequest;
import com.attendance.pro.tenant.TenantDtos.TenantCreateResponse;
import com.attendance.pro.tenant.TenantDtos.TenantResponse;
import com.attendance.pro.user.MemberDtos.InviteResponse;
import com.attendance.pro.user.MemberInviteService;
import com.attendance.pro.user.MemberInviteService.InviteOutcome;
import com.attendance.pro.user.MemberService;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

/**
 * 테넌트 CRUD/정지 + 최초 TENANT_ADMIN 초대 발급 서비스(SYSTEM_ADMIN 전용 API가 사용).
 * 최초 관리자 등록은 {@link MemberService}에 위임해 UserMapper 접근을 user 패키지에 유지한다.
 *
 * 생성 플로우(CR3-5): Tx(tenant INSERT + 관리자 PENDING INSERT) 커밋
 * → ① INVITE 메일 발송(mailSent) → ② 당해·익년 공휴일 sync(holidaysSynced) → 응답.
 * 두 후처리는 상호 독립(각자 예외 삼킴·플래그·수습 경로 분리: 메일=admin-invite, 공휴일=W013 수동 동기화).
 * 메일이 먼저인 이유: 외부 API 대기가 초대 발송을 지연시키지 않게.
 */
@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final MemberService memberService;
    private final MemberInviteService memberInviteService;
    private final HolidayService holidayService;
    private final LeaveService leaveService;
    private final PasswordResetRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;

    public TenantService(TenantMapper tenantMapper, UserMapper userMapper, MemberService memberService,
            MemberInviteService memberInviteService, HolidayService holidayService,
            LeaveService leaveService, PasswordResetRateLimiter rateLimiter,
            TransactionTemplate transactionTemplate) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.memberService = memberService;
        this.memberInviteService = memberInviteService;
        this.holidayService = holidayService;
        this.leaveService = leaveService;
        this.rateLimiter = rateLimiter;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 테넌트 생성 + 최초 TENANT_ADMIN 초대(PENDING) — 초기 비밀번호 방식 폐지(D11 TODO 이행).
     * country는 필수(ProfileCountry 검증) — 최초 관리자 초대 메일은 처음부터 소재국 언어로 발송된다(CR3-1).
     */
    public TenantCreateResponse create(SessionUser actor, TenantCreateRequest request) {
        //서브도메인 예약어(www/admin/api 등)는 테넌트 코드로 선점 불가 — 서브도메인 병행 방식과의 충돌 방지
        if (TenantHostResolver.RESERVED_LABELS.contains(request.tenantCode())) {
            throw ApiException.badRequest("TENANT_CODE_RESERVED", "tenant.code.reserved");
        }
        if (tenantMapper.existsByCode(request.tenantCode())) {
            throw ApiException.conflict("TENANT_CODE_DUPLICATED", "tenant.code.duplicated");
        }
        ProfileCountry country = ProfileCountry.of(request.country());
        if (country == null) {
            throw ApiException.badRequest("COUNTRY_UNSUPPORTED", "validation.country.supported");
        }
        //[Tx] tenant + 관리자(PENDING) — 커밋 후에 후처리 2종(메일/공휴일)을 실행한다
        Provisioned provisioned = transactionTemplate.execute(status -> {
            TenantCreate create = new TenantCreate(request.tenantCode(), request.name(), country.name());
            tenantMapper.insert(create);
            long adminUserId = memberService.registerPendingAdmin(create.getTenantId(),
                    request.adminEmail(), request.adminName());
            //국가별 기본 휴가 종류 시드(#10) — 연차 자동계산이 findAnnual에 의존하므로 생성 시점에 보장
            leaveService.seedDefaults(create.getTenantId(), country.name());
            return new Provisioned(create.getTenantId(), adminUserId);
        });
        //① INVITE 메일({inviterName}=SA 세션 name) — 실패해도 생성은 성공(admin-invite 재발송이 수습)
        InviteOutcome mail = memberInviteService.sendInvite(provisioned.tenantId(),
                provisioned.adminUserId(), request.adminEmail(), request.adminName(), actor.name());
        //② 당해·익년 공휴일 동기화 — 실패 허용(W013 수동 동기화가 수습)
        boolean holidaysSynced = holidayService.syncInitialYears(provisioned.tenantId());
        return new TenantCreateResponse(provisioned.tenantId(), request.tenantCode(), request.name(),
                country.name(), TenantStatus.ACTIVE, provisioned.adminUserId(), request.adminEmail(),
                UserStatus.PENDING, mail.mailSent(), holidaysSynced);
    }

    /** 생성 트랜잭션 결과. */
    private record Provisioned(long tenantId, long adminUserId) {
    }

    /**
     * 최초 관리자 초대 재발송 — 그 테넌트의 PENDING인 TENANT_ADMIN이 정확히 1명일 때만(모호성 거부).
     * mailSent=false(SMTP 실패)·미수신 수습 용도.
     */
    public InviteResponse adminInvite(SessionUser actor, long tenantId) {
        if (tenantMapper.findById(tenantId) == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        List<User> pendingAdmins = userMapper.findByTenant(tenantId).stream()
                .filter(user -> user.role() == Role.TENANT_ADMIN && user.status() == UserStatus.PENDING)
                .toList();
        if (pendingAdmins.size() != 1) {
            throw ApiException.conflict("TENANT_ADMIN_INVITE_INVALID", "tenant.admin-invite.invalid");
        }
        User admin = pendingAdmins.get(0);
        rateLimiter.checkInviteResend(tenantId, admin.email());
        InviteOutcome mail = memberInviteService.sendInvite(tenantId, admin.userId(),
                admin.email(), admin.name(), actor.name());
        return new InviteResponse(admin.userId(), admin.email(), mail.mailSent(), mail.expiresAt());
    }

    public List<TenantResponse> list() {
        return tenantMapper.findAllWithMemberCount();
    }

    public TenantResponse get(long tenantId) {
        TenantResponse tenant = tenantMapper.findByIdWithMemberCount(tenantId);
        if (tenant == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        return tenant;
    }

    /**
     * 정지/재개. 자기 소속 테넌트 정지는 400(운영자 셀프 락아웃 방지).
     */
    @Transactional
    public TenantResponse updateStatus(SessionUser actor, long tenantId, TenantStatus status) {
        if (tenantMapper.findById(tenantId) == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        if (status == TenantStatus.SUSPENDED && actor.tenantId() == tenantId) {
            throw ApiException.badRequest("TENANT_SELF_SUSPEND", "tenant.suspend.self");
        }
        tenantMapper.updateStatus(tenantId, status);
        return get(tenantId);
    }

}
