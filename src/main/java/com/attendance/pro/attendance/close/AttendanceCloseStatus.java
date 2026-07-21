package com.attendance.pro.attendance.close;

/** 근태 마감 상태 — 신청(REQUESTED) → 승인(APPROVED)/반려(REJECTED). 취소는 REQUESTED 행 삭제로 처리. */
public enum AttendanceCloseStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
