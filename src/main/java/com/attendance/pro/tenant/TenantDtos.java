package com.attendance.pro.tenant;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 테넌트 관리 API(/api/v1/system/tenants) 요청/응답 DTO 모음.
 */
public final class TenantDtos {

    private TenantDtos() {
    }

    @Schema(description = "schema.billing-method", enumAsRef = true)
    public enum BillingMethod {
        INVOICE, CARD
    }

    @Schema(description = "schema.tenant-create-request")
    public record TenantCreateRequest(
            @Schema(description = "schema.field.tenant-code", example = "ACME")
            @NotBlank(message = "{validation.tenant-code.required}")
            @Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "{validation.tenant-code.format}")
            String tenantCode,

            @Schema(description = "schema.field.tenant-name", example = "에이크미(주)")
            @NotBlank(message = "{validation.tenant-name.required}")
            @Size(max = 100, message = "{validation.tenant-name.size}")
            String name,

            @Schema(description = "schema.tenant-create-request.admin-email", example = "admin@acme.co.kr")
            @NotBlank(message = "{validation.email.required}")
            @Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}")
            String adminEmail,

            @Schema(description = "schema.tenant-create-request.admin-name", example = "김관리")
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 50, message = "{validation.name.size}")
            String adminName) {
    }

    @Schema(description = "schema.tenant-create-response")
    public record TenantCreateResponse(
            long tenantId, String tenantCode, String name, TenantStatus status,
            long adminUserId, String adminEmail,
            @Schema(description = "schema.field.initial-password") String initialPassword) {
    }
    //initialPassword는 이 응답에서 단 한 번만 평문 반환(저장은 BCrypt). 초대 링크는 Phase 3.

    @Schema(description = "schema.tenant-response")
    public record TenantResponse(
            long tenantId, String tenantCode, String name, TenantStatus status,
            @Schema(description = "schema.field.member-count") int memberCount,
            LocalDateTime createdAt) {
    }

    @Schema(description = "schema.tenant-status-request")
    public record TenantStatusRequest(
            @NotNull(message = "{validation.tenant-status.required}") TenantStatus status) {
    }

    @Schema(description = "schema.tenant-profile-request")
    public record TenantProfileRequest(
            @Schema(description = "schema.field.country", example = "KR")
            @NotBlank(message = "{validation.country.required}")
            @Pattern(regexp = "KR|JP", message = "{validation.country.supported}")
            String country,                                           //사업자 식별번호 체계를 결정(KR/JP)

            //형식은 국가별(KR ###-##-#####, JP 13자리)이라 어노테이션이 아닌 서비스에서 검증
            @NotBlank(message = "{validation.biz-reg-no.required}")
            @Size(max = 20, message = "{validation.biz-reg-no.size}")
            String businessRegNo,                                     //[암호화 저장]
            @Size(max = 50, message = "{validation.ceo-name.size}") String ceoName,
            @Size(max = 200, message = "{validation.address.size}") String address,
            @Size(max = 50, message = "{validation.contact-name.size}") String contactName,
            @Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}") String contactEmail,
            @Pattern(regexp = "^[0-9+][0-9\\- ]{1,19}$", message = "{validation.contact-phone.format}")
            @Size(max = 20, message = "{validation.contact-phone.size}") String contactPhone) { //[암호화 저장] — 형식 검증으로 마스킹값 재제출 차단(MSK-10)

        @Override
        public String toString() {  //로그 유출 방지: 민감 필드는 toString에서 제외
            return "TenantProfileRequest[businessRegNo=***, contactPhone=***, ceoName=%s]".formatted(ceoName);
        }
    }

    @Schema(description = "schema.tenant-profile-response")
    public record TenantProfileResponse(
            long tenantId,
            @Schema(description = "schema.field.country", example = "KR")
            String country,                                           //KR=사업자등록번호, JP=法人番号 — 프론트 라벨 분기
            @Schema(description = "schema.field.biz-reg-no-masked", example = "123-**-*****")
            String businessRegNoMasked,                               //마스킹값 — 필드명으로 마스킹 여부를 드러냄(오독 방지)
            String ceoName, String address, String contactName, String contactEmail,
            @Schema(description = "schema.field.contact-phone-masked", example = "010-****-5678")
            String contactPhoneMasked,                                //마스킹값
            LocalDateTime updatedAt) {
    }

    @Schema(description = "schema.tenant-billing-request")
    public record TenantBillingRequest(
            @NotNull(message = "{validation.billing-method.required}") BillingMethod billingMethod,
            @Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}") String billingEmail,
            @Size(max = 200, message = "{validation.pg-key.size}") String pgCustomerKey, //[암호화 저장, CARD 필수]
            @Pattern(regexp = "^\\d{4}$", message = "{validation.card-last4.format}") String cardLast4,
            @Size(max = 20, message = "{validation.card-brand.size}") String cardBrand,
            @Size(max = 20, message = "{validation.plan.size}") String plan,
            LocalDate billedFrom,
            @Size(max = 500, message = "{validation.memo.size}") String memo) {

        @Override
        public String toString() {  //빌링키 로그 유출 방지
            return "TenantBillingRequest[billingMethod=%s, pgCustomerKey=***]".formatted(billingMethod);
        }
    }

    @Schema(description = "schema.tenant-billing-response")
    public record TenantBillingResponse(
            long tenantId, BillingMethod billingMethod, String billingEmail,
            @Schema(description = "schema.field.has-billing-key")
            boolean hasBillingKey,                                    //빌링키는 존재 여부만. 원문은 어떤 응답에도 없음
            @Schema(description = "schema.field.card-masked", example = "**** **** **** 1234")
            String cardMasked,                                        //card_last4로 조립. 빌링키 필드는 존재하지 않음
            String cardBrand, String plan, LocalDate billedFrom, String memo,
            LocalDateTime updatedAt) {
    }

}
