package com.attendance.pro.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 3개 언어(ko/en/ja) 메시지 번들 검증.
 */
class MessagesTest {

    private final Messages messages = create();

    private static Messages create() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new Messages(messageSource);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("같은 키가 로케일별로 각 언어 텍스트로 해석된다")
    void resolvesPerLocale() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(messages.get("error.unauthorized")).isEqualTo("로그인이 필요합니다.");
        assertThat(messages.get("attendance.type.GO_TO_WORK")).isEqualTo("출근");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(messages.get("error.unauthorized")).isEqualTo("Login is required.");
        assertThat(messages.get("attendance.type.GO_TO_WORK")).isEqualTo("clock-in");

        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertThat(messages.get("error.unauthorized")).isEqualTo("ログインが必要です。");
        assertThat(messages.get("attendance.type.GO_TO_WORK")).isEqualTo("出勤");
    }

    @Test
    @DisplayName("MessageFormat 인자({0},{1})가 채워진다")
    void formatsArguments() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertThat(messages.get("attendance.stamp.success", "09:00", "出勤"))
                .isEqualTo("09:00に出勤を打刻しました。");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(messages.get("confirm.RE_ATTEND", "clock-out"))
                .isEqualTo("You have already completed clock-out today. Clock in again?");
    }

    @Test
    @DisplayName("미지원 로케일은 기본(한국어)으로 폴백된다")
    void fallsBackToDefault() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.get("error.unauthorized")).isEqualTo("로그인이 필요합니다.");
    }

    @Test
    @DisplayName("모든 언어 번들이 같은 키 집합을 가진다(번역 누락 방지)")
    void bundlesHaveSameKeys() throws Exception {
        java.util.Properties ko = load("/messages/messages.properties");
        java.util.Properties en = load("/messages/messages_en.properties");
        java.util.Properties ja = load("/messages/messages_ja.properties");
        assertThat(en.keySet()).containsExactlyInAnyOrderElementsOf(ko.keySet());
        assertThat(ja.keySet()).containsExactlyInAnyOrderElementsOf(ko.keySet());
    }

    private java.util.Properties load(String path) throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (var reader = new java.io.InputStreamReader(
                getClass().getResourceAsStream(path), java.nio.charset.StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

}
