package com.attendance.pro.billing;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 인당 과금 청구서 DTO. */
public final class BillingDtos {

    private BillingDtos() {
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
            int unitPrice,
            long subtotal,
            long vat,
            long total,
            InvoiceStatus status,
            LocalDateTime issuedAt) {

        static InvoiceResponse issued(Invoice i) {
            return new InvoiceResponse(i.ym(), i.maxSeats(), i.freeSeats(), i.billedSeats(),
                    i.unitPrice(), i.subtotal(), i.vat(), i.total(), InvoiceStatus.ISSUED, i.issuedAt());
        }
    }
}
