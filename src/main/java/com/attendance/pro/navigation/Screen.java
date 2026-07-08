package com.attendance.pro.navigation;

/**
 * 화면 레지스트리.
 * 실제 화면 명 대신 은닉 코드(W000~)를 API 계약에 노출한다(v1의 WindowManagement 계승).
 * 프론트는 이 코드로만 화면을 식별하며, 코드-컴포넌트 매핑은 프론트가 보관한다.
 */
public enum Screen {

    /** 인덱스(홈) */
    INDEX("W000", Access.PUBLIC),
    /** 로그인 */
    LOGIN("W001", Access.PUBLIC),
    /** 로그아웃(액션 화면 - 처리 후 로그인으로 전환) */
    LOGOUT("W002", Access.PUBLIC),
    /** 회원가입 */
    SIGNUP("W003", Access.PUBLIC),
    /** 관리자 */
    ADMIN("W004", Access.ADMIN_ONLY),
    /** 출결 */
    ATTENDANCE("W005", Access.LOGIN_REQUIRED),
    /** 출결 상세 */
    ATT_DETAILS("W006", Access.LOGIN_REQUIRED),
    /** 공통(헤더) - 직접 전개하지 않고 공통 텍스트 취득용 */
    COMMON("W999", Access.PUBLIC);

    /** 화면 접근 레벨 */
    public enum Access {
        PUBLIC, LOGIN_REQUIRED, ADMIN_ONLY
    }

    private final String code;
    private final Access access;

    Screen(String code, Access access) {
        this.code = code;
        this.access = access;
    }

    public String code() {
        return code;
    }

    public Access access() {
        return access;
    }

    /**
     * 화면 코드로 화면을 찾는다. 알 수 없는 코드는 null.
     */
    public static Screen fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (Screen screen : values()) {
            if (screen.code.equals(code)) {
                return screen;
            }
        }
        return null;
    }

}
