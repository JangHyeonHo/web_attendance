-- =========================================================
-- V44: 휴가 잔여(W015) 표기 교정 — 컬럼 헤더 '남은'(단독이라 어색) → '잔여'
-- ENG 'Remaining' / JPN '残り'는 자연스러워 유지, KOR만 교정.
-- =========================================================

UPDATE language_master SET lang_value = '잔여'
 WHERE window_id = 'W015' AND lang_key = 'REMAINING' AND lang = 'KOR';
