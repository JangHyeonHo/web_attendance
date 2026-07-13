-- =========================================================
-- V14: Phase 5.3 — 휴식 시간 수동 정정 + 인정 휴게 표시 화면 텍스트
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- W006: 인정 휴게(단일 열 — 실휴식/법정휴게 통합)
('W006','BREAK_RECOGNIZED','KOR','휴게'),
('W006','BREAK_RECOGNIZED','ENG','Break'),
('W006','BREAK_RECOGNIZED','JPN','休憩'),
-- W006: 정정 모달의 휴식 행(시작~종료)
('W006','TYPE_BREAK','KOR','휴식'),
('W006','TYPE_BREAK','ENG','Break'),
('W006','TYPE_BREAK','JPN','休憩'),
('W006','BREAK_START','KOR','시작'),
('W006','BREAK_START','ENG','Start'),
('W006','BREAK_START','JPN','開始'),
('W006','BREAK_END','KOR','종료'),
('W006','BREAK_END','ENG','End'),
('W006','BREAK_END','JPN','終了');
