package com.attendance.pro.leave;

/** 부여 출처. AUTO=법정 연차 자동 재계산(연도별 upsert), MANUAL=관리자 수동 조정. */
public enum LeaveSource {
    AUTO,
    MANUAL
}
