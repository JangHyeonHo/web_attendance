package com.attendance.pro.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.FieldCipher;
import com.attendance.pro.common.Masking;
import com.attendance.pro.tenant.TenantDtos.BillingMethod;
import com.attendance.pro.tenant.TenantDtos.TenantBillingRequest;
import com.attendance.pro.tenant.TenantDtos.TenantBillingResponse;
import com.attendance.pro.tenant.TenantDtos.TenantProfileRequest;
import com.attendance.pro.tenant.TenantDtos.TenantProfileResponse;

/**
 * 기업/결제 정보의 암·복호화 + 마스킹 조립 서비스(SystemTenantController가 사용).
 * 로그 규약: 이 서비스에서 요청/엔티티를 로그로 출력하지 않는다.
 * 복호화 평문은 지역변수까지만 — 응답 DTO에는 마스킹값만 싣는다.
 * 빌링키는 복호화하지 않는다(존재 여부 hasBillingKey만).
 */
@Service
public class TenantProfileService {

    private final TenantMapper tenantMapper;
    private final TenantProfileMapper tenantProfileMapper;
    private final TenantBillingMapper tenantBillingMapper;
    private final FieldCipher fieldCipher;

    public TenantProfileService(TenantMapper tenantMapper, TenantProfileMapper tenantProfileMapper,
            TenantBillingMapper tenantBillingMapper, FieldCipher fieldCipher) {
        this.tenantMapper = tenantMapper;
        this.tenantProfileMapper = tenantProfileMapper;
        this.tenantBillingMapper = tenantBillingMapper;
        this.fieldCipher = fieldCipher;
    }

    @Transactional(readOnly = true)
    public TenantProfileResponse getProfile(long tenantId) {
        requireTenant(tenantId);
        TenantProfile profile = tenantProfileMapper.findById(tenantId);
        if (profile == null) {
            throw ApiException.notFound("TENANT_PROFILE_NOT_FOUND", "tenant.profile.not-found");
        }
        return toResponse(profile);
    }

    /**
     * 기업 정보 create-or-replace(마스킹값 표시 + 전체 재입력 방식).
     * 응답은 조회 경로를 재사용한다(마스킹 일원화).
     */
    @Transactional
    public TenantProfileResponse upsertProfile(long tenantId, TenantProfileRequest request) {
        requireTenant(tenantId);
        tenantProfileMapper.upsert(tenantId,
                fieldCipher.encrypt(request.businessRegNo()),
                request.ceoName(), request.address(), request.contactName(), request.contactEmail(),
                fieldCipher.encrypt(request.contactPhone()));
        return toResponse(tenantProfileMapper.findById(tenantId));
    }

    @Transactional(readOnly = true)
    public TenantBillingResponse getBilling(long tenantId) {
        requireTenant(tenantId);
        TenantBilling billing = tenantBillingMapper.findById(tenantId);
        if (billing == null) {
            throw ApiException.notFound("TENANT_BILLING_NOT_FOUND", "tenant.billing.not-found");
        }
        return toResponse(billing);
    }

    /**
     * 결제 정보 create-or-replace. CARD인데 빌링키 미입력이면 400.
     */
    @Transactional
    public TenantBillingResponse upsertBilling(long tenantId, TenantBillingRequest request) {
        requireTenant(tenantId);
        if (request.billingMethod() == BillingMethod.CARD
                && (request.pgCustomerKey() == null || request.pgCustomerKey().isBlank())) {
            throw ApiException.badRequest("BILLING_CARD_KEY_REQUIRED", "tenant.billing.card-key.required");
        }
        String pgKeyEnc = request.pgCustomerKey() == null || request.pgCustomerKey().isBlank()
                ? null
                : fieldCipher.encrypt(request.pgCustomerKey());
        tenantBillingMapper.upsert(tenantId, request.billingMethod(), request.billingEmail(),
                pgKeyEnc, request.cardLast4(), request.cardBrand(), request.plan(),
                request.billedFrom(), request.memo());
        return toResponse(tenantBillingMapper.findById(tenantId));
    }

    private void requireTenant(long tenantId) {
        if (tenantMapper.findById(tenantId) == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
    }

    private TenantProfileResponse toResponse(TenantProfile profile) {
        //복호화 평문은 지역변수까지만 — DTO에는 마스킹값만
        String businessRegNo = fieldCipher.decrypt(profile.businessRegNoEnc());
        String contactPhone = fieldCipher.decrypt(profile.contactPhoneEnc());
        return new TenantProfileResponse(profile.tenantId(),
                Masking.bizRegNo(businessRegNo),
                profile.ceoName(), profile.address(), profile.contactName(), profile.contactEmail(),
                Masking.phone(contactPhone),
                profile.updatedAt());
    }

    private TenantBillingResponse toResponse(TenantBilling billing) {
        //빌링키는 복호화하지 않는다 — 존재 여부만
        boolean hasBillingKey = billing.pgCustomerKeyEnc() != null && !billing.pgCustomerKeyEnc().isBlank();
        return new TenantBillingResponse(billing.tenantId(), billing.billingMethod(), billing.billingEmail(),
                hasBillingKey, Masking.card(billing.cardLast4()), billing.cardBrand(),
                billing.plan(), billing.billedFrom(), billing.memo(), billing.updatedAt());
    }

}
