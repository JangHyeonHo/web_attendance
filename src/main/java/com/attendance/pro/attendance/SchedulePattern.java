package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 반복 근무 패턴(schedule_pattern) — 사람당 활성 1개. cycle_weeks 주 주기, anchor_monday 기준 원점(#13).
 */
public record SchedulePattern(
        long patternId,
        long tenantId,
        long userId,
        int cycleWeeks,
        LocalDate anchorMonday,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
