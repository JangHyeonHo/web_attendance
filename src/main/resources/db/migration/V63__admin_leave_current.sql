-- =========================================================
-- V63: 관리자 휴가 '현재 휴가자' 목록 + 직접 취소(#11) 라벨(W016)
--  - 당일이라 멤버가 취소 신청을 못 만든 승인 휴가도 관리자가 직접 취소할 수 있게.
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W016','CANCEL_REQUESTS_TITLE','KOR','취소 신청'),      ('W016','CANCEL_REQUESTS_TITLE','ENG','Cancellation requests'), ('W016','CANCEL_REQUESTS_TITLE','JPN','取消申請'),
('W016','CURRENT_LEAVES','KOR','현재/예정 휴가자'),      ('W016','CURRENT_LEAVES','ENG','Current & upcoming leaves'),   ('W016','CURRENT_LEAVES','JPN','現在・予定の休暇者'),
('W016','NO_CURRENT_LEAVES','KOR','현재/예정 휴가자가 없습니다.'), ('W016','NO_CURRENT_LEAVES','ENG','No current or upcoming leaves.'), ('W016','NO_CURRENT_LEAVES','JPN','現在・予定の休暇者はいません。'),
('W016','CANCEL_LEAVE','KOR','휴가 취소'),               ('W016','CANCEL_LEAVE','ENG','Cancel leave'),                  ('W016','CANCEL_LEAVE','JPN','休暇を取消'),
-- 시간 휴가 신청 모달(W015)의 단일 날짜 라벨(#12)
('W015','DATE','KOR','날짜'), ('W015','DATE','ENG','Date'), ('W015','DATE','JPN','日付');
