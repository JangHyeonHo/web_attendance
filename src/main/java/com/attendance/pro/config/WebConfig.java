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

/**
 * MVC 설정(인증/인가 인터셉터, 로그인 유저 주입, CORS).
 * 인증은 authInterceptor 단일 책임, 인가는 roleInterceptor(경로별 허용 role 화이트리스트) 단일 책임.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final List<String> corsAllowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
            RoleInterceptor roleInterceptor,
            LoginUserArgumentResolver loginUserArgumentResolver,
            @Value("${app.cors.allowed-origins:}") List<String> corsAllowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //로그인 필수 API (공개 3종: 로그인/화면 텍스트 조회/화면 전개만 제외)
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/i18n/**",
                        "/api/v1/navigation");
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
