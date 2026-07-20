package com.attendance.pro.attendance;

import java.time.LocalTime;

/**
 * 반복 패턴 슬롯 — (주차, 요일) → 근무(시업/종업/야간) 또는 휴무(off). 모든 스케줄은 시작시간 기준(#13).
 * day_of_week: 1..7(월..일).
 */
public record SchedulePatternSlot(
        long patternId,
        int weekIndex,
        int dayOfWeek,
        boolean off,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight) {
}
