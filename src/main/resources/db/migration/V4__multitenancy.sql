-- =========================================================
-- V4: 멀티테넌시 전환 (Pool 모델)
--  - 신규: tenant / tenant_profile / tenant_billing
--  - 기존 테이블에 tenant_id 도입 + DEFAULT 테넌트 backfill
--  - users: is_admin(boolean) -> role(3단계) + status
--  - holiday: 글로벌 -> 테넌트별 (PK 교체)
-- 주의: 이 파일은 재실행 내성을 갖도록 작성됨(부분 실패 시
--       flyway repair 후 동일 파일 재실행 가능 — docs/plan/data-migration-v4.md §8)
-- =========================================================

-- ---------------------------------------------------------
-- [1] 신규 테이블
-- ---------------------------------------------------------

-- 테넌트(고객사) 마스터
CREATE TABLE IF NOT EXISTS tenant (
    tenant_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '테넌트 ID',
    tenant_code VARCHAR(20)  NOT NULL COMMENT '로그인/URL용 테넌트 코드(대문자 영숫자)',
    name        VARCHAR(100) NOT NULL COMMENT '고객사명',
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT '상태(ACTIVE/SUSPENDED)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (tenant_id),
    UNIQUE KEY uk_tenant_code (tenant_code),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) COMMENT '테넌트(고객사)';
-- status는 users.role/status와 동일하게 VARCHAR+CHECK (§0 결정표 — backend TenantStatus enum 이름 매핑 정합)

-- 기업 정보 (SYSTEM_ADMIN 전용 — 상위 계획 §6-1)
-- 암호화 컬럼 포맷(텍스트, security-plan §2-3 정본): v1:{base64(iv12)}:{base64(ct||tag16)}
--   → ASCII 문자열이므로 VARCHAR 저장 (교차 검증 최종 결정 D-C)
CREATE TABLE IF NOT EXISTS tenant_profile (
    tenant_id       BIGINT         NOT NULL COMMENT '테넌트 ID (tenant 1:1)',
    business_reg_no VARCHAR(128)   NOT NULL COMMENT '사업자등록번호 [AES-256-GCM v1: 텍스트 암호문]',
    ceo_name        VARCHAR(50)    NULL COMMENT '대표자명',
    address         VARCHAR(200)   NULL COMMENT '사업장 주소',
    contact_name    VARCHAR(50)    NULL COMMENT '계약 담당자명',
    contact_email   VARCHAR(100)   NULL COMMENT '계약 담당자 이메일',
    contact_phone   VARCHAR(128)   NULL COMMENT '계약 담당자 연락처 [AES-256-GCM v1: 텍스트 암호문]',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_profile_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) COMMENT '테넌트 기업 정보 (SYSTEM_ADMIN 전용, 민감 필드 앱 레벨 암호화)';

-- 청구/결제 정보 (SYSTEM_ADMIN 전용 — 상위 계획 §6-1)
-- 카드 원본(PAN/CVC/유효기간)은 어떤 형태로도 저장하지 않는다. PG 빌링키 + 표시용 4자리만.
CREATE TABLE IF NOT EXISTS tenant_billing (
    tenant_id       BIGINT         NOT NULL COMMENT '테넌트 ID (tenant 1:1)',
    billing_method  TINYINT        NOT NULL DEFAULT 0 COMMENT '결제 방식(0=INVOICE 세금계산서/이체 1=CARD PG빌링키)',
    billing_email   VARCHAR(100)   NULL COMMENT '청구서/세금계산서 수신 이메일',
    pg_customer_key VARCHAR(1024)  NULL COMMENT 'PG 빌링키 [AES-256-GCM v1: 텍스트 암호문] — 어떤 API로도 평문 반환 금지',
    card_last4      CHAR(4)        NULL COMMENT '카드 마지막 4자리(표시용, 평문 허용 범위)',
    card_brand      VARCHAR(20)    NULL COMMENT '카드 브랜드(표시용)',
    plan            VARCHAR(20)    NOT NULL DEFAULT 'BASIC' COMMENT '플랜(과금은 Phase 4, 자리 확보)',
    billed_from     DATE           NULL COMMENT '과금 개시일',
    memo            VARCHAR(500)   NULL COMMENT '계약 메모',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_billing_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT ck_tenant_billing_method CHECK (billing_method IN (0, 1))
) COMMENT '테넌트 청구/결제 정보 (SYSTEM_ADMIN 전용, 카드 원본 비저장)';

