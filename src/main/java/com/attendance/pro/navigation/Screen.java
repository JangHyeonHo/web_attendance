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
    /** 출결 — 회사 구성원 전원의 홈(본인 출결). SYSTEM_ADMIN은 전개 거부 */
    ATTENDANCE("W005", Set.of(Role.MEMBER, Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 출결 상세 */
    ATT_DETAILS("W006", Set.of(Role.MEMBER, Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 테넌트 목록/생성 — SYSTEM_ADMIN의 홈 */
    SYSTEM_TENANTS("W007", Set.of(Role.SYSTEM_ADMIN)),
    /** 테넌트 상세(기업정보/결제정보) — W007에 임베드 전개 */
    TENANT_DETAIL("W008", Set.of(Role.SYSTEM_ADMIN)),
    /** 멤버 관리 — 인사관리자+총관리자(역할 지정 버튼은 총관리자에게만 렌더) */
    MEMBERS("W009", Set.of(Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 비밀번호 설정 — 공개(토큰 유효성은 API가 판정, 화면 전개는 무조건) */
    PASSWORD_SETUP("W010", null),
    /** 비밀번호 재설정 요청 — 공개 */
    PASSWORD_RESET("W011", null),
    /** 메일 템플릿 관리(글로벌 제품 자산 — SYSTEM_ADMIN) */
    MAIL_TEMPLATES("W012", Set.of(Role.SYSTEM_ADMIN)),
    /** 공휴일 관리 — 인사관리자+총관리자 */
    HOLIDAYS("W013", Set.of(Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 회사 메일 템플릿(오버라이드) 관리 — 총관리자 전용 */
    TENANT_MAIL_TEMPLATES("W014", Set.of(Role.TENANT_ADMIN)),
    /** 휴가(멤버) — 잔여·신청. 회사 구성원 전원 */
    LEAVE("W015", Set.of(Role.MEMBER, Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 휴가 관리(관리자) — 결재·부여·종류. 인사관리자+총관리자 */
    LEAVE_ADMIN("W016", Set.of(Role.HR_ADMIN, Role.TENANT_ADMIN)),
    /** 감사 로그 조회 — 운영사(SYSTEM_ADMIN) 전용 */
    AUDIT("W017", Set.of(Role.SYSTEM_ADMIN)),
    /** 청구서 조회 — 회사 총관리자(TENANT_ADMIN) 전용(재무 정보) */
    BILLING("W018", Set.of(Role.TENANT_ADMIN)),
    /** 회사 정보/결제 — 회사 총관리자(TENANT_ADMIN) 전용. 사업자정보·결제수단·계약 요약(재무·기밀 정보)(#14) */
    COMPANY_PROFILE("W019", Set.of(Role.TENANT_ADMIN)),
    /** 회사 설정 — 근태 보고서 등 운영 설정. 총관리자+인사관리자(정보/결제보다 낮은 권한도 관리 가능) */
    COMPANY_SETTINGS("W020", Set.of(Role.TENANT_ADMIN, Role.HR_ADMIN)),
    /** 근태 마감 관리 — 멤버 월 마감 신청 결재(승인/반려). 인사관리자+총관리자 */
    ATT_CLOSE_ADMIN("W021", Set.of(Role.HR_ADMIN, Role.TENANT_ADMIN)),
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
