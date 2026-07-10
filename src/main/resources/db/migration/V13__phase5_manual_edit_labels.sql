-- =========================================================
-- V13: Phase 5.2 — 수동 정정 수정(잘못 입력 복구) 화면 텍스트
--  이력 삭제는 제공하지 않는다 — 복구는 수정(시각/구분/사유 변경)으로.
-- =========================================================

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W006','EDIT','KOR','수정'),
('W006','EDIT','ENG','Edit'),
('W006','EDIT','JPN','変更'),
-- 정정 모달의 출근/퇴근 동시 등록 안내
('W006','MANUAL_HINT','KOR','등록할 항목을 켜고 시각을 선택하세요. 출근과 퇴근을 한 번에 등록할 수 있습니다.'),
('W006','MANUAL_HINT','ENG','Turn on the items to register and pick the time. Clock-in and clock-out can be registered together.'),
('W006','MANUAL_HINT','JPN','登録する項目をオンにして時刻を選択してください。出勤と退勤を同時に登録できます。');
