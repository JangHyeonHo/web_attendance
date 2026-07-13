package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.attendance.pro.config.LocaleConfig;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantHostResolver;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserMapper.RevalidationState;
import com.attendance.pro.user.UserStatus;

/**
 * 세션 스냅샷 재검증 — 테넌트 정지/계정 비활성의 즉시 반영(세션 잔존 권한 차단)과 role 갱신.
 * 재검증은 결합 쿼리 {@link UserMapper#findRevalidationState}(상태·role·비번시각·세션토큰) 1건으로 조회한다.
 */
@ExtendWith(MockitoExtension.class)
class SessionRevalidationInterceptorTest {

    private static final long TENANT_ID = 10L;
    private static final long USER_ID = 5L;
    /** 로그인 시점의 password_changed_at 스냅샷 — DB 현재 값과 다르면(방향 무관) 세션 회수 */
    private static final LocalDateTime PW_CHANGED_AT = LocalDateTime.of(2026, 7, 9, 9, 0);
    private static final SessionUser SNAPSHOT = new SessionUser(
            USER_ID, TENANT_ID, "ACME", "에이크미", "ta@acme.co.kr", "김관리", Role.TENANT_ADMIN,
            PW_CHANGED_AT, null);

    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private com.attendance.pro.audit.AuditService auditService;

    private SessionRevalidationInterceptor interceptor() {
        //base-domain "webatt.example"로 호스트 일치 검증까지 활성화(기본 요청 호스트 localhost는 NONE)
        return new SessionRevalidationInterceptor(userMapper, tenantMapper,
                new TenantHostResolver(tenantMapper, "webatt.example"), auditService);
    }

    private static RevalidationState dbState(Role role, UserStatus status) {
        return dbState(role, status, PW_CHANGED_AT, null);
    }

    private static RevalidationState dbState(Role role, UserStatus status, LocalDateTime passwordChangedAt) {
        return dbState(role, status, passwordChangedAt, null);
    }

    private static RevalidationState dbState(Role role, UserStatus status,
            LocalDateTime passwordChangedAt, String sessionToken) {
        return new RevalidationState(status, role, passwordChangedAt, sessionToken);
    }

    private static Tenant dbTenant(TenantStatus status) {
        return new Tenant(TENANT_ID, "ACME", "에이크미", "KR", status, LocalDateTime.now());
    }

