package com.attendance.pro.attendance.close;

import java.time.LocalDateTime;

/** attendance_close 1행. status는 REQUESTED/APPROVED/REJECTED. */
public record AttendanceClose(
        long closeId,
        long tenantId,
        long userId,
        int targetYear,
        int targetMonth,
        AttendanceCloseStatus status,
        LocalDateTime requestedAt,
        Long approverId,
        LocalDateTime decidedAt,
        String decisionNote) {
}
