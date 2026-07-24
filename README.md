# MT — 웹 출결 SaaS (가제 · 레포: web_attendance)

Spring Boot 4 + MyBatis + **MariaDB** 기반의 **멀티테넌트(SaaS)** 웹 출결(근태) 관리 시스템.

- **멀티테넌시(Pool 모델)**: 공유 스키마 + `tenant_id` 격리. 고객사(테넌트)별 독립 유저 풀,
  로그인은 `테넌트 코드 + 이메일 + 비밀번호`
- **테넌트 서브도메인 병행**: `APP_TENANT_BASE_DOMAIN` 설정 시 `{테넌트코드}.{도메인}` 접속에서
  테넌트가 호스트로 확정(코드 입력 불요, 세션-호스트 일치 강제). 미설정이면 코드 방식만 —
  와일드카드 DNS/TLS 준비 후 켜면 되는 무중단 전환 구조
- **3단계 권한**: `SYSTEM_ADMIN`(운영사) / `TENANT_ADMIN`(고객사 관리자) / `MEMBER`(직원).
  셀프 가입 없음 — 운영자가 고객사를 등록하면 고객사 관리자가 멤버를 등록
- **결제·기업 정보 보호**: 민감 필드 AES-256-GCM 앱 레벨 암호화 + 응답 마스킹.
  카드 원본(PAN/CVC)은 어떤 형태로도 저장하지 않음(PG 빌링키만, 빌링키는 어떤 응답에도 비노출)
- **REST API + Swagger**: 리소스별 엔드포인트와 타입 있는 DTO(record). API 문서는 `/swagger-ui.html`
- **3개 언어 대응(한/영/일)**: 에러·검증·출결 메시지와 Swagger 문서가 요청 언어로 응답
  (우선순위: navigation의 `lang`(세션 저장) → `Accept-Language` 헤더 → 한국어)
- **세션 쿠키 인증**: 로그인 후 세션 쿠키로 호출 + 경로별 role 화이트리스트 인가,
  로그인 레이트 리밋(계정 5회/IP 30회, 5분 창 → 429)
- **출결 2단계 처리**: 체크(사전 검사 + 토큰 발급) → 확정(동일 데이터 + 토큰, SHA-256 해시로 변조 탐지)
- **Flyway 마이그레이션**: 기동시 스키마 자동 생성/버전 관리 (기존 데이터는 V4가 DEFAULT 테넌트로 자동 이관)

## 실행 방법

요구사항: **JDK 21+**, MariaDB (또는 Docker)

```bash
# 1. DB 기동 (로컬에 MariaDB가 없다면)
docker compose up -d

# 2. 백엔드 기동 (스키마는 Flyway가 자동 생성)
./mvnw spring-boot:run

# 3. 프론트엔드 기동 (개발 모드, /api는 9080으로 프록시)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

- 서버: http://localhost:9080
- Swagger UI: http://localhost:9080/swagger-ui.html (스펙: `/v3/api-docs`)
- 초기 관리자 계정: `admin@attendance.local` / `Admin123!` — **운영 배포 전 반드시 변경/삭제**

DB 접속 정보는 환경변수로 주입한다(미지정시 로컬 기본값 `localhost:3306/attendance`, attendance/attendance).

```bash
export DB_URL="jdbc:mariadb://db-host:3306/attendance"
export DB_USERNAME="..."
export DB_PASSWORD="..."
```

프론트를 별도 포트/도메인으로 띄우는 경우 CORS 허용 오리진을 지정한다:
`APP_CORS_ALLOWED_ORIGINS=http://localhost:3000` (프로퍼티 `app.cors.allowed-origins`)

## API 개요