-- ---------------------------------------------------------
-- [2] DEFAULT 테넌트 시드 (기존 데이터 이관 대상)
--     tenant_id=1을 명시하되, 이후 모든 backfill은 코드('DEFAULT')로 참조한다.
-- ---------------------------------------------------------
INSERT INTO tenant (tenant_id, tenant_code, name, status)
SELECT 1, 'DEFAULT', '기본 테넌트', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE tenant_code = 'DEFAULT');

-- ---------------------------------------------------------
-- [3] users: tenant_id / role / status 도입, is_admin 폐기
-- ---------------------------------------------------------

-- [3-1] 컬럼 추가 (backfill 전이므로 전부 NULL 허용/기본값으로)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT      NULL COMMENT '테넌트 ID' AFTER user_id,
    ADD COLUMN IF NOT EXISTS role      VARCHAR(20) NULL
        COMMENT '권한(SYSTEM_ADMIN/TENANT_ADMIN/MEMBER)' AFTER is_admin,
    ADD COLUMN IF NOT EXISTS status    VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '계정 상태(PENDING/ACTIVE/DISABLED) — PENDING은 셀프가입 대비 예약' AFTER role;

-- [3-2] backfill: 전원 DEFAULT 테넌트 소속 + is_admin -> role 변환
UPDATE users
SET tenant_id = (SELECT t.tenant_id FROM tenant t WHERE t.tenant_code = 'DEFAULT'),
    role      = CASE WHEN is_admin THEN 'TENANT_ADMIN' ELSE 'MEMBER' END,
    status    = 'ACTIVE'
WHERE tenant_id IS NULL;

-- [3-3] V2 시드 관리자(admin@attendance.local)를 운영사 관리자로 승격
UPDATE users
SET role = 'SYSTEM_ADMIN'
WHERE email = 'admin@attendance.local'
  AND tenant_id = (SELECT t.tenant_id FROM tenant t WHERE t.tenant_code = 'DEFAULT')
  AND deleted = FALSE;

-- [3-4] 제약 확정 (단일 ALTER: 리빌드 1회, 문 단위 원자성)
--  * 새 UNIQUE(tenant_id,email) 추가와 구 UNIQUE(email) 제거를 같은 문에서 원자 교체
ALTER TABLE users
    MODIFY tenant_id BIGINT      NOT NULL COMMENT '테넌트 ID',
    MODIFY role      VARCHAR(20) NOT NULL COMMENT '권한(SYSTEM_ADMIN/TENANT_ADMIN/MEMBER)',
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY IF NOT EXISTS (tenant_id) REFERENCES tenant (tenant_id),
    ADD CONSTRAINT IF NOT EXISTS ck_users_role
        CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'MEMBER')),
    ADD CONSTRAINT IF NOT EXISTS ck_users_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    ADD UNIQUE KEY IF NOT EXISTS uk_users_tenant_email (tenant_id, email),
    DROP KEY IF EXISTS uk_users_email,
    DROP COLUMN IF EXISTS is_admin;

-- ---------------------------------------------------------
-- [4] attendance: tenant_id 도입
-- ---------------------------------------------------------
ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NULL COMMENT '테넌트 ID' AFTER attendance_id;

-- backfill은 소유 유저의 테넌트를 따른다(상수 대입보다 정합성 보장이 강함)
UPDATE attendance a
JOIN users u ON u.user_id = a.user_id
SET a.tenant_id = u.tenant_id
WHERE a.tenant_id IS NULL;

