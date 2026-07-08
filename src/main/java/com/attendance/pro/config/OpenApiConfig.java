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
        //Info는 springdoc i18n(MessageSource) 해석 대상이 아니므로 3개 언어를 병기한다.
        //태그/오퍼레이션 설명은 메시지 키로 작성되어 요청 언어(ko/en/ja)에 따라 번역된다.
        return new OpenAPI().info(new Info()
                .title("Web Attendance API")
                .description("""
                        웹 출결 시스템 백엔드 API. 세션 쿠키 인증(`POST /api/v1/auth/login`). \
                        출결 등록은 체크 → 확정 2단계(토큰 + 변조 탐지). 언어는 Accept-Language 또는 navigation의 lang(KOR/ENG/JPN).

                        Web attendance backend API. Session-cookie auth. Attendance is registered in two steps \
                        (check → confirm with token + tamper detection). Language follows Accept-Language or navigation lang.

                        Web勤怠システムのバックエンドAPI。セッションクッキー認証。打刻はチェック → 確定の2段階（トークン + 改ざん検知）。\
                        言語はAccept-Languageまたはnavigationのlangに従う。""")
                .version("v2.0.0"));
    }

}
