# V4 데이터 마이그레이션 상세 계획 — 멀티테넌시 전환 (Pool 모델)

- 상위 문서: [`docs/history/plan-saas-multitenancy.md`](../history/plan-saas-multitenancy.md) §3(데이터 모델), §6-1(기업/결제 정보)
- 대상 DB: MariaDB 10.11 / 11 (로컬은 `docker-compose.yml`의 `mariadb:11`, utf8mb4)
- 마이그레이션 파일: `src/main/resources/db/migration/V4__multitenancy.sql` (본 문서 §2의 SQL 그대로)
- 확정 전제: Pool 모델 · `UNIQUE(tenant_id, email)` · 3단계 `users.role` · `users.status`(ACTIVE/DISABLED 사용, PENDING은 스키마만 대비) · holiday 테넌트화 · `tenant_profile`/`tenant_billing`(암호화 컬럼 **VARCHAR — `v1:` 텍스트 암호문 저장**(교차 검증 최종 결정 D-C), 카드 원본 비저장) · 기존 데이터 `DEFAULT` 테넌트 backfill · V2 시드 관리자 SYSTEM_ADMIN 승격
- 신규 화면 UI 텍스트 시드는 **V4에 넣지 않고 V5로 분리**한다(§7).

---

## 0. 설계 결정 요약 (DBA 관점)

| 항목 | 결정 | 근거 |
|------|------|------|
| V4 파일 구성 | **단일 파일, 문(statement) 단위 재실행 내성(idempotent)** | MariaDB의 DDL은 비트랜잭셔널 — V4 도중 실패 시 부분 적용 상태가 남는다. `IF [NOT] EXISTS`와 `WHERE tenant_id IS NULL` 가드로 "repair 후 같은 파일 재실행"이 가능하게 작성 (§8) |
| 테이블별 ALTER 묶음 | 컬럼 추가(NULL 허용) → backfill UPDATE → **제약/인덱스는 테이블당 1개의 ALTER로 통합** | ALTER 1문 = 테이블 리빌드 1회 + 문 단위 원자성(전부 적용 or 전부 실패). NULL→NOT NULL 변경은 INSTANT가 아니므로 묶어서 1회만 리빌드 |
| `users.role`/`status` · **`tenant.status`** 타입 | `VARCHAR` + `CHECK` (MariaDB ENUM·TINYINT 미사용) | ENUM은 값 추가 시 ALTER 필요 + JDBC/MyBatis 매핑이 문자열과 이중화됨. CHECK는 MariaDB 10.2.1+에서 강제되므로 무결성 동일. tenant.status도 동일 결정 적용(backend `TenantStatus` enum 이름 매핑과 정합 — 교차 검증 발견 7) |
| `is_admin` 컬럼 | **V4에서 DROP** | 앱 신버전(role 사용)과 동시 배포·단일 인스턴스·정지 배포 전제라 과도기 불필요. 보수적으로 가려면 V4에서 남기고 V6에서 DROP하는 선택지도 있으나, 죽은 컬럼이 코드에 오독을 남기므로 즉시 제거를 권장 |
| `attendance` backfill 소스 | 상수(=DEFAULT)가 아니라 **`users` 조인** | 지금은 결과가 같지만, "자식 행의 tenant = 소유 유저의 tenant"라는 불변식을 마이그레이션 자체가 보장하게 됨. `attendance_check`/`work_schedule` 동일 |
| DEFAULT 테넌트 ID | `tenant_id = 1` 명시 INSERT + backfill은 `tenant_code='DEFAULT'` 서브쿼리 참조 | 하드코딩 `1` 산재 방지. 리허설/운영 어디서든 코드 기준으로 동작 |
| 암호화 컬럼 | **VARCHAR에 `v1:` 텍스트 암호문 저장** (VARBINARY 바이너리 직저장 미채택 — D-C) | security-plan §2-3의 버전 프리픽스 텍스트 포맷이 정본. §4 산정 근거 참조 |

배포 전제: **V4는 앱 정지 상태에서 적용**한다(구버전 앱은 `is_admin`을 조회하므로 신스키마와 비호환). 단일 인스턴스 + Flyway 기동 시 자동 적용 구조라, "신버전 배포 → 기동 시 V4 적용 → 서비스 재개"의 자연스러운 순서가 된다.

---

## 1. 실행 순서 개요