| Method | Path | 설명 | 인가 |
|--------|------|------|------|
| POST | `/api/v1/navigation` | **서버 주도 화면 전개** (화면 코드 + 텍스트 + 초기 데이터) | - |
| POST | `/api/v1/auth/login` | 로그인(테넌트 코드 + 이메일 + 비밀번호, 세션 발급) | - |
| POST | `/api/v1/auth/logout` | 로그아웃 | 세션 |
| GET | `/api/v1/auth/me` | 내 정보 | 세션 |
| GET | `/api/v1/attendance/status` | 현재 출결 상태(출근 대기/출근 중/휴식/퇴근 완료 + 24시간 경과 알림) | TA·MEMBER |
| POST | `/api/v1/attendance/check` | 출결 체크(사전 검사 + 확정 토큰 발급) | TA·MEMBER |
| POST | `/api/v1/attendance` | 출결 확정(스탬프 등록, 변조 탐지) | TA·MEMBER |
| GET | `/api/v1/attendance/monthly?year=&month=` | 월별 출결 상세(일자별 스케쥴/출퇴근 시각) | TA·MEMBER |
| POST | `/api/v1/system/tenants` | 테넌트 등록(소재국 필수, 관리자 계정 생성 + **초대 메일 발송**, 공휴일 자동 동기화) | SYSTEM_ADMIN |
| POST | `/api/v1/system/tenants/{id}/admin-invite` | 고객사 관리자 초대 메일 재발송 | SYSTEM_ADMIN |
| GET | `/api/v1/system/tenants` | 테넌트 목록(멤버 수 포함) | SYSTEM_ADMIN |
| PUT | `/api/v1/system/tenants/{id}/status` | 테넌트 정지/재개 | SYSTEM_ADMIN |
| GET/PUT | `/api/v1/system/tenants/{id}/profile` | 기업 정보(사업자번호·연락처는 암호화 저장, 조회는 마스킹) | SYSTEM_ADMIN |
| GET/PUT | `/api/v1/system/tenants/{id}/billing` | 결제 정보(빌링키 암호화 저장·비노출, `hasBillingKey`만) | SYSTEM_ADMIN |
| GET/POST | `/api/v1/tenant/members` | 자기 테넌트 멤버 목록/등록(**PENDING + 초대 메일** — 본인이 링크에서 비밀번호 설정) | TENANT_ADMIN |
| POST | `/api/v1/tenant/members/{id}/invite` | 초대 메일 재발송(구 토큰 무효화) | TENANT_ADMIN |
| DELETE | `/api/v1/tenant/members/{id}` | 멤버 삭제(소프트 — 오송신 수습, 토큰·세션 즉시 무효화) | TENANT_ADMIN |
| PUT | `/api/v1/tenant/members/{id}/schedule` | 개인 기본 근무시간 변경 | TENANT_ADMIN |
| GET/POST/PUT/DELETE | `/api/v1/tenant/holidays` | 공휴일 조회/등록/수정/삭제(NATIONAL/COMPANY) | TENANT_ADMIN |
| POST | `/api/v1/tenant/holidays/sync?year=` | 국가 공휴일 동기화(Nager.Date, 소재국 기준) | TENANT_ADMIN |
| POST | `/api/v1/auth/password/verify` | 초대/재설정 토큰 검증(마스킹된 안내 반환) | - |
| POST | `/api/v1/auth/password` | 토큰으로 비밀번호 설정(1회용, 기존 세션 전부 무효화) | - |
| POST | `/api/v1/auth/password/reset-request` | 비밀번호 재설정 메일 요청(존재 비노출·레이트 리밋) | - |
| GET/PUT | `/api/v1/admin/mail-templates` | 기본 메일 템플릿 목록/수정(용도×언어) + `/preview` 미리보기 | SYSTEM_ADMIN |
| GET/PUT/DELETE | `/api/v1/tenant/mail-templates` | **회사별 템플릿 오버라이드**(없으면 기본 폴백, DELETE=기본값 되돌리기) + `/preview` | TENANT_ADMIN |
| PUT | `/api/v1/tenant/members/{id}/status` | 멤버 활성/비활성(마지막 관리자 보호) | TENANT_ADMIN |
| PUT | `/api/v1/tenant/members/{id}/role` | 관리자 지정/해제(마지막 관리자 보호) | TENANT_ADMIN |
| GET | `/api/v1/i18n/{windowId}?lang=` | 화면 다국어 텍스트 조회 | - |
| GET/POST | `/api/v1/admin/i18n` | 언어 마스터 목록/등록(갱신) | SYSTEM_ADMIN |