ALTER TABLE attendance
    MODIFY tenant_id BIGINT NOT NULL COMMENT '테넌트 ID',
    ADD CONSTRAINT fk_attendance_tenant
        FOREIGN KEY IF NOT EXISTS (tenant_id) REFERENCES tenant (tenant_id),
    ADD KEY IF NOT EXISTS idx_attendance_tenant_user_stamped (tenant_id, user_id, stamped_at);
-- 주의: 기존 idx_attendance_user_stamped(user_id, stamped_at)는 유지한다.
--       fk_attendance_user(user_id)의 지지 인덱스이며, 삭제 시 InnoDB가 FK 인덱스를 요구해 실패한다.

-- ---------------------------------------------------------
-- [5] attendance_check: tenant_id 도입 (크로스 테넌트 토큰 방지 — §6)
--     토큰 수명이 최대 30분(deleteExpiredChecks — TTL 24h→30분 단축, security-plan §1 T7)이라 데이터가 항상 소량이다.
-- ---------------------------------------------------------
ALTER TABLE attendance_check
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NULL COMMENT '테넌트 ID' AFTER token;

UPDATE attendance_check c
JOIN users u ON u.user_id = c.user_id
SET c.tenant_id = u.tenant_id
WHERE c.tenant_id IS NULL;

ALTER TABLE attendance_check
    MODIFY tenant_id BIGINT NOT NULL COMMENT '테넌트 ID',
    ADD CONSTRAINT fk_attendance_check_tenant
        FOREIGN KEY IF NOT EXISTS (tenant_id) REFERENCES tenant (tenant_id),
    ADD KEY IF NOT EXISTS idx_attendance_check_tenant (tenant_id);

-- ---------------------------------------------------------
-- [6] work_schedule: tenant_id 도입
-- ---------------------------------------------------------
ALTER TABLE work_schedule
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NULL COMMENT '테넌트 ID' AFTER schedule_id;

UPDATE work_schedule s
JOIN users u ON u.user_id = s.user_id
SET s.tenant_id = u.tenant_id
WHERE s.tenant_id IS NULL;

ALTER TABLE work_schedule
    MODIFY tenant_id BIGINT NOT NULL COMMENT '테넌트 ID',
    ADD CONSTRAINT fk_work_schedule_tenant
        FOREIGN KEY IF NOT EXISTS (tenant_id) REFERENCES tenant (tenant_id),
    ADD KEY IF NOT EXISTS idx_work_schedule_tenant_user_date (tenant_id, user_id, work_date);
-- 기존 uk_work_schedule_user_date(user_id, work_date)는 유지:
--   "한 유저의 같은 날짜 스케쥴 1건" 규칙과 fk_work_schedule_user 지지 인덱스를 겸한다.

-- ---------------------------------------------------------
-- [7] holiday: 글로벌 -> 테넌트별 (PK 교체)
-- ---------------------------------------------------------
ALTER TABLE holiday
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NULL COMMENT '테넌트 ID' FIRST;

-- 기존 전사 공통 공휴일은 DEFAULT 테넌트의 공휴일로 이관
UPDATE holiday
SET tenant_id = (SELECT t.tenant_id FROM tenant t WHERE t.tenant_code = 'DEFAULT')
WHERE tenant_id IS NULL;

-- PK(holiday_date) -> PK(tenant_id, holiday_date): 반드시 단일 ALTER로 원자 교체
ALTER TABLE holiday
    MODIFY tenant_id BIGINT NOT NULL COMMENT '테넌트 ID',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (tenant_id, holiday_date),
    ADD CONSTRAINT fk_holiday_tenant
        FOREIGN KEY IF NOT EXISTS (tenant_id) REFERENCES tenant (tenant_id);

-- ---------------------------------------------------------
-- language_master: Phase 1 변경 없음 (제품 글로벌 — 상위 계획 §3)
-- 신규 화면 UI 텍스트 시드는 V5__seed_saas_texts.sql로 분리 (§7)
-- ---------------------------------------------------------