```
[0] 사전 백업 (mysqldump)                                  ← §8
[1] CREATE tenant / tenant_profile / tenant_billing        ← 신규 테이블 (참조 대상 먼저)
[2] INSERT DEFAULT 테넌트 (tenant_id=1, code='DEFAULT')
[3] users:      ADD COLUMN(tenant_id, role, status; NULL 허용)
                → backfill UPDATE (tenant_id=DEFAULT, is_admin→role 변환)
                → 시드 관리자 SYSTEM_ADMIN 승격 UPDATE
                → ALTER 1문: NOT NULL + FK + CHECK + UNIQUE(tenant_id,email) 추가
                             + UNIQUE(email) 제거 + is_admin DROP
[4] attendance: ADD COLUMN → users 조인 backfill → NOT NULL + FK + 신규 인덱스
[5] attendance_check: ADD COLUMN → backfill → NOT NULL + FK + 인덱스
[6] work_schedule:    ADD COLUMN → backfill → NOT NULL + FK + 인덱스
[7] holiday:    ADD COLUMN → backfill → NOT NULL + PK 교체((tenant_id,holiday_date)) + FK
[8] 검증 쿼리 (§8-2)  — language_master는 Phase 1 변경 없음
```

순서 원칙:
1. **부모 먼저**: `tenant`가 모든 FK의 참조 대상이므로 최선두. DEFAULT 테넌트 행도 backfill 이전에 존재해야 한다.
2. **컬럼 추가와 제약 부여를 분리**: NULL 허용으로 추가 → 데이터를 채운 뒤 → NOT NULL/FK/UNIQUE. backfill 전에 제약을 걸면 즉시 실패한다(상위 계획 §10 "2단 구성").
3. **새 UNIQUE를 추가한 뒤에 구 UNIQUE를 제거**: 유니크 무보장 구간을 만들지 않는다. 같은 ALTER 문 안에 넣으면 원자적으로 교체된다.
4. `users`가 자식 테이블들([4]~[6])의 backfill 조인 소스이므로 [3]이 [4]~[6]보다 먼저.

---

## 2. V4 DDL 전문 (V4__multitenancy.sql 초안)

```sql
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
    ADD CONSTRAINT IF NOT EXISTS fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
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
    ADD CONSTRAINT IF NOT EXISTS fk_attendance_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
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
    ADD CONSTRAINT IF NOT EXISTS fk_attendance_check_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
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
    ADD CONSTRAINT IF NOT EXISTS fk_work_schedule_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
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
    ADD CONSTRAINT IF NOT EXISTS fk_holiday_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id);

-- ---------------------------------------------------------
-- language_master: Phase 1 변경 없음 (제품 글로벌 — 상위 계획 §3)
-- 신규 화면 UI 텍스트 시드는 V5__seed_saas_texts.sql로 분리 (§7)
-- ---------------------------------------------------------
```

문법 비고 (MariaDB 10.11/11):
- `ADD COLUMN IF NOT EXISTS` / `ADD KEY IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS` / `DROP KEY IF EXISTS` / `DROP COLUMN IF EXISTS`는 MariaDB 확장 문법으로 두 버전 모두 지원 — 재실행 내성의 핵심.
- NULL 허용 `ADD COLUMN`은 INSTANT(메타데이터만)로 처리되지만, `MODIFY ... NOT NULL`은 테이블 리빌드를 유발한다. 그래서 테이블당 제약 ALTER를 1문으로 묶어 리빌드를 1회로 제한했다. 현 데이터 규모(개발/데모 수준)에서는 체감 비용 없음. 운영 데이터가 커진 후라면 backfill UPDATE를 `LIMIT` 배치 루프로 나누는 변형이 필요하나 현 시점 불필요.
- `holiday`의 `DROP PRIMARY KEY, ADD PRIMARY KEY(...)`는 같은 ALTER 안에 있어야 한다. 분리하면 그 사이에 PK 없는 테이블 상태가 생기고, 재실행 시 `DROP PRIMARY KEY` 단독 문이 실패한다.
- CHECK 제약은 MariaDB 10.2.1+에서 실제 강제된다(MySQL 5.7과 달리 무시되지 않음).

---

## 3. 인덱스 설계

### 3-1. 신규/변경 인덱스 목록과 지원 쿼리

