-- =========================================================
-- V72: 근태 마감(월 잠금) — 멤버가 지난달 근태를 마감 신청, 인사관리자 승인 시 그 달 정정 잠금.
--  - 멤버별·월별 1행(uk). status: REQUESTED → APPROVED/REJECTED(leave_request 승인 패턴 계승).
--  - APPROVED가 되면 그 (멤버, 년, 월)의 수동 정정(manual/manualBreak/updateManual)이 거부된다.
--  - '다음 달부터 신청 가능' = 대상 달이 이미 종료(현재 연월 > 대상 연월)해야 신청 가능(서비스 가드).
-- =========================================================
CREATE TABLE IF NOT EXISTS attendance_close (
    close_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '마감 ID',
    tenant_id      BIGINT       NOT NULL COMMENT '테넌트 ID',
    user_id        BIGINT       NOT NULL COMMENT '대상 멤버',
    target_year    SMALLINT     NOT NULL COMMENT '대상 연도',
    target_month   TINYINT      NOT NULL COMMENT '대상 월(1~12)',
    status         VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED|APPROVED|REJECTED',
    requested_by   BIGINT       NOT NULL COMMENT '신청자(본인)',
    requested_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 시각',
    approver_id    BIGINT       NULL COMMENT '결재자(인사관리자)',
    decided_at     DATETIME     NULL COMMENT '결재 시각',
    decision_note  VARCHAR(200) NULL COMMENT '결재 메모(반려 사유 등)',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (close_id),
    CONSTRAINT ck_attendance_close_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED')),
    CONSTRAINT ck_attendance_close_month CHECK (target_month BETWEEN 1 AND 12),
    UNIQUE KEY uk_attendance_close (tenant_id, user_id, target_year, target_month),
    KEY idx_attendance_close_pending (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='근태 월 마감';

-- 멤버 근무표(W006) 마감 신청/상태 라벨 -------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W006','CLOSE_REQUEST','KOR','근태 마감 신청'),        ('W006','CLOSE_REQUEST','ENG','Request month close'), ('W006','CLOSE_REQUEST','JPN','勤怠締め申請'),
('W006','CLOSE_CANCEL','KOR','마감 신청 취소'),         ('W006','CLOSE_CANCEL','ENG','Cancel request'),       ('W006','CLOSE_CANCEL','JPN','締め申請の取消'),
('W006','CLOSE_PENDING','KOR','마감 승인 대기 중'),      ('W006','CLOSE_PENDING','ENG','Awaiting approval'),    ('W006','CLOSE_PENDING','JPN','締め承認待ち'),
('W006','CLOSE_DONE','KOR','마감 완료 (정정 불가)'),     ('W006','CLOSE_DONE','ENG','Closed (locked)'),         ('W006','CLOSE_DONE','JPN','締め完了（訂正不可）'),
('W006','CLOSE_REJECTED','KOR','마감 반려됨'),          ('W006','CLOSE_REJECTED','ENG','Close rejected'),      ('W006','CLOSE_REJECTED','JPN','締め差戻し'),
('W006','CLOSE_NOT_ENDED','KOR','해당 월이 끝난 다음 달부터 신청할 수 있습니다.'),
('W006','CLOSE_NOT_ENDED','ENG','You can request only after the month has ended.'),
('W006','CLOSE_NOT_ENDED','JPN','対象月の終了後（翌月以降）に申請できます。'),
('W006','CLOSE_CONFIRM','KOR','{y}년 {m}월 근태를 마감 신청하시겠습니까? 승인되면 정정할 수 없습니다.'),
('W006','CLOSE_CONFIRM','ENG','Request close for {y}-{m}? Once approved, corrections are locked.'),
('W006','CLOSE_CONFIRM','JPN','{y}年{m}月の勤怠を締め申請しますか？承認後は訂正できません。'),

-- 관리자 근태 마감 관리(W021) ----------------------------
('W021','CLOSE_ADMIN_TITLE','KOR','근태 마감 관리'),     ('W021','CLOSE_ADMIN_TITLE','ENG','Attendance close'), ('W021','CLOSE_ADMIN_TITLE','JPN','勤怠締め管理'),
('W021','CLOSE_ADMIN_SUB','KOR','멤버가 신청한 월 근태 마감을 승인하거나 반려합니다. 승인 시 해당 월은 정정 불가로 잠깁니다.'),
('W021','CLOSE_ADMIN_SUB','ENG','Approve or reject members'' monthly close requests. Approval locks the month.'),
('W021','CLOSE_ADMIN_SUB','JPN','メンバーの月次締め申請を承認・差戻します。承認で当月は訂正不可になります。'),
('W021','CLOSE_PENDING_NONE','KOR','대기 중인 마감 신청이 없습니다.'),
('W021','CLOSE_PENDING_NONE','ENG','No pending close requests.'),
('W021','CLOSE_PENDING_NONE','JPN','保留中の締め申請はありません。'),
('W021','CLOSE_TARGET','KOR','대상 월'),                ('W021','CLOSE_TARGET','ENG','Target month'),     ('W021','CLOSE_TARGET','JPN','対象月'),
('W021','CLOSE_MEMBER','KOR','신청 멤버'),              ('W021','CLOSE_MEMBER','ENG','Member'),           ('W021','CLOSE_MEMBER','JPN','申請メンバー'),
('W021','CLOSE_REQUESTED_AT','KOR','신청일'),           ('W021','CLOSE_REQUESTED_AT','ENG','Requested'),  ('W021','CLOSE_REQUESTED_AT','JPN','申請日'),
('W021','CLOSE_APPROVE','KOR','승인'),                  ('W021','CLOSE_APPROVE','ENG','Approve'),         ('W021','CLOSE_APPROVE','JPN','承認'),
('W021','CLOSE_REJECT','KOR','반려'),                   ('W021','CLOSE_REJECT','ENG','Reject'),           ('W021','CLOSE_REJECT','JPN','差戻し'),
('W021','CLOSE_NAV','KOR','근태 마감'),                 ('W021','CLOSE_NAV','ENG','Close'),               ('W021','CLOSE_NAV','JPN','勤怠締め');
