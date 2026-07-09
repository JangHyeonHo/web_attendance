# 멀티테넌시 전환 테스트 계획서

- 대상: [plan-saas-multitenancy.md](../plan-saas-multitenancy.md)의 Phase 1~2 구현
- 전제(확정): Pool 모델(격리는 테스트로 보장 — 최우선) / 3단계 role / 테넌트 코드 로그인 /
  멤버는 TENANT_ADMIN 등록제 / 기업·결제 정보 암호화+마스킹 / 마지막 관리자 보호 /
  SYSTEM_ADMIN은 출결 데이터 접근 불가
- 현행 자산: 단위 35건(Mockito, DB 불요) + 실기동 스모크(curl, D9) + E2E 12단계
- 상태: 계획. 케이스 ID는 구현 시 테스트 메소드/스크립트 주석에 그대로 표기한다.

---

## 0. 공통 규약

### 0-1. HTTP 상태코드 규약 (전 케이스 공통 — 여기서 확정)

| 상황 | 응답 | 근거 |
|------|------|------|
| 미로그인 | **401** | 현행과 동일 |
| role 부족 (엔드포인트 자체에 권한 없음) | **403** | 엔드포인트 존재는 숨길 필요 없음 |
| role은 충분하나 **대상 리소스가 타 테넌트** | **404** | 존재 비노출 — 403을 주면 "그 ID가 존재한다"가 유출됨 |
| 로그인 실패(비밀번호·이메일·테넌트 코드·SUSPENDED·DISABLED 무관) | **401 단일 메시지** | 마스터 계획 §6 — 테넌트 존재 여부도 비노출 |
| 마지막 관리자 보호 위반 | **409** + 코드 `LAST_TENANT_ADMIN` (전 문서 통일 — 교차 검증 최종 결정 D-D) | 검증 오류가 아닌 상태 충돌 |
| 로그인 레이트 리밋 발동 | **429** + 코드 `RATE_LIMITED` (계정 실존 여부와 무관하게 동일 — security-plan §3) | 존재 오라클 방지 |

### 0-2. 표준 픽스처 (격리/권한/E2E 공통)

| 식별자 | 내용 |
|--------|------|
| 테넌트 A | code=`ACME`, ACTIVE |
| 테넌트 B | code=`BETA`, ACTIVE |
| 테넌트 S | code=`SLEEP`, **SUSPENDED** (LGN-05용) |
| `SA` | SYSTEM_ADMIN — **`DEFAULT` 테넌트 소속**(V4 시드 승격. `users.tenant_id NOT NULL`이므로 "무테넌트"는 불가 — 교차 검증 발견 14). 로그인은 항상 `{tenantCode:'DEFAULT'}` |
| `TA-D` | **DEFAULT 테넌트의 TENANT_ADMIN** — 리허설 시드의 is_admin 유저가 V4에서 변환된 계정(data-migration-v4 §8-1 필수 조건). 회귀 스모크(REG-04)·E2E-REG-01의 멤버 등록/출결 실행 주체(SA는 화이트리스트 정책상 `/tenant`·`/attendance` 403 — 발견 15) |
| `TA-A` / `TA-B` | 각 테넌트의 TENANT_ADMIN |
| `M-A` / `M-B` | **같은 이메일** `hong@example.com`이 A/B에 각각 존재, **비밀번호는 서로 다름** |
| 시드 데이터 | A/B 각각에 출결 스탬프·스케쥴·공휴일 1세트(서로 다른 값으로 — 응답 혼입 검출용) |

### 0-3. 테스트 레벨 표기

- **U** = 단위(Mockito, `mvn test`, DB 불요) — CI 머지 게이트
- **S** = 실기동 스모크(로컬 MariaDB + curl 스크립트) — 릴리즈 게이트
- **E** = E2E(Playwright, 프론트+백엔드 실기동) — 릴리즈 게이트

같은 케이스 ID가 U와 S에 모두 배치될 수 있다(서비스 규칙은 U로, 실제 SQL/HTTP는 S로 이중 검증).

---

## 1. 크로스 테넌트 격리 테스트 매트릭스 (최우선)

Pool 모델의 생명선. **이 절의 케이스 중 하나라도 실패하면 머지/릴리즈 불가**(마스터 계획 §5-3).

### 1-1. 행위자 × 대상 매트릭스 (요약표)

셀 = 기대 HTTP 코드(케이스 ID). "B의 ~"는 테넌트 B 소유 리소스를 A 세션(또는 SA 세션)으로 접근한다는 뜻.

| 행위자 \ 대상 | 자기 테넌트 출결/상세 | B의 멤버 목록·관리 | B 멤버의 출결·상세 | B의 check 토큰 사용 | B의 기업/결제 정보 |
|---|---|---|---|---|---|
| **MEMBER-A** | 200 (ISO-01) | 403 (ISO-04) | 404 (ISO-02) | 404 (ISO-03) | 403 (ISO-05) |
| **TENANT_ADMIN-A** | 200, B 데이터 0건 (ISO-06) | 404 (ISO-07) | 404 (ISO-08) | 404 (ISO-09) | 403 (ISO-10) |
| **SYSTEM_ADMIN** | 403 — 출결 자체 불가 (ISO-11) | 403 (ISO-12) | 403 (ISO-11) | 403 (ISO-11) | 200, 마스킹 (ISO-13) |

- MEMBER/TA의 타 테넌트 리소스는 **404**(존재 비노출), role 자체가 없는 엔드포인트는 **403**.
- SYSTEM_ADMIN 행이 403인 것은 격리 위반이 아니라 **정책**(출결 데이터 최소 접근, §4 보완정책 2) — 케이스는 §2에서 재사용.

