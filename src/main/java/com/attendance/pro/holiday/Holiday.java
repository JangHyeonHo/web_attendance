package com.attendance.pro.holiday;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공휴일(holiday 테이블 — 테넌트별).
 */
public record Holiday(
        long holidayId,
        long tenantId,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        /** 매년 반복 여부(COMPANY 전용 — NATIONAL은 동기화가 관리하므로 항상 false) */
        boolean recurring,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
