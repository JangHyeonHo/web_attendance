package com.attendance.pro.attendance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출결 체크 결과 코드.
 * confirmable = true 인 코드는 사용자의 "덮어쓰기/재출근" 확인을 받아 확정 가능,
 * false 인 코드는 처리 불가.
 */
@Schema(description = "출결 체크 결과 코드", enumAsRef = true)
public enum ConfirmCode {

    /** 이미 출근 중 - 덮어쓰기 확인 */
    ALREADY_WORKING(1, true, "이미 출근 중입니다. 출근을 덮어쓸까요?"),
    /** 이미 퇴근 완료 - 덮어쓰기 확인 */
    ALREADY_OFF_WORK(2, true, "이미 퇴근 중입니다. 퇴근을 덮어쓸까요?"),
    /** 이미 조퇴 완료 - 덮어쓰기 확인 */
    ALREADY_EARLY_DEPARTURE(3, true, "이미 조퇴하셨습니다. 조퇴를 덮어쓸까요?"),
    /** 같은 날 퇴근/조퇴 후 재출근 확인 */
    RE_ATTEND(4, true, "이미 %s 완료중입니다. 재출근을 하시겠습니까?"),
    /** 출근 전이므로 다른 타입 불가 */
    NOT_WORKING_YET(5, false, "출근 전이므로 퇴근/조퇴/휴식을 할 수 없습니다."),
    /** 출근 중이 아니므로 퇴근/조퇴 불가 */
    NOT_ON_DUTY(6, false, "출근 중이 아니기 때문에 %s 하실 수 없습니다."),
    /** 직전 기록상 휴식 불가 */
    CANNOT_BREAK(7, false, "%s 하셨으므로 휴식이 불가능합니다."),
    /** 휴식 중이므로 재출근 불가 */
    ON_BREAK_CANNOT_ATTEND(8, false, "휴식 처리가 되어 있으므로 퇴근을 하셔야 재 출근이 가능합니다.");

    private final int code;
    private final boolean confirmable;
    private final String messageFormat;

    ConfirmCode(int code, boolean confirmable, String messageFormat) {
        this.code = code;
        this.confirmable = confirmable;
        this.messageFormat = messageFormat;
    }

    public int code() {
        return code;
    }

    public boolean confirmable() {
        return confirmable;
    }

    /**
     * 표시 메시지 생성. %s 자리에는 관련 출결 타입명이 들어간다.
     */
    public String message(String typeLabel) {
        return messageFormat.replace("%s", typeLabel == null ? "" : typeLabel);
    }

}
