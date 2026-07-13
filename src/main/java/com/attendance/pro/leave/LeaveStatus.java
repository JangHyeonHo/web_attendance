package com.attendance.pro.leave;

/** 휴가 신청 상태. 승인(APPROVED)만 잔여에서 차감된다. */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED
}
