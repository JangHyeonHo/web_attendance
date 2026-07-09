package com.attendance.pro.navigation;

import java.util.Set;

import com.attendance.pro.user.Role;

/**
 * 화면 레지스트리.
 * 실제 화면 명 대신 은닉 코드(W000~)를 API 계약에 노출한다(v1의 WindowManagement 계승).
 * 프론트는 이 코드로만 화면을 식별하며, 코드-컴포넌트 매핑은 프론트가 보관한다.
 *
 * 접근 제어는 허용 role 집합으로 선언한다(null = 공개) — API 화이트리스트(RoleInterceptor)와 동일 방식.
 * W003(회원가입)은 폐기·영구 결번: fromCode("W003") → null → UNKNOWN_SCREEN 처리.
 */
public enum Screen {

    /** 인덱스(랜딩) */
    INDEX("W000", null),
    /** 로그인 */
    LOGIN("W001", null),
    /** 로그아웃(액션 화면 - 처리 후 로그인으로 전환) */
    LOGOUT("W002", null),
    /** 언어 마스터 관리(글로벌 리소스 — SYSTEM_ADMIN) */
    ADMIN("W004", Set.of(Role.SYSTEM_ADMIN)),
    /** 출결 — TENANT_ADMIN·MEMBER의 홈. SYSTEM_ADMIN은 전개 거부 */
    ATTENDANCE("W005", Set.of(Role.MEMBER, Role.TENANT_ADMIN)),
    /** 출결 상세 */
    ATT_DETAILS("W006", Set.of(Role.MEMBER, Role.TENANT_ADMIN)),
    /** 테넌트 목록/생성 — SYSTEM_ADMIN의 홈 */
    SYSTEM_TENANTS("W007", Set.of(Role.SYSTEM_ADMIN)),
    /** 테넌트 상세(기업정보/결제정보) — W007에 임베드 전개 */
    TENANT_DETAIL("W008", Set.of(Role.SYSTEM_ADMIN)),
    /** 멤버 관리 — 헤더 MEMBERS 메뉴로 진입 */
    MEMBERS("W009", Set.of(Role.TENANT_ADMIN)),
    /** 공통(헤더) - 직접 전개하지 않고 공통 텍스트 취득용 */
    COMMON("W999", null);

    private final String code;
    private final Set<Role> allowed;   //null = 공개, 그 외 = 집합에 든 role만

    Screen(String code, Set<Role> allowed) {
        this.code = code;
        this.allowed = allowed;
    }

    public String code() {
        return code;
    }

    public boolean isPublic() {
        return allowed == null;
    }

    public Set<Role> allowed() {
        return allowed;
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
