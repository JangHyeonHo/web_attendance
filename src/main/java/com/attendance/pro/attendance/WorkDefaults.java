package com.attendance.pro.attendance;

import java.time.LocalTime;

/**
 * 개인 기본 근무 스케줄(users.default_work_start/end) — "그날의 스케줄 해석"용 근태 질의 결과.
 */
public record WorkDefaults(LocalTime start, LocalTime end) {
}
