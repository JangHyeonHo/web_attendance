package com.attendance.pro.setting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UI 테마 설정값 파싱·AUTO 계절 해석 테스트(월 경계 전수).
 */
class UiThemeTest {

    @Test
    @DisplayName("of: 정확 일치만 허용 — 소문자/미지 문자열/null은 null")
    void parse() {
        assertThat(UiTheme.of("AUTO")).isEqualTo(UiTheme.AUTO);
        assertThat(UiTheme.of("SPRING")).isEqualTo(UiTheme.SPRING);
        assertThat(UiTheme.of("WINTER")).isEqualTo(UiTheme.WINTER);
        assertThat(UiTheme.of("spring")).isNull();
        assertThat(UiTheme.of("DARK")).isNull();
        assertThat(UiTheme.of(null)).isNull();
    }

    @Test
    @DisplayName("resolve: 고정 테마는 날짜와 무관하게 자기 자신")
    void fixedThemeIgnoresDate() {
        LocalDate midWinter = LocalDate.of(2026, 1, 15);
        assertThat(UiTheme.SPRING.resolve(midWinter)).isEqualTo(UiTheme.SPRING);
        assertThat(UiTheme.SUMMER.resolve(midWinter)).isEqualTo(UiTheme.SUMMER);
        assertThat(UiTheme.AUTUMN.resolve(midWinter)).isEqualTo(UiTheme.AUTUMN);
        assertThat(UiTheme.WINTER.resolve(LocalDate.of(2026, 8, 1))).isEqualTo(UiTheme.WINTER);
    }

    @Test
    @DisplayName("resolve(AUTO): 12개월 전수 — 3-5월 봄 / 6-8월 여름 / 9-11월 가을 / 12-2월 겨울")
    void autoResolvesBySeason() {
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 1, 1))).isEqualTo(UiTheme.WINTER);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 2, 28))).isEqualTo(UiTheme.WINTER);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 3, 1))).isEqualTo(UiTheme.SPRING);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 4, 15))).isEqualTo(UiTheme.SPRING);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 5, 31))).isEqualTo(UiTheme.SPRING);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 6, 1))).isEqualTo(UiTheme.SUMMER);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 7, 10))).isEqualTo(UiTheme.SUMMER);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 8, 31))).isEqualTo(UiTheme.SUMMER);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 9, 1))).isEqualTo(UiTheme.AUTUMN);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 10, 20))).isEqualTo(UiTheme.AUTUMN);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 11, 30))).isEqualTo(UiTheme.AUTUMN);
        assertThat(UiTheme.AUTO.resolve(LocalDate.of(2026, 12, 1))).isEqualTo(UiTheme.WINTER);
    }

}
