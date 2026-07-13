-- =========================================================
-- V18: Phase 6-2 후속 — 승인 휴가 취소(취소사유 필수)
--  두 경로: (1) 멤버 취소 신청 → 관리자 승인  (2) 관리자 직접 취소
--  당일·시작된 휴가는 멤버 신청 불가(관리자 직접 취소만).
--  CANCEL_REQUESTED = 취소 신청 접수(확정 전까지 잔여 계속 소진), 확정 시 CANCELED로 복원.
-- =========================================================

ALTER TABLE leave_request
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(200) NULL COMMENT '취소 사유' AFTER decision_note;

-- status 컬럼 확장(기존 VARCHAR(10) — 'CANCEL_REQUESTED' 16자 수용) + CHECK에 새 상태 추가
ALTER TABLE leave_request
    MODIFY status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태';

ALTER TABLE leave_request
    DROP CONSTRAINT IF EXISTS ck_leave_request_status,
    ADD CONSTRAINT ck_leave_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'CANCEL_REQUESTED'));

-- 화면 텍스트
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- W015 멤버
('W015','REQUEST_CANCEL','KOR','취소 신청'),        ('W015','REQUEST_CANCEL','ENG','Request cancel'),  ('W015','REQUEST_CANCEL','JPN','取消申請'),
('W015','CANCEL_REASON','KOR','취소 사유'),          ('W015','CANCEL_REASON','ENG','Cancel reason'),    ('W015','CANCEL_REASON','JPN','取消理由'),
('W015','STATUS_CANCEL_REQUESTED','KOR','취소 신청중'),('W015','STATUS_CANCEL_REQUESTED','ENG','Cancel requested'),('W015','STATUS_CANCEL_REQUESTED','JPN','取消申請中'),
('W015','CANCEL_SAME_DAY','KOR','당일·시작된 휴가는 관리자에게 취소를 요청하세요'),
('W015','CANCEL_SAME_DAY','ENG','For same-day or started leave, contact your admin to cancel'),
('W015','CANCEL_SAME_DAY','JPN','当日・開始済みの休暇は管理者に取消を依頼してください'),
-- W016 관리자
('W016','TAB_CANCELS','KOR','취소 신청'),            ('W016','TAB_CANCELS','ENG','Cancellations'),      ('W016','TAB_CANCELS','JPN','取消申請'),
('W016','CANCEL_APPROVE','KOR','취소 확정'),          ('W016','CANCEL_APPROVE','ENG','Confirm cancel'),  ('W016','CANCEL_APPROVE','JPN','取消確定'),
('W016','CANCEL_REJECT','KOR','취소 반려'),          ('W016','CANCEL_REJECT','ENG','Reject cancel'),    ('W016','CANCEL_REJECT','JPN','取消却下'),
('W016','CANCEL_LEAVE','KOR','휴가 취소'),           ('W016','CANCEL_LEAVE','ENG','Cancel leave'),      ('W016','CANCEL_LEAVE','JPN','休暇取消'),
('W016','CANCEL_REASON','KOR','취소 사유'),          ('W016','CANCEL_REASON','ENG','Cancel reason'),    ('W016','CANCEL_REASON','JPN','取消理由'),
('W016','NO_CANCELS','KOR','취소 신청이 없습니다'),   ('W016','NO_CANCELS','ENG','No cancellation requests'),('W016','NO_CANCELS','JPN','取消申請はありません'),
-- 상태 라벨(관리자 화면에서도 필요 — 공통 W999)
('W999','STATUS_APPROVED','KOR','승인'),             ('W999','STATUS_APPROVED','ENG','Approved'),       ('W999','STATUS_APPROVED','JPN','承認'),
('W999','STATUS_CANCEL_REQUESTED','KOR','취소 신청중'),('W999','STATUS_CANCEL_REQUESTED','ENG','Cancel requested'),('W999','STATUS_CANCEL_REQUESTED','JPN','取消申請中');
