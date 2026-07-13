package com.attendance.pro.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 휴가 부여(leave_grant). minutes 단위(음수 조정 가능).
 * AUTO(법정 연차 재계산)는 leaveYear로 유저·종류·연도당 1행 upsert, MANUAL은 leaveYear NULL.
 * expiresOn 이후(기준일 초과)는 잔여 합산에서 제외.
 */
public record LeaveGrant(
        Long leaveGrantId,
        long tenantId,
        long userId,
        long leaveTypeId,
        int minutes,
        LocalDate effectiveFrom,
        LocalDate expiresOn,
        LeaveSource source,
        Integer leaveYear,
        String memo,
        Long grantedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
