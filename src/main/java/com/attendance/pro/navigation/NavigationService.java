package com.attendance.pro.navigation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.attendance.pro.attendance.AttendanceService;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.language.LanguageService;
import com.attendance.pro.navigation.NavigationDtos.NavigateResponse;
import com.attendance.pro.navigation.NavigationDtos.NavigationReason;
import com.attendance.pro.setting.UiThemeService;

/**
 * 서버 주도 화면 전개 서비스.
 * 프론트는 URL 라우팅 없이 이 API의 결정만으로 화면을 전환한다(v1의 화면 전개 컨셉 계승).
 *
 * 결정 규칙:
 * <ul>
 *   <li>보호 화면 + 미로그인 → 로그인 화면</li>
 *   <li>허용 role 집합 미포함 → 각자의 홈 화면 + ROLE_DENIED</li>
 *   <li>로그인 상태에서 로그인/홈 요청 → 각자의 홈 화면(SYSTEM_ADMIN=A001, 그 외=M001)</li>
 *   <li>로그아웃 요청 → (컨트롤러에서 세션 무효화 후) 로그인 화면</li>
 *   <li>알 수 없는 화면 코드 → 인덱스(로그인 상태면 홈)</li>
 * </ul>
 */
@Service
public class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final LanguageService languageService;
    private final AttendanceService attendanceService;
    private final UiThemeService uiThemeService;

    public NavigationService(LanguageService languageService, AttendanceService attendanceService,
            UiThemeService uiThemeService) {
        this.languageService = languageService;
        this.attendanceService = attendanceService;
        this.uiThemeService = uiThemeService;
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
     * 요청 화면과 로그인 상태/role로 실제 표시할 화면을 결정한다. (순수 로직 - 단위 테스트 대상)
     *
     * @param onTenantHost 테넌트 서브도메인으로 접속했는가 — 그 회사의 입구이므로
     *                     비로그인 기본 화면이 랜딩(W000)이 아닌 로그인(W001)이 된다
     */
    public Decision decide(String requestedCode, SessionUser user, boolean onTenantHost) {
        Screen requested = Screen.fromCode(requestedCode);
        boolean loggedIn = user != null;
        //비로그인 기본 화면: 루트 도메인=랜딩, 테넌트 서브도메인=로그인
        Screen anonymousHome = onTenantHost ? Screen.LOGIN : Screen.INDEX;

        //알 수 없는(또는 미지정) 화면 코드는 기본 화면으로
        if (requested == null || requested == Screen.COMMON) {
            //로그인 상태라면 각자의 홈 화면으로
            if (loggedIn) {
                return new Decision(homeOf(user), NavigationReason.ALREADY_LOGGED_IN, false);
            }
            return new Decision(anonymousHome,
                    requestedCode == null ? null : NavigationReason.UNKNOWN_SCREEN, false);
        }

        //로그아웃 요청: 세션 무효화 후 로그인 화면으로
        if (requested == Screen.LOGOUT) {
            return new Decision(Screen.LOGIN, NavigationReason.LOGGED_OUT, loggedIn);
        }

        if (requested.isPublic()) {
            //로그인 상태에서 로그인/홈을 요청하면 각자의 홈 화면으로
            if (loggedIn && (requested == Screen.LOGIN || requested == Screen.INDEX)) {
                return new Decision(homeOf(user), NavigationReason.ALREADY_LOGGED_IN, false);
            }
            //테넌트 서브도메인에서 랜딩 요청은 로그인으로(랜딩은 루트 도메인 전용)
            if (!loggedIn && onTenantHost && requested == Screen.INDEX) {
                return new Decision(Screen.LOGIN, null, false);
            }
            return new Decision(requested, null, false);
        }
        if (!loggedIn) {
            return new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false);
        }
        if (!requested.allowed().contains(user.role())) {
            return new Decision(homeOf(user), NavigationReason.ROLE_DENIED, false); //허용 role 집합 미포함
        }
        return new Decision(requested, null, false);
    }

    /**
     * 로그인 유저의 홈 화면.
     * TENANT_ADMIN의 일상 업무는 출결(M001) — 멤버 관리(T001)는 헤더 메뉴로 진입.
     */
    private Screen homeOf(SessionUser user) {
        return switch (user.role()) {
        case SYSTEM_ADMIN -> Screen.SYSTEM_TENANTS;   //A001
        case TENANT_ADMIN -> Screen.ATTENDANCE;       //M001
        case HR_ADMIN -> Screen.ATTENDANCE;           //M001 — 인사관리자도 일상은 본인 출결
        case MEMBER -> Screen.ATTENDANCE;             //M001
        };
    }

    /**
     * 결정된 화면의 응답(다국어 텍스트 + 화면 초기 데이터)을 조립한다.
     *
     * @param hostTenantName 테넌트 서브도메인으로 접속한 경우 그 테넌트명(로그인 화면 브랜딩용), 아니면 null
     */
    public NavigateResponse assemble(Decision decision, SessionUser user, String lang, String hostTenantName) {
        Screen target = decision.target();
        Object data = loadScreenData(target, user);
        return new NavigateResponse(
                target.code(),
                lang,
                decision.reason(),
                user == null ? null : user.name(),
                user == null ? null : user.role(),
                hostTenantName,
                uiThemeService.resolved(), //확정 테마(AUTO 해석 완료) — 공개 화면(랜딩/로그인)도 테마 적용
                languageService.texts(target.code(), lang),
                languageService.texts(Screen.COMMON.code(), lang),
                data);
    }

    /**
     * 화면별 초기 데이터. v1의 "화면 전개시 데이터 동봉" 컨셉 계승.
     * A001/A002/T001는 초기 데이터 없이 시작(프론트가 각 API를 호출) — 화면 전개 응답 비대화 방지.
     */
    private Object loadScreenData(Screen target, SessionUser user) {
        if (target == Screen.ATTENDANCE && user != null) {
            try {
                return attendanceService.status(user.tenantId(), user.userId());
            } catch (Exception e) {
                //초기 데이터 취득 실패가 화면 전개 자체를 막지 않도록 한다
                log.error("attendance status load failed on navigation", e);
            }
        }
        return null;
    }

}