| 테이블 | 인덱스 | 신규/변경 | 지원하는 쿼리 (현행 매퍼 기준) |
|--------|--------|-----------|-------------------------------|
| tenant | PK(tenant_id) | 신규 | FK 참조, 세션 로드 |
| tenant | `uk_tenant_code(tenant_code)` | 신규 | 로그인 `{tenantCode,...}` → 테넌트 해석(신규 쿼리) |
| users | `uk_users_tenant_email(tenant_id, email)` | **UNIQUE(email) 대체** | `findByEmail`/`existsByEmail`의 테넌트 스코프 판(등호 2개 → 유니크 룩업) |
| users | ~~`uk_users_email(email)`~~ | **제거** | (아래 3-2 주의점) |
| attendance | `idx_attendance_tenant_user_stamped(tenant_id, user_id, stamped_at)` | 신규 | `findLatest`/`findLatestGoToWork`/`findBetween` — 2중 조건(`tenant_id=? AND user_id=?` 등호 + `stamped_at` 범위/정렬)을 3컬럼 전부 사용. 선두 `tenant_id`만으로 테넌트 단위 집계/리포트(Phase 2+)도 커버 |
| attendance | `idx_attendance_user_stamped(user_id, stamped_at)` | **유지** | `fk_attendance_user`의 지지 인덱스(삭제 시 ALTER 실패). 쿼리 관점에선 신규 인덱스와 중복이지만 FK 유지 비용으로 수용 — 쓰기 증폭이 문제되면 FK를 없애는 결정과 함께 재검토 |
| attendance_check | PK(token) | 유지 | `findCheckHash`/`deleteCheck` — 토큰 단건 룩업. `user_id`/`tenant_id`는 인덱스 불필요한 필터 조건 |
| attendance_check | `idx_attendance_check_tenant(tenant_id)` | 신규 | `fk_attendance_check_tenant` 지지 + 테넌트 단위 정리(장래) |
| attendance_check | (`created_at` 인덱스) | **추가 안 함** | `deleteExpiredChecks`는 풀스캔이지만 테이블이 상시 수십 행 이하(TTL 30분 청소) — 인덱스 무가치 |
| work_schedule | `idx_work_schedule_tenant_user_date(tenant_id, user_id, work_date)` | 신규 | `ScheduleMapper.findBetween`의 2중 조건판 + FK 지지 + 테넌트 단위 스케쥴 조회(Phase 3 관리 API) |
| work_schedule | `uk_work_schedule_user_date(user_id, work_date)` | 유지 | "유저당 일자별 1건" 무결성(user_id가 이미 단일 테넌트에 귀속되므로 tenant_id 불포함이 맞음) + `fk_work_schedule_user` 지지 |
| holiday | **PK `(tenant_id, holiday_date)`** | PK 교체 | `findHolidayDates`의 테넌트판: `tenant_id=?`(등호) + `holiday_date` 범위 — PK만으로 커버링 |
| language_master | 변경 없음 | — | Phase 1 스코프 밖 |

### 3-2. UNIQUE(email) → UNIQUE(tenant_id, email) 전환 주의점

1. **교체 순서**: 새 UNIQUE 추가와 구 UNIQUE 제거를 [3-4]의 단일 ALTER에 넣어 원자 교체. 분리 실행하면 "추가 후 제거 전" 구간(이중 유니크 — 무해)이 아니라 실수로 "제거 후 추가 전" 구간(유니크 무보장)이 생길 수 있다.
2. **충돌 불가능 확인**: backfill이 전 유저를 단일(DEFAULT) 테넌트에 넣으므로, 기존 `UNIQUE(email)`이 성립했다면 `UNIQUE(tenant_id, email)`도 자동 성립. 사전 중복 검사 불필요.
3. **email 단독 검색은 인덱스를 잃는다**: `uk_users_tenant_email`은 `tenant_id` 선두라 `WHERE email = ?` 단독 조건에 쓸 수 없다. 따라서 `findByEmail`/`existsByEmail`은 반드시 `tenant_id` 조건이 함께 추가되어야 한다(§9 영향표) — 이것은 성능 문제 이전에 **기능 요건**이다(같은 이메일이 테넌트별로 존재 가능하므로 email 단독 조회는 결과가 모호). SYSTEM_ADMIN용 전 테넌트 이메일 검색이 필요해지면 그때 비유니크 `KEY idx_users_email(email)`을 별도 추가.
4. **애플리케이션 의미 변화**: "이메일은 글로벌 유일" 가정에 기대던 로직(중복 가입 검사, 장래 비밀번호 재설정 등)은 전부 테넌트 스코프로 바뀐다. DB 제약이 바뀐 뒤 앱이 옛 가정으로 동작하면 `existsByEmail`이 false를 돌려주는 것이 아니라 **다른 테넌트의 동일 이메일에 걸려 오탐/누락**이 난다.
5. `deleted` 플래그와의 관계(기존 이슈 승계): soft delete된 행도 UNIQUE에 계속 참여하므로 "탈퇴 후 같은 이메일 재등록"은 여전히 불가. V4 스코프 밖이지만, 해결하려면 삭제 시 email에 suffix를 붙이는 방식 등 별도 결정 필요 — 현행 동작 유지가 V4의 입장.

---

## 4. 암호화 컬럼 (VARCHAR) 크기 산정과 저장 형식 결정

### 4-1. 저장 포맷 (앱 레벨 AES-256-GCM — security-plan §2-3 정본, 교차 검증 최종 결정 D-C)

```
v1:{base64(IV 12B)}:{base64(암호문 || GCM 태그 16B)}
→ 고정부 = "v1:"(3자) + base64(12B)=16자 + ":"(1자) = 20자
→ 가변부 = base64(평문 바이트 + 16B) = 4 × ceil((평문+16)/3) 자
```

