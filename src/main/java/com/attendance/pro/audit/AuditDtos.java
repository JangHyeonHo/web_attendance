package com.attendance.pro.audit;

import java.time.LocalDateTime;

import com.attendance.pro.audit.AuditLogMapper.AuditLogView;

/** 감사 조회 API DTO. */
public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogResponse(
            long auditId, String category, String event, Long tenantId, String tenantName,
            Long userId, String actor, String ip, String userAgent, String requestPath,
            String detail, LocalDateTime createdAt) {

        public static AuditLogResponse of(AuditLogView v) {
            return new AuditLogResponse(v.auditId(), v.category(), v.event(), v.tenantId(),
                    v.tenantName(), v.userId(), v.actor(), v.ip(), v.userAgent(), v.requestPath(),
                    v.detail(), v.createdAt());
        }
    }
}
