-- =========================================================
-- V65: 월 로타 편집기(W009 멤버 관리) 라벨(#13)
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','ROTA_TITLE','KOR','월 근무 로타'),        ('W009','ROTA_TITLE','ENG','Monthly rota'),       ('W009','ROTA_TITLE','JPN','月間ロタ'),
('W009','ROTA_EDIT','KOR','월 로타 편집'),         ('W009','ROTA_EDIT','ENG','Edit monthly rota'),   ('W009','ROTA_EDIT','JPN','月間ロタ編集'),
('W009','ROTA_HINT','KOR','고정 스케줄 위에 특정 날짜의 예외(야간 교대·휴무·다른 시간)를 월 단위로 지정할 수 있습니다.'),
('W009','ROTA_HINT','ENG','On top of the fixed schedule, set per-date exceptions (night shift, day off, different hours) by month.'),
('W009','ROTA_HINT','JPN','固定スケジュールの上に、特定日の例外（夜勤・休務・別時間）を月単位で指定できます。'),
('W009','SHIFT_DEFAULT','KOR','기본'),   ('W009','SHIFT_DEFAULT','ENG','Default'), ('W009','SHIFT_DEFAULT','JPN','基本'),
('W009','SHIFT_OFF','KOR','휴무'),       ('W009','SHIFT_OFF','ENG','Off'),         ('W009','SHIFT_OFF','JPN','休務'),
('W009','SHIFT_WORK','KOR','근무'),      ('W009','SHIFT_WORK','ENG','Work'),       ('W009','SHIFT_WORK','JPN','勤務'),
('W009','OVERNIGHT','KOR','야간(자정 넘김)'), ('W009','OVERNIGHT','ENG','Overnight'), ('W009','OVERNIGHT','JPN','夜勤（日跨ぎ）'),
('W009','FILL_WEEKDAYS','KOR','적용 요일'),  ('W009','FILL_WEEKDAYS','ENG','Apply to weekdays'), ('W009','FILL_WEEKDAYS','JPN','適用曜日'),
('W009','FILL_APPLY','KOR','요일 일괄 적용'), ('W009','FILL_APPLY','ENG','Apply'),      ('W009','FILL_APPLY','JPN','一括適用');
