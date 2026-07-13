package com.attendance.pro.audit;

import java.time.LocalDateTime;

/** 감사 로그 한 건(조회용). */
public record AuditLog(
        long auditId,
        Long tenantId,
        Long userId,
        String category,
        String event,
        String detail,
        String actorEmail,
        String ip,
        String userAgent,
        String requestPath,
        LocalDateTime createdAt) {
}