- GCM은 스트림형이라 패딩이 없다: 암호문 길이 = 평문 길이. IV는 GCM 권장 96bit(12B), 태그는 128bit(16B).
- `v1` 텍스트 프리픽스로 키 로테이션 대비 — 상위 계획의 "키 버전 프리픽스" 요건(security-plan §2-2).
- 전체가 ASCII 문자열이므로 **VARCHAR 컬럼에 그대로 저장**한다(utf8mb4에서 ASCII는 1바이트/자 — 팽창 없음).

### 4-2. 컬럼별 산정 (텍스트 포맷 기준 재계산)

| 컬럼 | 평문 최대 가정 | 평문 바이트 | 암호문 문자 수 (20 + 4×⌈(평문+16)/3⌉) | 컬럼 크기 | 여유율 |
|------|----------------|------------|----------------------------------------|-----------|--------|
| `tenant_profile.business_reg_no` | 한국 사업자번호 하이픈 포함 12자, 국제 확장 대비 20자(ASCII) | 20B | 20 + 48 = **68자** | **VARCHAR(128)** | 1.9배 |
| `tenant_profile.contact_phone` | E.164 최대 15자리 + 표기문자 여유 = 20자(ASCII) | 20B | 20 + 48 = **68자** | **VARCHAR(128)** | 1.9배 |
| `tenant_billing.pg_customer_key` | PG 빌링키/customerKey — 주요 PG 스펙 상한 255자(ASCII) 가정 | 255B | 20 + 364 = **384자** | **VARCHAR(1024)** | 2.7배 |

- 컬럼 크기 128/128/1024는 D-C 확정값. VARCHAR는 가변 길이라 여유분의 저장 비용이 0이고, 키 로테이션 재암호화 과도기(버전 병존)나 PG 교체 시 스펙 변화를 ALTER 없이 흡수한다.
- 세 컬럼 모두 인덱스를 만들지 않으므로(암호문 검색은 무의미 + 결정적 암호화 금지) 인덱스 프리픽스 길이 제한도 무관하다.

### 4-3. VARBINARY 직저장 vs VARCHAR 텍스트 포맷 — **VARCHAR 텍스트 포맷으로 확정 (D-C)**

당초 이 문서는 VARBINARY 바이너리 직저장(29B 고정 오버헤드 포맷)을 채택했으나, 교차 검증(발견 4)에서 세 문서의 암호문 포맷이 3원화된 것이 확인되어 **security-plan §2의 `v1:` 텍스트 포맷을 정본으로 통일**했다.

| 관점 | VARCHAR + `v1:` 텍스트(채택) | VARBINARY 직저장(기각) |
|------|------------------------------|------------------------|
| 저장량 | base64로 약 4/3배 팽창 + 프리픽스 20자 — 대상이 짧은 필드 3개뿐이라 실비용 무시 가능 | 최소(평문+29B) |
| 키 버전 표현 | `v1:` 텍스트 프리픽스 — 사람이 읽어 버전 식별 가능, 파서 단순(`split(":", 3)`) | 1바이트 바이너리 버전 — 덤프/콘솔에서 판독 불가 |
| 애플리케이션 | MyBatis `String` ↔ VARCHAR 기본 매핑. FieldCipher가 String을 반환하므로 인코딩 계층이 **암호화 유틸 내부에 한 번만** 존재 | `byte[]` 매핑 자체는 단순하나, security-plan 정본 포맷과 바이트 레벨 비호환(복호화 파서 분열) |
| 운영 | 장애 시 SQL 콘솔에서 버전/형식 즉시 확인 가능 | 비가독(유출 억제 부수효과는 있으나 마스킹·로그 금지 규약으로 이미 커버) |

결론: 포맷의 단일 소스는 security-plan §2-3이며, 그 포맷이 텍스트이므로 저장도 **VARCHAR 텍스트**가 일관적이다. 33% 팽창 비용은 3개 소형 컬럼에서 무의미하다.

---

## 5. holiday 테넌트화 — PK 변경 절차

현행: `PRIMARY KEY (holiday_date)` (전사 공통). 목표: `PRIMARY KEY (tenant_id, holiday_date)` (테넌트별).

절차 (§2 [7]):
1. `ADD COLUMN tenant_id BIGINT NULL FIRST` — PK 선두가 될 컬럼이므로 물리 위치도 선두로(가독 목적, 기능 무관).
2. 기존 행 전부 `DEFAULT` 테넌트로 UPDATE — **기존 공휴일은 "DEFAULT 테넌트의 공휴일"이 된다.** 새 테넌트는 빈 공휴일로 시작하고, 온보딩 시 TENANT_ADMIN(Phase 3 관리 API) 또는 운영자가 등록한다. "신규 테넌트에 DEFAULT의 공휴일을 복사해주는" 편의는 스키마가 아닌 온보딩 로직의 결정 사항으로 남긴다(V4는 복사하지 않음).
3. 단일 ALTER로 `MODIFY NOT NULL` + `DROP PRIMARY KEY` + `ADD PRIMARY KEY (tenant_id, holiday_date)` + FK. 한 문장이므로 PK 부재 구간이 없다.

