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
            SessionUser user = new SessionUser(1L, 1L, "ACME", "에이크미(주)", "u@acme.co.kr", "유저", role,
                    java.time.LocalDateTime.now());
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
            //admin/** — SYSTEM_ADMIN만(TENANT_ADMIN도 403 — admin 하위는 전부 글로벌 제품 자산, TPL-01)
            "/api/v1/admin/i18n,                SYSTEM_ADMIN, allow",
            "/api/v1/admin/i18n,                TENANT_ADMIN, deny",
            "/api/v1/admin/i18n,                MEMBER,       deny",
            "/api/v1/admin/mail-templates,      SYSTEM_ADMIN, allow",
            "/api/v1/admin/audit,               SYSTEM_ADMIN, allow",
            "/api/v1/admin/audit,               TENANT_ADMIN, deny",
            "/api/v1/admin/audit,               HR_ADMIN,     deny",
            "/api/v1/admin/audit,               MEMBER,       deny",
            "/api/v1/admin/mail-templates,      TENANT_ADMIN, deny",
            "/api/v1/admin/mail-templates,      MEMBER,       deny",
            "/api/v1/admin/mail-templates/INVITE/KOR, SYSTEM_ADMIN, allow",
            "/api/v1/admin/mail-templates/preview,    MEMBER,       deny",
            //tenant/** — 멤버·공휴일은 인사관리자+총관리자(SYSTEM_ADMIN도 403)
            "/api/v1/tenant/members,            TENANT_ADMIN, allow",
            "/api/v1/tenant/members,            HR_ADMIN,     allow",
            "/api/v1/tenant/members,            SYSTEM_ADMIN, deny",
            "/api/v1/tenant/members,            MEMBER,       deny",
            "/api/v1/tenant/holidays,           HR_ADMIN,     allow",
            "/api/v1/tenant/members/5/status,   HR_ADMIN,     allow",
            "/api/v1/tenant/members/5/status,   SYSTEM_ADMIN, deny",
            //역할 지정·회사 메일은 총관리자 전용(인사관리자 403 — 직권 분산 Phase 6)
            "/api/v1/tenant/members/5/role,     TENANT_ADMIN, allow",
            "/api/v1/tenant/members/5/role,     HR_ADMIN,     deny",
            "/api/v1/tenant/mail-templates,     TENANT_ADMIN, allow",
            "/api/v1/tenant/mail-templates,     HR_ADMIN,     deny",
            "/api/v1/tenant/mail-templates/INVITE/KOR, HR_ADMIN, deny",
            //휴가(관리자) — 인사관리자+총관리자(직권 분산 밖 — 인사 업무), SYSTEM_ADMIN 배제
            "/api/v1/tenant/leave/requests/pending, HR_ADMIN,     allow",
            "/api/v1/tenant/leave/requests/pending, TENANT_ADMIN, allow",
            "/api/v1/tenant/leave/requests/pending, MEMBER,       deny",
            "/api/v1/tenant/leave/types,        HR_ADMIN,     allow",
            "/api/v1/tenant/leave/members/5/recompute, HR_ADMIN, allow",
            //휴가(멤버) — 회사 구성원 전원, SYSTEM_ADMIN 배제
            "/api/v1/attendance/leave/balances, MEMBER,       allow",
            "/api/v1/attendance/leave/balances, HR_ADMIN,     allow",
            "/api/v1/attendance/leave/requests, TENANT_ADMIN, allow",
            "/api/v1/attendance/leave/balances, SYSTEM_ADMIN, deny",
            //attendance/** — 회사 구성원 전원(SYSTEM_ADMIN 명시 배제 — ISO-11/ROLE-03~05)
            "/api/v1/attendance/status,         MEMBER,       allow",
            "/api/v1/attendance/status,         HR_ADMIN,     allow",
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
