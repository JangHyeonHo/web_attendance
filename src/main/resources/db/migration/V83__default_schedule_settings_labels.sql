-- 회사설정(W020) — 신규 멤버 기본 스케줄 편집 라벨(스케줄 단일화 ③).
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W020','DEFAULT_SCHEDULE_TITLE','KOR','신규 멤버 기본 스케줄'), ('W020','DEFAULT_SCHEDULE_TITLE','ENG','New member default schedule'), ('W020','DEFAULT_SCHEDULE_TITLE','JPN','新規メンバーの基本スケジュール'),
('W020','DEFAULT_SCHEDULE_NOTE','KOR','멤버를 등록하면 이 스케줄이 그 멤버의 정기 스케줄로 자동 적용됩니다. 이후 ''멤버 → 관리 → 근무 스케줄''에서 개별 조정하세요.'),
('W020','DEFAULT_SCHEDULE_NOTE','ENG','When a member is registered, this schedule is applied as their regular schedule. Adjust per member under Members → Manage → Work schedule.'),
('W020','DEFAULT_SCHEDULE_NOTE','JPN','メンバーを登録すると、このスケジュールがそのメンバーの定期スケジュールとして適用されます。以降は「メンバー → 管理 → 勤務スケジュール」で個別に調整してください。'),
('W020','SHIFT_WORK','KOR','근무'),      ('W020','SHIFT_WORK','ENG','Work'),  ('W020','SHIFT_WORK','JPN','勤務'),
('W020','SHIFT_OFF','KOR','휴무'),       ('W020','SHIFT_OFF','ENG','Off'),    ('W020','SHIFT_OFF','JPN','休務'),
('W020','WORK_START','KOR','근무 시작'),  ('W020','WORK_START','ENG','Start'), ('W020','WORK_START','JPN','始業'),
('W020','WORK_END','KOR','근무 종료'),    ('W020','WORK_END','ENG','End'),     ('W020','WORK_END','JPN','終業'),
('W020','NIGHT_WORK','KOR','야간(자정 넘김)'), ('W020','NIGHT_WORK','ENG','Overnight'), ('W020','NIGHT_WORK','JPN','夜勤（日跨ぎ）');