설계 노트:
- PK 컬럼 순서 `(tenant_id, holiday_date)`는 `findHolidayDates`의 접근 패턴(테넌트 등호 + 날짜 범위)과 정확히 일치 — InnoDB 클러스터드 인덱스만으로 커버되어 보조 인덱스 불요.
- AUTO_INCREMENT가 없는 테이블이라 PK 교체에 제약이 없다(AI 컬럼이 있었다면 "AI 컬럼은 키의 일부여야 함" 규칙 때문에 순서를 더 신경써야 했음).
- 같은 날짜가 테넌트마다 존재 가능해지므로, 유니크 위반 없이 재실행해도 안전(UPDATE는 `tenant_id IS NULL` 가드).

---

## 6. attendance_check 테넌트 컬럼과 크로스 테넌트 토큰 방지

토큰 흐름: 체크 API가 `token(UUID) + payload_hash`를 저장 → 확정 API가 `findCheckHash(token, userId)`로 해시 대조 → `deleteCheck(token)`. 상위 계획 §7-④ "check 토큰을 크로스 테넌트로 사용 불가"를 만족시키기 위한 DB/쿼리 조건:

1. **컬럼**: `tenant_id BIGINT NOT NULL` + FK (§2 [5]). backfill은 `users` 조인 — 토큰 발급자의 테넌트가 곧 토큰의 테넌트.
2. **조회 조건(필수)**: `findCheckHash`를 `WHERE token=? AND user_id=? AND tenant_id=?`로 — 세션의 tenantId와 불일치하면 토큰이 "존재하지 않는 것"으로 처리된다(존재 여부 비노출 원칙과 일치). user_id 조건이 이미 있어 UUID 추측 + user_id 일치까지 필요하지만, user_id 재사용/혼선(예: 향후 테넌트별 DB 분리·병합, 백업 복원 실수) 시나리오에서도 2중 조건이 최후 방어선이 된다 — 상위 계획 §5-2의 원칙 그대로.
3. **삭제 조건(현행 버그성 허점 보완)**: 현행 `deleteCheck`는 `WHERE token = ?` 뿐이라 이론상 타 유저/타 테넌트의 토큰을 무효화(삭제)할 수 있다. `WHERE token=? AND user_id=? AND tenant_id=?`로 강화한다. `deleteExpiredChecks`는 시스템 유지보수 쿼리이므로 테넌트 조건 없이 유지(전 테넌트 청소가 올바른 동작).
4. **앱 레벨 보강(확정)**: `payload_hash` 계산 입력에 `tenantId`를 포함시키면, 만에 하나 행이 잘못 매칭되어도 해시 대조 단계에서 한 번 더 실패한다. DB 스키마 요건은 아니며 Phase 1 구현 시 반영 — backend-api §2.2(AttendanceService)에 동일하게 확정 기록(교차 검증 발견 12 해소).
5. **인덱스**: 조회가 PK(token) 단건 룩업이므로 추가 인덱스 불요. `idx_attendance_check_tenant(tenant_id)`는 FK 지지용.
6. **(선택) 구조적 강제**: `users`에 `UNIQUE(user_id, tenant_id)`를 추가하고 자식 FK를 복합 `(user_id, tenant_id) → users(user_id, tenant_id)`로 걸면 "토큰의 테넌트 ≠ 유저의 테넌트"인 행 자체가 존재할 수 없게 된다. 인덱스 증가 비용이 있어 V4 기본안에는 넣지 않고, Phase 3 격리 강화 검토 항목으로 남긴다.

---

## 7. 시드 정책

- **V4 안에서 하는 것**:
  - DEFAULT 테넌트 INSERT (§2 [2]) — `tenant_id=1, tenant_code='DEFAULT', name='기본 테넌트'`. `WHERE NOT EXISTS` 가드로 재실행 안전.
  - V2 시드 관리자(`admin@attendance.local`)의 **SYSTEM_ADMIN 승격** (§2 [3-3]). 소속은 DEFAULT 테넌트 그대로 둔다(운영사 전용 테넌트 분리는 상위 계획 §8-7 미결 항목과 함께 운영 시점 판단).
  - 그 외 데이터 시드는 넣지 않는다. `tenant_profile`/`tenant_billing`은 DEFAULT 테넌트 행을 만들지 않는다(개발/데모용 테넌트라 기업/결제 정보가 없는 것이 정상 — 1:1 관계는 "0 또는 1행"으로 운용).
