package com.attendance.pro.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.attendance.pro.auth.AdminInterceptor;
import com.attendance.pro.auth.AuthInterceptor;
import com.attendance.pro.auth.LoginUserArgumentResolver;

/**
 * MVC 설정(인증 인터셉터, 로그인 유저 주입, CORS).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final List<String> corsAllowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
            AdminInterceptor adminInterceptor,
            LoginUserArgumentResolver loginUserArgumentResolver,
            @Value("${app.cors.allowed-origins:}") List<String> corsAllowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //로그인 필수 API (로그인/회원가입/화면 텍스트 조회는 제외)
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/users",
                        "/api/v1/i18n/**",
                        "/api/v1/admin/**");
        //관리자 전용 API
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/v1/admin/**");
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
