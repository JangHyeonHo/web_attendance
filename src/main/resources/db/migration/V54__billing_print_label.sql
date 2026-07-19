-- =========================================================
-- V54: 청구서 화면(W018) 인쇄 버튼 라벨 — 공용 PRINT(인쇄)와 구분해 '청구서 인쇄'로 명확화
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W018','PRINT_INVOICE','KOR','청구서 인쇄'), ('W018','PRINT_INVOICE','ENG','Print invoice'), ('W018','PRINT_INVOICE','JPN','請求書を印刷');
