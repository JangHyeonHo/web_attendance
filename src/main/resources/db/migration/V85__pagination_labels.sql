-- =========================================================
-- V85: 페이지네이션 공통 라벨(#9 리스트 상한 정책).
--  무한 증가 리스트(멤버 목록·휴가 신청 내역)를 페이지 번호 방식으로 전환하며
--  이전/다음 버튼 접근성 라벨과 전체 건수 표기를 공통(W999)으로 시드한다.
-- =========================================================
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W999','PAGE_PREV','KOR','이전 페이지'), ('W999','PAGE_PREV','ENG','Previous page'), ('W999','PAGE_PREV','JPN','前のページ'),
('W999','PAGE_NEXT','KOR','다음 페이지'), ('W999','PAGE_NEXT','ENG','Next page'),     ('W999','PAGE_NEXT','JPN','次のページ'),
('W999','PAGE_TOTAL','KOR','전체 {0}건'), ('W999','PAGE_TOTAL','ENG','{0} total'),    ('W999','PAGE_TOTAL','JPN','全{0}件');
