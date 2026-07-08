package com.attendance.pro.common;

import java.util.Objects;

/**
 * 시스템 전역에서 사용하는 공통 코드/유틸리티 모음.
 */
public final class CodeMap {

    public static final String STRING_TRUE = "1";
    public static final String ERROR = "E";
    public static final String SUCCESS = "S";
    public static final String RES = "res";
    public static final String MSG = "msg";
    public static final String RESULT = "result";
    public static final String RED = "redirect";

    public static final String KOREAN = "KOR";
    public static final String ENGLISH = "ENG";

    private CodeMap() {
    }

    /**
     * null 안전 비교. 양쪽 다 null이면 true.
     */
    public static boolean isEqual(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * a가 b들 중 하나라도 같은 값이 있으면 true.
     */
    public static boolean isAnyEqual(Object a, Object... b) {
        for (Object c : b) {
            if (isEqual(a, c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStringEqual(String value) {
        return STRING_TRUE.equals(value);
    }

    public static boolean isEmpty(String a) {
        return a == null || a.isEmpty();
    }

    public static boolean isEmpty(Object o) {
        return o == null;
    }

    /**
     * msg가 null이면 defaultMsg를 반환한다.
     */
    public static String getMsg(String msg, String defaultMsg) {
        return msg == null ? defaultMsg : msg;
    }

}
