package com.attendance.pro.billing;

import java.time.LocalDateTime;

/** 확정(마감) 청구서 한 건(invoice 테이블 스냅샷). */
public record Invoice(
        long tenantId,
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
        LocalDateTime issuedAt) {
}
