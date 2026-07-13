package com.attendance.pro.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.HolidayService;
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
 * 테넌트 CRUD/정지 + 최초 관리자 초대 전환 테스트 — INV-08(U)·HOL-07·admin-invite.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final SessionUser SYSTEM_ADMIN =
            new SessionUser(1L, 1L, "DEFAULT", "기본 테넌트", "admin@attendance.local", "관리자",
                    Role.SYSTEM_ADMIN, LocalDateTime.now());

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MemberService memberService;
    @Mock
    private MemberInviteService memberInviteService;
    @Mock
    private HolidayService holidayService;

    private TenantService service() {
        //레이트 리미터는 실물(테스트별 새 인스턴스 — 임계 3회/5분에 도달하지 않는다)
        return new TenantService(tenantMapper, userMapper, memberService, memberInviteService,
                holidayService, new com.attendance.pro.auth.PasswordResetRateLimiter(), immediateTx());
    }

    /** 콜백을 그 자리에서 실행하는 트랜잭션 템플릿(단위 테스트용 — 실 커밋 없음). */
    private static TransactionTemplate immediateTx() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    private static TenantCreateRequest createRequest(String country) {
        return new TenantCreateRequest("ACME", "에이크미(주)", country, "admin@acme.co.kr", "김관리");
    }

    private static User pendingAdmin(long userId) {
        return new User(userId, 10L, "admin@acme.co.kr", "hash", null, "김관리", null,
                LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100",
                Role.TENANT_ADMIN, UserStatus.PENDING, false, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("INV-08(U): 생성은 tenant INSERT + 관리자 PENDING 등록 + 초대 발송 + 공휴일 동기(initialPassword 없음)")
    void createProvisionsInviteAndSyncsHolidays() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(false);
        when(tenantMapper.insert(any(TenantCreate.class))).thenAnswer(inv -> {
            inv.getArgument(0, TenantCreate.class).setTenantId(10L);
            return 1;
        });
        when(memberService.registerPendingAdmin(10L, "admin@acme.co.kr", "김관리")).thenReturn(100L);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
        when(memberInviteService.sendInvite(10L, 100L, "admin@acme.co.kr", "김관리", "관리자"))
                .thenReturn(new InviteOutcome(true, expiresAt));
        when(holidayService.syncInitialYears(10L)).thenReturn(true);

        TenantCreateResponse response = service().create(SYSTEM_ADMIN, createRequest("KR"));

        assertThat(response.tenantId()).isEqualTo(10L);
        assertThat(response.tenantCode()).isEqualTo("ACME");
        assertThat(response.country()).isEqualTo("KR");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.adminUserId()).isEqualTo(100L);
        assertThat(response.adminStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(response.mailSent()).isTrue();
        assertThat(response.holidaysSynced()).isTrue();
        verify(memberService).registerPendingAdmin(10L, "admin@acme.co.kr", "김관리");
    }

    @Test
    @DisplayName("HOL-07a: 미지원 country는 400 COUNTRY_UNSUPPORTED — 아무것도 생성되지 않음")
    void unsupportedCountryRejected() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(false);

        assertThatThrownBy(() -> service().create(SYSTEM_ADMIN, createRequest("US")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo("COUNTRY_UNSUPPORTED");
                    assertThat(apiException.getMessageKey()).isEqualTo("validation.country.supported");
                });
        verify(tenantMapper, never()).insert(any(TenantCreate.class));
    }

    @Test
    @DisplayName("HOL-07b: 공휴일 동기 실패에도 생성은 성공 + holidaysSynced=false")
    void holidaySyncFailureTolerated() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(false);
        when(tenantMapper.insert(any(TenantCreate.class))).thenAnswer(inv -> {
            inv.getArgument(0, TenantCreate.class).setTenantId(10L);
            return 1;
        });
        when(memberService.registerPendingAdmin(anyLong(), anyString(), anyString())).thenReturn(100L);
        when(memberInviteService.sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new InviteOutcome(true, LocalDateTime.now()));
        when(holidayService.syncInitialYears(10L)).thenReturn(false);

        TenantCreateResponse response = service().create(SYSTEM_ADMIN, createRequest("JP"));

        assertThat(response.country()).isEqualTo("JP");
        assertThat(response.holidaysSynced()).isFalse();
    }

    @Test
    @DisplayName("메일 발송 실패에도 생성은 성공 + mailSent=false(admin-invite 재발송이 수습)")
    void mailFailureTolerated() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(false);
        when(tenantMapper.insert(any(TenantCreate.class))).thenAnswer(inv -> {
            inv.getArgument(0, TenantCreate.class).setTenantId(10L);
            return 1;
        });
        when(memberService.registerPendingAdmin(anyLong(), anyString(), anyString())).thenReturn(100L);
        when(memberInviteService.sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new InviteOutcome(false, null));
        when(holidayService.syncInitialYears(10L)).thenReturn(true);

        TenantCreateResponse response = service().create(SYSTEM_ADMIN, createRequest("KR"));

        assertThat(response.mailSent()).isFalse();
        assertThat(response.holidaysSynced()).isTrue();
    }

    @Test
    @DisplayName("테넌트 코드 중복이면 409 TENANT_CODE_DUPLICATED — 관리자 등록 없음")
    void duplicateCodeRejected() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(true);

        assertThatThrownBy(() -> service().create(SYSTEM_ADMIN, createRequest("KR")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(409);
                    assertThat(apiException.getCode()).isEqualTo("TENANT_CODE_DUPLICATED");
                    assertThat(apiException.getMessageKey()).isEqualTo("tenant.code.duplicated");
                });
        verify(memberService, never()).registerPendingAdmin(anyLong(), any(), any());
    }

    @Test
    @DisplayName("admin-invite: PENDING TENANT_ADMIN이 1명이면 재발송 200 계약")
    void adminInviteResends() {
        when(tenantMapper.findById(10L))
                .thenReturn(new Tenant(10L, "ACME", "에이크미(주)", "KR", TenantStatus.ACTIVE, LocalDateTime.now()));
        when(userMapper.findByTenant(10L)).thenReturn(List.of(pendingAdmin(100L)));
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
        when(memberInviteService.sendInvite(10L, 100L, "admin@acme.co.kr", "김관리", "관리자"))
                .thenReturn(new InviteOutcome(true, expiresAt));

        InviteResponse response = service().adminInvite(SYSTEM_ADMIN, 10L);

        assertThat(response.userId()).isEqualTo(100L);
        assertThat(response.mailSent()).isTrue();
        assertThat(response.inviteExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("admin-invite: PENDING 관리자 0명/2명 이상이면 409(모호성 거부), 미존재 테넌트는 404")
    void adminInviteGuards() {
        when(tenantMapper.findById(10L))
                .thenReturn(new Tenant(10L, "ACME", "에이크미(주)", "KR", TenantStatus.ACTIVE, LocalDateTime.now()));
        //0명(전원 ACTIVE)
        when(userMapper.findByTenant(10L)).thenReturn(List.of());
        assertThatThrownBy(() -> service().adminInvite(SYSTEM_ADMIN, 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(409);
                    assertThat(apiException.getCode()).isEqualTo("TENANT_ADMIN_INVITE_INVALID");
                    assertThat(apiException.getMessageKey()).isEqualTo("tenant.admin-invite.invalid");
                });
        //2명
        when(userMapper.findByTenant(10L)).thenReturn(List.of(pendingAdmin(100L), pendingAdmin(101L)));
        assertThatThrownBy(() -> service().adminInvite(SYSTEM_ADMIN, 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_ADMIN_INVITE_INVALID"));
        //미존재 테넌트
        when(tenantMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().adminInvite(SYSTEM_ADMIN, 99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
        verify(memberInviteService, never()).sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("미존재 테넌트 조회는 404 TENANT_NOT_FOUND")
    void getNotFound() {
        when(tenantMapper.findByIdWithMemberCount(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().get(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("자기 소속 테넌트 정지 시도는 400 TENANT_SELF_SUSPEND")
    void selfSuspendRejected() {
        when(tenantMapper.findById(1L))
                .thenReturn(new Tenant(1L, "DEFAULT", "기본 테넌트", "KR", TenantStatus.ACTIVE, LocalDateTime.now()));

        assertThatThrownBy(() -> service().updateStatus(SYSTEM_ADMIN, 1L, TenantStatus.SUSPENDED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo("TENANT_SELF_SUSPEND");
                });
        verify(tenantMapper, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("타 테넌트 정지/재개는 정상 처리된다")
    void suspendOtherTenant() {
        when(tenantMapper.findById(10L))
                .thenReturn(new Tenant(10L, "ACME", "에이크미(주)", "KR", TenantStatus.ACTIVE, LocalDateTime.now()));
        when(tenantMapper.findByIdWithMemberCount(10L))
                .thenReturn(new TenantResponse(10L, "ACME", "에이크미(주)", "KR", TenantStatus.SUSPENDED, 3,
                        LocalDateTime.now()));

        TenantResponse response = service().updateStatus(SYSTEM_ADMIN, 10L, TenantStatus.SUSPENDED);

        assertThat(response.status()).isEqualTo(TenantStatus.SUSPENDED);
        verify(tenantMapper).updateStatus(10L, TenantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("미존재 테넌트 정지는 404")
    void suspendNotFound() {
        when(tenantMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().updateStatus(SYSTEM_ADMIN, 99L, TenantStatus.SUSPENDED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

}