인가는 `RoleInterceptor`의 경로별 role 화이트리스트로 강제한다
(`/api/v1/system/**`·`/api/v1/admin/**` → SYSTEM_ADMIN, `/api/v1/tenant/**` → TENANT_ADMIN,
`/api/v1/attendance/**` → TENANT_ADMIN·MEMBER — 운영사 계정은 출결 기능을 쓰지 않는다).
테넌트 ID는 항상 **세션에서만** 취득하며 클라이언트 입력을 신뢰하지 않는다.

### 테넌트 서브도메인 병행 방식

`app.tenant.base-domain`(환경변수 `APP_TENANT_BASE_DOMAIN`)을 설정하면 서브도메인 접속이 켜진다:

- `acme.webatt.example` 접속 → 테넌트 **ACME 확정**. 로그인에 테넌트 코드 불요(입력란 자동 숨김),
  바디에 다른 코드를 보내면 400(`TENANT_CODE_MISMATCH`) — 모호성을 조용히 삼키지 않는다
- 루트 도메인/미설정 → 기존 방식(로그인 바디 `tenantCode` 필수)
- 미등록 서브도메인 로그인은 통일 401(테넌트 존재 비노출), 예약어(`www`/`admin`/`api`/`app`/`mail`)는
  테넌트로 해석하지 않으며 테넌트 코드로 등록도 불가(400 `TENANT_CODE_RESERVED`)
- **세션-호스트 일치 강제**: 다른 테넌트의 서브도메인으로 온 세션 쿠키는 즉시 무효화
  (host-only 쿠키 + 서버측 검증의 이중 방어)
- 테넌트 서브도메인의 비로그인 초기 화면은 랜딩이 아닌 **로그인(W001)** — 랜딩은 루트 도메인 전용.
  navigation 응답의 `hostTenantName`으로 로그인 화면에 회사명이 표시된다

운영 전환 시 필요한 인프라: 와일드카드 DNS(`*.도메인`) + 와일드카드 TLS 인증서(Let's Encrypt는
DNS-01 챌린지), 리버스 프록시의 `Host` 헤더 전달.

상세 스키마와 응답 예시는 Swagger UI 참조.

### 서버 주도 화면 전개 (Navigation)

이 프로젝트의 프론트는 **URL 라우팅 없이 서버가 결정한 화면 코드로만 화면을 전환**하는
Server-Driven Navigation 컨셉을 사용한다(v1의 화면 전개 방식 계승).
실제 화면 명은 은닉 코드(W000~)로만 노출된다.

```
POST /api/v1/navigation  {screen: "M001", lang: "KOR"}
→ {
    screen: "W001",            // 서버가 결정한 실제 표시 화면(여기선 미로그인이라 로그인으로)
    reason: "LOGIN_REQUIRED",  // 요청과 다른 화면이 된 사유
    userName: null,
    texts:   { ... },          // 해당 화면의 다국어 텍스트(language_master)
    headers: { ... },          // 공통(W999) 텍스트
    data:    { ... }           // 화면 초기 데이터(M001면 출결 상태)
  }
```

