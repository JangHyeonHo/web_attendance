package com.attendance.pro.setting;

import java.time.LocalDate;
import java.time.Month;

/**
 * 시스템 전역 UI 테마 설정값.
 * AUTO는 저장 전용 값 — 화면 전개 시 서버 날짜의 계절로 해석되어 내려간다(프론트는 확정값만 받는다).
 */
public enum UiTheme {

    /** 서버 날짜 기준 계절 자동(3-5월 봄 / 6-8월 여름 / 9-11월 가을 / 12-2월 겨울) */
    AUTO,
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    /** 대문자 정확 일치만 허용(설정값 계약) — 미지원 문자열은 null */
    public static UiTheme of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 확정 테마(AUTO는 date의 계절로 해석) — 반환값은 절대 AUTO가 아니다 */
    public UiTheme resolve(LocalDate date) {
        if (this != AUTO) {
            return this;
        }
        Month month = date.getMonth();
        return switch (month) {
        case MARCH, APRIL, MAY -> SPRING;
        case JUNE, JULY, AUGUST -> SUMMER;
        case SEPTEMBER, OCTOBER, NOVEMBER -> AUTUMN;
        case DECEMBER, JANUARY, FEBRUARY -> WINTER;
        };
    }

}
