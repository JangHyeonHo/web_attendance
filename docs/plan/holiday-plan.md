# 공휴일 도메인 상세 설계 — 국가 공휴일 자동 취득 + 회사 지정 공휴일 (Phase 3)

- 상위 문서: [plan-saas-multitenancy.md](../plan-saas-multitenancy.md) / 결정 기록: [patch-notes-2026-07-saas.md](../patch-notes-2026-07-saas.md)(D10~D20에서 이어짐)
- 범위: Phase 3 공휴일 도메인 — ① 국가 공휴일 자동 동기화(Nager.Date), ② 회사 지정 공휴일 CRUD,
  ③ 그 전제가 되는 **tenant.country 승격**(V6 `tenant_profile.country` → `tenant.country` 이관).
- 요구사항(소유자 확정):
  1. **국가 공휴일 자동 취득** — 공개 API [Nager.Date](https://date.nager.at) 사용(무인증, KR/JP 지원, 승인됨).
     장애·오차 대비 수동 등록/수정 병행.
  2. **회사 지정 공휴일** — 창립기념일·전사 휴가 등 테넌트별 지정, 프론트에서 확인 가능.
- 정본 결정(전 문서 공통 — 반드시 준수):
  - 국가는 **tenant.country 단일 출처**. `tenant_profile.country`(V6)는 제거하고 tenant로 승격 — 승격 DDL은 이 문서 몫(§1-1).
  - holiday 확장: `holiday_name` + `holiday_type('NATIONAL'/'COMPANY')`. **PK(tenant_id, holiday_date) 유지** —
    같은 날짜에 한 행만 존재하며, NATIONAL/COMPANY가 겹치면 **COMPANY 우선**(§1-2, §2-4).
  - 동기화 주체: TENANT_ADMIN의 연도 지정 동기화 + 테넌트 생성 시 당해·익년 자동 동기화(실패 허용 — §2-5).
  - 신규 화면은 **W013**(W010~W012는 이메일 온보딩 계획이 선점). 마이그레이션은 **V7 단일 파일 합류**(자기 몫만 §1).

표기: 본문 코드는 실제 컨벤션(record DTO, MyBatis 어노테이션 매퍼, `ApiException` 메시지 키 지연 해석)을
따르는 **구현 지시 수준의 설계**다. HTTP 상태코드 규약은 test-plan §0-1을 따른다.

---

## 1. 데이터 모델 — V7 자기 몫 DDL

`V7__phase3.sql`은 Phase 3 도메인(이메일 온보딩/스케줄/공휴일)이 **합류하는 단일 파일**이다.
아래는 공휴일 도메인 몫만이며, V4와 같은 재실행 내성 규약(전 구문 IF EXISTS/IF NOT EXISTS 또는
조건부 실행 — 부분 실패 시 `flyway repair` 후 동일 파일 재실행 가능)을 지킨다.

### 1-1. tenant.country 승격 (V6 tenant_profile.country → tenant)

국가는 "기업 정보의 속성"이 아니라 **테넌트 자체의 속성**이다(공휴일 동기화·식별번호 체계·Phase 4
과금 통화가 전부 이 축에 걸림). 기업 정보(tenant_profile)는 미등록일 수 있어 공휴일 동기화의
출처로 쓸 수 없으므로 tenant로 승격한다.

```sql
-- [H-1] tenant.country 추가 (V6과 동일 근거로 기본 KR — 기존 테넌트는 전부 한국 형식 검증을 통과한 값)
ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS country CHAR(2) NOT NULL DEFAULT 'KR'
        COMMENT '소재국(ISO 3166-1 alpha-2) — 공휴일 동기화·사업자 식별번호 체계 결정' AFTER name;

-- [H-2] tenant_profile.country가 있는 행은 그 값으로 backfill (프로필 미등록 테넌트는 기본 KR 유지)
--       컬럼 제거([H-3]) 후 재실행되면 이 UPDATE가 파스 단계에서 실패하므로 information_schema 가드 + EXECUTE IMMEDIATE
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tenant_profile' AND COLUMN_NAME = 'country');
SET @sql = IF(@has_col > 0,
    'UPDATE tenant t JOIN tenant_profile p ON p.tenant_id = t.tenant_id SET t.country = p.country',
    'SELECT 1');
EXECUTE IMMEDIATE @sql;

-- [H-3] 승격 완료 — profile 쪽 컬럼 제거 (단일 출처 강제. 남기면 두 값이 갈라지는 순간 정본 분쟁)
ALTER TABLE tenant_profile DROP COLUMN IF EXISTS country;
```

- V6의 결정을 계승해 **DB CHECK는 두지 않는다** — 지원 국가 목록은 앱(`ProfileCountry` enum)이 단일 출처.
- [H-2]는 backfill 성공 후 [H-3] 전에 실패해 재실행돼도 멱등(같은 값 재대입), [H-3] 후 재실행이면 가드가 SELECT 1로 우회.

### 1-2. holiday 확장 (명칭 + 유형)

```sql
-- [H-4] name → holiday_name 리네임(정본 결정의 컬럼명 준수) + 유형/타임스탬프 추가
ALTER TABLE holiday
    CHANGE COLUMN IF EXISTS name holiday_name VARCHAR(100) NOT NULL
        COMMENT '공휴일 명칭(NATIONAL은 Nager.Date localName — 현지어)',
    ADD COLUMN IF NOT EXISTS holiday_type VARCHAR(10) NOT NULL DEFAULT 'COMPANY'
        COMMENT '유형(NATIONAL=국가 공휴일, 동기화 대상 / COMPANY=회사 지정, 동기화 불가침)' AFTER holiday_name,
    ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    ADD CONSTRAINT IF NOT EXISTS ck_holiday_type CHECK (holiday_type IN ('NATIONAL', 'COMPANY'));
```

- **기존 행 backfill = COMPANY**(DEFAULT로 흡수). 기존 행은 운영자가 수동 등록한 값으로 출처를 특정할 수
  없다 — NATIONAL로 분류하면 첫 재동기화의 삭제 범위(§2-4)에 들어가 **의도치 않게 지워질 수 있으므로**
  불가침인 COMPANY가 안전한 기본값이다.
- **PK(tenant_id, holiday_date) 유지** — 같은 날짜에는 한 행만 존재한다. 즉 NATIONAL/COMPANY는 행으로
  공존하지 않으며, "겹치면 COMPANY 우선"은 동기화 알고리즘의 규칙(기존 COMPANY 행이 있는 날짜는
  NATIONAL을 삽입하지 않음 — §2-4)으로 실현된다. 출결 계산은 유형 무관 "휴일"로 동일하므로 손실이 없다.
- 유형은 users.role/status와 동일하게 VARCHAR + CHECK(백엔드 `HolidayType` enum 이름 매핑 정합 — V4 §0 결정 계승).
- 언어 마스터 시드(W013/W007/W999 신규 키)도 V7 자기 몫에 `INSERT IGNORE`로 합류한다(§7-2. V5 방식 —
  관리자가 이미 등록/수정한 키는 덮어쓰지 않는다).

---

## 2. Nager.Date 연동 설계

### 2-1. 엔드포인트와 응답 스키마

`GET https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}` — 무인증, JSON 배열.

| 필드 | 예(KR 2026) | 사용 여부 |
|------|-------------|-----------|
| `date` | `"2026-03-01"` | **사용** — `holiday_date` |
| `localName` | `"삼일절"` | **사용** — `holiday_name` (현지어 명칭. JP면 일본어 — 소재국 언어가 곧 그 회사 구성원의 언어라는 전제) |
| `name` | `"Independence Movement Day"` | 미사용(영문 명칭 — localName 채택으로 대체) |
| `countryCode` | `"KR"` | 검증용(요청 국가와 일치 확인) |
| `global` | `true` | **필터** — `true`인 항목만 채용 |
| `counties` | `null` 또는 `["JP-13", …]` | **필터** — 지역 한정(counties 비어있지 않음 = global false)은 **제외** |
| `fixed` / `launchYear` / `types` | — | `types`에 `"Public"` 포함 항목만 채용, 나머지 미사용 |

**지역 한정 공휴일 제외 근거**: 테넌트 소재지(도/현)를 모델이 갖지 않으므로 지역 휴일을 채용하면
비대상 지역 회사 전체가 휴일 처리된다. KR/JP의 법정 공휴일은 현재 전부 `global=true`라 실질 손실이
없고, 필요해지면 회사 지정(COMPANY)으로 등록하면 된다.

### 2-2. 클라이언트 구현 — `holiday/NagerDateClient`

- Spring Boot 4.1 내장 **`RestClient`** 사용(신규 의존성 없음). 외부 호출은 항상 서버가 수행한다.
- base URL은 `app.holiday.nager.base-url`(기본 `https://date.nager.at`)로 프로퍼티화 —
  스모크/E2E에서 로컬 스텁 서버로 치환하는 유일한 지점(§8-3).
- **타임아웃**: connect 3초 / read 5초(`ClientHttpRequestFactorySettings`). 동기화는 관리자 조작이라
  수 초 대기는 허용되지만, 무한 대기로 요청 스레드를 잡아두지 않는다.
- **재시도**: IO 예외·5xx에 한해 **1회 재시도**(총 2회, 간격 500ms 고정). GET 멱등이라 안전.
  4xx는 재시도하지 않는다(404 = 해당 연도/국가 데이터 없음 — 즉시 실패 확정). resilience4j 등
  라이브러리는 도입하지 않는다(호출 지점이 하나뿐, 단순 루프로 충분).
- **프록시 환경**: 아웃바운드 HTTPS가 프록시 경유인 환경에서는 JVM 표준 시스템 프로퍼티
  (`https.proxyHost`/`https.proxyPort`)로 주입한다 — JVM은 `HTTPS_PROXY` 환경변수를 자동으로 읽지
  않으므로 기동 스크립트/스모크 스크립트에서 명시한다. 프록시가 사설 CA로 TLS를 재서명하는 환경이면
  해당 CA를 JVM truststore에 등록한다(**TLS 검증 비활성화 금지**). 코드는 프록시를 몰라도 되게
  표준 프로퍼티만 존중한다(전용 설정 키 미도입).

### 2-3. 응답 검증 (전부 통과해야 DB에 손댐)

1. HTTP 200 + JSON 배열로 파싱 가능.
2. 필터(`global=true` && `types`에 Public && `countryCode` 일치) 적용 후 **1건 이상** —
   0건이면 실패로 간주하고 중단. KR/JP에 공휴일 0인 해는 없으므로, 빈 응답은 데이터 이상이며
   이를 성공 처리하면 §2-4의 삭제 단계가 그 해 NATIONAL 전체를 지우는 사고가 된다.
3. 각 `date`가 ISO-8601로 파싱되고 연도가 요청 `year`와 일치(불일치 항목이 하나라도 있으면 전체 중단).
4. `localName`은 100자 초과 시 절단(컬럼 한계) — 실데이터에 없는 방어.

검증 실패·타임아웃·재시도 소진은 `502 HOLIDAY_SYNC_UPSTREAM`(§3-2)으로 귀결되고 **DB는 무변경**.

### 2-4. 동기화 알고리즘 (`HolidayService.sync(tenantId, year)`) — 단일 트랜잭션

1. `tenantMapper.findById(tenantId)`의 **tenant.country**로 국가 확정(`ProfileCountry.of` — 단일 출처).
2. Nager.Date 호출 + §2-3 검증(트랜잭션 밖 — 외부 IO 동안 커넥션을 잡지 않는다). 통과 후 트랜잭션 시작:
3. `DELETE FROM holiday WHERE tenant_id=? AND holiday_type='NATIONAL' AND holiday_date BETWEEN {year}-01-01 AND {year}-12-31`
   — **해당 연도의 NATIONAL만** 삭제(대체공휴일 변동·API 수정분 반영, 타 연도·COMPANY 불가침).
4. 취득 목록을 `INSERT IGNORE INTO holiday (tenant_id, holiday_date, holiday_name, holiday_type) VALUES (…, 'NATIONAL')`
   — 같은 날짜에 **COMPANY 행이 이미 있으면 IGNORE로 건너뜀 = COMPANY 우선** 규칙의 구현.
     삭제(3)가 NATIONAL만 지웠으므로 살아남은 행은 전부 COMPANY다.
5. 응답으로 `{year, country, fetched, inserted, deleted, skippedCompany}` 카운트 반환(관리 화면 피드백).

재동기화는 멱등(같은 응답이면 delete+insert 결과 동일). NATIONAL을 수동 삭제/수정했던 날짜는
재동기화가 **API 값으로 복원**한다 — 이것이 §3-3 정책의 전제.

### 2-5. 테넌트 생성 시 자동 동기화 — 동기 호출 + 실패 허용 (택1 명시)

- `TenantService.create()`의 **테넌트 생성 커밋 후**, 같은 요청 안에서 당해·익년 2회 `sync`를
  try-catch로 호출한다. **실패해도 생성은 이미 성공**(예외 삼킴 + WARN 로그).
  결과는 `TenantCreateResponse.holidaysSynced`(boolean)로 동봉 — false면 W007이 "공휴일 자동 등록
  실패, 관리자에게 W013 수동 동기화 안내" 문구를 표시한다(§5-2).
- 비동기(@Async) 대신 동기+실패 허용을 택한 근거: 현 코드베이스에 비동기 인프라가 없고(스레드풀
  설정·실패 관측 수단 추가 비용), 외부 호출 2회 × 최대 (3+5)초×2는 테넌트 생성(운영자 수동 조작,
  드묾)에서 수용 가능한 지연이다. 성공/실패가 응답에 즉시 실려 운영자가 그 자리에서 인지한다.
- 당해·익년 범위 근거: 생성 직후 출결 달력(W006)이 당장 쓰는 범위. 그 밖의 연도는 필요 시 수동 동기화.

### 2-6. 연도 범위 정책

수동 동기화 허용 연도 = **현재 연도 −1 ~ +2**(서버 기준). 과거 1년은 소급 조회(월별 상세가 과거를
보여줌), 미래 2년은 스케줄 선등록 대비. 범위 밖은 `400 HOLIDAY_YEAR_RANGE` — 원거리 연도 남발로
외부 API를 불필요하게 두드리는 것을 막는 안전핀이기도 하다(멱등 조작이라 별도 레이트 리밋은 두지 않음).

---

## 3. API 계약 — `/api/v1/tenant/holidays` (TENANT_ADMIN 전용)

컨트롤러: `holiday/TenantHolidayController.java`. 경로가 `/api/v1/tenant/**` 접두사라
**RoleInterceptor 기존 화이트리스트(TENANT_ADMIN)에 자동 편입 — 인가 설정 변경 없음**(D12 경로 규약의
이득). SYSTEM_ADMIN은 403(테넌트 내부 데이터 미접근 정책 일관). tenantId는 항상 세션에서(파라미터 금지).

### 3-1. 엔드포인트 표

| 메소드/경로 | 요청 | 응답 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `POST /api/v1/tenant/holidays/sync?year=2026` | 쿼리 `year`(필수) | 200 `HolidaySyncResponse` | 400 `HOLIDAY_YEAR_RANGE` / `holiday.year.range`, 502 `HOLIDAY_SYNC_UPSTREAM` / `holiday.sync.upstream` |
| `GET /api/v1/tenant/holidays?year=2026` | 쿼리 `year`(필수) | 200 `List<HolidayResponse>`(날짜 오름차순) | 400 `INVALID_INPUT` |
| `POST /api/v1/tenant/holidays` | `HolidayCreateRequest` | 201 `HolidayResponse` | 409 `HOLIDAY_DATE_DUPLICATED` / `holiday.date.duplicated`, 400 `INVALID_INPUT` |
| `PUT /api/v1/tenant/holidays/{holidayDate}` | `HolidayUpdateRequest` | 200 `HolidayResponse` | 404 `HOLIDAY_NOT_FOUND` / `holiday.not-found`, 400 `INVALID_INPUT` |
| `DELETE /api/v1/tenant/holidays/{holidayDate}` | (없음) | 204 | 404 `HOLIDAY_NOT_FOUND` |

- 식별자는 **날짜 자체**(`{holidayDate}` = `yyyy-MM-dd`) — PK(tenant_id, holiday_date)와 정합, 대리키 불요.
- 타 테넌트 날짜라는 개념이 없다(세션 tenantId로 항상 2중 조건) — 격리는 매퍼 규약으로 보장(§8 ISO 재사용).

### 3-2. DTO (`holiday/HolidayDtos.java`)

```java
public record HolidayResponse(LocalDate holidayDate, String holidayName,
        HolidayType holidayType, LocalDateTime updatedAt) {}

public record HolidaySyncResponse(int year, String country,
        int fetched, int inserted, int deleted, int skippedCompany) {}

public record HolidayCreateRequest(
        @NotNull(message = "{validation.holiday-date.required}") LocalDate holidayDate,
        @NotBlank(message = "{validation.holiday-name.required}")
        @Size(max = 100, message = "{validation.holiday-name.size}") String holidayName) {}

public record HolidayUpdateRequest(
        @NotBlank(message = "{validation.holiday-name.required}")
        @Size(max = 100, message = "{validation.holiday-name.size}") String holidayName) {}
```

- **수동 등록(POST)은 항상 COMPANY로 저장**(요청에 type 없음). 외부 API 장애 시 국가 공휴일을 수동
  등록하는 경우도 COMPANY로 들어가며, 이후 동기화가 성공해도 그 행은 불가침으로 남는다(표기 유형만
  다를 뿐 출결 계산 동일 — 무해). type을 열어주면 "수동 NATIONAL"이 다음 동기화에서 소리 없이
  지워지는 함정이 생기므로 닫는다.

### 3-3. NATIONAL 행의 수정·삭제 — **허용 + 복원 경고** (택1 결정)

- **결정**: NATIONAL 행도 `PUT`(명칭 수정)·`DELETE`를 **허용**한다. 단 프론트는 NATIONAL 대상 조작 시
  "재동기화하면 이 변경은 국가 공휴일 데이터로 되돌아갑니다"를 인라인 확인 패널로 경고한다(§5-1).
- 근거: 수동 수정 기능 병행은 소유자 요구 1의 명시 사항이고, 그 대상은 주로 NATIONAL이다
  (임시공휴일 미반영·명칭 오차·"그날 우리는 근무" 등). 금지하면 오차 대응 경로 자체가 사라진다.
  허용의 위험(재동기화가 되돌림)은 §2-4가 멱등이라 데이터 파손이 아니라 "의도 상실"에 그치고,
  경고 + `HolidaySyncResponse` 카운트로 가시화된다. 영구히 다르게 하고 싶으면 NATIONAL 삭제 후
  같은 날짜에 COMPANY 등록(동기화 불가침)이 정식 경로 — 이 안내도 경고 문구에 포함한다(`SYNC_REVERT_WARN`).
- `PUT`은 명칭만 수정 가능, **유형 변경 불가**(수정으로 NATIONAL↔COMPANY를 오가면 §2-4의 삭제/보존
  경계가 흐려짐). 유형을 바꾸는 유일한 길은 삭제 후 재등록.

### 3-4. 매퍼 (`holiday/HolidayMapper.java`)

테넌트 전파 규약(tenantId 첫 `@Param` + 전 쿼리 2중 조건) 준수. `findByYear` / `insert(IGNORE 아님 —
중복은 409로 노출)` / `insertNationalIgnore(배치)` / `updateName` / `deleteByDate` /
`deleteNationalByYear`. 조회 정렬은 `holiday_date ASC`.

---

## 4. 테넌트 생성 API 변경 — country 필수

### 4-1. 백엔드

- `TenantCreateRequest`에 `country` 추가(필수):

```java
@Schema(description = "schema.field.country", example = "KR")
@NotBlank(message = "{validation.country.required}")
String country,   //ProfileCountry.of()로 검증 — 미지원이면 400 COUNTRY_UNSUPPORTED / validation.country.supported
```

- `TenantService.create()`: `ProfileCountry.of(request.country())` null이면
  `400 COUNTRY_UNSUPPORTED`(기존 코드·키 재사용). `TenantCreate`/`TenantMapper.insert`에 country 전달.
- `Tenant`/`TenantResponse`/`TenantCreateResponse`에 `country` 필드 추가(W007 목록·생성 결과 표시),
  `TenantCreateResponse`에 `holidaysSynced`(boolean, §2-5) 추가.
- 생성 후 당해·익년 자동 동기화(§2-5).
- **country 수정 API는 두지 않는다**(Phase 3). 소재국 변경은 법인 이전급 이벤트로, 기존 NATIONAL
  공휴일 전량 교체가 얽힌다 — 필요 시 운영 절차(수동 SQL + 재동기화)로 처리하고 후속 과제로 기재.

### 4-2. 기업 정보(W008) API에서 country 제거

- `TenantProfileRequest.country` **삭제** — 검증·마스킹의 국가는 `tenantMapper.findById(tenantId)`의
  tenant.country를 사용(`upsertProfile`의 `requireTenant`가 이미 tenant를 조회하므로 반환형만 활용).
- `TenantProfileMapper.upsert`에서 country 파라미터 제거. `TenantProfile` record의 country는
  **tenant JOIN으로 공급**(`SELECT p.…, t.country FROM tenant_profile p JOIN tenant t …`) —
  `TenantProfileResponse.country`(라벨 분기용)는 계약 불변으로 유지된다.

### 4-3. 프론트 W007 생성 폼

- `TenantsScreen.tsx` 생성 폼에 **소재국 셀렉트**(KR/JP) 추가 — 키 `COUNTRY`/`COUNTRY_KR`/`COUNTRY_JP`를
  W007 window로 신규 시드(언어 키는 window 단위 — W008 기존 키를 가로쓰지 않는 현행 관례, §7-2).
- 생성 결과 패널에 `holidaysSynced=false`면 `HOLIDAY_SYNC_FAILED_NOTICE` 문구 표시.
- W008(`TenantDetailScreen.tsx`) 기업 정보 폼에서 **country 입력 제거, 표시 전용으로 전환**
  (응답의 country로 `BIZ_REG_NO_KR/JP` 라벨 분기는 현행 그대로).

---

## 5. 프론트엔드 변경

### 5-1. 신규 화면 W013 — 공휴일 관리 (`HolidaysScreen.tsx`, TENANT_ADMIN 전용)

- `Screen.java`에 `HOLIDAYS("W013", Set.of(Role.TENANT_ADMIN))` 추가(W010~W012는 이메일 온보딩 결번 준수).
  초기 데이터 없음(W007~W009 패턴 — 텍스트는 navigate 응답의 W013 texts + W999 공통).
- 헤더 메뉴(App.tsx): TENANT_ADMIN 행에 `HOLIDAYS`(W013) 버튼 추가 —
  `HOME · ATTEND(W005) · MEMBERS(W009) · HOLIDAYS(W013) · LOGOUT`.
- 화면 구성:
  - **연도 셀렉터**(현재 −1 ~ +2 — §2-6과 동일 범위) + `GET /tenant/holidays?year=` 목록 테이블:
    날짜(요일은 Intl API — W006 방식) / 명칭 / **유형 뱃지**(`TYPE_NATIONAL` 파란 계열,
    `TYPE_COMPANY` 초록 계열 — 목록에서 한눈에 구분) / 행별 수정·삭제 버튼.
  - **동기화 버튼**: 인라인 확인 패널 경유(`window.confirm` 미사용 — 언어 마스터를 못 쓰는 현행 관례).
    확인 문구 `SYNC_CONFIRM`(해당 연도 NATIONAL이 국가 데이터로 교체됨을 고지). 성공 시
    `HolidaySyncResponse` 카운트를 `SYNC_DONE` 문구에 삽입 표시 후 목록 재조회. 502는 배너로 표시.
  - **등록 폼**: 날짜(date input) + 명칭 → `POST`(COMPANY 고정). 409는 필드 에러로 표시.
  - **NATIONAL 행 수정·삭제**: 인라인 확인 패널에 `SYNC_REVERT_WARN`(재동기화 시 복원됨 + 영구 변경은
    COMPANY 재등록 안내 — §3-3) 표시 후 실행. COMPANY 행은 일반 확인만.
- `endpoints.ts`에 `tenantHolidayApi { list(year), sync(year), create, update, remove }`,
  `types.ts`에 `HolidayEntry`/`HolidaySyncResult`/`HolidayCreateRequest` 등 추가.

### 5-2. W006 월별 상세 — 공휴일 이름 표시 (기존 구조 활용)

- `DailyAttendance`에 `holidayName`(nullable String) 추가 — **추가 필드라 프론트 하위호환**.
- `DetailsScreen.tsx`의 휴일 셀을 `{day.holidayName ?? t('HOLIDAY')}`로 — 공휴일이면 명칭
  ("삼일절"/"創立記念日"), 개인 휴가(work_schedule.holiday)면 명칭이 없어 기존 `HOLIDAY` 라벨 폴백.
- W005는 W006 임베드 표시이므로 자동 반영(별도 변경 없음).

---

## 6. 기존 로직 영향

| 지점 | 영향 | 내용 |
|------|------|------|
| `MonthlyAttendanceAssembler` | **경미** | 현재 공휴일을 `Set<LocalDate>`(날짜만)로 소비 — 휴일 판정 로직은 불변. 명칭 표시를 위해 파라미터를 `Map<LocalDate, String> holidays`로 바꾸고(판정은 `containsKey`), 휴일 행 생성 시 `holidays.get(day)`를 `DailyAttendance.holidayName`에 싣는다(개인 휴일은 null). 단위 테스트 시그니처만 동반 수정 |
| `ScheduleMapper.findHolidayDates` | 대체 | `findHolidaysBetween`(date+name 조회)로 교체 — holiday 매퍼 로직이 커지므로 신설 `HolidayMapper`로 이동, ScheduleMapper는 work_schedule 전용으로 정리. 호출부는 `AttendanceService.monthly` 1개소 |
| `ProfileCountry` | 의미 확장·이름 유지 | 값의 출처가 tenant.country로 이동하고 "공휴일 동기화 국가 코드 공급"(enum name = Nager countryCode = ISO alpha-2) 역할이 추가된다. `TenantCountry`로의 개명은 기각 — 검증·마스킹 전략과 기존 테스트가 걸려 있어 diff 대비 이득이 없다. javadoc만 "테넌트 소재국별 규칙(식별번호 검증·마스킹 + 공휴일 동기화 국가)"으로 갱신 |
| `TenantProfileService` | 변경 | 검증·마스킹의 국가를 요청이 아닌 **tenant.country**에서 취득(§4-2). `COUNTRY_UNSUPPORTED` 분기는 테넌트 생성 쪽으로 이동 |
| `RoleInterceptor`/`WebConfig` | **무변경** | `/api/v1/tenant/**` 화이트리스트가 신규 경로를 자동 포괄(D12) |
| 격리 규약 | 준수 | holiday 전 쿼리 tenantId 2중 조건 + 세션 tenantId만 사용. 테넌트 A의 동기화·CRUD는 B에 0건 영향(HOL-S3) |
| V4와의 관계 | 없음 | V4 [7]의 PK 교체는 완료 전제. V7은 컬럼 확장만 |

---

## 7. 신규 메시지·언어 마스터 키

### 7-1. `messages/messages*.properties` (ko/en/ja 3벌 — 서버 조립 메시지)

| 키 | KOR (EN/JA는 동일 취지로 번역) |
|----|-------------------------------|
| `holiday.not-found` | 해당 날짜의 공휴일이 없습니다. |
| `holiday.date.duplicated` | 같은 날짜의 공휴일이 이미 등록되어 있습니다. |
| `holiday.year.range` | 동기화는 작년부터 2년 후까지의 연도만 지정할 수 있습니다. |
| `holiday.sync.upstream` | 공휴일 데이터 취득에 실패했습니다. 잠시 후 다시 시도해 주세요. |
| `validation.holiday-date.required` | 날짜를 입력해 주세요. |
| `validation.holiday-name.required` | 공휴일 명칭을 입력해 주세요. |
| `validation.holiday-name.size` | 공휴일 명칭은 100자 이내로 입력해 주세요. |
| `api.tenant-holiday.*` | Swagger tag/summary/description 키 일습(sync/list/create/update/delete — 기존 `api.system-tenant.*` 패턴) |

기존 키 재사용: `validation.country.required` / `validation.country.supported`(V6 시드 — 테넌트 생성 검증으로 이동).

### 7-2. `language_master` 시드 (V7 자기 몫, INSERT IGNORE, 3개국어)

| window | lang_key | KOR / ENG / JPN |
|--------|----------|------------------|
| W999 | `HOLIDAYS` | 공휴일 / Holidays / 祝日 |
| W007 | `COUNTRY` | 소재국 / Country / 所在国 |
| W007 | `COUNTRY_KR` | 대한민국 / South Korea / 韓国 |
| W007 | `COUNTRY_JP` | 일본 / Japan / 日本 |
| W007 | `HOLIDAY_SYNC_FAILED_NOTICE` | 공휴일 자동 등록에 실패했습니다. 고객사 관리자가 공휴일 화면에서 동기화해 주세요. / Automatic holiday registration failed. Ask the tenant admin to sync on the holidays screen. / 祝日の自動登録に失敗しました。管理者が祝日画面で同期してください。 |
| W013 | `HOLIDAYS_TITLE` | 공휴일 관리 / Holiday Management / 祝日管理 |
| W013 | `YEAR` | 년 / Year / 年 |
| W013 | `DATE` | 날짜 / Date / 日付 |
| W013 | `NAME` | 명칭 / Name / 名称 |
| W013 | `TYPE` | 유형 / Type / 種別 |
| W013 | `TYPE_NATIONAL` | 국가 공휴일 / National / 国民の祝日 |
| W013 | `TYPE_COMPANY` | 회사 지정 / Company / 会社指定 |
| W013 | `SYNC` | 국가 공휴일 동기화 / Sync national holidays / 祝日を同期 |
| W013 | `SYNC_CONFIRM` | 이 연도의 국가 공휴일을 최신 데이터로 교체합니다. 회사 지정 공휴일은 유지됩니다. 실행할까요? / Replace this year's national holidays with the latest data. Company holidays are kept. Proceed? / この年の国民の祝日を最新データで置き換えます。会社指定は維持されます。実行しますか？ |
| W013 | `SYNC_DONE` | 동기화 완료: 추가 {inserted} / 삭제 {deleted} / 회사 지정 우선 {skipped} / Synced: added {inserted}, removed {deleted}, kept company {skipped} / 同期完了：追加 {inserted}・削除 {deleted}・会社指定優先 {skipped} |
| W013 | `SYNC_REVERT_WARN` | 국가 공휴일을 수정·삭제해도 다음 동기화에서 원래대로 복원됩니다. 영구히 바꾸려면 삭제 후 회사 지정으로 등록하세요. / Edits to national holidays are restored on the next sync. To make it permanent, delete and re-register as a company holiday. / 国民の祝日への変更は次回の同期で元に戻ります。恒久的に変えるには削除して会社指定で登録してください。 |
| W013 | `ADD_HOLIDAY` | 회사 공휴일 등록 / Add company holiday / 会社の祝日を登録 |
| W013 | `DELETE_CONFIRM` | 이 공휴일을 삭제할까요? / Delete this holiday? / この祝日を削除しますか？ |
| W013 | `EMPTY` | 등록된 공휴일이 없습니다. 동기화를 실행해 주세요. / No holidays registered. Run a sync. / 祝日が未登録です。同期を実行してください。 |

(`{inserted}` 등 플레이스홀더 치환은 프론트에서 문자열 replace — 서버 메시지 조립 대상 아님.)

---

## 8. 테스트 계획

레벨 표기·픽스처는 test-plan §0을 따른다(U=단위 Mockito·DB 불요 / S=실기동 스모크 curl / E=E2E).
케이스 ID는 테스트 메소드/스크립트 주석에 그대로 표기.

### 8-1. 단위 (U — CI 머지 게이트)

| ID | 대상 | 검증 |
|----|------|------|
| HOL-01 | Assembler | `Map<LocalDate,String>` 휴일이 `holidayName`으로 실림. 개인 휴일(schedule.holiday)은 name null. 휴일 판정·야근·미퇴근 기존 규칙 회귀 없음 |
| HOL-02 | NagerDateClient 파서 | `global=false`·counties 있음·`types`에 Public 없음 → 제외. date/localName 채용 |
| HOL-03 | HolidayService.sync | 검증 실패(빈 목록·연도 불일치·파싱 불가) 시 예외 + **매퍼 delete/insert 미호출**(빈 응답이 한 해를 지우지 않음) |
| HOL-04 | HolidayService.sync | COMPANY 존재 날짜는 skip 카운트(INSERT IGNORE 경로), 삭제는 NATIONAL+해당 연도만(매퍼 인자 검증) |
| HOL-05 | HolidayService.sync | 연도 범위 밖 400 `HOLIDAY_YEAR_RANGE`(경계 −1/+2는 통과) |
| HOL-06 | HolidayService CRUD | 중복 409 / 미존재 404 / PUT은 명칭만(유형 불변) / POST는 항상 COMPANY |
| HOL-07 | TenantService.create | country 미지원 400 `COUNTRY_UNSUPPORTED` / sync 예외 시에도 생성 성공 + `holidaysSynced=false` |
| HOL-08 | TenantProfileService | 검증·마스킹 국가가 **tenant.country**에서 유래(KR 테넌트에 JP 형식 400 — 기존 CTY 케이스를 출처만 바꿔 재검증) |
| HOL-09 | NagerDateClient | 5xx/IO 1회 재시도 후 성공 채용, 4xx 즉시 실패(재시도 없음) — RestClient는 base-url 스텁 또는 인터페이스 목킹 |

**외부 API 목킹 방침**: `NagerDateClient`를 인터페이스로 두고 서비스 단위 테스트는 Mockito 목(현행
DB 불요 원칙 유지). 클라이언트 자체(HOL-02/09)는 `app.holiday.nager.base-url`을 로컬 스텁으로 돌려
검증한다 — 단위 레벨에서 실 외부 API를 절대 호출하지 않는다(CI가 프록시 밖일 수 있음).

### 8-2. 실기동 스모크 (S — 릴리즈 게이트, curl)

| ID | 시나리오 |
|----|----------|
| HOL-S1 | V7 리허설: V6 상태 DB에 적용 — tenant.country backfill(KR), name→holiday_name 리네임, 기존 행 COMPANY. **부분 실패 지점([H-2] 후·[H-3] 후)에서 repair→재실행 통과** |
| HOL-S2 | TA-A 로그인 → sync?year=당해 → 200, 목록에 NATIONAL 수 건. 재실행 시 inserted=deleted(멱등) |
| HOL-S3 | 격리: A의 sync/CRUD 후 B의 목록 0건 영향. MEMBER 403, SA 403(role 부족) |
| HOL-S4 | COMPANY 등록 → 같은 날짜 sync → COMPANY 잔존(skippedCompany=1). NATIONAL 삭제 → 재sync → 복원 |
| HOL-S5 | 테넌트 생성(country=JP) → holidaysSynced=true, 당해·익년 JP 공휴일 적재. Nager 스텁을 죽인 상태로 생성 → 201 + holidaysSynced=false |
| HOL-S6 | `GET /attendance/monthly` 응답 days에 holidayName 동봉(공휴일 날짜), 개인 휴일은 null |
| HOL-S7 | sync 스텁이 빈 배열/500 응답 → 502, 기존 행 무변경 |

**스모크의 외부 API**: 기본은 **로컬 스텁 서버**(고정 KR/JP 픽스처 JSON — 결정적, 프록시 무관).
릴리즈 전 1회에 한해 실 Nager.Date 호출 검증(HOL-S2를 실 base-url로) — 이때 JVM에
`https.proxyHost/Port`를 주입하고 프록시 CA truststore를 확인한다(§2-2). 실검증 실패는 릴리즈
차단이 아니라 조사 항목(외부 가용성은 우리 결함이 아님 — 스텁 그린이 게이트).

### 8-3. E2E (Playwright)

HOL-E1: SA가 W007에서 country=KR 테넌트 생성 → TA 로그인 → 헤더 HOLIDAYS로 W013 진입 → 목록에
NATIONAL 뱃지 확인 → COMPANY "창립기념일" 등록 → 동기화 실행(카운트 표시) → W006 이동, 해당 날짜
행에 "창립기념일"/국가 공휴일 명칭 표시 확인 → 언어 전환(JPN) 후 라벨 확인.

---

## 9. 다른 도메인과의 합류 지점 (조정 필요 목록)

| 지점 | 내용 |
|------|------|
| `V7__phase3.sql` | 단일 파일에 이메일 온보딩·스케줄 몫과 합류. 본 문서 몫은 §1의 [H-1]~[H-4] + §7-2 시드. **구획 주석으로 분리, 타 도메인이 tenant/holiday를 만지면 순서 조정 필요** |
| `TenantCreateRequest/Response` | country·holidaysSynced 추가. 이메일 온보딩이 초대 링크로 `initialPassword` 반환을 대체하면 같은 DTO를 양쪽이 수정 — 필드 병합 조정 |
| `Screen.java` / App.tsx 헤더 | W013 추가. W010~W012(이메일 온보딩)와 enum·메뉴 병합 |
| W999 시드 | `HOLIDAYS` 키 추가 — 타 계획의 W999 추가 키와 INSERT IGNORE로 공존(충돌 없음, 파일 병합만) |
| work_schedule(개인 휴일) | 스케줄 도메인이 W006/W005 표시를 바꾸면 §5-2의 휴일 셀 폴백(`t('HOLIDAY')`)과 표시 규칙 합의 필요 |
