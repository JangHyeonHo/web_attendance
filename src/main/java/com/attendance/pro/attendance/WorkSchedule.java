package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 근무 스케쥴(work_schedule 테이블).
 */
public record WorkSchedule(
        long scheduleId,
        long userId,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean holiday) {
}
