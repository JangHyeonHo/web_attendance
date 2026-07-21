-- 근태 마감 관리(W021) — 목록을 '결재 대기'(상시)와 '마감 완료'(선택 월)로 분리해
-- 승인 이력이 쌓여도 목록이 무한정 길어지지 않도록 한다.
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W021','CLOSE_PENDING_SECTION','KOR','결재 대기'),      ('W021','CLOSE_PENDING_SECTION','ENG','Pending approval'), ('W021','CLOSE_PENDING_SECTION','JPN','決裁待ち'),
('W021','CLOSE_APPROVED_SECTION','KOR','마감 완료 (대상 월 조회)'), ('W021','CLOSE_APPROVED_SECTION','ENG','Closed (by target month)'), ('W021','CLOSE_APPROVED_SECTION','JPN','締め完了（対象月で照会）'),
('W021','CLOSE_APPROVED_NONE','KOR','해당 월에 마감 완료된 건이 없습니다.'),
('W021','CLOSE_APPROVED_NONE','ENG','No closed months for the selected month.'),
('W021','CLOSE_APPROVED_NONE','JPN','その月に締め完了した件はありません。');
