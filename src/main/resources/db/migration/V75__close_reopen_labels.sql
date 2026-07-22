-- 근태 마감 관리(W021) — 마감 취소(#11) + 상태 표시 라벨 추가.
-- 관리자 목록이 REQUESTED(대기)와 APPROVED(완료)를 함께 보여주고, 승인 건은 '마감 취소'로 되돌린다.
INSERT INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W021','CLOSE_STATUS','KOR','상태'),                    ('W021','CLOSE_STATUS','ENG','Status'),           ('W021','CLOSE_STATUS','JPN','状態'),
('W021','CLOSE_ST_REQUESTED','KOR','대기'),              ('W021','CLOSE_ST_REQUESTED','ENG','Pending'),    ('W021','CLOSE_ST_REQUESTED','JPN','保留'),
('W021','CLOSE_ST_APPROVED','KOR','마감 완료'),          ('W021','CLOSE_ST_APPROVED','ENG','Closed'),      ('W021','CLOSE_ST_APPROVED','JPN','締め完了'),
('W021','CLOSE_REOPEN','KOR','마감 취소'),               ('W021','CLOSE_REOPEN','ENG','Reopen'),           ('W021','CLOSE_REOPEN','JPN','締め取消'),
('W021','CLOSE_REOPEN_CONFIRM','KOR','이 마감을 취소하고 다시 열린 상태로 되돌리시겠습니까? 해당 월의 정정 잠금이 해제됩니다.'),
('W021','CLOSE_REOPEN_CONFIRM','ENG','Reopen this close? The month will be unlocked for corrections again.'),
('W021','CLOSE_REOPEN_CONFIRM','JPN','この締めを取り消して再び開いた状態に戻しますか？当月の訂正ロックが解除されます。');