### 1-2. 상세 케이스

| ID | 레벨 | 행위자(세션) | 요청 | 기대 | 검증 포인트 |
|----|------|--------------|------|------|-------------|
| ISO-01 | S | M-A | `GET /api/v1/attendance/status`, `GET .../monthly` | 200 | 응답 스탬프가 전부 A 시드값. B 시드값(다른 시각)이 한 건도 안 섞임 |
| ISO-02 | S | M-A | 요청 바디/쿼리에 `tenantId=B`, `userId=M-B.id`를 **주입 변조** 후 출결 조회/등록 | 200 | 변조값 무시(테넌트는 항상 세션에서 — §5 원칙), 결과는 자기 데이터. 관리용 userId 파라미터가 있는 API면 404 |
| ISO-03 | S | M-A | M-B 세션에서 발급받은 check 토큰으로 `POST /api/v1/attendance` (confirm) | 404 | `findCheckHash`가 `(token, tenantId, userId)` 3중 바인딩. 코드 `CHECK_NOT_FOUND` 계열, 토큰 존재 비노출 |
| ISO-04 | S | M-A | `GET /api/v1/tenant/members` | 403 | role 부족(MEMBER) |
| ISO-05 | S | M-A | `GET /api/v1/system/tenants/{B}/profile` | 403 | role 부족 |
| ISO-06 | S | TA-A | `GET /api/v1/tenant/members` | 200 | **B 멤버(M-B, TA-B)가 목록에 0건**. 같은 이메일 M-B가 안 보이는 것까지 단언 |
| ISO-07 | S | TA-A | `PUT /api/v1/tenant/members/{M-B.id}/status`·`/role` (Phase 2 계약의 대상 지정 API 전부 — D-D) | 404 | ID 추측 공격 차단, 존재 비노출 |
| ISO-08 | S(**이연**) | TA-A | B 멤버의 출결 현황/월별 상세(관리자용 조회 API, `userId=M-B.id`) | 404 | **Phase 3 이연** — 해당 API(TENANT_ADMIN 출결 현황 조회)는 Phase 1~2에 존재하지 않음(전 출결 API가 `@LoginUser` 셀프서비스 — 발견 13). Phase 3 도입 시 활성화. 그때까지는 대체 스모크: 매퍼 SQL 직접 실행으로 2중 조건(`AND tenant_id=`) 확인 |
| ISO-09 | S | TA-A | B에서 발급된 check 토큰으로 confirm | 404 | ISO-03과 동일 규칙을 관리자 세션에서도 |
| ISO-10 | S | TA-A | `GET /api/v1/system/tenants/{A}/profile` (자기 테넌트 것이라도) | 403 | 기업/결제 정보는 SYSTEM_ADMIN 전용(§6-1). Phase 2에서 비민감 항목 별도 API가 생기면 그 API만 200 |
| ISO-11 | S | SA | `GET /api/v1/attendance/status`, `.../monthly`, `POST .../check` | 403 | **SYSTEM_ADMIN 출결 접근 불가 정책**. tenantId를 파라미터로 지정해도 동일 |
| ISO-12 | S | SA | `GET/POST /api/v1/tenant/members` | 403 | 멤버 관리는 TENANT_ADMIN 전용. SA는 멤버 "수"(메타)만 테넌트 목록에서 |
| ISO-13 | S | SA | `GET /api/v1/system/tenants`, `/{id}/profile`, `/{id}/billing` | 200 | 전 테넌트 조회 가능. 단 **암호화 필드는 마스킹값만**(§3) |
| ISO-14 | U | — | AttendanceService가 매퍼 호출 시 세션의 tenantId를 그대로 전달하는지 `verify(mapper).findLatest(eq(TENANT_A), eq(USER_ID))` 등으로 검증 (status/check/confirm 3경로) | pass | 서비스 레이어의 tenantId 전파 누락 검출 (3건: ISO-14a/b/c) |
| TTL-01 | S | M-A | check 토큰 발급 **31분 후** confirm 호출 | 404 | 체크토큰 TTL 30분 단축(security-plan §1 T7 확정, D-F) — `deleteExpiredChecks` 상수 24h→30분의 실기동 검증 |

### 1-3. 로그인 격리 — 같은 이메일이 두 테넌트에 존재

`UNIQUE(tenant_id, email)` 전환의 핵심 시나리오. AuthService 단위(매퍼 mock) + 스모크 이중 배치.

