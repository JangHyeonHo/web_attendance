package com.attendance.pro.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.attendance.pro.auth.AuthInterceptor;
import com.attendance.pro.auth.LoginUserArgumentResolver;
import com.attendance.pro.auth.RoleInterceptor;
import com.attendance.pro.auth.SessionRevalidationInterceptor;
import com.attendance.pro.auth.SlidingSessionCookieInterceptor;

/**
 * MVC 설정(인증/인가 인터셉터, 로그인 유저 주입, CORS).
 * 세션 재검증 → 인증(authInterceptor) → 인가(roleInterceptor) 순으로 각자 단일 책임.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionRevalidationInterceptor sessionRevalidationInterceptor;
    private final SlidingSessionCookieInterceptor slidingSessionCookieInterceptor;
    private final AuthInterceptor authInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final List<String> corsAllowedOrigins;

    public WebConfig(SessionRevalidationInterceptor sessionRevalidationInterceptor,
            SlidingSessionCookieInterceptor slidingSessionCookieInterceptor,
            AuthInterceptor authInterceptor,
            RoleInterceptor roleInterceptor,
            LoginUserArgumentResolver loginUserArgumentResolver,
            @Value("${app.cors.allowed-origins:}") List<String> corsAllowedOrigins) {
        this.sessionRevalidationInterceptor = sessionRevalidationInterceptor;
        this.slidingSessionCookieInterceptor = slidingSessionCookieInterceptor;
        this.authInterceptor = authInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //세션 스냅샷 재검증(정지/비활성 → 세션 무효화, role 변경 → 스냅샷 갱신).
        //navigation 등 공개 API도 포함해 전 /api에 최우선 적용한다.
        registry.addInterceptor(sessionRevalidationInterceptor)
                .addPathPatterns("/api/**");
        //세션 쿠키 슬라이딩 갱신 — 재검증 뒤(회수된 세션은 갱신 대상 아님), 인증된 세션만 대상.
        registry.addInterceptor(slidingSessionCookieInterceptor)
                .addPathPatterns("/api/**");
        //로그인 필수 API (공개 4종: 로그인/화면 텍스트 조회/화면 전개/비밀번호 설정·재설정만 제외)
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/i18n/**",
                        "/api/v1/navigation",
                        "/api/v1/auth/password/**");
        //인가(경로별 허용 role 화이트리스트) — attendance 경로 등록 필수(SYSTEM_ADMIN 403)
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns(
                        "/api/v1/system/**",
                        "/api/v1/admin/**",
                        "/api/v1/tenant/**",
                        "/api/v1/attendance/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    /**
     * CORS는 프론트를 별도 포트/도메인으로 띄우는 환경에서만
     * app.cors.allowed-origins(콤마 구분)로 허용한다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsAllowedOrigins.isEmpty()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(corsAllowedOrigins.toArray(String[]::new))
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowCredentials(true);
        }
    }

}
