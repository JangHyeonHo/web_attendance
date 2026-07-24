-- =========================================================
-- V89: 서비스 가제 MT(미라이타임) 적용 — 라벨에 박힌 구 표기 'Web Attendance' 교체.
--  대상은 '프로젝트명'으로 쓰인 표기만: 랜딩 부제(vX.X)·푸터 제품명·저작권.
--  일반명사로 쓰인 attendance(영문 설명문 등)는 대상 아님.
--  INDEX_TITLE ENG는 한/일( '웹 출결 관리 시스템' 계열 설명문)과 달리 구 서비스명을
--  그대로 쓰고 있었으므로 설명문으로 정렬한다.
-- =========================================================
UPDATE language_master SET lang_value = 'MT v2.0'
 WHERE window_id = 'W000' AND lang_key = 'INDEX_SUB';

UPDATE language_master SET lang_value = 'Web-based Attendance System'
 WHERE window_id = 'W000' AND lang_key = 'INDEX_TITLE' AND lang = 'ENG';

UPDATE language_master SET lang_value = 'MT — 웹 출결 관리 시스템'
 WHERE window_id = 'W000' AND lang_key = 'LANDING_FOOTER_PRODUCT' AND lang = 'KOR';
UPDATE language_master SET lang_value = 'MT — web-based attendance management'
 WHERE window_id = 'W000' AND lang_key = 'LANDING_FOOTER_PRODUCT' AND lang = 'ENG';
UPDATE language_master SET lang_value = 'MT — Web勤怠管理システム'
 WHERE window_id = 'W000' AND lang_key = 'LANDING_FOOTER_PRODUCT' AND lang = 'JPN';

UPDATE language_master SET lang_value = '© 2026 MT. All rights reserved.'
 WHERE window_id = 'W000' AND lang_key = 'LANDING_FOOTER_COPYRIGHT';
