-- =========================================================
-- V22: 인당(seat) 과금
--  - 기존 tenant_billing에 인당 단가·무료 인원 확장(운영사가 회사별 설정)
--  - 월중 최대 활성 좌석 사용량(high-water mark) 기록 테이블
--  - 마감 확정 청구서 스냅샷(단가/인원/금액을 확정 시점 기준으로 보존)
-- 과금식: 과금인원 = max(0, 월중최대활성 - 무료인원), 공급가 = 과금인원 × 인당단가,
--         부가세 = round(공급가 × 10%), 합계 = 공급가 + 부가세. 통화 KRW.
-- =========================================================

ALTER TABLE tenant_billing
    ADD COLUMN IF NOT EXISTS per_seat_amount INT NOT NULL DEFAULT 2000
        COMMENT '인당 월 단가(원, VAT 별도)' AFTER plan,
    ADD COLUMN IF NOT EXISTS free_seats INT NOT NULL DEFAULT 5
        COMMENT '무료 인원(이 수까지는 과금 제외)' AFTER per_seat_amount;

-- 월중 최대 활성 사용자 수(월별 high-water mark). 멤버 활성/비활성/삭제·청구서 조회 시 갱신,
-- 항상 GREATEST로만 올라가 그 달의 피크를 보존한다(월중 최대 과금 기준).
CREATE TABLE IF NOT EXISTS tenant_seat_usage (
    tenant_id  BIGINT   NOT NULL COMMENT '테넌트 ID',
    ym         CHAR(7)  NOT NULL COMMENT '청구월(YYYY-MM)',
    max_seats  INT      NOT NULL DEFAULT 0 COMMENT '월중 최대 활성 사용자 수',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, ym)
) COMMENT '월별 최대 좌석 사용량(월중 최대 과금 기준)';

-- 마감(확정) 청구서. 이 테이블에 행이 있으면 그 달은 확정(ISSUED), 없으면 잠정(조회 시 실시간 계산).
-- 단가/무료인원/최대인원을 확정 시점 값으로 스냅샷해 이후 단가 변경이 과거 청구서에 소급되지 않는다.
CREATE TABLE IF NOT EXISTS invoice (
    tenant_id    BIGINT   NOT NULL COMMENT '테넌트 ID',
    ym           CHAR(7)  NOT NULL COMMENT '청구월(YYYY-MM)',
    max_seats    INT      NOT NULL COMMENT '월중 최대 활성 수(스냅샷)',
    free_seats   INT      NOT NULL COMMENT '무료 인원(스냅샷)',
    billed_seats INT      NOT NULL COMMENT '과금 인원 = max(0, max_seats - free_seats)',
    unit_price   INT      NOT NULL COMMENT '인당 단가 스냅샷(원, VAT 별도)',
    subtotal     INT      NOT NULL COMMENT '공급가(과금인원 × 단가)',
    vat          INT      NOT NULL COMMENT '부가세(공급가 × 10%, 반올림)',
    total        INT      NOT NULL COMMENT '합계(공급가 + 부가세)',
    issued_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '마감 확정 시각',
    PRIMARY KEY (tenant_id, ym)
) COMMENT '확정(마감) 청구서 스냅샷';
