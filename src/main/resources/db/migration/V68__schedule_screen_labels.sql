-- =========================================================
-- V68: 통합 근무 스케줄 화면(반복 패턴 + 월 로타 한 화면) 라벨(W009, #13)
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SCHEDULE_TITLE','KOR','근무 스케줄'),     ('W009','SCHEDULE_TITLE','ENG','Work schedule'),   ('W009','SCHEDULE_TITLE','JPN','勤務スケジュール'),
('W009','SCHEDULE_MANAGE','KOR','근무 스케줄'),    ('W009','SCHEDULE_MANAGE','ENG','Work schedule'),  ('W009','SCHEDULE_MANAGE','JPN','勤務スケジュール'),
('W009','PATTERN_SECTION','KOR','반복 패턴'),      ('W009','PATTERN_SECTION','ENG','Repeating pattern'), ('W009','PATTERN_SECTION','JPN','繰り返しパターン'),
('W009','ROTA_SECTION','KOR','월 달력(예외 지정)'), ('W009','ROTA_SECTION','ENG','Monthly calendar (exceptions)'), ('W009','ROTA_SECTION','JPN','月間カレンダー（例外指定）'),
('W009','FOLLOW_PATTERN','KOR','패턴 따름'),       ('W009','FOLLOW_PATTERN','ENG','Follow pattern'),  ('W009','FOLLOW_PATTERN','JPN','パターンに従う'),
('W009','SAVE_PATTERN','KOR','패턴 저장'),         ('W009','SAVE_PATTERN','ENG','Save pattern'),      ('W009','SAVE_PATTERN','JPN','パターン保存'),
('W009','SAVE_ROTA','KOR','달력 저장'),            ('W009','SAVE_ROTA','ENG','Save calendar'),        ('W009','SAVE_ROTA','JPN','カレンダー保存');
