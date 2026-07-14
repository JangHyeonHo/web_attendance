-- =========================================================
-- V29: 공휴일 날짜 중복 허용(#7)
--  - PK(tenant_id, holiday_date) → 대리키 holiday_id 로 교체
--  - 같은 날짜에 여러 공휴일(국가+회사, 회사 다건) 공존 가능
--  - FK(fk_holiday_tenant) 백킹 인덱스는 idx_holiday_tenant_date로 대체(조회도 이 인덱스 사용)
--  - 명칭 수정/삭제(개별) 경로는 제거(읽기전용) — 동기화만 NATIONAL을 연도 단위로 교체
-- =========================================================
ALTER TABLE holiday
    DROP PRIMARY KEY,
    ADD COLUMN holiday_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD INDEX idx_holiday_tenant_date (tenant_id, holiday_date);
