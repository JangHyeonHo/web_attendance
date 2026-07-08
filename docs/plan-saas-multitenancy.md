# SaaS 멀티테넌시 전환 계획서

- 목표: 출결 시스템을 **고객사(테넌트)별로 독립된 유저 풀을 갖는 SaaS**로 전환한다.
- 상태: **계획 확정(구현 전)**. §8의 결정 항목이 확정되어 Phase 1 착수 가능.
- 참고: v1 Admin 화면의 원 구상 메모("로그인에 회사(부서) 시스템 추가 및 DB변경 — 회사코드/회사명/직급")의 SaaS판이다.

---

## 1. 테넌트 격리 모델 (핵심 결정 #1)

| 모델 | 격리 방식 | 장점 | 단점 |
|------|-----------|------|------|
| ① Silo | 테넌트별 DB 인스턴스 | 최강 격리, 개별 백업/이전 용이 | 운영비·배포·마이그레이션 비용이 테넌트 수에 비례 |
| ② Schema-per-tenant | 테넌트별 스키마 | 중간 격리 | Flyway를 테넌트 수만큼 실행, 커넥션 풀 복잡 |
| ③ **Pool (공유 스키마 + tenant_id 컬럼)** | 행 단위 구분 | 운영 단순, 비용 최소, 테넌트 생성이 INSERT 한 건 | 코드 실수 = 데이터 유출. 격리를 코드/테스트로 보장해야 함 |

**권장: ③ Pool 모델.** 근거:
- 현 규모(단일 앱, 소규모 팀 대상)에서 ①②는 과잉. 테넌트 온보딩이 "행 추가"로 끝나는 게 SaaS 초기의 핵심 민첩성.
- 단, ③의 약점(코드 실수)은 **§5의 강제 장치 + §7의 크로스 테넌트 격리 테스트**로 상쇄한다.
- 장래에 대형 고객이 격리를 요구하면 그 고객만 ①로 분리하는 하이브리드로 확장 가능(스키마가 동일하므로).

## 2. 테넌트 식별 방식 (핵심 결정 #2)

| 방식 | UX | 인프라 | 비고 |
|------|-----|--------|------|
| **A. 로그인 시 테넌트 코드 입력** | 로그인 폼에 "회사 코드" 필드 추가 | 변경 없음 | Phase 1 권장. 서버 주도 화면전개와 자연스럽게 결합 |
| B. 서브도메인 (acme.attendance.app) | 코드 입력 불필요 | 와일드카드 DNS + 인증서 | Phase 4 후보. A와 병행 가능(서브도메인 → 코드 자동 결정) |
| C. 이메일 글로벌 유니크(테넌트 자동 판별) | 가장 단순한 로그인 | 변경 없음 | 같은 사람이 두 회사에 속할 수 없음 → 배제 |

**권장: Phase 1은 A, 운영 도메인이 생기면 B를 얹는다.**
이메일 유니크 범위는 **테넌트 내 유니크**(`UNIQUE(tenant_id, email)`)로 — 같은 이메일이 회사별로 별도 계정이 될 수 있어야 한다.

## 3. 데이터 모델 변경 (V4 마이그레이션)

```sql
-- 신규
CREATE TABLE tenant (
    tenant_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(20)  NOT NULL,          -- 로그인/URL용 코드 (UNIQUE)
    name        VARCHAR(100) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 0, -- 0=ACTIVE 1=SUSPENDED
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_code)
);
```

기존 테이블 영향:

| 테이블 | 변경 | 이유 |
|--------|------|------|
| `users` | `tenant_id` FK 추가, `UNIQUE(email)` → `UNIQUE(tenant_id, email)`, `is_admin` → `role`(§4) | 테넌트별 유저 풀 |
| `attendance`, `attendance_check` | `tenant_id` 추가 (+ 인덱스 `(tenant_id, user_id, stamped_at)`) | 쿼리는 user_id 기반이라 필수는 아니나, 방어(2중 조건)·테넌트 단위 집계/리포트를 위해 추가 |
| `work_schedule` | `tenant_id` 추가 | 동상 |
| `holiday` | **글로벌 → 테넌트별**: `tenant_id` 추가, PK `(tenant_id, holiday_date)` | 회사마다 휴일이 다름 |
| `language_master` | Phase 1 변경 없음(제품 글로벌) | 테넌트별 문구 오버라이드는 Phase 4(§9) |

**기존 데이터 이관**: V4에서 기본 테넌트(`DEFAULT`, "기본 테넌트")를 생성하고 기존 users/attendance/... 를 backfill → 기존 계정은 `DEFAULT` 테넌트 소속으로 계속 동작. 시드 관리자(V2)는 §4의 SYSTEM_ADMIN으로 승격.