| ID | 레벨 | 요청 | 기대 | 검증 포인트 |
|----|------|------|------|-------------|
| LGN-01 | U+S | `POST /auth/login {tenantCode:ACME, email:hong@…, pw:pwA}` | 200 | 세션 `tenantId=A`, `GET /auth/me`가 A 계정. 이후 출결 조회가 A 데이터만 |
| LGN-02 | U+S | `{tenantCode:BETA, email:hong@…, pw:pwB}` | 200 | 세션 `tenantId=B`, **A의 출결이 전혀 안 보임** |
| LGN-03 | U+S | `{tenantCode:BETA, email:hong@…, pw:pwA}` (**A의 비밀번호로 B 로그인**) | 401 | 크리덴셜이 테넌트 스코프로 검증됨. 단일 메시지 |
| LGN-04 | U+S | `{tenantCode:NOPE, …}` (미존재 테넌트 코드) | 401 | LGN-03과 **완전히 동일한 응답 본문**(테넌트 존재 비노출) |
| LGN-05 | S | `{tenantCode:SLEEP, …}` (SUSPENDED 테넌트의 유효 계정) | 401 | 동일 단일 메시지. 정지 즉시 전 멤버 로그인 차단 |
| LGN-06 | U+S | status=DISABLED 멤버의 유효 크리덴셜 | 401 | ACTIVE만 로그인 허용(§6). 동일 단일 메시지 |
| LGN-07 | S | LGN-01 후 로그아웃 없이 LGN-02 (세션 재로그인) | 200 | 세션 재발급으로 **이전 테넌트 컨텍스트가 잔존하지 않음** + 언어 설정 승계(D8 회귀) |
| LGN-08 | U+S | 같은 계정 키로 5분 내 실패 5회 후 6번째 시도 | 429 | 계정 임계(security-plan §3-1). 응답 코드 `RATE_LIMITED`, `Retry-After` 헤더 없음 |
| LGN-09 | U | 같은 IP에서 계정을 바꿔가며 5분 내 실패 30회 후 시도 | 429 | IP 임계 — 스터핑 차단 |
| LGN-10 | U | 차단 시간 경과 후 재시도 / 임계 전 성공 로그인 | 200 / 계정 카운터 초기화 | 차단 해제·성공 시 초기화·윈도우 슬라이딩(LoginRateLimiter 단위 4건의 일부) |
| LGN-11 | S | **존재하지 않는** tenantCode/email 조합으로 임계 초과 | 429 (실존 계정과 **완전히 동일한 응답**) | 429가 계정 실존 오라클이 되지 않음(스모크 1건 — 발견 8 권고) |

> **레벨 배치 근거(중요)**: ISO-01~13은 스모크가 **주 검증**이다. 현행 단위 테스트는 매퍼를 mock하므로
> "매퍼 SQL에 `tenant_id` 조건이 실제로 들어갔는가"(Pool 모델 최대 리스크)를 단위로는 증명할 수 없다.
> 단위(U)는 서비스가 tenantId를 전달하는지(ISO-14)와 규칙 분기까지만 커버한다. → §7 CI 게이트, §8 갭 참조.

---

## 2. 권한(Role) 테스트

### 2-1. role별 엔드포인트 접근 매트릭스

각 행이 케이스 1건(4개 세션으로 순차 호출하는 스모크 + RoleInterceptor 단위). `—` = 해당 없음.

| ID | 엔드포인트 | 미로그인 | MEMBER | TENANT_ADMIN | SYSTEM_ADMIN |
|----|-----------|---------|--------|--------------|--------------|
| ROLE-01 | `POST /api/v1/auth/login` | 200/401 | — | — | — |
| ROLE-02 | `GET /api/v1/auth/me`, `POST /auth/logout` | 401 | 200 | 200 | 200 |
| ROLE-03 | `GET /api/v1/attendance/status` | 401 | 200 | 200 | **403** |
| ROLE-04 | `POST /api/v1/attendance/check`, `POST /api/v1/attendance` | 401 | 200 | 200 | **403** |
| ROLE-05 | `GET /api/v1/attendance/monthly` | 401 | 200 | 200 | **403** |
| ROLE-06 | `POST /api/v1/users` (**폐기된 공개 가입**) | 404 | 404 | 404 | 404 |
| ROLE-07 | `POST /api/v1/navigation` | 200 | 200 | 200 | 200 (공개 — 결과 화면이 role별, §2-2) |
| ROLE-08 | `GET /api/v1/i18n/{windowId}` | 200 | 200 | 200 | 200 |
| ROLE-09 | `GET/POST /api/v1/admin/i18n` (글로벌 언어 마스터) | 401 | 403 | **403** | 200 |
| ROLE-10 | `POST /api/v1/system/tenants` (테넌트 생성) | 401 | 403 | 403 | 201 |
| ROLE-11 | `GET /api/v1/system/tenants` (목록) | 401 | 403 | 403 | 200 |
| ROLE-12 | `PUT /api/v1/system/tenants/{id}/status` (정지/재개 — 경로 라벨 정정, 발견 17. 테넌트명 수정 `PUT /{id}`는 Phase 3 이연으로 계약에서 제외) | 401 | 403 | 403 | 200 |
| ROLE-13 | `GET/PUT /api/v1/system/tenants/{id}/profile`, `/billing` | 401 | 403 | 403 | 200 |
| ROLE-14 | `GET/POST /api/v1/tenant/members` | 401 | 403 | 200/201 | **403** |
| ROLE-15 | `PUT /api/v1/tenant/members/{id}/status`·`/{id}/role` (상태/역할 변경 — D-D 계약) | 401 | 403 | 200 | **403** |

- 굵은 403 = 이번 전환에서 **새로 생기는 거부**로, 회귀가 아니라 신규 정책임을 명시:
  - ROLE-03~05 SYSTEM_ADMIN 403 — 출결 데이터 접근 불가 정책(ISO-11과 동일 케이스, 매트릭스에는 완결성 위해 재기재)
  - ROLE-09 TENANT_ADMIN 403 — 언어 마스터는 "제품 글로벌"이므로 운영사 전용(§3, §4). **현행은 단일 admin이 200이었음** → 프론트 W004 분기와 세트로 검증
- 단위(U)로는 `RoleInterceptor`에 (경로, role) 조합을 표 그대로 파라미터라이즈드 테스트(1건의 `@ParameterizedTest`로 매트릭스 전 셀). 스모크(S)로는 실 HTTP 상태코드를 세션 4종으로 확인.

### 2-2. 화면 전개(navigation) role 분기 — 단위