- **V5로 분리하는 것**: 신규 화면 UI 텍스트 시드는 **`V5__seed_saas_texts.sql` 단일 파일**로 별도 작성한다(V3와 동일하게 `INSERT IGNORE` 방식). 구성(교차 검증 최종 결정 D-E — 파일명·구성의 확정 기록은 이 문서가 소유): ① 관리 화면 키 — 테넌트 목록 W007 / 테넌트 상세 W008 / 멤버 관리 W009 / 공통 W999 / 로그인 W001 (키 목록 정본: frontend-plan §7) ② 랜딩 W000 `LANDING_*` 키 (카피 정본: landing-page.md §2). **폐기 키 DELETE는 하지 않는다** — V3 시드의 `W003`/`INDEX_*`(및 `W999/SIGNUP`) 행은 어떤 화면도 참조하지 않는 무해한 잔존으로 허용(D-A 확정).
  - 분리 이유: ① V4는 구조(DDL) 마이그레이션으로 실패 시 대응 절차(§8)가 무겁다 — 단순 문구 INSERT가 섞이면 실패 지점/재실행 판단이 흐려진다. ② 문구는 Phase 2 화면 구현과 함께 확정되므로 라이프사이클이 다르다(상위 계획 §9 Phase 2 "언어 마스터에 신규 화면 텍스트 시드(V5)"와 일치). ③ V4 리허설을 문구 미확정 상태에서도 진행 가능.

---

## 8. 리허설 / 실패 대응 (롤백 불가 전제)

### 8-1. 로컬 docker DB 리허설 순서

```bash
# 1) 깨끗한 DB로 리허설 (기존 볼륨 보존을 원하면 프로젝트명 분리: docker compose -p v4test ...)
docker compose down -v && docker compose up -d

# 2) 현행 버전 기동 → V1~V3 적용 확인
#    (앱 기동: DB_URL 기본값이 localhost:3306/attendance 이므로 그대로)
./mvnw spring-boot:run   # 기동 후 종료
docker exec web-attendance-db mariadb -uattendance -pattendance attendance \
  -e "SELECT version, description, success FROM flyway_schema_history;"

# 3) 리허설용 데이터 투입 — 반드시 "이관 대상이 있는" 상태를 만든다:
#    일반 유저 2명 이상 + is_admin 유저 1명(V2 시드 관리자와 별도) + 출결/스케쥴/공휴일/check 토큰 각 수 건
#    (V2 관리자만 있는 빈 DB 리허설은 backfill 경로를 검증하지 못한다)
#    [필수 — 교차 검증 발견 15] is_admin 유저 1명은 V4에서 DEFAULT 소속 TENANT_ADMIN으로 변환된다.
#    이 계정이 회귀 E2E(E2E-REG-01)·기존 curl 스모크(REG-04)의 멤버 등록/출결 실행 주체가 되므로
#    (시드 SYSTEM_ADMIN은 화이트리스트 정책상 /tenant·/attendance 403 — security-plan §6-1) 생략 불가.

# 4) V4 파일을 배치하고 신버전 기동 → Flyway가 V4 적용
```

### 8-2. 검증 체크리스트 (V4 적용 직후, 리허설/운영 공통)

```sql
-- (a) 이력: V4 success=1
SELECT version, success FROM flyway_schema_history WHERE version = '4';

-- (b) NULL/미이관 잔존 0건
SELECT (SELECT COUNT(*) FROM users       WHERE tenant_id IS NULL)
     + (SELECT COUNT(*) FROM attendance  WHERE tenant_id IS NULL)
     + (SELECT COUNT(*) FROM attendance_check WHERE tenant_id IS NULL)
     + (SELECT COUNT(*) FROM work_schedule    WHERE tenant_id IS NULL)
     + (SELECT COUNT(*) FROM holiday          WHERE tenant_id IS NULL) AS null_rows; -- 0

-- (c) role 변환 결과: SYSTEM_ADMIN=1(시드 관리자), 구 is_admin=TRUE 수 = TENANT_ADMIN+SYSTEM_ADMIN 수
SELECT role, status, COUNT(*) FROM users GROUP BY role, status;

-- (d) 자식-부모 테넌트 정합 (0건이어야 함)
SELECT COUNT(*) FROM attendance a JOIN users u USING (user_id) WHERE a.tenant_id <> u.tenant_id;
SELECT COUNT(*) FROM work_schedule s JOIN users u USING (user_id) WHERE s.tenant_id <> u.tenant_id;

-- (e) 제약/인덱스 존재 확인
SHOW CREATE TABLE users;    -- uk_users_tenant_email 有 / uk_users_email 無 / is_admin 無
SHOW CREATE TABLE holiday;  -- PRIMARY KEY (tenant_id, holiday_date)

-- (f) 실행계획: 신규 인덱스 사용 확인 (type=ref/range, key= 아래 값)
EXPLAIN SELECT * FROM attendance
 WHERE tenant_id=1 AND user_id=1 AND stamped_at > NOW() - INTERVAL 48 HOUR
 ORDER BY stamped_at DESC LIMIT 1;                    -- idx_attendance_tenant_user_stamped
EXPLAIN SELECT * FROM users WHERE tenant_id=1 AND email='admin@attendance.local'; -- uk_users_tenant_email
EXPLAIN SELECT holiday_date FROM holiday WHERE tenant_id=1
   AND holiday_date >= '2026-01-01' AND holiday_date < '2026-02-01';              -- PRIMARY
```

