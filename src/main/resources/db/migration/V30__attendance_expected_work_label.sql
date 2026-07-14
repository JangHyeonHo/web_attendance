-- =========================================================
-- V30: 근태 상세 월 합계 — 예정근무 라벨(W006)(#1)
--  월 합계 footer에 예정근무/인정휴게/실근무 합계를 나란히 표기
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W006','EXPECTED_WORK','KOR','예정근무'), ('W006','EXPECTED_WORK','ENG','Scheduled'), ('W006','EXPECTED_WORK','JPN','予定勤務'),
('W006','ACTUAL_WORK','KOR','실근무'), ('W006','ACTUAL_WORK','ENG','Actual'), ('W006','ACTUAL_WORK','JPN','実勤務');
