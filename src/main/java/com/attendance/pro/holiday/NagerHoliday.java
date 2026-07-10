package com.attendance.pro.holiday;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nager.Date PublicHolidays 응답 항목(원시 파싱 — 필터·검증은 HolidayService §2-3).
 * date는 검증(파싱·연도 일치)을 서비스에서 수행하기 위해 문자열로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NagerHoliday(
        String date,
        String localName,
        String name,
        String countryCode,
        Boolean global,
        List<String> counties,
        List<String> types) {
}
