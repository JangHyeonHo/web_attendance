package com.attendance.pro.tenant;

import java.time.LocalDateTime;

/**
 * 테넌트 기업 정보(tenant_profile 테이블).
 * {@code *Enc} 필드는 "v1:" 텍스트 암호문(String)만 보관한다 — 평문을 필드로 갖지 않는다.
 */
public record TenantProfile(
        long tenantId,
        String country,
        String businessRegNoEnc,
        String ceoName,
        String postalCode,
        String address,
        String addressDetail,
        String contactName,
        String contactEmail,
        String contactPhoneEnc,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