확정 화면 코드(D-A): **W007=테넌트 목록 / W008=테넌트 상세(기업·결제, W007 임베드) / W009=멤버 관리**. 거부 사유는 `ROLE_DENIED`(구 `ADMIN_ONLY` 개명). 홈: SYSTEM_ADMIN→W007, TENANT_ADMIN/MEMBER→W005.

| ID | 레벨 | 입력 | 기대 |
|----|------|------|------|
| NAV-01 | U | MEMBER가 W004 요청 | 출결 화면(W005, 홈) + `ROLE_DENIED` |
| NAV-02 | U | TENANT_ADMIN이 W004 요청 | 자기 홈 = **출결 화면(W005)** + `ROLE_DENIED` (멤버 관리 W009는 헤더 메뉴로 진입 — D-A) |
| NAV-03 | U | SYSTEM_ADMIN이 W004/홈 요청 | W004는 허용(SYSTEM_ADMIN 전용), 홈 요청은 테넌트 목록 화면(W007) |
| NAV-04 | U | TENANT_ADMIN이 W007/W008 요청 | 자기 홈(W005)으로 + `ROLE_DENIED` |
| NAV-05 | U | SYSTEM_ADMIN이 W005(출결)/W006(상세)/W009(멤버) 요청 | 자기 홈(W007)으로 + `ROLE_DENIED` (API뿐 아니라 화면 전개에서도 차단) |
| NAV-06 | U | 미로그인이 W003(구 가입 화면) 요청 | 가입 폐기 반영 — 인덱스 또는 로그인으로 (Screen 레지스트리에서 SIGNUP 제거 확인) |
| NAV-07 | U | MEMBER가 W009 요청 | 출결 화면(W005) + `ROLE_DENIED` |

### 2-3. 마지막 관리자 보호

TENANT_ADMIN이 0명인 테넌트 방지(§4 보완정책 1). 서비스 단위(매퍼 mock: 활성 TA 카운트) + 스모크.

| ID | 레벨 | 시나리오 | 기대 |
|----|------|----------|------|
| ADM-01 | U+S | 테넌트의 **유일한** TENANT_ADMIN을 MEMBER로 강등 | 409 `LAST_TENANT_ADMIN` |
| ADM-02 | U+S | 유일한 TENANT_ADMIN을 DISABLED로 비활성 | 409 `LAST_TENANT_ADMIN` |
| ADM-03 | U | TENANT_ADMIN 2명일 때 1명 강등 | 200 (보호가 과잉 차단하지 않음) |
| ADM-04 | U | 유일한 TENANT_ADMIN이 **자기 자신**을 강등/비활성 | 409 (자기 대상도 동일 규칙) |
| ADM-05 | U | TA 2명 중 1명이 DISABLED인 상태에서 남은 **활성** 1명 강등 | 409 — 카운트 기준은 "ACTIVE인 TENANT_ADMIN 수" |
| ADM-06 | S | ADM-01을 SYSTEM_ADMIN 경유(테넌트 정지 아님, 멤버 API)로 우회 시도 | 403 (ROLE-15 — SA는 멤버 API 자체가 불가하므로 우회 경로 없음 확인) |

### 2-4. 보안 헤더 / 운영 프로파일 (스모크 — security-plan §5·§7-2 P1 격상 대응, 발견 10)

| ID | 레벨 | 케이스 | 기대 |
|----|------|--------|------|
| SEC-01 | S | 임의 API·정적 자원 응답 헤더 | `Content-Security-Policy`·`X-Content-Type-Options: nosniff`·`X-Frame-Options: DENY` 존재, `/api/` 응답에 `Cache-Control: no-store` |
| SEC-02 | S | `SPRING_PROFILES_ACTIVE=prod` 기동 후 `GET /v3/api-docs` | 404 (Swagger 완전 비활성) |
| SEC-03 | S | 로그인 응답의 `Set-Cookie` | `HttpOnly`·`SameSite=Lax` 명시 (prod에서는 `Secure` 추가) |

---

## 3. 암호화/마스킹 단위 테스트

대상: **JCA 직접 구현** AES-256-GCM 유틸 `FieldCipher`(security-plan §2 정본 — spring-security-crypto 방식은 기각됨, D-C)와 마스킹 유틸(가칭 `Masking`). 전부 순수 단위(U) — DB·컨텍스트 불요.

### 3-1. AES-GCM 암호화

| ID | 레벨 | 케이스 | 기대/방법 |
|----|------|--------|-----------|
| CRY-01 | U | 라운드트립: 사업자번호·전화·빌링키 각 대표값 + 한글/공백/최대길이 | `decrypt(encrypt(x)) == x` |
| CRY-02 | U | **키 버전 프리픽스**: 암호문이 `v1:{base64(iv12)}:{base64(ct||tag)}` 텍스트 형식(security-plan §2-3) | 프리픽스·구분자 파싱 단위 검증. `v1:` 프리픽스 없는 입력은 명시적 예외(레거시 평문 오인 방지) |
| CRY-03 | U | 키 로테이션: v1 키로 만든 암호문을 v2가 현행 키인 키맵(`{v1:…, v2:…}`)에서 복호화 | 성공. 신규 암호화는 항상 최신 버전 프리픽스 |
| CRY-04 | U | 무결성(GCM 태그): 암호문 중간 변조 후 복호화 | 예외 발생(silent corruption 없음) |
| CRY-05 | U | IV 랜덤성: 같은 평문 2회 암호화 | 서로 다른 암호문(IV 재사용 없음 — GCM에서 IV 재사용은 치명적) |
| CRY-06 | S | 키 미주입 기동: **prod 프로파일**에서 `APP_CRYPTO_KEY` 환경변수 없이 앱 기동 | **기동 실패(fail-fast)** — 평문 저장으로 조용히 진행되지 않음. (개발 프로파일은 application.properties의 개발 기본키로 기동 허용 — D-C.) 잘못된 키(base64 32바이트 아님)는 전 프로파일에서 기동 실패 |
| CRY-07 | U | 미지 키 버전: `v9:...` 복호화 | 명시적 예외(코드로 식별 가능) |

