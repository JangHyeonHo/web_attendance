package com.attendance.pro.tenant;

import java.time.LocalDateTime;

/**
 * 테넌트(tenant 테이블).
 */
public record Tenant(
        Long tenantId,
        String tenantCode,
        String name,
        TenantStatus status,
        LocalDateTime createdAt) {
}
