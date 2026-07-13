-- =========================================================
-- V16: Phase 6-2 — 휴가(leave) 관리
--  - users.hire_date: 연차 자동 계산 기산일(입사일). 기존 행은 created_at 날짜로 백필
--  - leave_type: 회사별 휴가 종류(연차는 내장 시드, 그 외 커스텀)
--  - leave_grant: 부여(법정 AUTO 재계산 / 관리자 MANUAL 조정) — 분 단위
--  - leave_request: 신청·승인(승인 시에만 잔여 차감)
-- 잔여 = Σ(유효 grant.minutes) − Σ(APPROVED request.minutes)
-- =========================================================

-- [1] 입사일(연차 기산) ------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS hire_date DATE NULL COMMENT '입사일(연차 자동계산 기산)' AFTER work_days;

-- 기존 행 백필: 계정 생성일의 날짜 부분(정확한 입사일은 관리자가 이후 조정)
UPDATE users SET hire_date = DATE(created_at) WHERE hire_date IS NULL;

-- [2] 휴가 종류 --------------------------------------------
CREATE TABLE IF NOT EXISTS leave_type (
    leave_type_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '휴가종류 ID',
    tenant_id         BIGINT       NOT NULL COMMENT '테넌트 ID',
    code              VARCHAR(30)  NOT NULL COMMENT '코드(ANNUAL 등 — 테넌트 내 유일)',
    name              VARCHAR(50)  NOT NULL COMMENT '표시 명칭',
    paid              BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '유급 여부',
    unit              VARCHAR(10)  NOT NULL DEFAULT 'DAY' COMMENT '부여/사용 단위(DAY/HOUR)',
    requires_approval BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '승인 필요 여부',
    is_annual         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '법정 연차 성격(자동계산 대상)',
    active            BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '활성 여부(비활성=신규 신청 불가)',
    sort_order        INT          NOT NULL DEFAULT 0 COMMENT '표시 정렬',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (leave_type_id),
    UNIQUE KEY uk_leave_type_code (tenant_id, code),
    CONSTRAINT fk_leave_type_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT ck_leave_type_unit CHECK (unit IN ('DAY', 'HOUR'))
) COMMENT '휴가 종류(테넌트별)';

-- 연차(ANNUAL) 내장 시드 — 기존 전 테넌트에 1행씩
INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval, is_annual, active, sort_order)
SELECT t.tenant_id, 'ANNUAL', '연차', TRUE, 'DAY', TRUE, TRUE, TRUE, 0
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM leave_type lt WHERE lt.tenant_id = t.tenant_id AND lt.code = 'ANNUAL'
);

-- [3] 부여 ------------------------------------------------
CREATE TABLE IF NOT EXISTS leave_grant (
    leave_grant_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '부여 ID',
    tenant_id       BIGINT       NOT NULL COMMENT '테넌트 ID',
    user_id         BIGINT       NOT NULL COMMENT '대상 유저',
    leave_type_id   BIGINT       NOT NULL COMMENT '휴가종류',
    minutes         INT          NOT NULL COMMENT '부여량(분 — 조정은 음수 가능)',
    effective_from  DATE         NOT NULL COMMENT '유효 시작',
    expires_on      DATE         NULL COMMENT '소멸일(NULL=무기한)',
    source          VARCHAR(10)  NOT NULL COMMENT '출처(AUTO 법정재계산 / MANUAL 조정)',
    leave_year      INT          NULL COMMENT 'AUTO 연차의 대상 연도(재계산 upsert 키)',
    memo            VARCHAR(200) NULL COMMENT '메모',
    granted_by      BIGINT       NULL COMMENT '부여자(관리자)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (leave_grant_id),
    KEY idx_leave_grant_user (tenant_id, user_id, leave_type_id),
    -- AUTO 연차 재계산 upsert 키(유저·종류·연도 1행) — MANUAL은 leave_year NULL이라 제약 밖
    UNIQUE KEY uk_leave_grant_auto (tenant_id, user_id, leave_type_id, leave_year),
    CONSTRAINT fk_leave_grant_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_leave_grant_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (leave_type_id),
    CONSTRAINT ck_leave_grant_source CHECK (source IN ('AUTO', 'MANUAL'))
) COMMENT '휴가 부여';

