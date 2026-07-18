package com.attendance.pro.billing;

import java.time.LocalDateTime;

import com.attendance.pro.tenant.TenantDtos.BillingMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 인당 과금 청구서 DTO. */
public final class BillingDtos {

    private BillingDtos() {
    }

    /**
     * 회사(테넌트) 결제 정보 조회 응답(#14) — 결제수단·청구 이메일·비고만.
     * 카드 원본/PG 키 등 민감 정보는 반환하지 않는다(등록 화면은 방식·연락처만 다룬다).
     */
    @Schema(description = "schema.billing-profile")
    public record BillingProfileResponse(
            BillingMethod billingMethod,
            String billingEmail,
            String memo) {
    }

    /** 회사 결제 정보 등록/수정 요청(#14). 가격 필드는 포함하지 않는다(운영사 전용). */
    public record BillingProfileRequest(
            @NotNull BillingMethod billingMethod,
            @Email @Size(max = 100) String billingEmail,
            @Size(max = 500) String memo) {
    }

    /**
     * 회사에 보여주는 계약 요약(#14) — 읽기 전용. 요금제·인당 단가·무료 좌석은 운영사(계약)가 정하는 값이라
     * 회사는 조회만 한다("5명 넘으면 과금" 같은 조건을 회사가 확인할 수 있게).
     */
    @Schema(description = "schema.billing-contract")
    public record ContractSummaryResponse(
            String plan,
            int perSeatAmount,
            int freeSeats) {
    }

    /** 청구서 확정 상태 — PROVISIONAL(진행 중/미마감, 실시간 계산) / ISSUED(마감 확정 스냅샷). */
    public enum InvoiceStatus {
        PROVISIONAL, ISSUED
    }

    /**
     * 월별 청구서 한 건. 금액은 전부 원(KRW), 부가세 별도 라인.
     * issuedAt은 ISSUED일 때만 값이 있다(PROVISIONAL은 null).
     */
    @Schema(description = "schema.invoice-response")
    public record InvoiceResponse(
            String ym,
            int maxSeats,
            int freeSeats,
            int billedSeats,
            long seatDays,
            int daysInMonth,
            int unitPrice,
            long subtotal,
            long vat,
            long total,
            InvoiceStatus status,
            LocalDateTime issuedAt) {

        static InvoiceResponse issued(Invoice i) {
            return new InvoiceResponse(i.ym(), i.maxSeats(), i.freeSeats(), i.billedSeats(),
                    i.seatDays(), i.daysInMonth(), i.unitPrice(), i.subtotal(), i.vat(), i.total(),
                    InvoiceStatus.ISSUED, i.issuedAt());
        }
    }
}
