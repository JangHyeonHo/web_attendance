-- =========================================================
-- V51: 근태 보고서 결재(도장)란 설정·인쇄 라벨(W999 공용 — W019 설정·W006 인쇄).
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','REPORT_SETTINGS','KOR','근태 보고서 설정'),  ('W999','REPORT_SETTINGS','ENG','Attendance report'),   ('W999','REPORT_SETTINGS','JPN','勤怠レポート設定'),
('W999','REPORT_STAMP_TOGGLE','KOR','결재(도장)란 표시'), ('W999','REPORT_STAMP_TOGGLE','ENG','Show approval (stamp) box'), ('W999','REPORT_STAMP_TOGGLE','JPN','決裁（押印）欄を表示'),
('W999','REPORT_STAMP_HINT','KOR','켜면 Excel·인쇄 근태 보고서 우상단에 결재란(인사담당자·총괄담당자)이 표시되어, 인쇄 후 도장을 받을 수 있습니다.'),
('W999','REPORT_STAMP_HINT','ENG','When on, an approval box (HR/General manager) appears at the top-right of the Excel and printed attendance report for stamping.'),
('W999','REPORT_STAMP_HINT','JPN','オンにすると、Excel・印刷の勤怠レポート右上に決裁欄（人事担当者・総括担当者）が表示され、印刷後に押印できます。'),
('W999','APPROVAL','KOR','결재'),          ('W999','APPROVAL','ENG','Approval'),        ('W999','APPROVAL','JPN','決裁'),
('W999','HR_MANAGER','KOR','인사담당자'),    ('W999','HR_MANAGER','ENG','HR manager'),    ('W999','HR_MANAGER','JPN','人事担当者'),
('W999','GENERAL_MANAGER','KOR','총괄담당자'), ('W999','GENERAL_MANAGER','ENG','General manager'), ('W999','GENERAL_MANAGER','JPN','総括担当者');
