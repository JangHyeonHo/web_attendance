-- =========================================================
-- V46: 내보내기/인쇄 공용 라벨(W999) — 근태(W006)·청구서(W018) 등에서 공용 사용
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','EXPORT_EXCEL','KOR','Excel 내보내기'), ('W999','EXPORT_EXCEL','ENG','Export Excel'), ('W999','EXPORT_EXCEL','JPN','Excel出力'),
('W999','PRINT','KOR','인쇄'),                  ('W999','PRINT','ENG','Print'),          ('W999','PRINT','JPN','印刷');