-- [4] 신청 ------------------------------------------------
CREATE TABLE IF NOT EXISTS leave_request (
    leave_request_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '신청 ID',
    tenant_id        BIGINT       NOT NULL COMMENT '테넌트 ID',
    user_id          BIGINT       NOT NULL COMMENT '신청 유저',
    leave_type_id    BIGINT       NOT NULL COMMENT '휴가종류',
    start_at         DATETIME     NOT NULL COMMENT '시작(일단위=당일 00:00, 시간단위=실시각)',
    end_at           DATETIME     NOT NULL COMMENT '종료(일단위=종료일 익일 00:00)',
    minutes          INT          NOT NULL COMMENT '차감 분',
    day_unit         BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '일 단위 신청 여부(false=시간단위)',
    half_day         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '반차 여부(일단위 표시용)',
    reason           VARCHAR(200) NULL COMMENT '사유',
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT '상태',
    decided_by       BIGINT       NULL COMMENT '결재자',
    decided_at       DATETIME     NULL COMMENT '결재 시각',
    decision_note    VARCHAR(200) NULL COMMENT '결재 메모(반려 사유 등)',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (leave_request_id),
    KEY idx_leave_request_user (tenant_id, user_id),
    KEY idx_leave_request_status (tenant_id, status),
    CONSTRAINT fk_leave_request_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_leave_request_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (leave_type_id),
    CONSTRAINT ck_leave_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
) COMMENT '휴가 신청';

