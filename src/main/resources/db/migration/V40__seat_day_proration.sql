-- =========================================================
-- V40: 등록 시점 일할계산(seat-day proration) 도입
--  - 좌석 변동 이벤트 로그(재생용): 변동일 + 변동 후 활성 좌석 수
--  - invoice에 좌석-일 누적·청구월 일수 스냅샷 컬럼 추가
-- 정책(docs/billing-calculation.md §4 확정 v1):
--   · 증원/무료→유료: 등록일 "다음 날부터" 월말까지 일할 과금(당일 제외)
--   · 감원/유료→무료: 당월 유지, 다음 달 1일부터 반영(비대칭)
--   · 공급가 = round(좌석일 × 인당월단가 ÷ 월일수), 좌석일 = 기초좌석×일수 + Σ(증원Δ×잔여일)
-- =========================================================

CREATE TABLE IF NOT EXISTS seat_change_event (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'PK',
    tenant_id    BIGINT   NOT NULL COMMENT '테넌트 ID',
    event_date   DATE     NOT NULL COMMENT '변동 발효일(서버 날짜)',
    active_seats INT      NOT NULL COMMENT '변동 후 활성 좌석(ACTIVE·비삭제·SYSTEM_ADMIN 제외)',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 시각',
    PRIMARY KEY (id),
    KEY idx_seat_event (tenant_id, event_date, id)
) COMMENT '좌석 변동 이벤트 로그(일할계산 재생용)';

ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS seat_days BIGINT NOT NULL DEFAULT 0
        COMMENT '좌석-일 누적(기초좌석×일수 + Σ증원×잔여일)' AFTER billed_seats,
    ADD COLUMN IF NOT EXISTS days_in_month INT NOT NULL DEFAULT 0
        COMMENT '청구월 일수(스냅샷)' AFTER seat_days;

-- 기존 테넌트 이월 기준선: 전월 말일자로 현재 활성 좌석 1건 시드.
-- event_date < 당월 1일 이므로 도입 월은 "기초좌석(전액)"으로 계산돼 기존 동작과 연속(도입 월 일할 왜곡 없음).
INSERT INTO seat_change_event (tenant_id, event_date, active_seats)
SELECT t.tenant_id,
       LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)),
       (SELECT COUNT(*) FROM users u
         WHERE u.tenant_id = t.tenant_id AND u.status = 'ACTIVE'
           AND u.deleted = FALSE AND u.role <> 'SYSTEM_ADMIN')
FROM tenant t;
