package com.attendance.pro.audit;

/** 감사 이벤트 분류. AUTH=인증/세션, ERROR=애플리케이션 에러. (확장 지점) */
public enum AuditCategory {
    AUTH,
    ERROR
}
