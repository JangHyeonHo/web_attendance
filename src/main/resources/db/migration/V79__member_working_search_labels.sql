-- 멤버 관리(W009) — '근무 현황' 검색(#6 재설계): 특정 날짜·시각에 실제 근무 중인 멤버를 실효 스케줄로 조회.
-- 기존 '근무 시간대 ㅁ~ㅁ'(개인 기본 스케줄 겹침)을 대체한다.
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SEARCH_WORKING_AT','KOR','근무 시점'),   ('W009','SEARCH_WORKING_AT','ENG','Working at'),   ('W009','SEARCH_WORKING_AT','JPN','勤務時点'),
('W009','SEARCH_DATE','KOR','날짜'),              ('W009','SEARCH_DATE','ENG','Date'),               ('W009','SEARCH_DATE','JPN','日付'),
('W009','SEARCH_TIME','KOR','시각'),              ('W009','SEARCH_TIME','ENG','Time'),               ('W009','SEARCH_TIME','JPN','時刻'),
('W009','WORKING_RESULT','KOR','{date} {time} 근무 중'),
('W009','WORKING_RESULT','ENG','Working on {date} at {time}'),
('W009','WORKING_RESULT','JPN','{date} {time} 勤務中');
