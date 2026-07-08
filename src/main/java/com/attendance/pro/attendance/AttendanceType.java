package com.attendance.pro.attendance;

import com.attendance.pro.common.ApiException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출결 타입.
 */
@Schema(description = "출결 타입", enumAsRef = true)
public enum AttendanceType {

    /** 출근 */
    GO_TO_WORK(1, "출근"),
    /** 퇴근 */
    OFF_WORK(2, "퇴근"),
    /** 조퇴 */
    EARLY_DEPARTURE(3, "조퇴"),
    /** 휴식 */
    BREAK(4, "휴식");

    private final int code;
    private final String label;

    AttendanceType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static AttendanceType fromCode(int code) {
        for (AttendanceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw ApiException.badRequest("INVALID_TYPE", "알 수 없는 출결 타입입니다: " + code);
    }

}
