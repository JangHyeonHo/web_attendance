package com.attendance.pro.attendance;

/**
 * 스탬프 등록 경로 — 버튼 클릭(자동)과 정정 등록(수동)은 데이터로도 구분된다(Phase 5).
 */
public enum StampSource {
    /** 출결 화면 버튼(체크→확정 2단계) */
    AUTO,
    /** 정정 등록(사유 필수) */
    MANUAL
}