## 4. 권한 모델: 2단계 → 3단계

현행 `is_admin`(boolean)으로는 "운영사 관리자"와 "고객사 관리자"를 구분할 수 없다.

| role | 소속 | 권한 |
|------|------|------|
| `SYSTEM_ADMIN` | 운영사 | 테넌트 생성/정지, 전 테넌트 조회, 글로벌 언어 마스터 관리 |
| `TENANT_ADMIN` | 고객사 | 자기 테넌트의 멤버 관리(초대/승인/비활성), 스케쥴/공휴일 관리, 출결 현황 조회 |
| `MEMBER` | 고객사 | 자기 출결 등록/조회 |

- `users.role` enum 컬럼(v1의 `USER_RANK` 개념의 정돈된 부활 — 마이그레이션 문서 §5에서 "복원 후보"로 기록했던 항목).
- `SessionUser`에 `tenantId` + `role` 추가. `AdminInterceptor` → `RoleInterceptor`로 일반화(경로별 요구 role).
- 화면 전개(navigation): W004(관리자)를 role별로 분기 — TENANT_ADMIN은 테넌트 관리 화면, SYSTEM_ADMIN은 테넌트 목록 화면. 필요시 화면 코드 추가(W007 테넌트 관리 등).

3단계로 충분하되, 역할 개수 외에 **정책으로 보완해야 하는 지점** 3가지:
1. **마지막 관리자 보호**: 테넌트의 유일한 TENANT_ADMIN을 강등/비활성할 수 없게 서버에서 차단(관리자 0명 테넌트 방지).
2. **SYSTEM_ADMIN의 데이터 접근 경계**: 운영자는 테넌트 메타(기업정보/결제/사용량/멤버 수)까지만 보고, **테넌트 내부의 출결 데이터는 조회하지 않는 것을 기본**으로 한다(개인정보 최소 접근 원칙). 장애 지원 등으로 필요해지면 감사 로그를 남기는 별도 절차로.
3. 부서 단위 권한(부서장이 부서원 출결 열람 등)은 `depart_cd`가 이미 있으므로 장래 role 추가 없이 "TENANT_ADMIN 부여 범위" 확장으로 대응 가능 — 지금은 스코프 밖.

## 5. 테넌트 컨텍스트 전파와 격리 강제 (Pool 모델의 생명선)

**원칙: 테넌트 ID는 클라이언트가 보내는 값이 아니라, 항상 세션에서 꺼낸다.**
(요청 바디/파라미터의 tenant 값은 신뢰하지 않음 — SYSTEM_ADMIN 전용 API만 예외)

전파 경로: 로그인 시 `SessionUser(tenantId, role, ...)` 저장 → `@LoginUser`로 컨트롤러 주입 → 서비스가 매퍼에 명시 전달.

코드 실수(테넌트 조건 누락)를 막는 3중 장치:
1. **매퍼 시그니처 규약**: 테넌트 소유 테이블을 만지는 모든 매퍼 메소드는 `@Param("tenantId")`를 필수 첫 파라미터로. (리뷰에서 눈에 띄게)
2. **2중 조건**: user_id 기반 쿼리도 `AND tenant_id = #{tenantId}`를 병기 — user_id 유출/혼선 시에도 크로스 테넌트 접근 차단.
3. **격리 테스트(§7)를 CI 게이트로**: 테넌트 A 세션으로 B 데이터 접근을 시도하는 테스트가 실패하면 머지 불가.

(MyBatis에는 Hibernate `@TenantId` 같은 자동 필터가 없으므로, 인터셉터로 SQL을 자동 변조하는 방식보다 위의 명시적 규약이 디버깅 가능성 면에서 낫다고 판단. 필요해지면 Phase 3에서 MyBatis Interceptor 기반 가드(누락 감지 로깅)를 추가 검토.)

## 6. 온보딩/가입 플로우 재설계

**[확정]** 현행 공개 회원가입(`POST /users`)은 폐기하고, 전 과정을 관리자 등록제로 운영한다:

```
[운영사]  SYSTEM_ADMIN이 테넌트(고객사) 생성 + 기업/결제 정보 등록(§6-1)
          + 최초 TENANT_ADMIN 계정 발급
              POST /api/v1/system/tenants  {code, name, adminEmail, adminName}
              → 초기 비밀번호 발급(초대 링크는 Phase 3)

[고객사]  TENANT_ADMIN이 멤버를 직접 등록
              POST /api/v1/tenant/members  {email, name, ...}
```