### 3-2. 마스킹

| ID | 레벨 | 입력 | 기대 |
|----|------|------|------|
| MSK-01 | U | 사업자번호 `1234567890` | `123-**-*****` (마스터 계획 §6-1 포맷 그대로) |
| MSK-02 | U | `card_last4="1234"`, brand | `**** **** **** 1234` — **PAN 원본은 애초에 저장하지 않으므로 입력은 last4뿐**임을 시그니처로 강제 |
| MSK-03 | U | 전화 `01012345678` / `02-123-4567` | `010-****-5678` 형식(국번 길이 가변 안전). 포맷은 이 케이스로 확정한다 |
| MSK-04 | U | null / 빈 문자열 / 비정상 길이 | 예외 없이 전체 마스킹(`***`) 또는 null — 부분 노출이 절대 없음 |
| MSK-05 | U | toString: **요청 DTO `TenantProfileRequest`/`TenantBillingRequest`**의 `toString()` 출력 (평문 유출 위험 지점은 record 자동 toString의 요청 DTO — 도메인 record는 암호문만 보유. 발견 18 정정) | 사업자번호·전화·빌링키 **평문 미포함**(`***`/`[PROTECTED]` 형태 — backend-api §1.3 오버라이드 규약) |

### 3-3. "빌링키가 어떤 응답에도 포함되지 않음" 검증 방법 (3중)

빌링키 비노출은 "존재하지 않음의 증명"이라 단일 테스트로 안 되며, 아래 3층으로 고정한다.

| ID | 레벨 | 방법 |
|----|------|------|
| MSK-06 | U | **타입 수준 부재**: 응답 DTO(record)에 빌링키 필드 자체가 없음 — `TenantBillingResponse.class.getRecordComponents()`를 리플렉션 스캔해 `pgCustomerKey`/`billingKey` 계열 이름 부재 단언. 도메인 객체를 실수로 직렬화해도 잡히도록 Jackson 직렬화 결과 JSON에 키 부재도 단언 |
| MSK-07 | S | **런타임 전수 스캔**: 스모크 시드에 식별 가능한 빌링키 원문(예: `SMOKE-BILLKEY-7f3a…`)을 등록 → 전 API 스모크의 **모든 응답 본문을 저장해 두고** 종료 시 `grep -r "SMOKE-BILLKEY"` + `jq` 재귀 키 스캔(`pg_customer_key`, `pgCustomerKey`)이 0건 |
| MSK-08 | S | **로그 스캔**: 스모크 실행 후 애플리케이션 로그 파일에서 빌링키 원문·사업자번호 원문 grep 0건 |
| MSK-09 | S | 조회 응답 마스킹 실측: `GET /system/tenants/{id}/profile`·`/billing` 응답이 MSK-01/02 포맷의 마스킹값이며 **원문·암호문 어느 쪽도 아님** |
| MSK-10 | S | 수정 화면 왕복: profile을 PUT으로 갱신(전체 재입력 방식) 후 재조회 — 마스킹값을 그대로 되돌려 보내는 실수(마스킹값이 DB에 저장되는 사고)가 서버에서 거부되는지 |

---

## 4. 기존 테스트 35건 영향 분류

분류: **A 무변경** / **B 픽스처·시그니처만 수정(단언 불변)** / **C 재설계**.
원칙: B에서 **단언(assert) 라인은 건드리지 않는다** — 단언 변경이 필요하면 C로 승격하고 리뷰에서 별도 승인(§6 회귀 기준).

| 테스트 (파일) | 건수 | 분류 | 필요한 변경 |
|---------------|------|------|-------------|
| `AttendanceServiceTest` §EvaluateRules (noRecentRecord, sameTypeRepeat, breakRepeatAllowed, reAttendSameDay, cannotAttendOnBreak, offWorkRules, breakRules) | 7 | **A** | 없음 — `evaluate()`는 순수 함수, 테넌트 무관 |
| `AttendanceServiceTest` §CheckConfirmFlow (checkIssuesToken, checkRejected, confirmDetectsTampering, confirmStamps, breakToggleEnds) | 5 | **B** | mock 시그니처에 tenantId 추가: `findLatest(TENANT_A, USER_ID)`, `insertCheck(anyString(), eq(TENANT_A), eq(USER_ID), …)`, `findCheckHash(token, TENANT_A, USER_ID)`. 호출부 `service.check(TENANT_A, USER_ID, req)` 형태로. 토큰/해시/상태 단언 불변 |
| `AttendanceServiceTest` §StatusQuery (waiting, working, offWorkYesterday, offWorkToday, breakStatus) | 5 | **B** | `findLatest`/`findLatestGoToWork` mock에 tenantId 추가. 상태 매핑 단언 불변 |
| `MonthlyAttendanceAssemblerTest` (normalDay, overnight, noOffWorkOver48h, consecutiveGoToWork, sameDayOverwrite, holidays, scheduleOverride, emptyStamps) | 8 | **B(경미)** | `AttendanceStamp`/`WorkSchedule` 생성자에 tenantId 필드가 추가되면 헬퍼 `stamp()` 한 곳만 수정. 페어링/야근 25시 표기/휴일 단언 전부 불변. 조립기 자체는 테넌트 무관 유지가 정답 — 조립기에 테넌트 분기가 "생기면" 설계 리뷰 대상 |
| `NavigationServiceTest` (anonymous, loggedInUser, admin, logout, unknownScreen) | 5 | **C 재설계** | `SessionUser(id, email, name, boolean)` → `(id, tenantId, tenantCode, tenantName, email, name, role)` 전환으로 픽스처 전면 교체. `anonymous`: W003(SIGNUP) 기대값이 가입 폐기로 변경. `loggedInUser` → MEMBER 케이스로 개명. `admin` → TENANT_ADMIN/SYSTEM_ADMIN **2건으로 분리**(W004 분기 상이 — TA는 `ROLE_DENIED`로 홈 W005). §2-2 NAV-01~07이 신규 추가분 |
| `MessagesTest` (resolvesPerLocale, formatsArguments, fallsBackToDefault, bundlesHaveSameKeys) | 4 | **A** | 없음. 신규 메시지 키(테넌트/멤버/마스킹/LAST_TENANT_ADMIN/RATE_LIMITED 등)를 3언어 동시 추가하지 않으면 `bundlesHaveSameKeys`가 **자동으로 실패** — 이 테스트가 신규 기능의 번역 게이트 역할 |
| `WebAttendanceApplicationTests` (contextLoads) | 1 | **A** | `@Disabled` 유지. 단 로컬 실행 시 V4 마이그레이션 리허설을 겸함(암호화 키 env 필요 — CRY-06과 연동) |
| **계** | **35** | A: 12 / B: 18 / C: 5 | |

