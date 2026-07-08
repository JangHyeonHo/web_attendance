package com.attendance.pro.config;

import java.util.Locale;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Swagger(OpenAPI) 문서 설정.
 * UI: /swagger-ui.html, 스펙: /v3/api-docs
 *
 * i18n:
 * - 태그/오퍼레이션/응답 설명은 어노테이션에 메시지 키를 쓰면 springdoc이 요청 로케일로 해석한다.
 * - 스키마(@Schema) 설명은 springdoc 기본 해석이 JVM 기본 로케일에 고정되므로,
 *   {@link #schemaDescriptionI18nCustomizer}가 요청 로케일로 직접 해석한다.
 *   (springdoc.cache.disabled=true 로 문서가 요청마다 재생성되어야 동작)
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        //Info는 springdoc i18n(MessageSource) 해석 대상이 아니므로 3개 언어를 병기한다.
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

    /**
     * components.schemas의 설명(메시지 키)을 요청 로케일로 해석한다.
     */
    @Bean
    public OpenApiCustomizer schemaDescriptionI18nCustomizer(MessageSource messageSource) {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Locale locale = LocaleContextHolder.getLocale();
            for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
                resolveSchema(schema, messageSource, locale);
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private void resolveSchema(Schema<?> schema, MessageSource messageSource, Locale locale) {
        schema.setDescription(resolve(schema.getDescription(), messageSource, locale));
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Schema<?> property : properties.values()) {
                resolveSchema(property, messageSource, locale);
            }
        }
        if (schema.getItems() != null) {
            resolveSchema(schema.getItems(), messageSource, locale);
        }
    }

    private String resolve(String description, MessageSource messageSource, Locale locale) {
        //메시지 키 규약(schema.*)에 해당하는 설명만 해석하고, 그 외에는 원문 유지
        if (description == null || !description.startsWith("schema.")) {
            return description;
        }
        try {
            return messageSource.getMessage(description, null, locale);
        } catch (NoSuchMessageException e) {
            return description;
        }
    }

}
