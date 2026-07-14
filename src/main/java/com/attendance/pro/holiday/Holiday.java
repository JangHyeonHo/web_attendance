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
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