멤버 셀프가입+승인제는 채택하지 않음(필요해지면 `users.status=PENDING` 경로만 열면 되도록 스키마는 대비).

- `users.status`(PENDING/ACTIVE/DISABLED) 컬럼 추가. 로그인은 ACTIVE만 허용.
- 로그인: `POST /api/v1/auth/login {tenantCode, email, password}` — 실패 사유는 현행처럼 단일 메시지(테넌트 존재 여부도 비노출).
- 이메일 초대/비밀번호 재설정은 메일 발송 인프라가 필요하므로 Phase 3로 분리.

### 6-1. 기업 정보 / 결제 정보 (신규 확정 요구)

특정 기업 상대 B2B 운영이므로 테넌트에 기업 정보와 청구/결제 정보를 함께 관리한다.

```sql
-- 기업 정보 (SYSTEM_ADMIN 전용 관리)
CREATE TABLE tenant_profile (
    tenant_id       BIGINT PRIMARY KEY,           -- tenant FK (1:1)
    business_reg_no VARBINARY(256) NOT NULL,      -- 사업자등록번호 [암호화]
    ceo_name        VARCHAR(50),
    address         VARCHAR(200),
    contact_name    VARCHAR(50),                  -- 계약 담당자
    contact_email   VARCHAR(100),
    contact_phone   VARBINARY(256),               -- [암호화]
    created_at / updated_at
);

-- 청구/결제 정보 (SYSTEM_ADMIN 전용 관리)
CREATE TABLE tenant_billing (
    tenant_id       BIGINT PRIMARY KEY,           -- tenant FK (1:1)
    billing_method  TINYINT NOT NULL DEFAULT 0,   -- 0=INVOICE(계산서) 1=CARD
    billing_email   VARCHAR(100),                 -- 청구서/세금계산서 수신
    pg_customer_key VARBINARY(512),               -- PG 빌링키 [암호화]
    card_last4      CHAR(4),                      -- 표시용(평문 허용 범위)
    card_brand      VARCHAR(20),
    plan            VARCHAR(20) DEFAULT 'BASIC',
    billed_from     DATE,
    memo            VARCHAR(500),
    created_at / updated_at
);
```

**결제 정보 3원칙:**
1. **카드 원본 정보(PAN/CVC/유효기간)는 절대 저장하지 않는다.** 카드 결제는 PG(결제대행)의
   빌링키 방식만 사용 — 원본은 PG가 보관하고 우리는 빌링키 + 표시용 마지막 4자리만 갖는다.
   (원본을 저장하는 순간 PCI-DSS 준수 대상이 되어 개인 운영 범위를 벗어남)
   B2B 특성상 기본 결제는 세금계산서/계좌이체(INVOICE)로 하고 카드는 옵션.
2. **저장 암호화**: 사업자등록번호·연락처·빌링키는 AES-256-GCM 애플리케이션 레벨 암호화.
   키는 환경변수/KMS로 주입(코드·저장소에 두지 않음), 암호문에 키 버전 프리픽스를 붙여
   키 로테이션에 대비. 구현은 `spring-security-crypto`(이미 도입됨)의 AES-GCM 유틸 활용.
3. **마스킹**: 조회 API 응답은 사업자번호 `123-**-*****`, 카드 `**** **** **** 1234` 형태만 반환하고
   빌링키는 어떤 API로도 반환하지 않는다. 로그에 결제 필드 출력 금지(toString 제외 처리).
   수정 화면도 마스킹값 표시 + 전체 재입력 방식(부분 노출 없음).

접근 통제: 두 테이블 모두 SYSTEM_ADMIN 전용. TENANT_ADMIN에게는 자기 회사의
청구 이메일/플랜 등 비민감 항목 조회만 허용(Phase 2에서 범위 확정).
대표자명·담당자 연락처는 개인정보이므로 계약 종료 시 파기 정책도 함께 정의(Phase 3 감사로그와 세트).

## 7. 검증 전략

기존(단위 35건 + E2E 12단계)에 추가:

- **격리 테스트(최우선)**: 테넌트 A/B 두 세션을 만들어 ① A가 B 멤버의 출결/상세 조회 불가 ② A의 TENANT_ADMIN이 B 멤버 관리 불가 ③ 같은 이메일이 A/B에 각각 존재할 때 로그인이 올바른 계정으로 ④ check 토큰을 크로스 테넌트로 사용 불가 — 를 검증.
- 상태머신/페어링 등 기존 단위 테스트는 tenantId 파라미터만 추가되고 로직 불변 확인.
- E2E: "테넌트 코드 포함 로그인 → 멤버 등록 → 그 멤버로 출결" 시나리오 추가.
- (Phase 3) Testcontainers로 매퍼 SQL의 tenant 조건을 실 DB에서 검증 — 기존 TODO와 합류.

