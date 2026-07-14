-- =========================================================
-- V26: 휴가 잔여 만기일 테이블 라벨(W015)
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W015','LEAVE_TYPE','KOR','휴가 종류'),  ('W015','LEAVE_TYPE','ENG','Leave type'),  ('W015','LEAVE_TYPE','JPN','休暇の種類'),
('W015','REMAINING','KOR','남은'),        ('W015','REMAINING','ENG','Remaining'),    ('W015','REMAINING','JPN','残り'),
('W015','EXPIRES','KOR','만기일'),        ('W015','EXPIRES','ENG','Expires'),        ('W015','EXPIRES','JPN','期限'),
('W015','NO_EXPIRY','KOR','무기한'),      ('W015','NO_EXPIRY','ENG','No expiry'),    ('W015','NO_EXPIRY','JPN','無期限');
