-- =========================================================
-- V53: 기업 정보 주소 세분화 — 우편번호·상세주소 컬럼 추가(기존 address=기본주소 유지)
--      + 회사 정보(W019) 라벨 + 청구서 공급받는자(W999) 라벨
-- =========================================================

ALTER TABLE tenant_profile
    ADD COLUMN postal_code    VARCHAR(10)  NULL COMMENT '우편번호' AFTER ceo_name,
    ADD COLUMN address_detail VARCHAR(200) NULL COMMENT '상세주소' AFTER address;

-- 회사 정보(W019, 회사 자율관리)·테넌트 상세(W008, 시스템관리자) 공통 입력 라벨 — 주소(ADDRESS)는 이미 존재
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W019','POSTAL_CODE','KOR','우편번호'),   ('W019','POSTAL_CODE','ENG','Postal code'),      ('W019','POSTAL_CODE','JPN','郵便番号'),
('W019','ADDRESS_DETAIL','KOR','상세주소'), ('W019','ADDRESS_DETAIL','ENG','Address detail'), ('W019','ADDRESS_DETAIL','JPN','詳細住所'),
('W008','POSTAL_CODE','KOR','우편번호'),   ('W008','POSTAL_CODE','ENG','Postal code'),      ('W008','POSTAL_CODE','JPN','郵便番号'),
('W008','ADDRESS_DETAIL','KOR','상세주소'), ('W008','ADDRESS_DETAIL','ENG','Address detail'), ('W008','ADDRESS_DETAIL','JPN','詳細住所');

-- 청구서 공급받는자 블록(W999) — 대표자 라벨(주소는 라벨 없이 표기)
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','INV_CEO','KOR','대표자'), ('W999','INV_CEO','ENG','Representative'), ('W999','INV_CEO','JPN','代表者');