---

## 5. E2E 시나리오 (Playwright)

기존 12단계 E2E와 같은 스타일(단일 여정, 단계마다 화면 단언). 백엔드+프론트 실기동, 시드는 SYSTEM_ADMIN 계정만(V4의 시드 승격) — 테넌트는 시나리오 안에서 만든다.

### E2E-MT-01: 멀티테넌트 풀 여정 (신규, 16단계)

```
사전: docker compose up -d && ./mvnw spring-boot:run && npm run dev
      (암호화 키는 application.properties의 개발 기본키로 충분 — APP_CRYPTO_KEY 주입은 prod만 필수, D-C)
계정: SA(시드, DEFAULT 소속), 이후 단계에서 생성되는 TA-A, TA-B, hong@example.com(A/B 각각)
```

| # | 단계 | 조작 (Playwright 수준) | 단언 |
|---|------|------------------------|------|
| 1 | 랜딩 | `page.goto('/')` | W000 **랜딩** 렌더링 — 히어로 타이틀이 **`LANDING_HERO_TITLE` 텍스트**("출근의 순간을, 신뢰할 수 있는 기록으로" — landing-page.md §2-1 기준, D-E), 헤더·언어 스위처 표시 (URL 라우팅 없음 — 이후 전 단계 URL 불변) |
| 2 | 로그인 화면 | 로그인 진입 | **회사 코드 입력 필드 존재**. 가입 링크(W003) 부재 |
| 3 | SA 로그인 | **`{tenantCode:'DEFAULT'}` 고정**(backend-api §8.3 #1과 동일 표기 — `tenantCode`는 `@NotBlank`라 "코드 없이"는 400, 발견 14) + SA 크리덴셜 입력 | **테넌트 목록 화면(W007)** 으로 전개(출결 화면 아님) |
| 4 | 테넌트 A 생성 | `{code:ACME, name:…, adminEmail:ta-a@…}` + 기업정보(사업자번호 `1234567890`)·결제정보(INVOICE) 입력, 초기 비밀번호 메모 | 목록에 ACME 표시. 상세의 사업자번호가 `123-**-*****`로 **마스킹 표시**, 빌링키 입력란은 재조회 시 공란/마스킹 |
| 5 | 테넌트 B 생성 | `{code:BETA, …}` 동일 | 목록에 2건 |
| 6 | SA 경계 확인 | SA 상태로 출결 화면(W005) 강제 요청(navigate 호출) | 출결 화면으로 **전개되지 않고** 자기 홈(W007) 유지 + 거부 사유 |
| 7 | TA-A 로그인 | 로그아웃 → `{ACME, ta-a@…, 초기비번}` | **출결 화면(W005)으로 전개**(TENANT_ADMIN 홈 — D-A) + 헤더에 MEMBERS 메뉴 표시. MEMBERS 클릭 → 멤버 관리 화면(W009) 진입 |
| 8 | 멤버 등록(A) | W009에서 `hong@example.com` 등록 (초기 비밀번호는 **서버 생성** — 1회 표시 패널의 값을 `pwA`로 메모, backend-api §1.4) | 멤버 목록에 1건 |
| 9 | 멤버 로그인(A) | 로그아웃 → `{ACME, hong@…, pwA}` | 출결 화면(W005), 상태 "출근 대기" |
| 10 | 출결(A) | 출근 버튼 → 위치 허용 → 체크 → 확정 | 상태 "출근 중" + 성공 메시지. 월별 상세(W006)에 오늘 출근 시각 표시 |
| 11 | 멤버 등록(B) | 로그아웃 → TA-B 로그인 → W009에서 **같은 이메일** `hong@example.com` 등록 (서버 생성 초기 비밀번호를 `pwB`로 메모) | 등록 성공(테넌트 내 유니크 — 이메일 중복 오류가 나면 실패) |
| 12 | 같은 이메일, B 로그인 | 로그아웃 → `{BETA, hong@…, pwB}` | 출결 화면 상태 **"출근 대기"** — 10단계의 A 출근이 **보이지 않음**. 월별 상세 스탬프 공란 |
| 13 | 교차 확인 | B에서 출근 등록 → 로그아웃 → `{ACME, hong@…, pwA}` 재로그인 | A는 여전히 10단계 시각의 "출근 중". B의 스탬프가 A 월별 상세에 **미출현** |
| 14 | 관리자 격리 | TA-A로 로그인 → 멤버 목록 | B 멤버 미표시(hong@…이 1건만 — A 소속) |
| 15 | 비밀번호 교차 | 로그아웃 → `{BETA, hong@…, pwA}` 로그인 시도 | 단일 실패 메시지(LGN-03의 UI판) |
| 16 | 언어 회귀 | JPN 전환 후 9~10단계 화면 재확인 | 상태 라벨·버튼이 일본어(신규 화면 텍스트 포함 — V5 시드 검증) |

스크립트 골격(발췌):

```ts
test('E2E-MT-01', async ({ page }) => {
  // 12단계 핵심 단언 — 같은 이메일, 테넌트별 별세계
  await login(page, 'BETA', 'hong@example.com', 'pwB');
  await expect(page.getByTestId('work-status')).toHaveText(/出勤待ち|출근 대기/);
  await openMonthly(page);
  await expect(page.getByTestId('stamp-in')).toHaveCount(0); // A의 09:xx 출근이 새어 나오면 실패
});
```

### E2E-REG-01: 기존 12단계 회귀 (수정 재실행)

**사전 조건(발견 15)**: DEFAULT 테넌트에 TENANT_ADMIN 계정(`TA-D`, §0-2)이 존재해야 한다 — 리허설 시드의 is_admin 유저가 V4에서 변환된 계정(data-migration-v4 §8-1 필수 조건). 시드 SA는 화이트리스트 정책상 멤버 등록·출결 모두 403이므로 실행 주체가 될 수 없다. REG-04(기존 curl 스모크 재실행)도 동일하게 `TA-D`/일반 멤버 계정으로 수행한다.

기존 12단계 시나리오를 그대로 유지하되 변경점 2개만 반영: ① 로그인 폼에 회사 코드(`DEFAULT` 테넌트) 입력 ② 가입 화면(W003) 단계는 "TENANT_ADMIN(`TA-D`)의 멤버 등록"으로 대체. 그 외 단계(출결 체크→확정, 월별 상세, 언어 전환, 관리자 언어 마스터)는 **단언 무변경**으로 통과해야 한다.

---

## 6. 회귀 기준 — 기존 기능 무변경 확인 방법

| ID | 대상 | 방법 | 판정 |
|----|------|------|------|
| REG-01 | 출결 상태머신 | §4의 A/B 분류 준수 — `EvaluateRules` 7건 + `CheckConfirmFlow`/`StatusQuery` 10건이 **단언 라인 diff 0**으로 그린. PR 리뷰 체크리스트에 "기존 테스트의 assert 변경 여부" 항목 추가, 변경 시 별도 승인 | `mvn test` |
| REG-02 | 월별 페어링(야근 25시, 미퇴근 48h, 덮어쓰기, 휴일) | `MonthlyAttendanceAssemblerTest` 8건 그린 + 조립기 시그니처에 테넌트가 **들어가지 않았는지** 확인(들어갔다면 설계 리뷰) | `mvn test` |
| REG-03 | 3개 언어 | `MessagesTest` 4건 그린 — 특히 `bundlesHaveSameKeys`가 신규 키의 ko/en/ja 동시 추가를 강제. E2E-MT-01 16단계에서 신규 화면 일본어 스팟 체크 | `mvn test` + E2E |
| REG-04 | 전 API 동작 | 기존 curl 스모크 스크립트를 `DEFAULT` 테넌트 계정으로 재실행 — V4 backfill 후 기존 계정이 그대로 동작하는지(마이그레이션 정합성) | 스모크 |
| REG-05 | 사용자 여정 | E2E-REG-01 (§5) — 회사 코드 입력 외 단언 무변경 통과 | E2E |
| REG-06 | 화면 전개 | NavigationService 재설계(§4 C 분류) 후에도 미로그인 리다이렉트·로그아웃·미지 화면 처리 등 기존 결정 규칙 케이스가 role 모델 위에서 동일 결과 | `mvn test` |

---

## 7. 테스트 레벨 배치와 CI 게이트

### 7-1. 배치 매트릭스

| 케이스 그룹 | 단위(U) | 스모크(S) | E2E(E) | 배치 근거 |
|-------------|---------|-----------|--------|-----------|
| 격리 HTTP 매트릭스 ISO-01~13 | — | ● 주검증 | 일부(E2E-MT-01 #12~14) | 격리의 실체는 **매퍼 SQL의 tenant 조건**. Mockito 단위는 매퍼를 mock하므로 증명 불가 → 실 DB 필수 |
| 서비스 tenantId 전파 ISO-14 | ● | — | — | `verify()`로 충분, DB 불요 |
| 로그인 격리 LGN-01~07 | ●(01~04,06) | ● 전건 | #11~15 | 크리덴셜 스코프 규칙은 단위로, 세션·SUSPENDED는 실기동으로 |
| role 매트릭스 ROLE-01~15 | ●(인터셉터 파라미터라이즈드 1식) | ● 전건 | 스팟(#3,6) | 인터셉터 규칙표는 순수 로직. 실 상태코드는 스모크 |
| navigation 분기 NAV-01~07 | ● | — | #3,6,7 | 결정 규칙은 순수 함수(D7 설계 그대로) |
| 레이트리밋 LGN-08~11 / TTL-01 / 보안 헤더 SEC-01~03 | ●(08~10) | ●(08,11 / TTL / SEC 전건) | — | 리미터 규칙은 단위, 429 실응답·TTL·헤더·prod 거동은 실기동 |
| 마지막 관리자 ADM-01~06 | ●(01~05) | ●(01,02,06) | — | 카운트 규칙은 mock으로 완결. 실 SQL 카운트는 스모크 2건만 |
| 암호화 CRY-01~05,07 | ● | — | — | 순수 유틸, DB·컨텍스트 불요 |
| 키 fail-fast CRY-06 | — | ● | — | 기동 거동은 실기동에서만 |
| 마스킹 MSK-01~06 | ● | — | #4 | 포맷·타입 부재는 단위 |
| 빌링키 전수/로그 스캔 MSK-07~10 | — | ● | — | "부재 증명"은 실응답 전수 스캔이 유일한 방법 |
| 기존 35건(갱신판) | ● | — | — | 현행 유지 |
| 회귀 REG-04~05 | — | ● | ● | 실기동 성격 |

### 7-2. 건수 집계

| 레벨 | 건수 | 내역 |
|------|------|------|
| 단위(U) | **74** | 기존 35(무변경 12 + 수정 18 + 재설계 5) + 신규 39 = ISO-14×3, LGN 5 + 레이트리밋 3(LGN-08~10), ROLE 인터셉터 매트릭스 15셀분 1식(15케이스 상당→집계는 8메소드), NAV 7, ADM 5, CRY 6, MSK 6 |
| 스모크(S) | **약 50 항목** | ISO 12(ISO-08 이연) + TTL-01 + LGN 7 + 레이트리밋 2(LGN-08, 11) + ROLE 15 + ADM 3 + SEC 3 + CRY-06 + MSK 4 + REG-04 (전 API 재실행 1식) |
| E2E(E) | **2 시나리오 / 28단계** | E2E-MT-01(16단계 신규) + E2E-REG-01(12단계 수정 재실행) |

### 7-3. CI/릴리즈 게이트

| 게이트 | 내용 | 실패 시 |
|--------|------|---------|
| **PR 머지 (CI)** | `mvn test` 전건 그린(격리 U·권한 U·암호화/마스킹 U 포함) + `npm run build`(타입체크). **격리·권한 계열 케이스 실패는 예외 없이 머지 불가**(마스터 계획 §5-3) | 머지 차단 |
| **Phase 1 완료** | 스모크 전 항목(ISO/LGN/ROLE) + E2E-REG-01 + E2E-MT-01의 #7~15 | Phase 완료 선언 불가 |
| **Phase 2 완료** | 위 + MSK-07~10(빌링키 스캔) + E2E-MT-01 전 단계 | 동상 |
| **권고(Phase 1로 앞당김 검토)** | 마스터 계획은 Testcontainers를 Phase 3에 두었으나, **ISO 매트릭스만이라도 Testcontainers로 CI에 편입**할 것을 권고 — 현 구조에서 "tenant 조건 누락"을 CI가 잡지 못하는 기간이 Phase 1~2 내내 존재 | — |

### 7-4. 알려진 갭 (명시)

1. **격리의 CI 자동 검증 부재**: 단위 테스트는 매퍼를 mock하므로, 매퍼 SQL에서 `AND tenant_id=` 누락(Pool 모델 최대 리스크, §10)을 **CI가 검출하지 못한다**. 스모크는 수동 실행이라 실수로 건너뛸 수 있음 → 완화: §7-3 권고(ISO만 Testcontainers 선행) + 매퍼 시그니처 규약 리뷰 체크리스트.
2. **빌링키 비노출은 열거 기반**: MSK-07의 전수 스캔은 "스모크가 호출한 엔드포인트"만 커버한다. 신규 엔드포인트가 추가되면 스캔 대상에 자동 편입되지 않음 → 완화: 스모크 스크립트가 OpenAPI(`/v3/api-docs`)에서 엔드포인트 목록을 읽어 **미호출 엔드포인트가 있으면 실패**하도록 작성.
3. E2E는 Playwright 신규 도입(현행 12단계는 수동/반자동) — 도입 자체가 Phase 1 작업 항목.

---

## 교차 검증 반영 이력(2026-07-08)

- D-A/D-B: NAV-01~07을 확정 코드(W007/W008/W009)·`ROLE_DENIED`·홈(TA→W005) 기준으로 정정, E2E-MT-01 #7을 "W005 전개 후 헤더로 W009 진입"으로 수정. ROLE-15는 `/status`·`/role` 경로로, ISO-07은 Phase 2 계약 기준으로 정정.
- D-C/D-E: §3 서두를 JCA 직접 구현으로, CRY-02/07을 `v1:` 텍스트 포맷으로, CRY-06을 `APP_CRYPTO_KEY`·prod 프로파일 기준으로 수정. E2E #1 타이틀 단언을 `LANDING_HERO_TITLE` 기준으로 변경.
- D-D/D-F: 에러 코드 `LAST_ADMIN` → `LAST_TENANT_ADMIN`(§0-1, ADM-01/02), MSK-05 대상을 요청 DTO 2종으로 교체, ROLE-12 경로 라벨 정정.
- 발견 8~10·13~15: LGN-08~11(레이트리밋 429)·TTL-01(토큰 30분)·SEC-01~03(보안 헤더/prod) 추가, ISO-08 Phase 3 이연, SA 픽스처를 DEFAULT 소속·`{tenantCode:'DEFAULT'}` 고정으로, DEFAULT의 TENANT_ADMIN(`TA-D`)을 픽스처·E2E-REG-01/REG-04 사전 조건으로 명시. 건수 집계 갱신(U 74 / S 약 50).
