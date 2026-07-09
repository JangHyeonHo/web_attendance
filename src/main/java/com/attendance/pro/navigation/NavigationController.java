package com.attendance.pro.navigation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.navigation.NavigationDtos.NavigateRequest;
import com.attendance.pro.navigation.NavigationDtos.NavigateResponse;
import com.attendance.pro.navigation.NavigationService.Decision;
import com.attendance.pro.tenant.TenantHostResolver;
import com.attendance.pro.tenant.TenantHostResolver.HostTenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 서버 주도 화면 전개 API.
 * 프론트(SPA)는 URL 라우팅 없이 이 엔드포인트가 돌려주는 화면 코드로만 화면을 전환한다.
 */
@Tag(name = "Navigation", description = "api.navigation.tag")
@RestController
public class NavigationController {

    /** 세션에 저장하는 언어 설정 키(LocaleResolver와 공유) */
    private static final String SESSION_LANG_KEY = com.attendance.pro.config.LocaleConfig.SESSION_LANG_KEY;
    private static final String DEFAULT_LANG = "KOR";

    private final NavigationService navigationService;
    private final TenantHostResolver tenantHostResolver;

    public NavigationController(NavigationService navigationService, TenantHostResolver tenantHostResolver) {
        this.navigationService = navigationService;
        this.tenantHostResolver = tenantHostResolver;
    }

    @Operation(summary = "api.navigation.navigate.summary", description = "api.navigation.navigate.description")
    @PostMapping("/api/v1/navigation")
    public NavigateResponse navigate(@Valid @RequestBody NavigateRequest request, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        SessionUser user = session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);

        String lang = resolveLang(request.lang(), httpRequest);
        //서브도메인/코드 병행: 테넌트 서브도메인이면 비로그인 기본 화면이 로그인이 된다
        HostTenant hostTenant = tenantHostResolver.resolve(httpRequest);

        Decision decision = navigationService.decide(request.screen(), user, hostTenant.claimsTenant());
        //로그아웃 결정이면 세션 무효화(언어 설정은 새 세션에 이어준다)
        if (decision.logout() && session != null) {
            session.invalidate();
            user = null;
            httpRequest.getSession(true).setAttribute(SESSION_LANG_KEY, lang);
        }
        String hostTenantName = hostTenant.tenant() == null ? null : hostTenant.tenant().name();
        return navigationService.assemble(decision, user, lang, hostTenantName);
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
