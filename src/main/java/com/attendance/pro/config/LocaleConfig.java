package com.attendance.pro.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 요청 로케일 설정.
 * 우선순위: 세션에 저장된 언어(LANG: KOR/ENG/JPN) → Accept-Language 헤더 → 한국어.
 * 세션 언어는 navigation API의 lang 파라미터로 저장된다.
 */
@Configuration
public class LocaleConfig {

    /** 세션 언어 속성 키 (NavigationController와 공유) */
    public static final String SESSION_LANG_KEY = "LANG";

    /** 지원 로케일 (미지원 Accept-Language는 한국어로 폴백) */
    private static final List<Locale> SUPPORTED = List.of(Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE);

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                //1. 세션에 저장된 언어
                HttpSession session = request.getSession(false);
                Object saved = session == null ? null : session.getAttribute(SESSION_LANG_KEY);
                if (saved != null) {
                    return toLocale(String.valueOf(saved));
                }
                //2. Accept-Language 헤더(지원 언어만)
                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage != null) {
                    try {
                        Locale matched = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
                        if (matched != null) {
                            return matched;
                        }
                    } catch (IllegalArgumentException ignored) {
                        //헤더 형식이 잘못된 경우 기본 언어로
                    }
                }
                //3. 기본: 한국어
                return Locale.KOREAN;
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                //세션 저장은 NavigationController가 담당하므로 여기서는 미사용
            }
        };
    }

    /**
     * 시스템 언어 코드(KOR/ENG/JPN) → 로케일.
     */
    public static Locale toLocale(String lang) {
        return switch (lang) {
            case "ENG" -> Locale.ENGLISH;
            case "JPN" -> Locale.JAPANESE;
            default -> Locale.KOREAN;
        };
    }

}
