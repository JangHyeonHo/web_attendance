-- =========================================================
-- V13: Phase 5.1 — 수동 정정 삭제(잘못 입력 복구) 화면 텍스트
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W006','DELETE','KOR','삭제'),
('W006','DELETE','ENG','Delete'),
('W006','DELETE','JPN','削除'),
('W006','DELETE_CONFIRM','KOR','이 정정 기록을 삭제할까요?'),
('W006','DELETE_CONFIRM','ENG','Delete this correction record?'),
('W006','DELETE_CONFIRM','JPN','この修正記録を削除しますか？'),
-- 정정 모달의 출근/퇴근 동시 등록 섹션
('W006','MANUAL_HINT','KOR','등록할 항목을 켜고 시각을 선택하세요. 출근과 퇴근을 한 번에 등록할 수 있습니다.'),
('W006','MANUAL_HINT','ENG','Turn on the items to register and pick the time. Clock-in and clock-out can be registered together.'),
('W006','MANUAL_HINT','JPN','登録する項目をオンにして時刻を選択してください。出勤と退勤を同時に登録できます。');
