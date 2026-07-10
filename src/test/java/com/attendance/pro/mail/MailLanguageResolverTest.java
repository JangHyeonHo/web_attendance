package com.attendance.pro.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 메일 언어 해석 테스트 — TPL-04(U): tenant.country → 메일 언어, 미지원 값은 ENG 방어.
 */
class MailLanguageResolverTest {

    private final MailLanguageResolver resolver = new MailLanguageResolver();

    @Test
    @DisplayName("KR→KOR, JP→JPN(대소문자/공백 무관)")
    void supportedCountries() {
        assertThat(resolver.resolve("KR")).isEqualTo("KOR");
        assertThat(resolver.resolve("JP")).isEqualTo("JPN");
        assertThat(resolver.resolve(" kr ")).isEqualTo("KOR");
    }

    @Test
    @DisplayName("미지원 국가/누락은 ENG 폴백(방어 — 실측 도달 불가)")
    void fallbackToEnglish() {
        assertThat(resolver.resolve("US")).isEqualTo("ENG");
        assertThat(resolver.resolve("")).isEqualTo("ENG");
        assertThat(resolver.resolve(null)).isEqualTo("ENG");
    }

}
