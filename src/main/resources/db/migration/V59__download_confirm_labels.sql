-- =========================================================
-- V59: 근무표 다운로드 확인 모달 라벨 — 공용 CONFIRM(W999) + 확인 문구(W006, {ym}/{fmt} 치환)
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','CONFIRM','KOR','확인'), ('W999','CONFIRM','ENG','Confirm'), ('W999','CONFIRM','JPN','確認'),
('W006','DOWNLOAD_CONFIRM','KOR','{ym} 근무표를 {fmt} 형식으로 다운로드합니다.'),
('W006','DOWNLOAD_CONFIRM','ENG','Download the {ym} timesheet as {fmt}.'),
('W006','DOWNLOAD_CONFIRM','JPN','{ym} の勤務表を {fmt} でダウンロードします。');
