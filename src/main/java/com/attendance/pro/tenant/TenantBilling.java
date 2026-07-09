package com.attendance.pro.tenant;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.attendance.pro.tenant.TenantDtos.BillingMethod;

/**
 * 테넌트 청구/결제 정보(tenant_billing 테이블).
 * pgCustomerKeyEnc는 "v1:" 텍스트 암호문(String)이며 어떤 API 응답에도 평문 반환하지 않는다.
 * 카드 원본(PAN/CVC/유효기간)은 어떤 형태로도 저장하지 않는다 — 표시용 last4/brand만.
 */
public record TenantBilling(
        long tenantId,
        BillingMethod billingMethod,
        String billingEmail,
        String pgCustomerKeyEnc,
        String cardLast4,
        String cardBrand,
        String plan,
        LocalDate billedFrom,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
