-- =========================================================
-- V58: 청구서(W018) 회사 정보 미등록 안내 라벨 — 청구월과 청구서 사이 경고 박스에 표시.
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W018','INVOICE_PROFILE_MISSING','KOR','청구서에 표시할 회사 정보가 없습니다. 회사 정보를 입력해 주세요.'),
('W018','INVOICE_PROFILE_MISSING','ENG','No company information to show on this invoice. Please enter your company info.'),
('W018','INVOICE_PROFILE_MISSING','JPN','請求書に表示する会社情報がありません。会社情報を入力してください。');
