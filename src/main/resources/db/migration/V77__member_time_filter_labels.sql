-- 멤버 관리(W009) — 근무 시간대 검색(#6) + 필터 초기화 라벨. 리스트의 시업/종업 열을
-- 월 기본급 열로 바꾸고(#5), 시간대는 검색 조건으로 이동한 데 따른 라벨 추가.
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SEARCH_WORK_TIME','KOR','근무 시간대'),  ('W009','SEARCH_WORK_TIME','ENG','Work time'),  ('W009','SEARCH_WORK_TIME','JPN','勤務時間帯'),
('W009','FILTER_RESET','KOR','초기화'),          ('W009','FILTER_RESET','ENG','Reset'),         ('W009','FILTER_RESET','JPN','リセット');
