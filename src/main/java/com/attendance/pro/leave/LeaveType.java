package com.attendance.pro.leave;

import java.time.LocalDateTime;

/**
 * 휴가 종류(leave_type). 테넌트별 커스텀 + ANNUAL(연차) 내장 시드.
 * isAnnual=연차 성격(자동 계산 대상), unit=부여/사용 단위(DAY/HOUR).
 */
public record LeaveType(
        Long leaveTypeId,
        long tenantId,
        String code,
        String name,
        boolean paid,
        LeaveUnit unit,
        /** 시간 단위 휴가 신청 허용 여부(#12) — 단위(unit)와 별개 */
        boolean hourlyEnabled,
        boolean requiresApproval,
        boolean isAnnual,
        boolean active,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