| 코드 | 화면 | 허용 role |
|------|------|-----------|
| W000 | 랜딩(회사/제품 소개) | 공개 |
| W001 | 로그인 | 공개 |
| W002 | 로그아웃(처리 후 W001로) | 공개 |
| A005 | 언어 마스터 관리 | SYSTEM_ADMIN |
| M001 | 출결 | TENANT_ADMIN·MEMBER |
| M002 | 출결 상세 | TENANT_ADMIN·MEMBER |
| A001 | 테넌트 관리 | SYSTEM_ADMIN |
| A002 | 기업/결제 정보(A001에 임베드 전개) | SYSTEM_ADMIN |
| T001 | 멤버 관리(초대·스케줄) | TENANT_ADMIN |
| W010 | 비밀번호 설정(메일 링크 `?token=` 진입) | 공개(토큰) |
| W011 | 비밀번호 재설정 요청 | 공개 |
| A004 | 메일 템플릿 관리 | SYSTEM_ADMIN |
| T002 | 공휴일 관리 | TENANT_ADMIN |
| T005 | 회사 메일 템플릿(오버라이드) | TENANT_ADMIN |
| W999 | 공통(헤더 텍스트용) | - |

전환 규칙: 보호 화면+미로그인 → W001(LOGIN_REQUIRED) / 허용 role 미포함 → 각자의 홈(ROLE_DENIED) /
로그인 상태의 W000·W001 → 홈 / 알 수 없는 코드 → W000.
홈 화면: SYSTEM_ADMIN → A001, TENANT_ADMIN·MEMBER → M001.
`lang`은 세션에 저장되어 이후 요청에도 적용된다(로그인/로그아웃의 세션 재발급에도 이월).
W003(회원가입)은 SaaS 전환으로 폐지 — 멤버 등록은 고객사 관리자의 T001에서만.

프론트는 이 응답의 `screen` 값으로만 컴포넌트를 스위칭하고(브라우저 URL 미사용),
개별 액션(로그인, 출결 등록 등)은 아래의 REST API를 사용한다.

### 출결 등록 흐름 (체크 → 확정)

```
POST /api/v1/attendance/check  {type: "GO_TO_WORK", latitude, longitude, placeInfo, terminal}
  → allowed=false                          : 처리 불가(예: 출근 전 퇴근). 메시지 표시
  → allowed=true, requiresConfirmation=true: 덮어쓰기/재출근 확인 필요. 사용자 확인 후 확정
  → allowed=true + token                   : 확정 가능

POST /api/v1/attendance  {token, ...체크와 동일한 데이터}
  → 201 등록 완료
  → 400 CHECK_MISMATCH (체크 시점과 데이터가 다름 = 변조)
```

출결 타입: `GO_TO_WORK`(출근) / `OFF_WORK`(퇴근) / `EARLY_DEPARTURE`(조퇴) / `BREAK`(휴식, 시작/종료 토글)

상태머신 규칙(구버전의 err_cd 1~8 계승):
- 최근 48시간 내 기록이 없으면 **출근만 허용**
- 같은 타입 반복(출근/퇴근/조퇴)은 **덮어쓰기 확인** 후 허용
- 같은 날 퇴근/조퇴 후 출근은 **재출근 확인** 후 허용
- 휴식 기록 상태에서는 재출근 불가, 퇴근/조퇴는 출근 중(또는 휴식 종료 후)에만 가능

## 아키텍처

도메인 패키지 구조(레이어 혼합 대신 기능 단위):

