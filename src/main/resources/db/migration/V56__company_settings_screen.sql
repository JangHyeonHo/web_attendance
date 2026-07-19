-- =========================================================
-- V56: 회사 설정 화면(W020) 분리 — 회사 정보/결제(W019, 총관리자 전용)에서
--      운영 설정(근태 보고서 등)을 떼어 총관리자+인사관리자가 관리하는 별도 화면으로.
--      결재란 설정 라벨(REPORT_*)은 이미 W999 공용이라 재시드 불요 — 제목·나비 라벨만 추가.
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','COMPANY_SETTINGS','KOR','회사 설정'),       ('W999','COMPANY_SETTINGS','ENG','Company settings'),  ('W999','COMPANY_SETTINGS','JPN','会社設定'),
('W999','COMPANY_SETTINGS_TITLE','KOR','회사 설정'), ('W999','COMPANY_SETTINGS_TITLE','ENG','Company settings'), ('W999','COMPANY_SETTINGS_TITLE','JPN','会社設定'),
('W999','COMPANY_SETTINGS_NOTE','KOR','근태 보고서 등 회사 공통 운영 설정입니다. 총관리자·인사관리자가 관리합니다.'),
('W999','COMPANY_SETTINGS_NOTE','ENG','Company-wide operational settings such as the attendance report. Managed by the owner and HR admins.'),
('W999','COMPANY_SETTINGS_NOTE','JPN','勤怠レポートなど会社共通の運用設定です。総括管理者・人事管理者が管理します。');
