package com.attendance.pro.attendance;

/**
 * 수동 정정 사유 코드 — "일부 선택 + 직접 입력" 스타일.
 * 자주 있는 사유(찍는 것을 잊음)가 선두, OTHER는 자유 텍스트가 필수다.
 */
public enum ManualReason {
    /** 찍는 것을 잊음 */
    FORGOT,
    /** 단말·통신 문제 */
    DEVICE,
    /** 외근·출장 */
    OFFSITE,
    /** 기타 — reasonText 필수 */
    OTHER;

    /** 대문자 정확 일치만 허용 — 미지원 문자열은 null */
    public static ManualReason of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
