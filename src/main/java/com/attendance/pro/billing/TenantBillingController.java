package com.attendance.pro.billing;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.billing.BillingDtos.BillingProfileRequest;
import com.attendance.pro.billing.BillingDtos.BillingProfileResponse;
import com.attendance.pro.billing.BillingDtos.InvoiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 회사(테넌트) 자사 청구서 조회 API — 총관리자(TENANT_ADMIN) 전용(재무 정보라 인사관리자 제외,
 * RoleInterceptor /api/v1/tenant/billing/** 화이트리스트). tenantId는 세션에서만 취득한다.
 * 진행 중인 달은 잠정(실시간 계산), 마감된 달은 확정 스냅샷을 돌려준다.
 */
@Tag(name = "TenantBilling", description = "api.tenant-billing.tag")
@RestController
@RequestMapping("/api/v1/tenant/billing")
public class TenantBillingController {

    private final BillingService billingService;

    public TenantBillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "api.tenant-billing.invoices")
    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(@LoginUser SessionUser user) {
        return billingService.listForTenant(user.tenantId());
    }

    @Operation(summary = "api.tenant-billing.profile-get")
    @GetMapping("/profile")
    public BillingProfileResponse profile(@LoginUser SessionUser user) {
        return billingService.getProfile(user.tenantId());
    }

    @Operation(summary = "api.tenant-billing.profile-update")
    @PutMapping("/profile")
    public BillingProfileResponse updateProfile(@LoginUser SessionUser user,
            @Valid @RequestBody BillingProfileRequest request) {
        return billingService.updateProfile(user.tenantId(), request);
    }
}