```
src/main/java/com/attendance/pro/
├── WebAttendanceApplication.java
├── config/
│   ├── WebConfig.java              # 인터셉터/CORS/ArgumentResolver 등록
│   └── OpenApiConfig.java          # Swagger 문서 정보
├── common/
│   ├── ApiException.java           # 서비스 예외(HTTP 상태 + 코드)
│   ├── ErrorResponse.java          # 공통 에러 응답(record)
│   └── GlobalExceptionHandler.java # 전역 예외 -> ErrorResponse 변환
├── auth/                           # 세션 인증·인가
│   ├── AuthController/Service      # 로그인(테넌트 스코프)/로그아웃/내정보
│   ├── SessionUser.java            # 세션 보관 유저(record: tenantId + role 포함)
│   ├── AuthInterceptor.java        # 로그인 검사(인증)
│   ├── RoleInterceptor.java        # 경로별 허용 role 화이트리스트(인가)
│   ├── LoginRateLimiter.java       # 로그인 실패 레이트 리밋(계정/IP 슬라이딩 윈도우)
│   └── @LoginUser + Resolver       # 컨트롤러에 세션 유저 주입
├── tenant/                         # 테넌트(고객사) — SYSTEM_ADMIN 전용
│   ├── SystemTenantController      # 등록/목록/정지·재개/기업·결제 정보
│   ├── TenantService, TenantProfileService (암호화·마스킹 책임)
│   └── Tenant/TenantProfile/TenantBilling(record) + 각 Mapper
├── user/                           # 멤버 — TENANT_ADMIN 전용
│   ├── MemberController/Service    # 목록/등록/활성·비활성/관리자 지정(마지막 관리자 보호)
│   └── User(record), Role, UserStatus, UserMapper
├── navigation/                     # 서버 주도 화면 전개
│   └── NavigationController/Service, Screen(W코드 enum)
├── attendance/                     # 출결(핵심 도메인, 전 쿼리 tenant_id 스코프)
│   ├── AttendanceController/Service
│   ├── AttendanceMapper, ScheduleMapper (MyBatis 어노테이션 매퍼)
│   ├── AttendanceType, ConfirmCode (enum 상태머신 코드)
│   ├── MonthlyAttendanceAssembler  # 월별 스케쥴 x 스탬프 페어링(순수 로직, 단위테스트 대상)
│   └── AttendanceStamp, WorkSchedule(record), AttendanceDtos
└── language/                       # 다국어 텍스트
    └── LanguageController/Service/Mapper, LanguageEntry, LanguageDtos

common/에는 FieldCipher(AES-256-GCM), Masking(마스킹 유틸), SecurityHeadersFilter,
ApiException(메시지 키 지연 해석), Messages(MessageSource 래퍼) 등 횡단 관심사가 있다.

src/main/resources/
├── application.properties          # 공통(개발 기본값)
├── application-prod.properties     # 운영(HSTS/Secure 쿠키/APP_CRYPTO_KEY 필수)
├── messages/                       # 서버 메시지 번들(ko 기본/en/ja)
└── db/migration/                   # Flyway 마이그레이션
    ├── V1__init.sql                # 스키마
    ├── V2__seed_admin.sql          # 초기 관리자
    ├── V3__seed_ui_texts.sql       # UI 텍스트 시드(3개국어)
    ├── V4__multitenancy.sql        # 멀티테넌시 전환 + 기존 데이터 이관
    └── V5__seed_saas_texts.sql     # SaaS 화면 텍스트 시드(3개국어)

frontend/                           # 프론트엔드 (Vite + React 19 + TypeScript)
└── src/                            # 서버 주도 화면 전개 기반 SPA (frontend/README.md 참조)
```

- **Map 남용 제거**: 요청/응답/도메인 모두 record 기반 타입. MyBatis는 생성자 인자명 자동매핑(`arg-name-based-constructor-auto-mapping`) 사용
- **매퍼**: XML 대신 어노테이션 매퍼(`@Select`/`@Insert`). 복잡했던 월 달력 SQL(Oracle `CONNECT BY`)은 자바(java.time)로 이동
- **비밀번호**: BCrypt (spring-security-crypto만 사용, 전체 Spring Security 미도입)

## DB 스키마 (MariaDB, Flyway 관리)

