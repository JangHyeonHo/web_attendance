-- =========================================================
-- V24: 청구서 금액 오버플로 방지 — subtotal/vat/total을 BIGINT로.
-- per_seat_amount 상한(1천만)×수백 좌석이면 INT(약 21.4억)를 넘겨 음수로 오버플로된다.
-- 인원(max/free/billed)·단가는 INT 유지(현실 범위), 금액 3열만 BIGINT.
-- =========================================================

ALTER TABLE invoice
    MODIFY subtotal BIGINT NOT NULL COMMENT '공급가(과금인원 × 단가)',
    MODIFY vat      BIGINT NOT NULL COMMENT '부가세(공급가 × 10%, 반올림)',
    MODIFY total    BIGINT NOT NULL COMMENT '합계(공급가 + 부가세)';