이후 기능 검증: 테넌트 2개를 만들어 상위 계획 §7 격리 테스트(A/B 상호 불가시, 동일 이메일 이중 계정 로그인, check 토큰 크로스 사용 불가)와 기존 E2E를 통과시킨 뒤에만 운영 적용으로 넘어간다. 운영 적용 전에는 **운영 덤프를 복원한 두 번째 리허설**(실데이터 형상 검증)을 한 번 더 수행한다.

### 8-3. Flyway는 롤백이 없다 — 실패 시 대응

전제 2가지: ① Flyway 무료판은 undo 미지원(forward-only). ② MariaDB의 DDL은 트랜잭션 롤백 대상이 아니므로, V4가 중간에 실패하면 **일부 문만 적용된 스키마 + `flyway_schema_history`에 success=0 행**이 남고 앱은 기동 거부한다.

**백업 시점(필수 관문)**: V4 적용 직전, 앱 정지 상태에서

```bash
docker exec web-attendance-db mariadb-dump -uroot -proot \
  --single-transaction --routines --triggers attendance > backup_pre_v4_$(date +%Y%m%d%H%M).sql
```

(운영도 동일 — 앱을 내린 뒤 덤프하므로 정지점 일관성이 보장된다. 덤프 완료 확인 전에는 신버전을 기동하지 않는다.)

**실패 시 두 가지 경로**:

1. **전진 복구(기본)** — V4가 재실행 내성으로 작성돼 있으므로:
   - 실패 원인 파악(에러 로그·`SHOW CREATE TABLE`로 어느 문까지 적용됐는지 확인) → V4 파일 수정.
   - `flyway repair`로 failed 기록 제거: Flyway CLI가 없으면 동등한 수동 조치 `DELETE FROM flyway_schema_history WHERE version='4' AND success=0;`
   - 앱 재기동 → 수정된 V4가 처음부터 재실행됨. `IF [NOT] EXISTS`/`WHERE tenant_id IS NULL` 가드 덕에 이미 적용된 문은 무해하게 통과한다.
   - 주의: `repair`는 "실패 기록 삭제 + 체크섬 재정렬"일 뿐 **스키마를 되돌리지 않는다.** 가드로 커버되지 않는 수동 변경을 했다면 재실행 전에 스키마를 직접 정돈해야 한다.
2. **백업 복원(전진 복구가 불가/불명확할 때)** — 데이터 규모가 작으므로 이쪽이 오히려 확실:
   ```bash
   docker exec -i web-attendance-db mariadb -uroot -proot \
     -e "DROP DATABASE attendance; CREATE DATABASE attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   docker exec -i web-attendance-db mariadb -uroot -proot attendance < backup_pre_v4_*.sql
   ```
   복원하면 `flyway_schema_history`도 V3 시점으로 함께 돌아가므로 repair 불필요. 구버전 앱으로 서비스 재개 가능.

**금지 사항**: 이미 적용된 V1~V3 파일은 절대 수정하지 않는다(체크섬 불일치로 전 환경 기동 실패). V4도 어느 한 환경에 success로 적용된 순간부터 수정 금지 — 이후 변경은 V5+로만.

---

## 9. 기존 매퍼 쿼리별 영향표

원칙(상위 계획 §5): 테넌트 소유 테이블을 만지는 모든 메소드에 `@Param("tenantId")`를 첫 파라미터로 추가하고, user_id 기반 쿼리에도 `AND tenant_id = #{tenantId}`를 병기(2중 조건).

