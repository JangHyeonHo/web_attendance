package com.attendance.pro.attendance;

import java.time.LocalTime;

/**
 * 개인 기본 근무 설정(users.default_work_start/end + work_days) — "그날의 스케줄 해석"용 근태 질의 결과.
 *
 * @param workDays 요일별 근무 플래그(월화수목금토일, '1'=근무). null이면 전 요일 근무로 해석(방어)
 */
public record WorkDefaults(LocalTime start, LocalTime end, String workDays) {

    /** 그 요일에 근무하는가(월=1 … 일=7). 플래그 결손은 근무로 해석(집계가 기록을 버리지 않게) */
    public static boolean worksOn(String workDays, java.time.DayOfWeek dayOfWeek) {
        if (workDays == null || workDays.length() != 7) {
            return true;
        }
        return workDays.charAt(dayOfWeek.getValue() - 1) == '1';
    }

}