    private MockHttpServletRequest loggedInRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_KEY, SNAPSHOT);
        session.setAttribute(LocaleConfig.SESSION_LANG_KEY, "JPN");
        request.setSession(session);
        return request;
    }

    @Test
    @DisplayName("유저/테넌트 모두 정상(ACTIVE)이면 세션 유지")
    void activeUserPasses() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();

        assertThat(interceptor().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("REV-01: 테넌트 SUSPENDED → 기존 세션 즉시 무효화(언어 설정만 이월)")
    void suspendedTenantInvalidatesSession() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.SUSPENDED));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
        assertThat(request.getSession(false).getAttribute(LocaleConfig.SESSION_LANG_KEY)).isEqualTo("JPN");
    }

    @Test
    @DisplayName("REV-02: 계정 DISABLED → 기존 세션 즉시 무효화")
    void disabledUserInvalidatesSession() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.DISABLED));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("REV-03: 계정 삭제(조회 null) → 기존 세션 즉시 무효화")
    void deletedUserInvalidatesSession() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(null);
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("REV-04: role 강등이 DB에 반영됐으면 세션 스냅샷도 즉시 갱신(잔존 TA 권한 차단)")
    void roleChangeRefreshesSnapshot() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.MEMBER, UserStatus.ACTIVE));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        SessionUser refreshed =
                (SessionUser) request.getSession(false).getAttribute(SessionUser.SESSION_KEY);
        assertThat(refreshed.role()).isEqualTo(Role.MEMBER);
        assertThat(refreshed.userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("SES-01(U): 로그인 이후 비밀번호가 변경되면(스냅샷 불일치) 즉시 무효화(언어 설정만 이월)")
    void passwordChangeInvalidatesOlderSession() {
        //세션 스냅샷(PW_CHANGED_AT) 이후에 비밀번호가 변경된 상황
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID))
                .thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE, PW_CHANGED_AT.plusMinutes(10)));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
        assertThat(request.getSession(false).getAttribute(LocaleConfig.SESSION_LANG_KEY)).isEqualTo("JPN");
    }

    @Test
    @DisplayName("동등 비교라 과거 방향 불일치(백업 복원·시계 오차)도 무효화한다(리뷰 P3-6)")
    void anyMismatchInvalidatesRegardlessOfDirection() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID))
                .thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE, PW_CHANGED_AT.minusMinutes(10)));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
    }

    private MockHttpServletRequest requestWithToken(String snapshotToken) {
        SessionUser snap = new SessionUser(USER_ID, TENANT_ID, "ACME", "에이크미", "ta@acme.co.kr",
                "김관리", Role.TENANT_ADMIN, PW_CHANGED_AT, snapshotToken);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_KEY, snap);
        session.setAttribute(LocaleConfig.SESSION_LANG_KEY, "JPN");
        request.setSession(session);
        return request;
    }

    @Test
    @DisplayName("SES-03(U): 다른 기기에서 새 로그인(세션 토큰 불일치) → 이전 세션 즉시 무효화")
    void supersededSessionInvalidates() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID))
                .thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE, PW_CHANGED_AT, "token-NEW"));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = requestWithToken("token-OLD");

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
        assertThat(request.getSession(false).getAttribute(LocaleConfig.SESSION_LANG_KEY)).isEqualTo("JPN");
    }

    @Test
    @DisplayName("SES-04(U): 세션 토큰 일치(같은 기기) → 세션 유지")
    void matchingTokenKeepsSession() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID))
                .thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE, PW_CHANGED_AT, "token-SAME"));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = requestWithToken("token-SAME");

        assertThat(interceptor().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        SessionUser kept =
                (SessionUser) request.getSession(false).getAttribute(SessionUser.SESSION_KEY);
        assertThat(kept.sessionToken()).isEqualTo("token-SAME");
    }

    @Test
    @DisplayName("password_changed_at이 NULL(이력 없음 — 기존 유저)이고 스냅샷도 NULL이면 세션 유지")
    void nullPasswordChangedAtKeepsSession() {
        SessionUser legacySnapshot = new SessionUser(USER_ID, TENANT_ID, "ACME", "에이크미",
                "ta@acme.co.kr", "김관리", Role.TENANT_ADMIN, null, null);
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID))
                .thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE, null));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();
        request.getSession(false).setAttribute(SessionUser.SESSION_KEY, legacySnapshot);

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isEqualTo(legacySnapshot);
    }

    @Test
    @DisplayName("SES-02: 재검증의 role 갱신 경로는 원래 passwordChangedAt 스냅샷을 보존한다")
    void roleRefreshPreservesPasswordChangedAt() {
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.MEMBER, UserStatus.ACTIVE));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        SessionUser refreshed =
                (SessionUser) request.getSession(false).getAttribute(SessionUser.SESSION_KEY);
        assertThat(refreshed.passwordChangedAt()).isEqualTo(PW_CHANGED_AT);
    }

    @Test
    @DisplayName("REV-05: 다른 테넌트의 서브도메인으로 온 세션은 즉시 무효화(쿠키 이식 차단)")
    void crossTenantHostInvalidatesSession() {
        //세션은 ACME(tenantId=10)인데 요청 호스트는 BETA 서브도메인
        when(tenantMapper.findByCode("BETA")).thenReturn(
                new Tenant(20L, "BETA", "베타(주)", "KR", TenantStatus.ACTIVE, LocalDateTime.now()));
        MockHttpServletRequest request = loggedInRequest();
        request.setServerName("beta.webatt.example");

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isNull();
        assertThat(request.getSession(false).getAttribute(LocaleConfig.SESSION_LANG_KEY)).isEqualTo("JPN");
    }

    @Test
    @DisplayName("자기 테넌트의 서브도메인으로 온 세션은 유효(호스트 일치)")
    void matchingTenantHostPasses() {
        when(tenantMapper.findByCode("ACME")).thenReturn(dbTenant(TenantStatus.ACTIVE));
        when(userMapper.findRevalidationState(TENANT_ID, USER_ID)).thenReturn(dbState(Role.TENANT_ADMIN, UserStatus.ACTIVE));
        when(tenantMapper.findById(TENANT_ID)).thenReturn(dbTenant(TenantStatus.ACTIVE));
        MockHttpServletRequest request = loggedInRequest();
        request.setServerName("acme.webatt.example");

        interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getSession(false).getAttribute(SessionUser.SESSION_KEY)).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("세션 없는 요청(비로그인/공개 API)은 DB 조회 없이 통과")
    void anonymousRequestPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(interceptor().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getSession(false)).isNull();
    }

}
