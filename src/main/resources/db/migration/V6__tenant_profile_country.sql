-- =========================================================
-- V6: 테넌트 기업 정보의 소재국 도입 (한/일 양국 지원)
--  - tenant_profile.country: 사업자 식별번호 체계를 결정
--    (KR=사업자등록번호 ###-##-#####, JP=法人番号 13자리)
--  - 기존 행은 전부 KR(현행 데이터가 한국 형식 검증을 통과한 값이므로)
--  - 지원 국가 목록은 앱(ProfileCountry enum)이 단일 출처 — DB CHECK를 두지 않아
--    국가 추가 시 마이그레이션 없이 enum 확장만으로 대응
-- =========================================================

ALTER TABLE tenant_profile
    ADD COLUMN IF NOT EXISTS country CHAR(2) NOT NULL DEFAULT 'KR'
        COMMENT '소재국(ISO 3166-1 alpha-2) — 사업자 식별번호 체계 결정' AFTER tenant_id;

-- ---------------------------------------------------------
-- W008 화면 라벨: 국가 선택 + 국가별 사업자 식별번호 명칭
-- (BIZ_REG_NO 범용 키는 잔존 허용 — 신 코드는 국가별 키만 사용)
-- ---------------------------------------------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W008','COUNTRY','KOR','소재국'),
('W008','COUNTRY','ENG','Country'),
('W008','COUNTRY','JPN','所在国'),
('W008','COUNTRY_KR','KOR','대한민국'),
('W008','COUNTRY_KR','ENG','South Korea'),
('W008','COUNTRY_KR','JPN','韓国'),
('W008','COUNTRY_JP','KOR','일본'),
('W008','COUNTRY_JP','ENG','Japan'),
('W008','COUNTRY_JP','JPN','日本'),
('W008','BIZ_REG_NO_KR','KOR','사업자등록번호'),
('W008','BIZ_REG_NO_KR','ENG','Business Registration No. (KR)'),
('W008','BIZ_REG_NO_KR','JPN','事業者登録番号（韓国）'),
('W008','BIZ_REG_NO_JP','KOR','법인번호(일본)'),
('W008','BIZ_REG_NO_JP','ENG','Corporate Number (JP)'),
('W008','BIZ_REG_NO_JP','JPN','法人番号');
