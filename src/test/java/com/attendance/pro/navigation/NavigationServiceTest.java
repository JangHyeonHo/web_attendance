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

/**
 * 서버 주도 화면 전개 결정 규칙 테스트.
 */
@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    @Mock
    private LanguageService languageService;

    @Mock
    private AttendanceService attendanceService;

    private NavigationService service() {
        return new NavigationService(languageService, attendanceService);
    }

    private static final SessionUser USER = new SessionUser(1L, "hong@example.com", "홍길동", false);
    private static final SessionUser ADMIN = new SessionUser(2L, "admin@attendance.local", "관리자", true);

    @Test
    @DisplayName("미로그인: 공개 화면은 그대로, 보호 화면은 로그인으로")
    void anonymous() {
        NavigationService service = service();
        assertThat(service.decide("W000", null)).isEqualTo(new Decision(Screen.INDEX, null, false));
        assertThat(service.decide("W001", null)).isEqualTo(new Decision(Screen.LOGIN, null, false));
        assertThat(service.decide("W003", null)).isEqualTo(new Decision(Screen.SIGNUP, null, false));
        assertThat(service.decide("W005", null))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W006", null))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
        assertThat(service.decide("W004", null))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false));
    }

    @Test
    @DisplayName("로그인 유저: 로그인/가입/홈 요청은 출결 화면으로, 관리자 화면은 차단")
    void loggedInUser() {
        NavigationService service = service();
        assertThat(service.decide("W001", USER))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W003", USER))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W000", USER))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
        assertThat(service.decide("W005", USER)).isEqualTo(new Decision(Screen.ATTENDANCE, null, false));
        assertThat(service.decide("W006", USER)).isEqualTo(new Decision(Screen.ATT_DETAILS, null, false));
        assertThat(service.decide("W004", USER))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ADMIN_ONLY, false));
    }

    @Test
    @DisplayName("관리자: 관리자 화면 접근 가능, 홈 요청은 관리자 화면으로")
    void admin() {
        NavigationService service = service();
        assertThat(service.decide("W004", ADMIN)).isEqualTo(new Decision(Screen.ADMIN, null, false));
        assertThat(service.decide("W001", ADMIN))
                .isEqualTo(new Decision(Screen.ADMIN, NavigationReason.ALREADY_LOGGED_IN, false));
    }

    @Test
    @DisplayName("로그아웃 요청: 세션 무효화 플래그와 함께 로그인 화면으로")
    void logout() {
        NavigationService service = service();
        assertThat(service.decide("W002", USER))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, true));
        //미로그인 상태의 로그아웃은 무효화할 세션이 없음
        assertThat(service.decide("W002", null))
                .isEqualTo(new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, false));
    }

    @Test
    @DisplayName("알 수 없는 화면 코드: 미로그인은 인덱스, 로그인 유저는 홈 화면으로")
    void unknownScreen() {
        NavigationService service = service();
        assertThat(service.decide("W777", null))
                .isEqualTo(new Decision(Screen.INDEX, NavigationReason.UNKNOWN_SCREEN, false));
        assertThat(service.decide(null, null)).isEqualTo(new Decision(Screen.INDEX, null, false));
        assertThat(service.decide("W777", USER))
                .isEqualTo(new Decision(Screen.ATTENDANCE, NavigationReason.ALREADY_LOGGED_IN, false));
    }

}
