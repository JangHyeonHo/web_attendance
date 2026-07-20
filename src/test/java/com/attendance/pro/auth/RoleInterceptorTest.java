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
                    java.time.LocalDateTime.now(), null);
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
            //자사 청구서 — 총관리자 전용(재무 정보, 인사관리자·SYSTEM_ADMIN 배제)
            "/api/v1/tenant/billing/invoices,   TENANT_ADMIN, allow",
            "/api/v1/tenant/billing/invoices,   HR_ADMIN,     deny",
            "/api/v1/tenant/billing/invoices,   MEMBER,       deny",
            "/api/v1/tenant/billing/invoices,   SYSTEM_ADMIN, deny",
            //회사 청구서 마감·조회는 운영사 전용(system/** 규칙)
            "/api/v1/system/tenants/1/invoices, SYSTEM_ADMIN, allow",
            "/api/v1/system/tenants/1/invoices/2026-07/close, SYSTEM_ADMIN, allow",
            "/api/v1/system/tenants/1/invoices, TENANT_ADMIN, deny",
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
    @DisplayName("규칙에 없는 경로는 fail-closed로 거부(403) — 이 인터셉터는 role 게이트 프리픽스에만 등록되므로 미매칭=설정오류")
    void unmatchedPathDeniedFailClosed() {
        //auth/me·navigation 등은 WebConfig에서 이 인터셉터에 등록되지 않아 실제로는 도달하지 않는다.
        //만약 등록 프리픽스가 규칙 없이 확장되면, 통과가 아니라 거부되어야 한다(권한 우회 방지).
        MockHttpServletRequest request = request("/api/v1/unmapped/whatever", Role.SYSTEM_ADMIN);
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    @DisplayName("컨텍스트 패스가 있어도 규칙이 정상 매칭된다(getRequestURI 대신 컨텍스트 제외 경로)")
    void contextPathStrippedForMatching() {
        //컨텍스트 패스 '/app'이 붙은 요청 — SYSTEM_ADMIN 전용 경로에 MEMBER 접근은 여전히 거부되어야 한다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/v1/system/tenants");
        request.setContextPath("/app");
        SessionUser member = new SessionUser(1L, 1L, "ACME", "에이크미(주)", "u@acme.co.kr", "유저", Role.MEMBER,
                java.time.LocalDateTime.now(), null);
        request.getSession(true).setAttribute(SessionUser.SESSION_KEY, member);
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
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
