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
        /** 종업이 익일인 교대(야간). 예정근무·법정휴게 계산이 반영(#13) */
        boolean crossesMidnight,
        /** 그 날 스케줄상 휴무(공휴일과 구분 — 패턴/로타의 OFF일). true면 근무 없음 */
        boolean off,
        boolean holiday) {

    /** 패턴 투영 등 메모리 전용 스케줄 생성(대리키 없음). */
    public static WorkSchedule projected(long userId, LocalDate date, LocalTime start, LocalTime end,
            boolean crossesMidnight, boolean off) {
        return new WorkSchedule(0L, userId, date, start, end, crossesMidnight, off, false);
    }
}