| 매퍼.메소드 | 추가될 tenant 조건 / 변경 | 사용할 인덱스 |
|---|---|---|
| `UserMapper.findByEmail` | `WHERE tenant_id=#{tenantId} AND email=#{email} AND deleted=FALSE` — **email 단독 조회는 기능적으로 모호해지므로 필수 변경.** SELECT 목록도 `is_admin AS admin` → `tenant_id, role, status`로 교체 | `uk_users_tenant_email` (유니크 룩업) |
| `UserMapper.insert` | `INSERT ... (tenant_id, email, ..., role, status)` — `is_admin` 컬럼 제거. 호출 경로 자체가 §6 온보딩 재설계로 관리자 등록제로 바뀜 | (INSERT — `uk_users_tenant_email`이 테넌트 내 중복을 최종 방어) |
| `UserMapper.existsByEmail` | `WHERE tenant_id=#{tenantId} AND email=#{email}` (→ `existsByTenantAndEmail`로 개명 권장) | `uk_users_tenant_email` |
| `AttendanceMapper.findLatest` | `AND tenant_id=#{tenantId}` 병기 | `idx_attendance_tenant_user_stamped` (tenant_id·user_id 등호 + stamped_at 범위·역순 정렬) |
| `AttendanceMapper.findLatestGoToWork` | `AND tenant_id=#{tenantId}` 병기 | `idx_attendance_tenant_user_stamped` (type/status는 인덱스 후 필터 — 선택도가 이미 user+기간으로 충분) |
| `AttendanceMapper.findBetween` | `AND tenant_id=#{tenantId}` 병기 | `idx_attendance_tenant_user_stamped` |
| `AttendanceMapper.insert` | `INSERT ... (tenant_id, user_id, ...)` — 세션의 tenantId를 명시 저장 | — |
| `AttendanceMapper.insertCheck` | `INSERT ... (token, tenant_id, user_id, ...)` | — |
| `AttendanceMapper.findCheckHash` | `AND tenant_id=#{tenantId}` 병기 (§6-2) | `PRIMARY(token)` — 단건 룩업, 나머지는 필터 |
| `AttendanceMapper.deleteCheck` | `WHERE token=#{token} AND user_id=#{userId} AND tenant_id=#{tenantId}` — **현행은 token 단독이라 타인 토큰 무효화 가능(§6-3), 조건 강화 필수** | `PRIMARY(token)` |
| `AttendanceMapper.deleteExpiredChecks` | **변경 없음** — 전 테넌트 대상 시스템 청소가 올바른 동작 | 풀스캔(상시 소량 테이블, §3-1) |
| `ScheduleMapper.findBetween` | `AND tenant_id=#{tenantId}` 병기 | `idx_work_schedule_tenant_user_date` (또는 기존 `uk_work_schedule_user_date` + tenant 필터 — 옵티마이저 선택, 어느 쪽도 정답) |
| `ScheduleMapper.findHolidayDates` | `WHERE tenant_id=#{tenantId} AND holiday_date >= ... AND < ...` — **글로벌 → 테넌트 조회로 의미 자체가 변경** | `PRIMARY(tenant_id, holiday_date)` 커버링 |
| `LanguageMapper.find` / `upsert` | **변경 없음** (language_master는 Phase 1 글로벌 유지, 테넌트 오버라이드는 Phase 4) | 기존 `uk_language_master` |

신규 쿼리(참고 — V4 스키마가 지원해야 하는 것): 로그인 시 `SELECT ... FROM tenant WHERE tenant_code=? AND status='ACTIVE'`(`uk_tenant_code`), Phase 2의 테넌트 CRUD/멤버 목록(`uk_users_tenant_email` 선두 컬럼 = `WHERE tenant_id=?` 멤버 목록도 커버), `tenant_profile`/`tenant_billing` PK 단건 조회.

---

## 10. 남는 리스크와 완화

| 리스크 | 완화 |
|--------|------|
| V4 중간 실패로 스키마 반쪽 상태 (DDL 비트랜잭셔널) | 문 단위 idempotent 작성 + 직전 덤프 + §8-3의 전진 복구/복원 이원 절차 |
| 매퍼의 tenant 조건 누락(스키마는 못 막음) | 상위 계획 §5의 3중 장치 + §7 격리 테스트 CI 게이트. §6-6의 복합 FK는 Phase 3 검토 |
| `is_admin` 즉시 DROP으로 구버전 앱 롤백 불가 | 앱 롤백 = DB 백업 복원과 세트로만 수행(§8-3 경로 2). 보수안(2단계 DROP)은 §0에 기록 |
| email 유니크 의미 변화에 앱이 뒤따르지 못함 | §3-2-4: V4와 매퍼 변경(§9)을 같은 PR로 — 상위 계획 §10과 동일 원칙 |

---

## 교차 검증 반영 이력(2026-07-08)

- D-C: 암호화 컬럼을 VARBINARY 직저장 → **VARCHAR + `v1:` 텍스트 포맷**(security-plan §2-3 정본)으로 전환 — business_reg_no/contact_phone VARCHAR(128), pg_customer_key VARCHAR(1024). §4의 산정 근거를 텍스트 포맷 기준으로 재계산, §4-3 결론 반전.
- 발견 7: `tenant.status`를 TINYINT → **VARCHAR(10)+CHECK('ACTIVE','SUSPENDED')**로 변경(users.role/status와 동일 결정, backend enum 이름 매핑 정합). DEFAULT 테넌트 INSERT·§9 신규 쿼리도 문자열 값으로 수정.
- D-E/D-A: V5 파일명을 **`V5__seed_saas_texts.sql`**(멤버/테넌트/랜딩 통합 단일 파일)로 확정, 폐기 키 DELETE 없이 W003/INDEX_* 잔존 허용.
- 발견 9·15: 체크토큰 수명 주석 1일 → 30분, 리허설 시드의 DEFAULT 소속 is_admin(→TENANT_ADMIN) 유저 1명을 필수 조건으로 격상.
