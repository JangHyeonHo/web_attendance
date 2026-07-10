package com.attendance.pro.tenant;

import java.time.LocalDateTime;

/**
 * 테넌트(tenant 테이블).
 * country는 소재국(ISO 3166-1 alpha-2) 단일 출처 — 공휴일 동기화·사업자 식별번호 체계·메일 언어를 결정한다(V7 [H-1] 승격).
 */
public record Tenant(
        Long tenantId,
        String tenantCode,
        String name,
        String country,
        TenantStatus status,
        LocalDateTime createdAt) {
}
