package com.attendance.pro.attendance;

import java.time.LocalDateTime;

/**
 * 출결 스탬프(attendance 테이블).
 * type/status는 DB 코드값 그대로 보관하고, 타입 변환은 {@link #type()}으로 한다.
 */
public record AttendanceStamp(
        long attendanceId,
        long userId,
        int typeCode,
        int status,
        LocalDateTime stampedAt) {

    /** 휴식 스탬프 상태: 시작 */
    public static final int STATUS_ACTIVE = 0;
    /** 휴식 스탬프 상태: 종료 */
    public static final int STATUS_BREAK_ENDED = 1;

    public AttendanceType type() {
        return AttendanceType.fromCode(typeCode);
    }

}
