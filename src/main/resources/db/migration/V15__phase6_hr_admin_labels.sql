-- =========================================================
-- V15: Phase 6 — 인사관리자(HR_ADMIN) 역할 + 라벨
-- =========================================================

-- users.role CHECK 제약에 HR_ADMIN 추가(V4 ck_users_role는 3종만 허용 → 역할 지정 시 위반)
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_role,
    ADD CONSTRAINT ck_users_role
        CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'HR_ADMIN', 'MEMBER'));

-- role 컬럼 주석도 실제 허용값과 일치시킨다
ALTER TABLE users
    MODIFY role VARCHAR(20) NOT NULL COMMENT '권한(SYSTEM_ADMIN/TENANT_ADMIN/HR_ADMIN/MEMBER)';

-- 기존 '관리자'(V5) → '총관리자'로 승격(인사관리자와 구분). UPDATE로 값 교체
UPDATE language_master SET lang_value = '총관리자'    WHERE window_id='W009' AND lang_key='ROLE_TENANT_ADMIN' AND lang='KOR';
UPDATE language_master SET lang_value = 'General admin' WHERE window_id='W009' AND lang_key='ROLE_TENANT_ADMIN' AND lang='ENG';
UPDATE language_master SET lang_value = '総管理者'    WHERE window_id='W009' AND lang_key='ROLE_TENANT_ADMIN' AND lang='JPN';

INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
-- 인사관리자 배지
('W009','ROLE_HR_ADMIN','KOR','인사관리자'),
('W009','ROLE_HR_ADMIN','ENG','HR admin'),
('W009','ROLE_HR_ADMIN','JPN','人事管理者'),
-- 역할 지정(총관리자 전용 UI)
('W009','MAKE_HR_ADMIN','KOR','인사관리자 지정'),
('W009','MAKE_HR_ADMIN','ENG','Make HR admin'),
('W009','MAKE_HR_ADMIN','JPN','人事管理者に指定'),
('W009','MAKE_TENANT_ADMIN','KOR','총관리자 지정'),
('W009','MAKE_TENANT_ADMIN','ENG','Make general admin'),
('W009','MAKE_TENANT_ADMIN','JPN','総管理者に指定'),
('W009','MAKE_MEMBER','KOR','일반 멤버로'),
('W009','MAKE_MEMBER','ENG','Make member'),
('W009','MAKE_MEMBER','JPN','一般メンバーに');
