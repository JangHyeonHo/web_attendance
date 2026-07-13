package com.attendance.pro.audit;

/** 감사 이벤트 코드(분류 포함). 새 감사 지점은 여기에 추가한다. */
public enum AuditEvent {

    LOGIN_SUCCESS(AuditCategory.AUTH),
    LOGIN_FAIL(AuditCategory.AUTH),
    /** 로그인 레이트리밋 차단 발동(임계 도달 시점 1회 — 차단 중 반복 시도는 재기록하지 않아 폭주 방지) */
    LOGIN_BLOCKED(AuditCategory.AUTH),
    LOGOUT(AuditCategory.AUTH),
    /** 세션 재검증 실패(계정 비활성/테넌트 정지/비번 변경/호스트 불일치)로 세션 회수 */
    SESSION_REVOKED(AuditCategory.AUTH),
    /** 처리되지 않은 예외 등 애플리케이션 에러(대개 500) */
    APP_ERROR(AuditCategory.ERROR);

    private final AuditCategory category;

    AuditEvent(AuditCategory category) {
        this.category = category;
    }

    public AuditCategory category() {
        return category;
    }
}
