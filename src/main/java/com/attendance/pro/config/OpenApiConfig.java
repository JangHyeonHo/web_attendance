package com.attendance.pro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Swagger(OpenAPI) 문서 설정.
 * UI: /swagger-ui.html, 스펙: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Web Attendance API")
                .description("""
                        웹 출결 시스템 백엔드 API.

                        - 인증은 세션 쿠키 기반: `POST /api/v1/auth/login` 후 발급되는 세션 쿠키로 호출한다.
                        - 출결 등록은 2단계: `POST /api/v1/attendance/check`(사전 검사 + 토큰 발급) →
                          `POST /api/v1/attendance`(동일 데이터 + 토큰으로 확정, 변조 탐지).
                        - `/api/v1/admin/**`는 관리자 계정 전용.""")
                .version("v2.0.0"));
    }

}
