package com.attendance.pro.navigation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.navigation.NavigationDtos.NavigateRequest;
import com.attendance.pro.navigation.NavigationDtos.NavigateResponse;
import com.attendance.pro.navigation.NavigationService.Decision;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 서버 주도 화면 전개 API.
 * 프론트(SPA)는 URL 라우팅 없이 이 엔드포인트가 돌려주는 화면 코드로만 화면을 전환한다.
 */
@Tag(name = "Navigation", description = "서버 주도 화면 전개 API (URL 없이 화면 코드로 전환)")
@RestController
public class NavigationController {

    /** 세션에 저장하는 언어 설정 키 */
    static final String SESSION_LANG_KEY = "LANG";
    private static final String DEFAULT_LANG = "KOR";

    private final NavigationService navigationService;

    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @Operation(summary = "화면 전개",
            description = """
                    요청한 화면 코드(W000~)에 대해 서버가 세션 상태(로그인/권한)를 보고
                    '실제로 표시할 화면'을 결정하여 화면 코드 + 다국어 텍스트 + 초기 데이터를 돌려준다.
                    - 요청과 다른 화면이 결정되면 reason에 사유가 들어간다.
                    - W002(로그아웃) 요청은 세션을 무효화하고 로그인 화면을 돌려준다.
                    - lang을 지정하면 세션에 저장되어 이후 요청에도 적용된다.""")
    @PostMapping("/api/v1/navigation")
    public NavigateResponse navigate(@Valid @RequestBody NavigateRequest request, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);

        String lang = resolveLang(request.lang(), httpRequest);

        Decision decision = navigationService.decide(request.screen(), user);
        //로그아웃 결정이면 세션 무효화
        if (decision.logout() && session != null) {
            session.invalidate();
            user = null;
        }
        return navigationService.assemble(decision, user, lang);
    }

    /**
     * 언어 결정: 요청값 > 세션 저장값 > 기본값(KOR). 요청값은 세션에 저장해 이후 요청에도 적용한다.
     */
    private String resolveLang(String requestedLang, HttpServletRequest httpRequest) {
        if (requestedLang != null) {
            httpRequest.getSession(true).setAttribute(SESSION_LANG_KEY, requestedLang);
            return requestedLang;
        }
        HttpSession session = httpRequest.getSession(false);
        Object saved = session == null ? null : session.getAttribute(SESSION_LANG_KEY);
        return saved == null ? DEFAULT_LANG : String.valueOf(saved);
    }

}