| 테이블 | 용도 |
|--------|------|
| `tenant` | 테넌트(고객사) 마스터: 코드(로그인용)/상태(ACTIVE·SUSPENDED) |
| `tenant_profile` | 기업 정보(1:1). 소재국(`country`: KR/JP)이 사업자 식별번호 체계를 결정(KR=사업자등록번호, JP=法人番号) — 검증·마스킹이 국가별. 식별번호·담당자 연락처는 AES-256-GCM 암호문(`v1:iv:ct` 텍스트) |
| `tenant_billing` | 결제 정보(1:1). PG 빌링키 암호문 + 표시용 카드 4자리. **카드 원본 비저장** |
| `users` | 회원. `UNIQUE(tenant_id, email)`, BCrypt 해시, role(3단계)/status |
| `attendance` | 출결 스탬프(타입/상태/시각/위치/단말) + `tenant_id` |
| `attendance_check` | 체크→확정 사이의 변조 방지 토큰(+요청 해시) + `tenant_id`(크로스 테넌트 토큰 차단) |
| `work_schedule` | 일자별 근무시간 오버라이드/개인 휴일 (미등록 일자는 09:00~18:00) + `tenant_id` |
| `holiday` | 테넌트별 공휴일(PK: tenant_id + holiday_date) |
| `language_master` | 다국어 텍스트(화면 그룹 + 키 + 언어) — 제품 글로벌(테넌트 무관). 시드는 V3/V5 |

`V4__multitenancy.sql`이 기존(v2.0) 데이터를 자동 이관한다: DEFAULT 테넌트 생성 → 전 유저/출결
backfill → `is_admin` → role 변환(V2 시드 관리자는 SYSTEM_ADMIN으로 승격) → 제약 원자 교체.
민감 필드 암호화 키는 `APP_CRYPTO_KEY`(base64 32바이트)로 주입 — 운영 프로파일은 미지정시 기동 실패.

## 테스트

```bash
./mvnw test
```

- `AttendanceServiceTest` — 출결 상태머신(체크 규칙 8종), 체크→확정 변조 탐지, 상태 조회 매핑
- `MonthlyAttendanceAssemblerTest` — 월별 페어링(정상/야근 25:10 표기/미퇴근/중복 출근/휴일/스케쥴 오버라이드)
- `WebAttendanceApplicationTests` — 컨텍스트 기동(DB 필요, 기본 비활성)

## 버전 이력

### v2.2.0 (2026-07) — 이메일 온보딩 + 근무 스케줄 + 공휴일 (Phase 3)
- **멤버 온보딩 전환**: 초기 비밀번호 표시 폐지 → 본인 확인 **초대 메일**(토큰 해시 저장·1회용·72h) → 링크에서 비밀번호 설정(PENDING→ACTIVE). 발송 전 이메일 재확인(오송신 방지), 멤버 소프트 삭제(오송신 수습)
- **비밀번호 찾기**: 이메일 재설정(30m 토큰, 존재 비노출, 레이트 리밋), 변경 시 기존 세션 전원 즉시 무효화(재로그인 강제)
- **메일**: SMTP 환경변수 구성(개발은 로깅 페이크), 템플릿은 DB(용도×3개국어) + SYSTEM_ADMIN 화면(A004)에서 수정·미리보기, 메일 언어=테넌트 소재국
- **회사별 메일 템플릿**: 기본 템플릿은 그대로 제공하고, 고객사 관리자가 자기 회사 문구만 오버라이드(T005) — 발송 해석: 회사 설정 → 기본. 기본값 되돌리기 지원
- **근무 스케줄**: 개인 기본 근무시간(멤버 등록 시 설정) + **법정 휴게 자동 산출**(KR 4h/30분·8h/1h, JP 6h초과/45분·8h초과/1h) — 총 근무시간 = 체류 − max(법정 휴게, 실제 휴식), 월별 상세에 휴게·근무시간 열
- **공휴일**: 국가 공휴일 Nager.Date 자동 동기화(소재국 기준) + 회사 지정 공휴일 CRUD(T002), 월별 뷰에 공휴일 이름 표시
- 의사결정 상세: `docs/patch-notes-2026-07-phase3.md` (D21~D23)

