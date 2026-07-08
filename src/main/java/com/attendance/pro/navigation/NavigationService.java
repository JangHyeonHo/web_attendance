package com.attendance.pro.navigation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.attendance.pro.attendance.AttendanceService;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.language.LanguageService;
import com.attendance.pro.navigation.NavigationDtos.NavigateResponse;
import com.attendance.pro.navigation.NavigationDtos.NavigationReason;

/**
 * 서버 주도 화면 전개 서비스.
 * 프론트는 URL 라우팅 없이 이 API의 결정만으로 화면을 전환한다(v1의 화면 전개 컨셉 계승).
 *
 * 결정 규칙(v1 RootController.loginAuth 계승):
 * <ul>
 *   <li>로그인 필요 화면 + 미로그인 → 로그인 화면</li>
 *   <li>관리자 화면 + 비관리자 → 미로그인이면 로그인, 일반 유저면 출결 화면</li>
 *   <li>로그인 상태에서 로그인/가입/홈 요청 → 관리자는 관리자, 일반 유저는 출결 화면</li>
 *   <li>로그아웃 요청 → (컨트롤러에서 세션 무효화 후) 로그인 화면</li>
 *   <li>알 수 없는 화면 코드 → 인덱스</li>
 * </ul>
 */
@Service
public class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final LanguageService languageService;
    private final AttendanceService attendanceService;

    public NavigationService(LanguageService languageService, AttendanceService attendanceService) {
        this.languageService = languageService;
        this.attendanceService = attendanceService;
    }

    /**
     * 화면 전개 결정 결과.
     *
     * @param target 실제 표시할 화면
     * @param reason 요청과 다른 화면이 된 사유(그대로면 null)
     * @param logout 세션을 무효화해야 하는지(로그아웃 요청)
     */
    public record Decision(Screen target, NavigationReason reason, boolean logout) {
    }

    /**
     * 요청 화면과 로그인 상태로 실제 표시할 화면을 결정한다. (순수 로직 - 단위 테스트 대상)
     */
    public Decision decide(String requestedCode, SessionUser user) {
        Screen requested = Screen.fromCode(requestedCode);
        boolean loggedIn = user != null;

        //알 수 없는(또는 미지정) 화면 코드는 인덱스로
        if (requested == null || requested == Screen.COMMON) {
            //로그인 상태라면 인덱스 대신 각자의 홈 화면으로
            if (loggedIn) {
                return new Decision(homeOf(user), NavigationReason.ALREADY_LOGGED_IN, false);
            }
            return new Decision(Screen.INDEX,
                    requestedCode == null ? null : NavigationReason.UNKNOWN_SCREEN, false);
        }

        //로그아웃 요청: 세션 무효화 후 로그인 화면으로
        if (requested == Screen.LOGOUT) {
            return new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, loggedIn);
        }

        //접근 레벨 검사
        switch (requested.access()) {
        case LOGIN_REQUIRED:
            if (!loggedIn) {
                return new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false);
            }
            return new Decision(requested, null, false);
        case ADMIN_ONLY:
            if (!loggedIn) {
                return new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false);
            }
            if (!user.admin()) {
                return new Decision(Screen.ATTENDANCE, NavigationReason.ADMIN_ONLY, false);
            }
            return new Decision(requested, null, false);
        case PUBLIC:
        default:
            //로그인 상태에서 로그인/가입/홈을 요청하면 각자의 홈 화면으로
            if (loggedIn && (requested == Screen.LOGIN || requested == Screen.SIGNUP || requested == Screen.INDEX)) {
                return new Decision(homeOf(user), NavigationReason.ALREADY_LOGGED_IN, false);
            }
            return new Decision(requested, null, false);
        }
    }

    /**
     * 로그인 유저의 홈 화면(관리자는 관리자 화면, 일반 유저는 출결 화면).
     */
    private Screen homeOf(SessionUser user) {
        return user.admin() ? Screen.ADMIN : Screen.ATTENDANCE;
    }

    /**
     * 결정된 화면의 응답(다국어 텍스트 + 화면 초기 데이터)을 조립한다.
     */
    public NavigateResponse assemble(Decision decision, SessionUser user, String lang) {
        Screen target = decision.target();
        Object data = loadScreenData(target, user);
        return new NavigateResponse(
                target.code(),
                decision.reason(),
                user == null ? null : user.name(),
                languageService.texts(target.code(), lang),
                languageService.texts(Screen.COMMON.code(), lang),
                data);
    }

    /**
     * 화면별 초기 데이터. v1의 "화면 전개시 데이터 동봉" 컨셉 계승.
     */
    private Object loadScreenData(Screen target, SessionUser user) {
        if (target == Screen.ATTENDANCE && user != null) {
            try {
                return attendanceService.status(user.userId());
            } catch (Exception e) {
                //초기 데이터 취득 실패가 화면 전개 자체를 막지 않도록 한다
                log.error("attendance status load failed on navigation", e);
            }
        }
        return null;
    }

}