-- [5] 화면 텍스트(W015 멤버 휴가 / W016 관리자 휴가 관리) -----
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- 헤더 메뉴(공통 W999)
('W999','LEAVE','KOR','휴가'),                ('W999','LEAVE','ENG','Leave'),               ('W999','LEAVE','JPN','休暇'),
('W999','LEAVE_ADMIN','KOR','휴가 관리'),      ('W999','LEAVE_ADMIN','ENG','Leave admin'),   ('W999','LEAVE_ADMIN','JPN','休暇管理'),
-- 휴가 화면 공유 표시 키(W015·W016 공통 — 공통 W999에 두어 양쪽에서 해석)
('W999','EMPTY','KOR','내역이 없습니다'),       ('W999','EMPTY','ENG','No records'),          ('W999','EMPTY','JPN','データがありません'),
('W999','LEAVE_TYPE','KOR','휴가 종류'),        ('W999','LEAVE_TYPE','ENG','Leave type'),     ('W999','LEAVE_TYPE','JPN','休暇種類'),
('W999','PERIOD','KOR','기간'),                ('W999','PERIOD','ENG','Period'),             ('W999','PERIOD','JPN','期間'),
('W999','AMOUNT','KOR','차감'),                ('W999','AMOUNT','ENG','Amount'),             ('W999','AMOUNT','JPN','消化'),
('W999','REASON','KOR','사유'),                ('W999','REASON','ENG','Reason'),             ('W999','REASON','JPN','理由'),
('W999','HALF_DAY','KOR','반차'),              ('W999','HALF_DAY','ENG','Half day'),         ('W999','HALF_DAY','JPN','半休'),
('W999','START_DATE','KOR','시작일'),           ('W999','START_DATE','ENG','Start date'),     ('W999','START_DATE','JPN','開始日'),
('W999','BALANCE','KOR','잔여'),               ('W999','BALANCE','ENG','Balance'),           ('W999','BALANCE','JPN','残数'),
('W999','GRANTED','KOR','부여'),               ('W999','GRANTED','ENG','Granted'),           ('W999','GRANTED','JPN','付与'),
('W999','USED','KOR','사용'),                  ('W999','USED','ENG','Used'),                 ('W999','USED','JPN','使用'),
('W999','MODE_DAY','KOR','일 단위'),            ('W999','MODE_DAY','ENG','By day'),           ('W999','MODE_DAY','JPN','日単位'),
('W999','MODE_HOUR','KOR','시간 단위'),          ('W999','MODE_HOUR','ENG','By hour'),         ('W999','MODE_HOUR','JPN','時間単位'),
-- 수량 표시용 짧은 단위(숫자 뒤 접미)
('W999','UNIT_DAY','KOR','일'),                ('W999','UNIT_DAY','ENG','d'),                ('W999','UNIT_DAY','JPN','日'),
('W999','UNIT_HOUR','KOR','시간'),             ('W999','UNIT_HOUR','ENG','h'),               ('W999','UNIT_HOUR','JPN','時間'),
-- W015 멤버 휴가
('W015','TITLE','KOR','휴가'),                ('W015','TITLE','ENG','Leave'),               ('W015','TITLE','JPN','休暇'),
('W015','BALANCE','KOR','잔여 휴가'),          ('W015','BALANCE','ENG','Balance'),           ('W015','BALANCE','JPN','残休暇'),
('W015','GRANTED','KOR','부여'),              ('W015','GRANTED','ENG','Granted'),           ('W015','GRANTED','JPN','付与'),
('W015','USED','KOR','사용'),                ('W015','USED','ENG','Used'),                 ('W015','USED','JPN','使用'),
('W015','APPLY','KOR','휴가 신청'),            ('W015','APPLY','ENG','Request leave'),       ('W015','APPLY','JPN','休暇申請'),
('W015','LEAVE_TYPE','KOR','휴가 종류'),       ('W015','LEAVE_TYPE','ENG','Leave type'),     ('W015','LEAVE_TYPE','JPN','休暇種類'),
('W015','MODE_DAY','KOR','일 단위'),           ('W015','MODE_DAY','ENG','By day'),           ('W015','MODE_DAY','JPN','日単位'),
('W015','MODE_HOUR','KOR','시간 단위'),        ('W015','MODE_HOUR','ENG','By hour'),         ('W015','MODE_HOUR','JPN','時間単位'),
('W015','HALF_DAY','KOR','반차'),             ('W015','HALF_DAY','ENG','Half day'),         ('W015','HALF_DAY','JPN','半休'),
('W015','START_DATE','KOR','시작일'),          ('W015','START_DATE','ENG','Start date'),     ('W015','START_DATE','JPN','開始日'),
('W015','END_DATE','KOR','종료일'),            ('W015','END_DATE','ENG','End date'),         ('W015','END_DATE','JPN','終了日'),
('W015','START_TIME','KOR','시작 시각'),       ('W015','START_TIME','ENG','Start time'),     ('W015','START_TIME','JPN','開始時刻'),
('W015','END_TIME','KOR','종료 시각'),         ('W015','END_TIME','ENG','End time'),         ('W015','END_TIME','JPN','終了時刻'),
('W015','REASON','KOR','사유'),               ('W015','REASON','ENG','Reason'),             ('W015','REASON','JPN','理由'),
('W015','MY_REQUESTS','KOR','내 신청 내역'),    ('W015','MY_REQUESTS','ENG','My requests'),   ('W015','MY_REQUESTS','JPN','申請履歴'),
('W015','CANCEL','KOR','신청 취소'),           ('W015','CANCEL','ENG','Cancel'),             ('W015','CANCEL','JPN','取消'),
('W015','SUBMIT','KOR','신청'),               ('W015','SUBMIT','ENG','Submit'),             ('W015','SUBMIT','JPN','申請'),
('W015','STATUS_PENDING','KOR','대기'),        ('W015','STATUS_PENDING','ENG','Pending'),    ('W015','STATUS_PENDING','JPN','承認待ち'),
('W015','STATUS_APPROVED','KOR','승인'),       ('W015','STATUS_APPROVED','ENG','Approved'),  ('W015','STATUS_APPROVED','JPN','承認'),
('W015','STATUS_REJECTED','KOR','반려'),       ('W015','STATUS_REJECTED','ENG','Rejected'),  ('W015','STATUS_REJECTED','JPN','却下'),
('W015','STATUS_CANCELED','KOR','취소'),       ('W015','STATUS_CANCELED','ENG','Canceled'),  ('W015','STATUS_CANCELED','JPN','取消済'),
('W015','NO_BALANCE','KOR','잔여가 부족합니다'), ('W015','NO_BALANCE','ENG','Insufficient balance'),('W015','NO_BALANCE','JPN','残数が不足しています'),
('W015','PERIOD','KOR','기간'),               ('W015','PERIOD','ENG','Period'),             ('W015','PERIOD','JPN','期間'),
('W015','AMOUNT','KOR','차감'),               ('W015','AMOUNT','ENG','Amount'),             ('W015','AMOUNT','JPN','消化'),
-- W016 관리자 휴가 관리
('W016','TITLE','KOR','휴가 관리'),            ('W016','TITLE','ENG','Leave management'),    ('W016','TITLE','JPN','休暇管理'),
('W016','TAB_APPROVALS','KOR','승인 대기'),     ('W016','TAB_APPROVALS','ENG','Approvals'),   ('W016','TAB_APPROVALS','JPN','承認待ち'),
('W016','TAB_MEMBERS','KOR','멤버 잔여'),       ('W016','TAB_MEMBERS','ENG','Member balances'),('W016','TAB_MEMBERS','JPN','メンバー残数'),
('W016','TAB_TYPES','KOR','휴가 종류'),         ('W016','TAB_TYPES','ENG','Leave types'),     ('W016','TAB_TYPES','JPN','休暇種類'),
('W016','APPROVE','KOR','승인'),              ('W016','APPROVE','ENG','Approve'),           ('W016','APPROVE','JPN','承認'),
('W016','REJECT','KOR','반려'),               ('W016','REJECT','ENG','Reject'),             ('W016','REJECT','JPN','却下'),
('W016','MEMBER','KOR','멤버'),               ('W016','MEMBER','ENG','Member'),             ('W016','MEMBER','JPN','メンバー'),
('W016','RECOMPUTE','KOR','연차 재계산'),        ('W016','RECOMPUTE','ENG','Recompute annual'),('W016','RECOMPUTE','JPN','年休再計算'),
('W016','RECOMPUTE_ALL','KOR','전체 재계산'),    ('W016','RECOMPUTE_ALL','ENG','Recompute all'),('W016','RECOMPUTE_ALL','JPN','全員再計算'),
('W016','GRANT','KOR','수동 부여'),            ('W016','GRANT','ENG','Grant'),               ('W016','GRANT','JPN','手動付与'),
('W016','GRANT_MINUTES','KOR','부여량(일)'),    ('W016','GRANT_MINUTES','ENG','Amount (days)'),('W016','GRANT_MINUTES','JPN','付与(日)'),
('W016','MEMO','KOR','메모'),                 ('W016','MEMO','ENG','Memo'),                 ('W016','MEMO','JPN','メモ'),
('W016','DECISION_NOTE','KOR','결재 메모'),     ('W016','DECISION_NOTE','ENG','Note'),        ('W016','DECISION_NOTE','JPN','決裁メモ'),
('W016','NO_PENDING','KOR','대기 중인 신청이 없습니다'),('W016','NO_PENDING','ENG','No pending requests'),('W016','NO_PENDING','JPN','承認待ちの申請はありません'),
('W016','ADD_TYPE','KOR','종류 추가'),          ('W016','ADD_TYPE','ENG','Add type'),         ('W016','ADD_TYPE','JPN','種類追加'),
('W016','CODE','KOR','코드'),                 ('W016','CODE','ENG','Code'),                 ('W016','CODE','JPN','コード'),
('W016','NAME','KOR','명칭'),                 ('W016','NAME','ENG','Name'),                 ('W016','NAME','JPN','名称'),
('W016','PAID','KOR','유급'),                 ('W016','PAID','ENG','Paid'),                 ('W016','PAID','JPN','有給'),
('W016','UNIT','KOR','단위'),                 ('W016','UNIT','ENG','Unit'),                 ('W016','UNIT','JPN','単位'),
('W016','ACTIVE','KOR','활성'),               ('W016','ACTIVE','ENG','Active'),             ('W016','ACTIVE','JPN','有効'),
('W016','REQUIRES_APPROVAL','KOR','승인 필요'), ('W016','REQUIRES_APPROVAL','ENG','Approval'),('W016','REQUIRES_APPROVAL','JPN','承認要');
