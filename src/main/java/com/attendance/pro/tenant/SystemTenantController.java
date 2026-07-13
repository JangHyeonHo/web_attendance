package com.attendance.pro.tenant;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.billing.BillingDtos.InvoiceResponse;
import com.attendance.pro.billing.BillingService;
import com.attendance.pro.tenant.TenantDtos.TenantBillingRequest;
import com.attendance.pro.tenant.TenantDtos.TenantBillingResponse;
import com.attendance.pro.tenant.TenantDtos.TenantCreateRequest;
import com.attendance.pro.tenant.TenantDtos.TenantCreateResponse;
import com.attendance.pro.tenant.TenantDtos.TenantProfileRequest;
import com.attendance.pro.tenant.TenantDtos.TenantProfileResponse;
import com.attendance.pro.tenant.TenantDtos.TenantResponse;
import com.attendance.pro.tenant.TenantDtos.TenantStatusRequest;
import com.attendance.pro.user.MemberDtos.InviteResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 테넌트 관리 API (SYSTEM_ADMIN 전용 — RoleInterceptor 화이트리스트).
 * 이 계열만 tenantId를 경로 변수로 받는 예외다(세션 tenantId를 쓰지 않는 유일한 API군).
 * 응답 DTO에 출결 데이터를 담는 필드를 만들지 않는다(테넌트 메타까지만).
 */
@Tag(name = "SystemTenant", description = "api.system-tenant.tag")
@RestController
@RequestMapping("/api/v1/system/tenants")
public class SystemTenantController {

    private final TenantService tenantService;
    private final TenantProfileService tenantProfileService;
    private final BillingService billingService;

    public SystemTenantController(TenantService tenantService, TenantProfileService tenantProfileService,
            BillingService billingService) {
        this.tenantService = tenantService;
        this.tenantProfileService = tenantProfileService;
        this.billingService = billingService;
    }

    @Operation(summary = "api.system-tenant.create.summary", description = "api.system-tenant.create.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.system-tenant.create.201"),
            @ApiResponse(responseCode = "409", description = "api.system-tenant.create.409")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantCreateResponse create(@LoginUser SessionUser user,
            @Valid @RequestBody TenantCreateRequest request) {
        //{inviterName} = 생성을 실행한 SYSTEM_ADMIN의 세션 name
        return tenantService.create(user, request);
    }

    @Operation(summary = "api.system-tenant.admin-invite.summary",
            description = "api.system-tenant.admin-invite.description")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "api.system-tenant.get.404"),
            @ApiResponse(responseCode = "409", description = "api.system-tenant.admin-invite.409")
    })
    @PostMapping("/{tenantId}/admin-invite")
    public InviteResponse adminInvite(@LoginUser SessionUser user,
            @PathVariable("tenantId") long tenantId) {
        return tenantService.adminInvite(user, tenantId);
    }

    @Operation(summary = "api.system-tenant.list.summary", description = "api.system-tenant.list.description")
    @GetMapping
    public List<TenantResponse> list() {
        return tenantService.list();
    }

    @Operation(summary = "api.system-tenant.get.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "api.system-tenant.get.404")
    })
    @GetMapping("/{tenantId}")
    public TenantResponse get(@PathVariable("tenantId") long tenantId) {
        return tenantService.get(tenantId);
    }

    @Operation(summary = "api.system-tenant.status.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.system-tenant.status.400")
    })
    @PutMapping("/{tenantId}/status")
    public TenantResponse updateStatus(@LoginUser SessionUser user,
            @PathVariable("tenantId") long tenantId,
            @Valid @RequestBody TenantStatusRequest request) {
        return tenantService.updateStatus(user, tenantId, request.status());
    }

    @Operation(summary = "api.system-tenant.profile-get.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "api.system-tenant.profile-get.404")
    })
    @GetMapping("/{tenantId}/profile")
    public TenantProfileResponse getProfile(@PathVariable("tenantId") long tenantId) {
        return tenantProfileService.getProfile(tenantId);
    }

    @Operation(summary = "api.system-tenant.profile-upsert.summary",
            description = "api.system-tenant.profile-upsert.description")
    @PutMapping("/{tenantId}/profile")
    public TenantProfileResponse upsertProfile(@PathVariable("tenantId") long tenantId,
            @Valid @RequestBody TenantProfileRequest request) {
        return tenantProfileService.upsertProfile(tenantId, request);
    }

    @Operation(summary = "api.system-tenant.billing-get.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "api.system-tenant.billing-get.404")
    })
    @GetMapping("/{tenantId}/billing")
    public TenantBillingResponse getBilling(@PathVariable("tenantId") long tenantId) {
        return tenantProfileService.getBilling(tenantId);
    }

    @Operation(summary = "api.system-tenant.billing-upsert.summary",
            description = "api.system-tenant.billing-upsert.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.system-tenant.billing-upsert.400")
    })
    @PutMapping("/{tenantId}/billing")
    public TenantBillingResponse upsertBilling(@PathVariable("tenantId") long tenantId,
            @Valid @RequestBody TenantBillingRequest request) {
        return tenantProfileService.upsertBilling(tenantId, request);
    }

    @Operation(summary = "api.system-tenant.invoices.summary")
    @GetMapping("/{tenantId}/invoices")
    public List<InvoiceResponse> invoices(@PathVariable("tenantId") long tenantId) {
        return billingService.listForTenant(tenantId);
    }

    @Operation(summary = "api.system-tenant.invoice-close.summary",
            description = "api.system-tenant.invoice-close.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.system-tenant.invoice-close.400"),
            @ApiResponse(responseCode = "409", description = "api.system-tenant.invoice-close.409")
    })
    @PostMapping("/{tenantId}/invoices/{ym}/close")
    public InvoiceResponse closeInvoice(@PathVariable("tenantId") long tenantId,
            @PathVariable("ym") String ym) {
        return billingService.close(tenantId, ym);
    }

}