### v2.1.0 (2026-07) — SaaS 멀티테넌시 전환
- **멀티테넌시(Pool 모델)**: 전 테이블 `tenant_id` 격리 + `V4__multitenancy.sql` 자동 이관(무중단 backfill 순서 보장). 로그인에 테넌트 코드 도입, `UNIQUE(tenant_id, email)`
- **3단계 role + 경로 화이트리스트 인가**: `RoleInterceptor` 도입, 운영사(SYSTEM_ADMIN)는 출결 API 접근 불가. 마지막 관리자 보호(강등/비활성 409)
- **테넌트/멤버 관리 API**: 운영자가 고객사 등록(관리자 계정 동시 생성, 초기 비밀번호 1회 표시) → 고객사 관리자가 멤버 등록. 셀프 가입(`POST /users`, W003) 폐지
- **기업/결제 정보 보호**: `FieldCipher`(AES-256-GCM, `v1:iv:ct` 텍스트 포맷) 앱 레벨 암호화 + 응답 마스킹(`123-**-*****`), 빌링키는 존재 여부(`hasBillingKey`)만 응답. 카드 원본 비저장(PCI-DSS 범위 회피)
- **보안 강화**: 로그인 레이트 리밋(계정 5회/IP 30회, 5분 창 → 429), 보안 응답 헤더 필터, 세션 쿠키 하드닝, 운영 프로파일(`prod`: HSTS·Secure 쿠키·암호화 키 필수)
- **랜딩 페이지(W000)**: 회사/제품 소개 화면 신설(3개국어, 텍스트는 DB 언어 마스터 단일 출처)
- 상세 계획·의사결정은 `docs/plan/` 7종 및 `docs/plan-saas-multitenancy.md` 참조

### v2.0.0 (2026-07) — MariaDB 전환 + REST API 재설계
- Spring Boot `3.5` → `4.1.0`, MyBatis Starter `4.0.1`, springdoc `3.0.3`
- **Oracle → MariaDB** 전환: Flyway 마이그레이션 도입, `docker-compose.yml` 제공, Oracle 전용 DDL 자동 생성기(AdminSettingLogic 등) 제거
- **API 재설계**: 단일 `/api`(win_id/action + Map) → 리소스별 REST 엔드포인트 + record DTO + Bean Validation. **구 API와 호환되지 않음**
- **Swagger(springdoc)** 도입: 전 엔드포인트/스키마 문서화
- 비밀번호 SHA-512(무솔트) → **BCrypt**, 로그인 401 통일 메시지(이메일 존재 여부 비노출), 세션 고정 공격 방지
- 화면 ID(W000~) 개념 제거, 다국어는 `/api/v1/i18n` API로 단순화(테이블명 오타 `LANGAUGE_MST` → `language_master` 정리)
- Thymeleaf 템플릿/sb-admin-2 정적 자원 등 프론트 잔재 제거(백엔드 전용화)
- 출결 상태머신/월별 페어링 로직을 java.time 기반으로 재작성 + 단위 테스트 26건

### v1.1.0 (2026-07) — 1차 현대화
- Spring Boot 2.5→3.5, Java 8→21, 명명규칙 정리, 생성자 주입, 매퍼 XML 비공개화, DB 접속정보 외부화 등
- 상세 내역은 git 히스토리 참조

## 남은 과제 (TODO)

- [ ] 근무 스케쥴/공휴일 등록 API (현재는 SQL로 직접 입력)
- [ ] 출결 데이터 조회 API의 페이징/기간 검색
- [ ] 멤버 초대 링크(초기 비밀번호 1회 표시 방식 대체) + 비밀번호 변경/재설정 API
- [ ] PG 결제위젯 연동(빌링키 수동 입력 대체)과 과금(플랜) 로직 — Phase 4
- [ ] 세션 저장소 외부화(다중 인스턴스 대비), 레이트 리미터의 분산 환경 대응
- [ ] 인증을 세션에서 토큰(JWT) 방식으로 전환할지 검토(모바일 클라이언트 대응시)
