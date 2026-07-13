package com.attendance.pro.leave;

/**
 * 휴가 신청 상태.
 * 잔여 소진 = APPROVED + CANCEL_REQUESTED(취소 확정 전까지 유효). CANCELED로 확정되면 복원.
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED,
    /** 멤버가 승인 휴가의 취소를 신청한 상태(관리자 확정 대기) */
    CANCEL_REQUESTED
}
