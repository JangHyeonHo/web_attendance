-- 스케줄 화면 통합(#1) — 멤버 관리의 '스케줄 수정' 모달(개인 기본 근무시간·요일)을
-- 근무 스케줄 화면 상단 '개인 기본 스케줄' 섹션으로 흡수. 별도 모달을 없애고 한 화면에서 편집한다.
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','DEFAULT_SCHEDULE','KOR','개인 기본 스케줄'),  ('W009','DEFAULT_SCHEDULE','ENG','Personal default'), ('W009','DEFAULT_SCHEDULE','JPN','個人の基本スケジュール'),
('W009','DEFAULT_HINT','KOR','정기·상세 스케줄이 없는 날에 적용되는 기본 근무 시간과 근무 요일입니다.'),
('W009','DEFAULT_HINT','ENG','Base hours and workdays applied when no regular or per-day schedule exists.'),
('W009','DEFAULT_HINT','JPN','定期・詳細スケジュールがない日に適用される基本勤務時間と勤務曜日です。');
