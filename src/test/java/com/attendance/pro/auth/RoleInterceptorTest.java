package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.Role;

/**
 * 경로별 허용 role 화이트리스트 인가 테스트.
 * 케이스 ID: test-plan §2-1 ROLE-02~05, 09~15 매트릭스의 인터셉터 단위판(파라미터라이즈드).
 */
class RoleInterceptorTest {

    private final RoleInterceptor interceptor = new RoleInterceptor();

    private MockHttpServletRequest request(String uri, Role role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (role != null) {
            SessionUser user = new SessionUser(1L, 1L, "ACME", "에이크미(주)", "u@acme.co.kr", "유저", role);
            request.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        }
        return request;
    }

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @CsvSource({
            //system/** — SYSTEM_ADMIN만
            "/api/v1/system/tenants,            SYSTEM_ADMIN, allow",
            "/api/v1/system/tenants,            TENANT_ADMIN, deny",
            "/api/v1/system/tenants,            MEMBER,       deny",
            "/api/v1/system/tenants/1/profile,  SYSTEM_ADMIN, allow",
            "/api/v1/system/tenants/1/profile,  TENANT_ADMIN, deny",
            "/api/v1/system/tenants/1/billing,  MEMBER,       deny",
            //admin/i18n/** — SYSTEM_ADMIN만(TENANT_ADMIN도 403 — 언어 마스터는 글로벌 제품 자산)
            "/api/v1/admin/i18n,                SYSTEM_ADMIN, allow",
            "/api/v1/admin/i18n,                TENANT_ADMIN, deny",
            "/api/v1/admin/i18n,                MEMBER,       deny",
            //tenant/** — TENANT_ADMIN만(SYSTEM_ADMIN도 403)
            "/api/v1/tenant/members,            TENANT_ADMIN, allow",
            "/api/v1/tenant/members,            SYSTEM_ADMIN, deny",
            "/api/v1/tenant/members,            MEMBER,       deny",
            "/api/v1/tenant/members/5/role,     TENANT_ADMIN, allow",
            "/api/v1/tenant/members/5/status,   SYSTEM_ADMIN, deny",
            //attendance/** — TENANT_ADMIN|MEMBER(SYSTEM_ADMIN 명시 배제 — ISO-11/ROLE-03~05)
            "/api/v1/attendance/status,         MEMBER,       allow",
            "/api/v1/attendance/status,         TENANT_ADMIN, allow",
            "/api/v1/attendance/status,         SYSTEM_ADMIN, deny",
            "/api/v1/attendance/check,          SYSTEM_ADMIN, deny",
            "/api/v1/attendance,                SYSTEM_ADMIN, deny",
            "/api/v1/attendance/monthly,        SYSTEM_ADMIN, deny",
            //규칙에 없는 인증 필수 경로는 role 무관 통과
            "/api/v1/auth/me,                   MEMBER,       allow",
            "/api/v1/auth/me,                   SYSTEM_ADMIN, allow",
            "/api/v1/navigation,                TENANT_ADMIN, allow",
    })
    @DisplayName("경로 × role 화이트리스트 매트릭스")
    void whitelistMatrix(String uri, Role role, String expected) {
        MockHttpServletRequest request = request(uri, role);
        MockHttpServletResponse response = new MockHttpServletResponse();
        if ("allow".equals(expected)) {
            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        } else {
            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(403);
                        assertThat(apiException.getCode()).isEqualTo("FORBIDDEN");
                    });
        }
    }

    @Test
    @DisplayName("미로그인은 role 검사 이전에 401")
    void unauthenticated() {
        MockHttpServletRequest request = request("/api/v1/system/tenants", null);
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));
    }

}
