package com.attendance.pro.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.attendance.AttendanceService;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.language.LanguageService;
import com.attendance.pro.navigation.NavigationDtos.NavigationReason;
import com.attendance.pro.navigation.NavigationService.Decision;
import com.attendance.pro.setting.UiThemeService;
import com.attendance.pro.user.Role;

/**
 * 서버 주도 화면 전개 결정 규칙 테스트(role 3종 × 화면 매트릭스).
 * 케이스 ID: test-plan §2-2 NAV-01~07.
 */
@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    @Mock
    private LanguageService languageService;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private UiThemeService uiThemeService;

    private NavigationService service() {
        return new NavigationService(languageService, attendanceService, uiThemeService);
    }

    private static final SessionUser MEMBER =
            new SessionUser(1L, 10L, "ACME", "에이크미(주)", "hong@example.com", "홍길동", Role.MEMBER,
                    java.time.LocalDateTime.now());
    private static final SessionUser TENANT_ADMIN =
            new SessionUser(2L, 10L, "ACME", "에이크미(주)", "ta@acme.co.kr", "김관리", Role.TENANT_ADMIN,
                    java.time.LocalDateTime.now());
    private static final SessionUser HR_ADMIN =
            new SessionUser(4L, 10L, "ACME", "에이크미(주)", "hr@acme.co.kr", "이인사", Role.HR_ADMIN,
                    java.time.LocalDateTime.now());
    private static final SessionUser SYSTEM_ADMIN =
            new SessionUser(3L, 1L, "DEFAULT", "기본 테넌트", "admin@attendance.local", "관리자", Role.SYSTEM_ADMIN,
                    java.time.LocalDateTime.now());

    @Test
    @DisplayName("미로그인: 공개 화면은 그대로, 보호 화면은 로그인으로")
    void anonymous() {
        NavigationService service = service();
        assertThat(service.decide("W000", null, false)).isEqualTo(new Decision(Screen.INDEX, null, false));
        assertThat(service.decide("W001", null, false)).isEqualTo(new Decision(Screen.LOGIN, null, false));
        assertThat(service.decide("W004", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W005", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W006", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W007", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W008", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W009", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
    }

    @Test
    @DisplayName("NAV-06: W003(폐기된 가입 화면)은 미지 코드로 처리 — 미로그인은 인덱스로")
    void signupScreenRemoved() {
        NavigationService service = service();
        //Screen 레지스트리에서 SIGNUP이 제거되어 fromCode는 null
        assertThat(Screen.fromCode("W003")).isNull();
        assertThat(service.decide("W003", null, false))
                .isEqualTo(new Decision(Screen.INDEX, NavigationReason.UNKNOWN_SCREEN, false));
        assertThat(service.decide("W003", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
    }

    @Test
    @DisplayName("MEMBER: 홈은 출결(W005), 출결/상세 접근 가능")
    void memberAllowedScreens() {
        NavigationService service = service();
        assertThat(service.decide("W001", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W000", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W005", MEMBER, false)).isEqualTo(new Decision(Screen.ATTENDANCE, null, false));
        assertThat(service.decide("W006", MEMBER, false)).isEqualTo(new Decision(Screen.ATT_DETAILS, null, false));
    }

    @Test
    @DisplayName("NAV-01: MEMBER가 W004 요청 → 홈(W005) + ROLE_DENIED")
    void nav01MemberDeniedAdmin() {
        assertThat(service().decide("W004", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("NAV-07: MEMBER가 W009(멤버 관리) 요청 → 홈(W005) + ROLE_DENIED")
    void nav07MemberDeniedMembers() {
        assertThat(service().decide("W009", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("NAV-02: TENANT_ADMIN이 W004 요청 → 자기 홈(W005) + ROLE_DENIED")
    void nav02TenantAdminDeniedAdmin() {
        assertThat(service().decide("W004", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("NAV-04: TENANT_ADMIN이 W007/W008 요청 → 자기 홈(W005) + ROLE_DENIED")
    void nav04TenantAdminDeniedSystemScreens() {
        NavigationService service = service();
        assertThat(service.decide("W007", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W008", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("TENANT_ADMIN: 홈은 출결(W005), 멤버 관리(W009)는 접근 가능")
    void tenantAdminAllowedScreens() {
        NavigationService service = service();
        assertThat(service.decide("W001", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W005", TENANT_ADMIN, false)).isEqualTo(new Decision(Screen.ATTENDANCE, null, false));
        assertThat(service.decide("W006", TENANT_ADMIN, false)).isEqualTo(new Decision(Screen.ATT_DETAILS, null, false));
        assertThat(service.decide("W009", TENANT_ADMIN, false)).isEqualTo(new Decision(Screen.MEMBERS, null, false));
    }

    @Test
    @DisplayName("NAV-03: SYSTEM_ADMIN은 W004 허용, 홈 요청은 테넌트 목록(W007)으로")
    void nav03SystemAdminHome() {
        NavigationService service = service();
        assertThat(service.decide("W004", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.ADMIN, null, false));
        assertThat(service.decide("W001", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W000", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W007", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.SYSTEM_TENANTS, null, false));
        assertThat(service.decide("W008", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.TENANT_DETAIL, null, false));
    }

    @Test
    @DisplayName("NAV-05: SYSTEM_ADMIN이 W005/W006/W009 요청 → 자기 홈(W007) + ROLE_DENIED")
    void nav05SystemAdminDeniedTenantScreens() {
        NavigationService service = service();
        assertThat(service.decide("W005", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W006", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W009", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("W010/W011(공개): 미로그인은 물론 로그인 중에도 전개 허용(메일 링크는 기존 세션과 무관)")
    void passwordScreensArePublicEvenWhenLoggedIn() {
        NavigationService service = service();
        assertThat(service.decide("W010", null, false)).isEqualTo(new Decision(Screen.PASSWORD_SETUP, null, false));
        assertThat(service.decide("W011", null, false)).isEqualTo(new Decision(Screen.PASSWORD_RESET, null, false));
        //로그인 상태의 공개 화면 홈 리다이렉트는 LOGIN/INDEX만 — W010/W011은 그대로 전개
        assertThat(service.decide("W010", MEMBER, false)).isEqualTo(new Decision(Screen.PASSWORD_SETUP, null, false));
        assertThat(service.decide("W011", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.PASSWORD_RESET, null, false));
        //테넌트 서브도메인에서도 전개 허용
        assertThat(service.decide("W010", null, true)).isEqualTo(new Decision(Screen.PASSWORD_SETUP, null, false));
    }

    @Test
    @DisplayName("W012(메일 템플릿): SYSTEM_ADMIN만 — 그 외는 홈 + ROLE_DENIED")
    void mailTemplatesScreenRoleGate() {
        NavigationService service = service();
        assertThat(service.decide("W012", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.MAIL_TEMPLATES, null, false));
        assertThat(service.decide("W012", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W012", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W012", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
    }

    @Test
    @DisplayName("W017(감사 로그): SYSTEM_ADMIN만 — 그 외는 홈 + ROLE_DENIED")
    void auditScreenRoleGate() {
        NavigationService service = service();
        assertThat(service.decide("W017", SYSTEM_ADMIN, false)).isEqualTo(new Decision(Screen.AUDIT, null, false));
        assertThat(service.decide("W017", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W017", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("W013(공휴일): TENANT_ADMIN만 — MEMBER/SYSTEM_ADMIN은 홈 + ROLE_DENIED")
    void holidaysScreenRoleGate() {
        NavigationService service = service();
        assertThat(service.decide("W013", TENANT_ADMIN, false)).isEqualTo(new Decision(Screen.HOLIDAYS, null, false));
        assertThat(service.decide("W013", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W013", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("HR_ADMIN(인사관리자): 홈=출결, 멤버(W009)·공휴일(W013)·출결(W005/W006) 허용")
    void hrAdminAllowedScreens() {
        NavigationService service = service();
        assertThat(service.decide("W000", HR_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W005", HR_ADMIN, false)).isEqualTo(new Decision(Screen.ATTENDANCE, null, false));
        assertThat(service.decide("W006", HR_ADMIN, false)).isEqualTo(new Decision(Screen.ATT_DETAILS, null, false));
        assertThat(service.decide("W009", HR_ADMIN, false)).isEqualTo(new Decision(Screen.MEMBERS, null, false));
        assertThat(service.decide("W013", HR_ADMIN, false)).isEqualTo(new Decision(Screen.HOLIDAYS, null, false));
        assertThat(service.decide("W015", HR_ADMIN, false)).isEqualTo(new Decision(Screen.LEAVE, null, false));
        assertThat(service.decide("W016", HR_ADMIN, false))
                .isEqualTo(new Decision(Screen.LEAVE_ADMIN, null, false));
    }

    @Test
    @DisplayName("휴가 화면: 멤버는 W015(휴가) 허용·W016(휴가 관리)은 홈+ROLE_DENIED")
    void leaveScreensAccess() {
        NavigationService service = service();
        assertThat(service.decide("W015", MEMBER, false)).isEqualTo(new Decision(Screen.LEAVE, null, false));
        assertThat(service.decide("W015", TENANT_ADMIN, false)).isEqualTo(new Decision(Screen.LEAVE, null, false));
        assertThat(service.decide("W016", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W016", TENANT_ADMIN, false))
                .isEqualTo(new Decision(Screen.LEAVE_ADMIN, null, false));
    }

    @Test
    @DisplayName("HR_ADMIN 배제: 회사 메일 템플릿(W014)·언어 마스터(W004)는 홈 + ROLE_DENIED")
    void hrAdminDeniedScreens() {
        NavigationService service = service();
        assertThat(service.decide("W014", HR_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
        assertThat(service.decide("W004", HR_ADMIN, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ROLE_DENIED, false));
    }

    @Test
    @DisplayName("로그아웃 요청: 세션 무효화 플래그와 함께 로그인 화면으로")
    void logout() {
        NavigationService service = service();
        assertThat(service.decide("W002", MEMBER, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, true));
        //미로그인 상태의 로그아웃은 무효화할 세션이 없음
        assertThat(service.decide("W002", null, false))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, false));
    }

    @Test
    @DisplayName("테넌트 서브도메인 접속: 비로그인 기본/랜딩/미지 코드가 전부 로그인 화면으로")
    void tenantHostAnonymousGoesToLogin() {
        NavigationService service = service();
        //초기 진입(null)과 랜딩(W000) 요청 모두 그 회사의 입구 = 로그인
        assertThat(service.decide(null, null, true)).isEqualTo(new Decision(Screen.LOGIN, null, false));
        assertThat(service.decide("W000", null, true)).isEqualTo(new Decision(Screen.LOGIN, null, false));
        assertThat(service.decide("W777", null, true))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.UNKNOWN_SCREEN, false));
        //보호 화면은 기존 규칙 그대로
        assertThat(service.decide("W005", null, true))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
    }

    @Test
    @DisplayName("테넌트 서브도메인 접속: 로그인 유저의 규칙은 루트 도메인과 동일(홈/허용 화면)")
    void tenantHostLoggedInUnchanged() {
        NavigationService service = service();
        assertThat(service.decide(null, MEMBER, true))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W005", MEMBER, true))
                .isEqualTo(new Decision(Screen.ATTENDANCE, null, false));
        assertThat(service.decide("W002", MEMBER, true))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, true));
    }

    @Test
    @DisplayName("알 수 없는 화면 코드: 미로그인은 인덱스, 로그인 유저는 홈 화면으로")
    void unknownScreen() {
        NavigationService service = service();
        assertThat(service.decide("W777", null, false))
                .isEqualTo(new Decision(Screen.INDEX, NavigationReason.UNKNOWN_SCREEN, false));
        assertThat(service.decide(null, null, false)).isEqualTo(new Decision(Screen.INDEX, null, false));
        assertThat(service.decide("W777", MEMBER, false))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W777", SYSTEM_ADMIN, false))
                .isEqualTo(new Decision(Screen.SYSTEM_TENANTS, NavigationReason.ALREADY_LOGGED_IN, false));
    }

}
