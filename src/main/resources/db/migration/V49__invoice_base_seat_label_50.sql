-- =========================================================
-- V49: 청구서 품목 라벨 — '기본 인원 (반값)'의 '반값'이 캐주얼해 '기본 인원 (50%)'로 교정.
-- =========================================================

UPDATE language_master SET lang_value = '기본 인원 (50%)'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'KOR';
UPDATE language_master SET lang_value = 'Base seats (50%)'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'ENG';
UPDATE language_master SET lang_value = '基本人数（50%）'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'JPN';
