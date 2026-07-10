-- =========================================================
-- V12: Phase 5 — 수동 출결 정정(사유) + 멤버별 근무 요일 + 관련 화면 텍스트
--  - attendance.source: 등록 경로 구분(AUTO=버튼 스탬프 / MANUAL=정정 등록). 기존 행은 전부 AUTO
--  - attendance.reason_*: 수동 정정 사유(코드 + 자유 텍스트) — AUTO 행은 항상 NULL
--  - users.work_days: 요일별 근무 플래그(월화수목금토일, '1'=근무). 기본 주5일
--  - 전 구문 재실행 내성(V4 방식)
-- =========================================================

ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS source VARCHAR(6) NOT NULL DEFAULT 'AUTO'
        COMMENT '등록 경로(AUTO=버튼 스탬프 / MANUAL=정정 등록)' AFTER terminal,
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(10) NULL
        COMMENT '수동 정정 사유 코드(FORGOT/DEVICE/OFFSITE/OTHER)' AFTER source,
    ADD COLUMN IF NOT EXISTS reason_text VARCHAR(200) NULL
        COMMENT '수동 정정 사유 자유 텍스트(OTHER는 필수)' AFTER reason_code;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS work_days CHAR(7) NOT NULL DEFAULT '1111100'
        COMMENT '요일별 근무 플래그(월화수목금토일, 1=근무)' AFTER default_work_end;

-- ---------------------------------------------------------
-- W006(출결 상세): 일자 상세 모달 + 정정 등록
-- ---------------------------------------------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W006','DAY_DETAIL','KOR','일자 상세'),
('W006','DAY_DETAIL','ENG','Day detail'),
('W006','DAY_DETAIL','JPN','日別詳細'),
('W006','MANUAL_ADD','KOR','정정 등록'),
('W006','MANUAL_ADD','ENG','Manual correction'),
('W006','MANUAL_ADD','JPN','打刻修正'),
('W006','REASON','KOR','사유'),
('W006','REASON','ENG','Reason'),
('W006','REASON','JPN','理由'),
('W006','REASON_FORGOT','KOR','찍는 것을 잊음'),
('W006','REASON_FORGOT','ENG','Forgot to stamp'),
('W006','REASON_FORGOT','JPN','打刻忘れ'),
('W006','REASON_DEVICE','KOR','단말·통신 문제'),
('W006','REASON_DEVICE','ENG','Device or network issue'),
('W006','REASON_DEVICE','JPN','端末・通信の問題'),
('W006','REASON_OFFSITE','KOR','외근·출장'),
('W006','REASON_OFFSITE','ENG','Off-site work or business trip'),
('W006','REASON_OFFSITE','JPN','外勤・出張'),
('W006','REASON_OTHER','KOR','기타(직접 입력)'),
('W006','REASON_OTHER','ENG','Other (enter reason)'),
('W006','REASON_OTHER','JPN','その他（直接入力）'),
('W006','REASON_TEXT','KOR','상세 사유'),
('W006','REASON_TEXT','ENG','Details'),
('W006','REASON_TEXT','JPN','詳細理由'),
('W006','SOURCE_MANUAL','KOR','수동'),
('W006','SOURCE_MANUAL','ENG','Manual'),
('W006','SOURCE_MANUAL','JPN','手動'),
('W006','SOURCE_AUTO','KOR','자동'),
('W006','SOURCE_AUTO','ENG','Auto'),
('W006','SOURCE_AUTO','JPN','自動'),
('W006','DAY_OFF','KOR','휴무'),
('W006','DAY_OFF','ENG','Day off'),
('W006','DAY_OFF','JPN','休み'),
('W006','TIME','KOR','시각'),
('W006','TIME','ENG','Time'),
('W006','TIME','JPN','時刻'),
('W006','TYPE','KOR','구분'),
('W006','TYPE','ENG','Type'),
('W006','TYPE','JPN','区分'),
('W006','TYPE_GO','KOR','출근'),
('W006','TYPE_GO','ENG','Clock in'),
('W006','TYPE_GO','JPN','出勤'),
('W006','TYPE_OFF','KOR','퇴근'),
('W006','TYPE_OFF','ENG','Clock out'),
('W006','TYPE_OFF','JPN','退勤'),
('W006','TYPE_EARLY','KOR','조퇴'),
('W006','TYPE_EARLY','ENG','Early departure'),
('W006','TYPE_EARLY','JPN','早退'),
('W006','TYPE_BREAK_START','KOR','휴식 시작'),
('W006','TYPE_BREAK_START','ENG','Break start'),
('W006','TYPE_BREAK_START','JPN','休憩開始'),
('W006','TYPE_BREAK_END','KOR','휴식 종료'),
('W006','TYPE_BREAK_END','ENG','Break end'),
('W006','TYPE_BREAK_END','JPN','休憩終了'),
('W006','EMPTY','KOR','기록이 없습니다'),
('W006','EMPTY','ENG','No records'),
('W006','EMPTY','JPN','記録がありません'),
('W006','DATE','KOR','날짜'),
('W006','DATE','ENG','Date'),
('W006','DATE','JPN','日付'),
-- W009(멤버 관리): 근무 요일 설정
('W009','WORK_DAYS','KOR','근무 요일'),
('W009','WORK_DAYS','ENG','Working days'),
('W009','WORK_DAYS','JPN','勤務曜日');
