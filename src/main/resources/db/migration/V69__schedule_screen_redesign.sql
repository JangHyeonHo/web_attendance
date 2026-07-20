-- =========================================================
-- V69: 근무 스케줄 화면 재구성 라벨(#13) — 정기/상세 스케줄 + 멤버 공통 적용(W009)
--  - '반복 패턴/주기' 표현 제거 → 정기 스케줄. '월 달력(예외)' → 상세 스케줄(일별).
--  - 야간(자정 넘김) → 심야근무. 정기 따름 ↔ 스케줄 변경. 다른 멤버 일괄 적용.
-- =========================================================
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','SCHEDULE_SUBTITLE','KOR','정기 스케줄과 일별 상세를 지정하고, 같은 스케줄을 다른 멤버에게도 적용합니다.'),
('W009','SCHEDULE_SUBTITLE','ENG','Set the regular and per-day schedule, and apply the same to other members.'),
('W009','SCHEDULE_SUBTITLE','JPN','定期スケジュールと日別詳細を設定し、同じ内容を他のメンバーにも適用します。'),
('W009','REGULAR_SCHEDULE','KOR','정기 스케줄 설정'),  ('W009','REGULAR_SCHEDULE','ENG','Regular schedule'),  ('W009','REGULAR_SCHEDULE','JPN','定期スケジュール設定'),
('W009','REGULAR_HINT','KOR','요일별 기본 근무 시간을 지정하면 매주 자동 반복됩니다.'),
('W009','REGULAR_HINT','ENG','Set default hours per weekday; repeats every week.'),
('W009','REGULAR_HINT','JPN','曜日別の基本勤務時間を設定すると毎週繰り返します。'),
('W009','DETAIL_SCHEDULE','KOR','상세 스케줄 설정 (일별)'), ('W009','DETAIL_SCHEDULE','ENG','Detailed schedule (per day)'), ('W009','DETAIL_SCHEDULE','JPN','詳細スケジュール設定（日別）'),
('W009','DETAIL_HINT','KOR','특정 날짜만 정기 스케줄과 다르게 지정합니다.'),
('W009','DETAIL_HINT','ENG','Override only specific dates versus the regular schedule.'),
('W009','DETAIL_HINT','JPN','特定の日付のみ定期スケジュールと異なる指定をします。'),
('W009','NIGHT_WORK','KOR','심야근무'),               ('W009','NIGHT_WORK','ENG','Overnight'),           ('W009','NIGHT_WORK','JPN','深夜勤務'),
('W009','FOLLOW_REGULAR','KOR','정기 스케줄 따름'),   ('W009','FOLLOW_REGULAR','ENG','Follow regular'),  ('W009','FOLLOW_REGULAR','JPN','定期に従う'),
('W009','CHANGE_SCHEDULE','KOR','스케줄 변경'),       ('W009','CHANGE_SCHEDULE','ENG','Change'),         ('W009','CHANGE_SCHEDULE','JPN','変更'),
('W009','REVERT_REGULAR','KOR','정기로 되돌리기'),    ('W009','REVERT_REGULAR','ENG','Revert to regular'), ('W009','REVERT_REGULAR','JPN','定期に戻す'),
('W009','SAVE_REGULAR','KOR','정기 스케줄 저장'),     ('W009','SAVE_REGULAR','ENG','Save regular'),      ('W009','SAVE_REGULAR','JPN','定期を保存'),
('W009','SAVE_DETAIL','KOR','상세 스케줄 저장'),      ('W009','SAVE_DETAIL','ENG','Save detail'),        ('W009','SAVE_DETAIL','JPN','詳細を保存'),
('W009','REGULAR_CLEAR','KOR','정기 스케줄 삭제'),    ('W009','REGULAR_CLEAR','ENG','Clear regular'),    ('W009','REGULAR_CLEAR','JPN','定期を削除'),
('W009','APPLY_TITLE','KOR','이 정기 스케줄을 다른 멤버에게도 적용'),
('W009','APPLY_TITLE','ENG','Apply this regular schedule to other members'),
('W009','APPLY_TITLE','JPN','この定期スケジュールを他のメンバーにも適用'),
('W009','APPLY_HINT','KOR','같은 정기 스케줄을 선택한 멤버에게 한 번에 지정합니다.'),
('W009','APPLY_HINT','ENG','Assign the same regular schedule to the selected members at once.'),
('W009','APPLY_HINT','JPN','同じ定期スケジュールを選択したメンバーに一括で設定します。'),
('W009','SELECT_MEMBERS','KOR','멤버 선택'),          ('W009','SELECT_MEMBERS','ENG','Select members'),  ('W009','SELECT_MEMBERS','JPN','メンバー選択'),
('W009','SEARCH_MEMBER','KOR','이름·이메일 검색'),    ('W009','SEARCH_MEMBER','ENG','Search name/email'), ('W009','SEARCH_MEMBER','JPN','氏名・メール検索'),
('W009','APPLY_TO_SELECTED','KOR','선택 멤버에 적용'), ('W009','APPLY_TO_SELECTED','ENG','Apply to selected'), ('W009','APPLY_TO_SELECTED','JPN','選択メンバーに適用'),
('W009','APPLIED_N','KOR','{n}명에게 적용됨'),        ('W009','APPLIED_N','ENG','Applied to {n} member(s)'), ('W009','APPLIED_N','JPN','{n}名に適用しました'),
('W009','SELECTED_N','KOR','{n}명 선택됨'),           ('W009','SELECTED_N','ENG','{n} selected'),        ('W009','SELECTED_N','JPN','{n}名選択');
