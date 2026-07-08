package com.attendance.pro.config;

import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 개발환경용 CORS 설정.
 * 로컬 프론트엔드(localhost:3000)에서의 요청을 허용한다.
 *
 * 주의: 현재 {@code @Configuration}이 붙어있지 않아 비활성 상태다.
 * 로컬 개발시 프론트를 별도 포트로 띄울 경우에만 {@code @Configuration}을 붙여 활성화한다.
 * (운용 환경에는 넣지 않는다)
 */
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowCredentials(true).allowedOrigins("http://localhost:3000");
    }

}