## 8. 결정 항목 (2026-07-08 확정)

| # | 질문 | 결정 |
|---|------|------|
| 1 | 격리 모델 | ✅ Pool(공유 스키마 + tenant_id) — §1 |
| 2 | 테넌트 식별 | ✅ 로그인 시 테넌트 코드 입력(Phase 1) → 서브도메인(Phase 4) — §2 |
| 3 | 멤버 가입 방식 | ✅ **운영자가 테넌트 등록 → 고객사 관리자(TENANT_ADMIN)가 멤버 등록.** 셀프가입 없음 — §6 |
| 4 | 셀프서브 테넌트 생성 | ✅ **불허. 운영자(SYSTEM_ADMIN) 생성만** — §6 |
| 5 | 권한 모델 | ✅ 3단계(SYSTEM_ADMIN/TENANT_ADMIN/MEMBER) + §4의 보완 정책 3건 |
| 6 | 기업/결제 정보 | ✅ **테넌트에 기업정보·청구정보 관리 추가. 암호화/마스킹 필수** — §6-1 |
| 7 | 기존 `DEFAULT` 테넌트의 기존 계정 처리 | 유지(개발/데모용), 운영 전 삭제 (미결 — 운영 시점 판단) |
| 8 | 과금/플랜(멤버 수 제한 등) | Phase 4로 보류. `tenant_billing.plan` 컬럼으로 자리 확보 |

## 9. 단계별 로드맵

### Phase 1 — 코어 멀티테넌시 (백엔드 중심, 최우선)
- V4 마이그레이션: `tenant` 테이블, 각 테이블 `tenant_id`/`role`/`status` 컬럼, 기본 테넌트 backfill
- 로그인 테넌트 스코프(`tenantCode`), `SessionUser` 확장, `RoleInterceptor`
- 전 매퍼/서비스에 tenantId 전파(§5 규약), holiday 테넌트화
- **격리 테스트 스위트** + 기존 테스트 갱신
- 프론트: 로그인 폼에 회사 코드 필드, 세션 타입 갱신
- 완료 기준: 두 테넌트가 서로 완전히 보이지 않는 상태로 기존 전 기능 동작(E2E)

### Phase 2 — 테넌트 관리 기능
- SYSTEM_ADMIN API: 테넌트 CRUD/정지 (`/api/v1/system/tenants`) + **기업/결제 정보 등록·조회(§6-1: AES-GCM 암호화, 응답 마스킹)**
- TENANT_ADMIN API: 멤버 목록/등록/비활성/승인 (`/api/v1/tenant/members`)
- 프론트: 관리자 화면을 role별 분기(테넌트 목록 화면 / 멤버 관리 화면), navigation Screen 확장
- 언어 마스터에 신규 화면 텍스트 시드(V5)

### Phase 3 — 운영 품질
- 스케쥴/공휴일 관리 API(TENANT_ADMIN) — 기존 TODO 합류
- 이메일 초대/비밀번호 재설정(메일 인프라 도입 시)
- 감사 로그(누가 언제 멤버를 추가/비활성했나), MyBatis 테넌트 가드 인터셉터 검토
- Testcontainers 통합 테스트, 운영 프로파일 분리 — 기존 TODO 합류

### Phase 4 — SaaS 고도화 (선택)
- 서브도메인 테넌트 식별, 테넌트별 UI 텍스트 오버라이드(language_master에 tenant_id nullable)
- 플랜/과금(멤버 수 제한), 사용량 리포트, 테넌트 데이터 내보내기(퇴출 지원)

## 10. 리스크

| 리스크 | 대응 |
|--------|------|
| 테넌트 조건 누락 → 데이터 유출 (Pool 모델 최대 리스크) | §5 3중 장치 + §7 격리 테스트 CI 게이트 |
| 기존 API 계약 변경(로그인에 tenantCode 추가 등) | 프론트를 같은 PR에서 함께 수정(모노레포 장점). 외부 소비자 없음 |
| 마이그레이션 중 기존 데이터 정합성 | V4는 backfill 후 NOT NULL 제약을 거는 2단 구성, 로컬 DB로 리허설 |
| 세션 기반 인증의 수평 확장(서버 다중화 시) | 당장은 단일 인스턴스 전제. 확장 시 Spring Session(Redis) 도입 — 기존 TODO(세션 저장소 외부화)와 동일 항목 |
| 화면 코드/navigation의 role 분기 복잡화 | Screen 레지스트리에 요구 role을 선언적으로 추가(현 구조 그대로 확장 가능) |
