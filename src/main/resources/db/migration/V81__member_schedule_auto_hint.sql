-- 멤버 등록(W009) — 근무시간 입력을 없애고, 회사 기본 스케줄이 정기 스케줄로 자동 생성됨을 안내(스케줄 단일화).
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SCHEDULE_AUTO_HINT','KOR','근무 스케줄은 회사 기본 스케줄로 자동 설정됩니다. 등록 후 ''관리 → 근무 스케줄''에서 개별 조정하세요.'),
('W009','SCHEDULE_AUTO_HINT','ENG','The work schedule is set from the company default. Adjust it per member under Manage → Work schedule after registration.'),
('W009','SCHEDULE_AUTO_HINT','JPN','勤務スケジュールは会社の基本スケジュールで自動設定されます。登録後「管理 → 勤務スケジュール」で個別に調整してください。');
