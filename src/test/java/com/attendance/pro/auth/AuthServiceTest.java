package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

/**
 * 테넌트 스코프 로그인 인증 테스트.
 * 케이스 ID: test-plan §1-3 LGN-01~04, 06.
 * 실패 사유와 무관하게 전부 동일한 401 메시지(auth.login.failed) — 존재 여부 비노출.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;
    private static final String PW_A = "PasswordA1!";
    private static final String PW_B = "PasswordB2!";

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private UserMapper userMapper;

    private AuthService service() {
        return new AuthService(tenantMapper, userMapper);
    }

    private static Tenant tenant(long id, String code, TenantStatus status) {
        return new Tenant(id, code, code + "(주)", "KR", status, LocalDateTime.now());
    }

    private static User user(long tenantId, String email, String rawPassword, Role role, UserStatus status) {
        return new User(1L, tenantId, email, new BCryptPasswordEncoder().encode(rawPassword), null,
                "홍길동", null, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0), "1111100", null,
                role, status, false, LocalDateTime.now(), LocalDateTime.now());
    }

    private void expectLoginFailed(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(401);
                    assertThat(apiException.getMessageKey()).isEqualTo("auth.login.failed");
                });
    }

    @Test
    @DisplayName("LGN-01: 테넌트 A의 크리덴셜로 로그인하면 세션이 A 스코프가 된다")
    void loginTenantA() {
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "hong@example.com"))
                .thenReturn(user(TENANT_A, "hong@example.com", PW_A, Role.MEMBER, UserStatus.ACTIVE));

        SessionUser session = service().authenticate("ACME", "hong@example.com", PW_A);

        assertThat(session.tenantId()).isEqualTo(TENANT_A);
        assertThat(session.tenantCode()).isEqualTo("ACME");
        assertThat(session.role()).isEqualTo(Role.MEMBER);
        assertThat(session.email()).isEqualTo("hong@example.com");
    }

    @Test
    @DisplayName("LGN-02: 같은 이메일이 B에도 있으면 B 코드+B 비밀번호로 B 스코프 로그인")
    void loginSameEmailOtherTenant() {
        when(tenantMapper.findByCode("BETA")).thenReturn(tenant(TENANT_B, "BETA", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_B, "hong@example.com"))
                .thenReturn(user(TENANT_B, "hong@example.com", PW_B, Role.MEMBER, UserStatus.ACTIVE));

        SessionUser session = service().authenticate("BETA", "hong@example.com", PW_B);

        assertThat(session.tenantId()).isEqualTo(TENANT_B);
        verify(userMapper).findByEmail(TENANT_B, "hong@example.com");  //테넌트 스코프 조회
    }

    @Test
    @DisplayName("LGN-03: A의 비밀번호로 B 로그인은 401 — 크리덴셜이 테넌트 스코프로 검증됨")
    void crossTenantPasswordRejected() {
        when(tenantMapper.findByCode("BETA")).thenReturn(tenant(TENANT_B, "BETA", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_B, "hong@example.com"))
                .thenReturn(user(TENANT_B, "hong@example.com", PW_B, Role.MEMBER, UserStatus.ACTIVE));

        expectLoginFailed(() -> service().authenticate("BETA", "hong@example.com", PW_A));
    }

    @Test
    @DisplayName("LGN-04: 미존재 테넌트 코드는 완전히 동일한 401(테넌트 존재 비노출)")
    void unknownTenantCode() {
        when(tenantMapper.findByCode("NOPE")).thenReturn(null);
        expectLoginFailed(() -> service().authenticate("NOPE", "hong@example.com", PW_A));
    }

    @Test
    @DisplayName("LGN-05(단위판): SUSPENDED 테넌트의 유효 계정도 동일한 401")
    void suspendedTenantRejected() {
        when(tenantMapper.findByCode("SLEEP")).thenReturn(tenant(30L, "SLEEP", TenantStatus.SUSPENDED));
        expectLoginFailed(() -> service().authenticate("SLEEP", "hong@example.com", PW_A));
    }

    @Test
    @DisplayName("LGN-06: DISABLED 멤버의 유효 크리덴셜도 동일한 401 — ACTIVE만 로그인 허용")
    void disabledUserRejected() {
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "hong@example.com"))
                .thenReturn(user(TENANT_A, "hong@example.com", PW_A, Role.MEMBER, UserStatus.DISABLED));

        expectLoginFailed(() -> service().authenticate("ACME", "hong@example.com", PW_A));
    }

    @Test
    @DisplayName("INV-02(U): PENDING(초대 대기) 멤버의 유효 크리덴셜도 동일한 401 — ACTIVE만 로그인 허용")
    void pendingUserRejected() {
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "hong@example.com"))
                .thenReturn(user(TENANT_A, "hong@example.com", PW_A, Role.MEMBER, UserStatus.PENDING));

        expectLoginFailed(() -> service().authenticate("ACME", "hong@example.com", PW_A));
    }

    @Test
    @DisplayName("세션 스냅샷에 유저의 password_changed_at이 그대로 실린다 — 재로그인 강제의 동등 비교 기준")
    void sessionCarriesPasswordChangedAt() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 7, 9, 9, 0, 0, 123_000_000);
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "hong@example.com")).thenReturn(
                new User(1L, TENANT_A, "hong@example.com", new BCryptPasswordEncoder().encode(PW_A),
                        changedAt, "홍길동", null, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0), "1111100", null,
                        Role.MEMBER, UserStatus.ACTIVE, false, LocalDateTime.now(), LocalDateTime.now()));

        SessionUser session = service().authenticate("ACME", "hong@example.com", PW_A);

        assertThat(session.passwordChangedAt()).isEqualTo(changedAt);
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 동일한 401")
    void unknownEmailRejected() {
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "nobody@example.com")).thenReturn(null);
        expectLoginFailed(() -> service().authenticate("ACME", "nobody@example.com", PW_A));
    }

    @Test
    @DisplayName("테넌트 코드는 대문자 정규화(trim + upper) 후 해석된다")
    void tenantCodeNormalized() {
        when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TENANT_A, "ACME", TenantStatus.ACTIVE));
        when(userMapper.findByEmail(TENANT_A, "hong@example.com"))
                .thenReturn(user(TENANT_A, "hong@example.com", PW_A, Role.MEMBER, UserStatus.ACTIVE));

        SessionUser session = service().authenticate("  acme ", "hong@example.com", PW_A);

        assertThat(session.tenantId()).isEqualTo(TENANT_A);
        verify(tenantMapper).findByCode("ACME");
    }

}
