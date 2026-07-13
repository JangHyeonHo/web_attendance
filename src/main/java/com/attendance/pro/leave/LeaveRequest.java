package com.attendance.pro.leave;

import java.time.LocalDateTime;

/**
 * 휴가 신청(leave_request). minutes=차감 분. dayUnit=일 단위(반차 halfDay),
 * false면 시간 단위(start~end 실시각). APPROVED만 잔여 차감.
 */
public record LeaveRequest(
        Long leaveRequestId,
        long tenantId,
        long userId,
        long leaveTypeId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int minutes,
        boolean dayUnit,
        boolean halfDay,
        String reason,
        LeaveStatus status,
        Long decidedBy,
        LocalDateTime decidedAt,
        String decisionNote,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
