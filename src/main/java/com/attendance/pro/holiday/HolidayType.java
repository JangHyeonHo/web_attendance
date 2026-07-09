package com.attendance.pro.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공휴일 유형.
 * NATIONAL은 동기화 대상(연도 단위 delete+insert), COMPANY는 동기화 불가침(수동 등록·창립기념일 등).
 * 같은 날짜에는 한 행만 존재(PK tenant_id, holiday_date) — 겹치면 COMPANY 우선(동기화가 IGNORE로 건너뜀).
 */
@Schema(description = "schema.holiday-type", enumAsRef = true)
public enum HolidayType {
    NATIONAL, COMPANY
}
