-- =========================================================
-- V48: 청구서 품목 라벨 개선 — '무료 인원(반값 적용)'은 "무료인데 과금"으로 모순처럼 읽혀
-- '기본 인원 (반값)'으로 교정(‘추가 인원’과 기본/추가 대구, 단가 칸의 반값과 일관).
-- =========================================================

UPDATE language_master SET lang_value = '기본 인원 (반값)'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'KOR';
UPDATE language_master SET lang_value = 'Base seats (50%)'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'ENG';
UPDATE language_master SET lang_value = '基本人数（半額）'
 WHERE window_id = 'W999' AND lang_key = 'INV_LINE_FREE' AND lang = 'JPN';
