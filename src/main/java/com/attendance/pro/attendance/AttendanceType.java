package com.attendance.pro.attendance;

import com.attendance.pro.common.ApiException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출결 타입.
 */
@Schema(description = "출결 타입", enumAsRef = true)
public enum AttendanceType {

    /** 출근 */
    GO_TO_WORK(1),
    /** 퇴근 */
    OFF_WORK(2),
    /** 조퇴 */
    EARLY_DEPARTURE(3),
    /** 휴식 */
    BREAK(4);

    private final int code;

    AttendanceType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * 타입 표시명(출근/퇴근/조퇴/휴식)의 메시지 키. Messages로 로케일별 해석한다.
     */
    public String labelKey() {
        return "attendance.type." + name();
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
