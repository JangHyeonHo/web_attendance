# 멀티테넌시 계획 문서 7종 교차 검증 보고서 (cross-review)

- 검증일: 2026-07-08
- 대상: `plan-saas-multitenancy.md`(마스터), `security-plan.md`, `data-migration-v4.md`, `backend-api.md`, `frontend-plan.md`, `landing-page.md`, `test-plan.md`
- 관점: 완결성 비평 — 문서 간 불일치·모순·누락을 심각도(구현 차단 → 불일치 → 경미) 순으로 번호를 붙여 기록. 문제없는 점검 항목은 말미 §D에 "확인됨"으로 기록.
- 심각도 정의:
  - **구현 차단**: 두 문서가 서로 모순되어 어느 쪽대로도 구현을 시작할 수 없거나, 그대로 구현하면 다른 문서의 완료 기준(테스트)을 통과할 수 없는 것.
  - **불일치**: 구현은 시작할 수 있으나 문서 수정 없이는 통합 시점에 반드시 충돌·재작업이 발생하는 것.
  - **경미**: 표기·범위·용어 수준. 리뷰에서 잡히겠지만 지금 정리하는 것이 싸다.

---

## A. 구현 차단 (6건)

### 1. 화면 코드 W008의 의미 충돌 + W009의 백엔드 부재 — 화면 코드 체계가 두 갈래

- **[backend-api.md §5.1/§5.2/§7.4]** W007=시스템 관리자(테넌트 목록+기업/결제), W008=**멤버 관리**(TENANT_ADMIN 홈). W009 없음. V5 시드도 W007에 `PROFILE_TITLE`·`BILLING_*`을, W008에 `MEMBER_LIST_TITLE`·`ADD_MEMBER`를 배치.
- **[frontend-plan.md §1-2/§4/§7]** W007=테넌트 관리(목록), W008=**테넌트 상세(기업/결제, W007에 임베드)**, W009=**멤버 관리**. V5 키 74종이 이 3분할 기준으로 작성됨(`/i18n/W008` 직접 취득 등 구현 상세까지 W008=상세 전제).
- **문제**: 같은 코드 W008이 백엔드에서는 "멤버 관리", 프론트에서는 "기업/결제 상세"다. Screen enum 등록, navigation 홈 분기, V5 시드의 window_id, `/i18n/{windowId}` 취득, E2E 화면 단언이 전부 갈라진다. test-plan은 W007만 코드로 언급(NAV-03)하고 멤버 관리 화면 코드를 특정하지 않아 중재 기준도 없다.
- **권고**: **프론트 3분할(W007 목록 / W008 상세 / W009 멤버)을 정본으로 채택**하라. 근거: ① W005→W006 임베드+`/i18n` 직접 취득 패턴과 일치, ② "코드→컴포넌트 1:1"(backend-api 스스로 밝힌 D7 원칙)에 부합 — 백엔드안은 W007 한 코드에 목록·기업·결제를 다 싣는다. 수정처: backend-api §5.1 표에 W009 추가·W008 의미 교체, §5.2 enum(`TENANT_DETAIL("W008", SYSTEM_ADMIN)`, `MEMBERS("W009", TENANT_ADMIN)`), §5.3 `homeOf`, §7.4 시드 키의 window_id 재배치(기업/결제 키→W008, 멤버 키→W009). test-plan NAV-02~04와 E2E-MT-01 #7에 확정 코드 명기. **→ 반영 완료(backend-api §5.1/§5.2/§7.4, test-plan §2-2·§5 — 최종 결정 D-A: 프론트 3분할 채택)**

### 2. TENANT_ADMIN 홈 화면과 NavigationReason 개명 — role별 홈 분기 불일치

- **[backend-api.md §5.3]** `homeOf`: TENANT_ADMIN → **W008(멤버 관리)**. `NavigationReason.ADMIN_ONLY` **이름 유지**(값 추가/삭제 없음).
- **[frontend-plan.md §1-3]** TENANT_ADMIN → **W005(출결)** ("일상 업무가 출결"). reason은 `ADMIN_ONLY` → **`ROLE_DENIED`로 개명**. §8 T1도 "TENANT_ADMIN/MEMBER→W005"를 단언.
- **[test-plan.md §2-2]** NAV-01 "출결 화면 + `ADMIN_ONLY` (현행 유지)" — 백엔드 편. NAV-02 "TENANT_ADMIN이 W004 요청 → 테넌트 관리(멤버 관리) 화면" — 홈=멤버 관리, 역시 백엔드 편.
- **문제**: 로그인 직후 도착 화면과 거부 사유 문자열이 문서마다 다르다. E2E-MT-01 #7("TA-A 로그인 → 멤버 관리 화면으로 전개")과 frontend T1("TENANT_ADMIN→W005")은 **동시에 참일 수 없다**.
- **권고**: ① 홈: **TENANT_ADMIN→W009(멤버 관리)로 통일** 권장 — 마스터 §4("W004를 role별 분기 — TENANT_ADMIN은 테넌트 관리 화면")의 취지이고, TA가 출결도 하려면 헤더 ATTEND 메뉴로 1클릭이면 된다. frontend-plan §1-3·§8(T1, T7)을 수정. 반대로 "TA의 일상=출결" UX를 채택한다면 backend §5.3·test NAV-02·E2E-MT-01 #7을 함께 수정 — 어느 쪽이든 **세 문서 동시 수정**이 조건. ② reason: `ADMIN_ONLY` **이름 유지**로 통일(값 변경 없음이 프론트/E2E 회귀 영향 최소 — 백엔드 근거 타당). frontend-plan §1-3·§5-1(`NavigationReason` 타입)·§4-3에서 `ROLE_DENIED` 표기 제거. **→ 반영 완료(최종 결정 D-A가 권고와 달리 ①TA 홈=W005(frontend 안) ②`ROLE_DENIED` 개명을 채택 — backend-api §5.3/§8.3, test-plan NAV-01~07·E2E-MT-01 #7 동시 수정으로 3문서 정합)**

### 3. 권한 판정 모델 충돌 — atLeast 서열(backend) vs 화이트리스트(security/test)

- **[security-plan.md §6-1/§6-3]** 경로별 허용 role **화이트리스트**: `/api/v1/attendance/**`={TENANT_ADMIN, MEMBER} — **SYSTEM_ADMIN 명시 403**. `/api/v1/tenant/**`={TENANT_ADMIN} — SYSTEM_ADMIN도 403.
- **[test-plan.md §1/§2]** ISO-11(SA 출결 403), ISO-12·ROLE-14(SA의 `/tenant/members` 403), ROLE-03~05(SA 출결 403), NAV-05(SA의 W005 요청 → 홈으로 차단)를 릴리즈 게이트로 확정.
- **[backend-api.md §3.1~§3.3]** `Role.atLeast()` **서열 비교**(MEMBER<TENANT_ADMIN<SYSTEM_ADMIN) + WebConfig가 `/system/**`·`/admin/**`·`/tenant/**`에만 RoleInterceptor 등록. `/api/v1/attendance/**`에는 **role 인터셉터 자체가 없다**(authInterceptor의 로그인 검사뿐). §3.1은 "SYSTEM_ADMIN이 `/api/v1/tenant/members`를 불러도 운영 테넌트 멤버만 보인다"며 **SA 접근 허용을 명문화** — ROLE-14와 정면 모순. Screen도 `requiredRole=MEMBER`+atLeast라 SA의 W005 전개가 **허용**되어 NAV-05 실패.
- **문제**: backend-api대로 구현하면 격리/권한 테스트 매트릭스의 SA 관련 셀(ISO-11·12, ROLE-03~05·14·15, NAV-05, E2E-MT-01 #6)이 전부 빨간불 — CI 게이트 통과 불가. 이것은 마스터 §4 보완정책 2(운영자의 출결 데이터 비접근)의 코드 레벨 보장(security §6-3의 1번 경로 차단)을 무너뜨리는 회귀다.
- **권고**: **security-plan §6-1의 RouteRule 화이트리스트 방식을 정본으로** backend-api §3.2(RoleInterceptor를 `Set<Role> allowed` 기반으로), §3.3(WebConfig에 `/api/v1/attendance/**` 규칙 추가), §3.1(atLeast 삭제 또는 화면 전개 전용으로 강등, "SA의 /tenant 접근 허용" 문단 삭제)을 재작성하라. Screen도 `requiredRole` 단일 값+서열 대신 **허용 role 집합**(W005/W006={MEMBER, TENANT_ADMIN})으로 — frontend-plan §1-2의 "접근 role" 열이 이미 집합 표기다. **→ 반영 완료(backend-api §3.1~§3.3 화이트리스트 재작성·§5.2 허용 role 집합 — 최종 결정 D-B: security-plan §6-1 정본, atLeast 기각)**

### 4. FieldCipher 암호문 포맷·구현이 3개 문서에서 3가지 — 상호 호환 불가

- **[security-plan.md §2-1~§2-3]** spring-security-crypto를 **명시적으로 기각**(IV 16B, 버전 개념 부재, raw 키 주입 부자연 — 표까지 그려 근거 제시)하고 **JCA 직접 구현**으로 확정. 포맷 `v1:{base64(iv)}:{base64(ct||tag)}` **텍스트의 UTF-8 바이트**. 키는 `APP_CRYPTO_KEY` = **base64 32바이트**.
- **[backend-api.md §6.1]** `AesBytesEncryptor(hexKey, hexSalt, KeyGenerators.secureRandom(12), GCM)` — **기각된 spring-security-crypto 방식** + **hex 키 + salt**(PBKDF2 유도 경로) + **1바이트 버전 프리픽스**.
- **[data-migration-v4.md §2 주석/§4-1]** `[키버전 1B][IV 12B][암호문][태그 16B]` **바이너리 직저장, 고정 오버헤드 29B** — 컬럼 크기 산정(§4-2)이 이 포맷 기준.
- **[test-plan.md §3]** "spring-security-crypto 기반 AES-256-GCM 유틸" 전제 + CRY-06 환경변수명 **`ENCRYPT_KEY`**(다른 두 문서는 `APP_CRYPTO_KEY`).
- **문제**: 세 포맷은 바이트 레벨에서 호환되지 않는다(복호화 파서가 다름). 키 인코딩(base64 vs hex+salt)과 환경변수명도 갈라져 기동 설정까지 3원화. security-plan §10이 "마스터에서 조정했다"고 선언했는데 backend-api·test-plan이 그 조정을 반영하지 않고 마스터의 옛 문구를 따랐다.
- **권고**: **security-plan §2를 정본으로**(기각 근거가 문서화된 유일한 안): ① backend-api §6.1 코드 스케치를 JCA `Cipher("AES/GCM/NoPadding")`+`v1:` 텍스트 포맷+`APP_CRYPTO_KEY`(base64 32B)로 교체. ② data-migration §2 주석·§4-1·§4-2를 base64 텍스트 포맷 기준으로 재산정(오버헤드 ≈ 4/3배+프리픽스 — security §2-3이 이미 "VARBINARY(256)/(512)로 여유 충분"을 계산해 둠. 결론 컬럼 크기는 불변이므로 DDL은 안 바뀐다). ③ test-plan §3 서두 문구와 CRY-06 변수명(`APP_CRYPTO_KEY`), CRY-02의 `{v1}` 표기를 `v1:`로 수정. **→ 반영 완료(backend-api §6.1, data-migration-v4 §0/§2/§4, test-plan §3 — 최종 결정 D-C: security-plan §2 정본. 단 컬럼은 VARBINARY 유지가 아니라 VARCHAR(128/128/1024)로 전환, 개발 기본키 허용·prod 필수로 조정되어 security-plan §2-2/§2-3도 수정)**

### 5. 테넌트/멤버 API 계약 분열 — 메소드·경로·필드명·에러코드가 backend와 frontend에서 1:1이 아님

- **[backend-api.md §1.3/§1.4 vs frontend-plan.md §5]** 전수 대조 결과:
  | 항목 | backend-api | frontend-plan | 판정 |
  |---|---|---|---|
  | 상태/프로필/빌링/멤버 갱신 메소드 | **PUT** | `post()` 호출(**POST**) | 충돌 |
  | 멤버 role 변경 | `PUT /tenant/members/{id}` (name+departCd+role 일괄 `MemberUpdateRequest`) | `POST /tenant/members/{id}/role` (role 단독) | 경로·모델 충돌 |
  | 멤버 단건 조회 / DELETE / password-reset, 테넌트 단건 조회 / 이름 수정(PUT `{id}`) | 정의됨(7+9 엔드포인트) | **부재**(endpoints.ts에 없음, 화면에도 삭제·재발급 UI 없음) | 프론트 누락 |
  | 테넌트 생성 요청 필드 | `code` | `tenantCode` | 필드명 충돌 |
  | 생성 응답 구조 | 평면(`tenantId, code, …, initialPassword`) | 중첩(`{tenant: TenantSummary, …}`) — Member도 동일 | 구조 충돌 |
  | 프로필 응답 필드 | `businessRegNo`/`contactPhone`(마스킹값을 원 필드명에) | `businessRegNoMasked`/`contactPhoneMasked` | 필드명 충돌 |
  | 빌링 응답 | **`hasBillingKey` 없음** | `hasBillingKey: boolean` 필수(security-plan §2-4도 "존재 여부만 hasBillingKey 불리언" 확정) | **백엔드 누락** |
  | 멤버 등록 요청 | `role` `@NotNull` 필수 | `role` 필드 없음(email/name/departCd만) | 충돌 |
  | `NavigateResponse` | §8.1 "계약 불변(응답 필드 동일)" | `role: Role \| null` **필드 추가**(헤더 분기의 전제) | 충돌 |
  | LoginResponse | `tenantCode`+`tenantName`(비null) | `tenantCode` 없음, `tenantName: string \| null`(SA는 null) | 부분 충돌 |
  | 마지막 관리자 에러코드 | `LAST_TENANT_ADMIN`(security-plan도 동일) | `LAST_ADMIN_PROTECTED` | test-plan은 **`LAST_ADMIN`** — 3원 분열 |
- **문제**: types/endpoints가 "백엔드 record와 1:1"이라는 frontend-plan의 선언(§5-2)이 성립하지 않는다. 어느 쪽대로 먼저 구현해도 통합 시 전면 재작업.
- **권고**: 계약의 정본은 backend-api §1로 하되 **양방향 수정**: ① frontend §5를 backend 메소드(PUT)·경로(`PUT /members/{id}`)·평면 응답·`code` 필드로 재작성하고, 누락된 password-reset·삭제 UI를 §4-2에 추가(혹은 Phase 2 범위 제외를 양쪽에 명기). ② 단, **`hasBillingKey`는 backend §1.3 `TenantBillingResponse`에 추가**(security-plan 확정 사항의 백엔드 누락)하고, 마스킹 필드명은 오독 방지상 프론트안(`…Masked`)이 우수하므로 backend DTO 필드명을 역반영 권장. ③ `NavigateResponse.role` 추가를 backend §8.1의 "계약 불변" 목록에서 빼고 변경 목록에 명기. ④ 에러코드는 **`LAST_TENANT_ADMIN`으로 통일** — frontend §4-2·§5-2, test-plan §0-1·§2-3(`LAST_ADMIN`) 수정. **→ 반영 완료(최종 결정 D-D: 테넌트 8·멤버 4 엔드포인트 확정 — backend-api §1.1/§1.3/§1.4(tenantCode 통일·hasBillingKey·…Masked 역반영·PUT /role 신설·단건 조회/삭제/재발급/이름 수정 Phase 3 이연), frontend-plan §3-2/§5(PUT·평면 record·LAST_TENANT_ADMIN·NavigateResponse.role), test-plan §0-1/§2)**

### 6. V5 시드 3파일 경합 + W000 랜딩 키 충돌 — Flyway 버전과 키가 동시에 깨짐

- **[data-migration-v4.md §7 / backend-api.md §2.2]** `V5__seed_tenant_ui_texts.sql` / **[frontend-plan.md §7]** `V5__seed_tenancy_ui_texts.sql` / **[landing-page.md §2, §4-3]** `V5__seed_landing_texts.sql` — **같은 버전 번호 V5에 파일명 3종**. Flyway는 동일 버전 중복 시 기동 실패한다(landing-page §4-3 스스로 "파일 번호 충돌 시 조정" 각주로 인지).
- **키 충돌**: frontend §7의 W000 13키와 landing-page §2의 약 33키가 **같은 화면(W000)에 서로 다른 랜딩 설계**를 시드한다.
  - 동일 키 다른 카피: `LANDING_HERO_SUB`("…월별 리포트를 갖춘…" vs "…2단계 변조 탐지로…"), `LANDING_CTA_SUB`("이메일을 보내주시면 담당자가…" vs "…하루 안에 회신드립니다").
  - 같은 개념 다른 키: `FEATURE_GEO_*` vs `LANDING_FEAT1_*`(기능 카드 4종 전부), `LANDING_CTA` vs `LANDING_CTA_CONTACT`.
  - 구조 자체 상이: frontend는 히어로+기능 4카드 2섹션, landing은 히어로(배지·타이틀 포함)+기능+신뢰 3카드+도입 3단계+CTA+푸터 6섹션.
  - `INDEX_SUB`: frontend §7 "V5에서 **삭제**" vs landing §2 "**유지**(다른 화면 폴백 용도)" — 정면 모순.
- **권고**: ① 랜딩 콘텐츠는 **landing-page.md를 정본**으로(전담 기획 문서, 카피 검수 체크리스트 보유) frontend-plan §2의 섹션 구성·§7 W000 키 표를 landing 키 체계(`LANDING_*`)로 교체, `CONTACT_EMAIL` 키만 frontend 안(3언어 동일 값 키)을 landing §2-5의 `{CONTACT_EMAIL}` 치환 방식으로 채택해 유지. ② 시드는 **단일 파일 `V5__seed_tenancy_ui_texts.sql`로 병합**(관리 화면 키 + 랜딩 키 + 폐기 DELETE — 소유는 data-migration 문서 §7에 파일명·구성 확정 기록). ③ `INDEX_SUB`는 랜딩이 W000을 대체하므로 **삭제(frontend 안)**로 확정하고 landing §2 문구 수정. ④ frontend §7 "74키" 합계와 §8 T4·test-plan E2E-MT-01 #16의 텍스트 단언도 병합 결과 기준으로 갱신. **→ 반영 완료(최종 결정 D-E: 단일 파일명은 `V5__seed_saas_texts.sql`, 랜딩 키는 landing-page.md 정본 + CONTACT_EMAIL 키만 frontend 안 — data-migration-v4 §7, frontend-plan §7(95키), landing-page §2/§4-3, test-plan E2E #1. 단 D-A에 따라 INDEX_SUB 포함 폐기 키는 DELETE하지 않고 무해 잔존 허용)**

---

## B. 불일치 (12건)

### 7. `tenant.status` 타입: TINYINT(V4) vs VARCHAR 이름 매핑(backend)
- [data-migration-v4.md §2 [1], 마스터 §3] `status TINYINT (0=ACTIVE 1=SUSPENDED)` ↔ [backend-api.md §2.1] `TenantStatus.java`: "DB는 VARCHAR(10), MyBatis 기본 이름 매핑". 그대로 구현하면 enum 이름↔숫자 매핑이 어긋나 조회 즉시 깨진다.
- 권고: V4가 `users.role`에 적용한 자기 결정("ENUM/숫자 대신 VARCHAR+CHECK" — §0 결정표)과 일관되게 **tenant.status를 VARCHAR(10)+CHECK('ACTIVE','SUSPENDED')로 변경**하고 마스터 §3의 DDL도 함께 수정. (TINYINT 유지 시 backend에 TypeHandler 명시가 필요 — 비권장.) **→ 반영 완료(data-migration-v4 §0/§2, plan-saas-multitenancy §3)**

### 8. 로그인 레이트리밋(429) — security-plan P1 필수인데 backend-api·test-plan·frontend-plan 모두 미반영
- [security-plan.md §3, §9 "P1 필수"] `LoginRateLimiter`, 429 `RATE_LIMITED`, 3언어 메시지 키, 단위 4건. ↔ backend-api: §1.2 login 에러표에 429 없음, §2.2 신규 파일 19종에 LoginRateLimiter 없음, §7.1 메시지 키에 `RATE_LIMITED` 계열 없음. test-plan: LGN-01~07 어디에도 임계/차단/429 케이스 없음. frontend §3-1: 429 수신 시 표시 처리 없음.
- 권고: backend-api §1.2 login 행에 `429 RATE_LIMITED / auth.login.rate-limited` 추가, §2.2에 `auth/LoginRateLimiter.java`, §7.1에 키 추가. test-plan §1-3에 LGN-08~11(계정 임계 5회/IP 임계/차단 해제/성공 시 초기화 — security §3-1의 단위 4건 + 429 응답이 계정 실존과 무관하게 동일함을 단언하는 스모크 1건) 추가. frontend §3-1에 429 메시지 표시(서버 메시지 그대로) 1줄 추가. **→ 반영 완료(backend-api §1.2/§2.2/§7.1, test-plan §0-1·LGN-08~11, frontend-plan §3-1)**

### 9. 체크토큰 TTL 30분 — security-plan 확정인데 backend·V4 문서가 "현행 24h" 서술 유지
- [security-plan.md §1 T7, §9, §10] "24시간 → **30분** 단축" P1 필수로 확정. ↔ [backend-api.md §4.3] `deleteExpiredChecks()` "**불변**". [data-migration-v4.md §2 [5] 주석] "토큰 수명이 최대 **1일**". test-plan에 TTL 케이스 없음.
- 권고: backend-api §4.3의 deleteExpiredChecks 행을 "시간 조건 24h→30분 변경(쿼리 상수)"으로, data-migration §2 [5]·§3-1 주석의 "1일"을 "30분"으로 수정. test-plan §1-2에 "발급 31분 후 confirm → 404"(스모크) 1건 추가. **→ 반영 완료(backend-api §4.3, data-migration-v4 §2/§3-1, test-plan TTL-01 — D-F)**

### 10. 운영 프로파일 P1 격상·SecurityHeadersFilter — backend 파일 목록과 test-plan에 부재
- [security-plan.md §5, §7-2, §9] `SecurityHeadersFilter`(P1 필수), `application-prod.properties`(Swagger off·Secure 쿠키·HSTS — Phase 3 TODO에서 **P1로 격상**), 쿠키 명시 설정 2줄. ↔ backend-api §2.2 변경 파일 요약표에 세 항목 모두 없음(신규 19파일에 필터 미포함, properties 변경 미기재). test-plan에 보안 헤더/Swagger 비활성/쿠키 속성 검증 케이스 0건.
- 권고: backend-api §2.2에 `common/SecurityHeadersFilter.java`(신규)·`application.properties`(쿠키 2줄)·`application-prod.properties`(신규) 행 추가. test-plan에 스모크 3건(응답 헤더 존재 — CSP/nosniff/frame, prod 기동 시 `/v3/api-docs` 404, Set-Cookie에 HttpOnly·SameSite=Lax) 추가. **→ 반영 완료(backend-api §2.2, test-plan §2-4 SEC-01~03)**

### 11. `deleteCheck` 강화 조건에서 user_id 탈락
- [data-migration-v4.md §6-3] "현행 token 단독은 타 유저 토큰 무효화 가능 → `WHERE token=? AND user_id=? AND tenant_id=?`로 강화" ↔ [backend-api.md §4.3] `deleteCheck(tenantId, token)` — `WHERE token AND tenant_id`뿐, **user_id 누락**. 같은 테넌트 내 타 유저 토큰 무효화 허점이 절반만 막힌다.
- 권고: backend-api §4.3을 `deleteCheck(tenantId, token, userId)` 3중 조건으로 수정(마스터 §5-2 "2중 조건" 원칙의 자기 일관성). **→ 반영 완료(backend-api §4.3)**

### 12. payload_hash에 tenantId 포함 여부 — 권고(V4 문서) vs 명시 거부(backend)
- [data-migration-v4.md §6-4] "payload_hash 입력에 tenantId 포함 — **Phase 1 구현 시 반영**" ↔ [backend-api.md §2.2 AttendanceService 행] "payloadHash는 userId 기반 **유지**(토큰 조회가 tenant 스코프라 충분)". 서로 반대 결정을 확정 어조로 기록.
- 권고: 한쪽으로 결정해 두 문서에 동일하게 기록. 판단 재료: backend 논거(조회가 3중 조건이면 해시 단계 도달 전 차단)가 맞지만, V4 문서의 시나리오(백업 복원 실수 등 user_id 재사용)에서는 심층 방어 가치가 있고 비용이 문자열 연결 1개다 — **포함(V4 안)** 권장. **→ 반영 완료(backend-api §2.2 AttendanceService 행을 "tenantId 포함"으로 정정, data-migration-v4 §6-4를 확정 표기)**

### 13. ISO-08이 존재하지 않는 "관리자용 멤버 출결 조회 API"를 검증 대상으로 지정
- [test-plan.md §1-2 ISO-08] "TA-A가 B 멤버의 출결 현황/월별 상세(관리자용 조회 API, `userId=M-B.id`) → 404" ↔ backend-api.md의 Phase 1~2 설계에 userId를 받는 출결 조회 API가 **없다**(출결 4종은 전부 `@LoginUser` 셀프서비스 — §8.1, security-plan T8-①도 이를 "확인됨"으로 못박음). 마스터 §4의 TENANT_ADMIN "출결 현황 조회"는 Phase 미배정.
- 권고: ISO-08을 "해당 API 도입 시(Phase 3 스케쥴/현황 관리와 함께) 활성화"로 이연 표기하거나, 매퍼 레벨 검증(스모크에서 SQL 직접 실행으로 2중 조건 확인)으로 대체. 마스터 §9 Phase 3 목록에 "TENANT_ADMIN 출결 현황 조회 API"를 명시해 소속 Phase를 확정. **→ 반영 완료(test-plan ISO-08 이연+대체 스모크, plan-saas-multitenancy §9 Phase 3)**

### 14. 테스트 픽스처의 SYSTEM_ADMIN 전제가 V4 스키마·DTO와 모순
- [test-plan.md §0-2] SA는 "무테넌트 **또는** 시스템 테넌트 소속" ↔ [data-migration-v4.md §2 [3-4]] `users.tenant_id NOT NULL`(무테넌트 불가), SA=DEFAULT 테넌트 소속. [test-plan.md §5 E2E-MT-01 #3] "**코드 없이** 또는 시스템 코드로 SA 로그인" ↔ [backend-api.md §1.2] `tenantCode @NotBlank` — 코드 없는 로그인은 400.
- 권고: test-plan §0-2를 "SA = DEFAULT 테넌트 소속 SYSTEM_ADMIN(V4 승격 시드)"로, E2E-MT-01 #3을 `{tenantCode:'DEFAULT'}` 고정으로 수정(backend §8.3 #1과 동일 표기). **→ 반영 완료(test-plan §0-2·§5 — D-F: tenant_code 'DEFAULT' 전 문서 통일)**

### 15. DEFAULT 테넌트에 TENANT_ADMIN이 없음 — 회귀 E2E·스모크 실행 주체 공백
- V4 후 신규 DB의 DEFAULT 테넌트에는 SYSTEM_ADMIN 1명뿐(V2 시드 승격, [data-migration-v4.md §7]). 그런데 [test-plan.md E2E-REG-01]은 "가입 단계를 **TENANT_ADMIN의 멤버 등록**으로 대체", [REG-04]는 "기존 curl 스모크를 DEFAULT 테넌트 계정으로 재실행"을 요구한다. 화이트리스트 정책(§3 확정 시) SA는 `/tenant/members`도 출결도 403이므로 **DEFAULT 테넌트에서 멤버를 등록하거나 출결을 수행할 계정이 없다**. 어느 문서도 DEFAULT용 TENANT_ADMIN 생성 절차를 정의하지 않음(마스터 §8-7은 "기존 계정 유지"만).
- 권고: test-plan §5·§6에 사전 단계를 명시 — "SA가 (일반 테넌트 생성 API로) 테스트 테넌트를 만들어 진행"하거나, 리허설 시드([data-migration-v4.md §8-1 3)])에 "DEFAULT 소속 is_admin 유저 1명(→TENANT_ADMIN 변환) 포함"을 필수 조건으로 격상. 후자가 REG-04(기존 계정 그대로 재실행)의 취지에 맞다. **→ 반영 완료(후자 채택 — data-migration-v4 §8-1 필수 격상, test-plan §0-2 `TA-D` 픽스처·E2E-REG-01/REG-04 사전 조건)**

### 16. 전화번호 마스킹 포맷 불일치
- [security-plan.md §2-4] `010-****-5678`(국번 노출) = [test-plan.md MSK-03] 동일 ↔ [backend-api.md §6.3·§1.3 예시] `***-****-5678`(뒤 4자리만).
- 권고: 한 포맷으로 확정 후 3문서 정렬. 노출 최소화 관점에서 backend 안(`***-****-5678`)이 보수적이나, security-plan이 규칙 문서이므로 그쪽을 고치는 경우 MSK-03 기대값도 함께. **→ 반영 완료(`010-****-5678`로 확정 — 규칙 문서인 security-plan §2-4·test-plan MSK-03 유지, backend-api §1.3/§6.3 정정)**

### 17. ROLE-12 경로 라벨 오류
- [test-plan.md §2-1 ROLE-12] "`PUT /api/v1/system/tenants/{id}` (정지/재개)" ↔ [backend-api.md §1.3] `{id}`=이름 수정, 정지/재개는 `{id}/status`.
- 권고: ROLE-12를 `/status`로 수정(또는 두 엔드포인트를 별도 행으로). **→ 반영 완료(test-plan ROLE-12 — 이름 수정 `PUT /{id}`는 D-D에 따라 Phase 3 이연으로 계약에서 제외)**

### 18. MSK-05 toString 검증 대상이 어긋남
- [test-plan.md MSK-05] `TenantProfile`/`TenantBilling` **도메인 객체**의 toString 검증 ↔ 도메인 record는 애초에 암호문 `byte[]`만 보유([backend-api.md §2.1]). 실제 평문 유출 위험 지점은 **요청 DTO**(record 자동 toString)이고 security-plan §8-1·backend-api §1.3이 오버라이드하는 것도 `TenantProfileRequest`/`TenantBillingRequest`다.
- 권고: MSK-05 대상을 요청 DTO 2종으로 교체(도메인 record는 byte[] 주소가 찍힐 뿐이므로 유지해도 무해하나 핵심이 아님). **→ 반영 완료(test-plan MSK-05, backend-api §6.2에 toString 오버라이드를 구현 규약으로 명시 — D-F)**

---

## C. 경미 (7건)

### 19. V5 폐기 키 DELETE 범위 불일치
- backend-api §1.5/§7.4는 `W003` 행만 삭제. frontend §7은 `W003` + `W999/SIGNUP` + `W000/INDEX_SUB`까지. 권고: 병합 V5(§6 권고)의 DELETE 목록을 frontend 안으로 통일하고 backend §7.4에 반영. **→ 반영 완료(권고와 달리 최종 결정 D-A에 따라 **DELETE 전면 폐지** — 폐기 키는 무해 잔존 허용으로 backend-api §1.5/§7.4, frontend-plan §6/§7, data-migration-v4 §7 통일)**

### 20. W003 시드 잔존 타이밍 미명시
- signup 폐기는 P1(security §9 필수), V5 정리는 P2 — P1 기간 동안 W003 텍스트가 언어 마스터 관리 화면(W004)과 `GET /api/v1/i18n/W003`에 남는다. 실해는 없으나 방침 미기록. 권고: backend-api §1.5에 "P1 동안 잔존 허용, V5(P2)에서 일괄 정리" 1줄 명시. **→ 반영 완료(backend-api §1.5 — D-A에 따라 "영구 잔존 허용(무해)"로 명시, V5 정리 없음)**

### 21. SessionUser 필드 구성 차이
- security-plan §6-1은 5필드 record, backend-api §3.1은 7필드(tenantCode·tenantName 추가). 기능상 backend가 정본(LoginResponse 조립 필요). 권고: security-plan §6-1 코드 조각을 backend 정의로 갱신. **→ 반영 완료(security-plan §6-1)**

### 22. 마지막 관리자 카운트의 잠금 방식 표기 차이
- security-plan §6-2는 `SELECT … FOR UPDATE`(동시 상호 강등 레이스 방지)를 명시, backend-api §1.4·§4.2(`countActiveTenantAdmins`)는 `@Transactional` 카운트만. 권고: backend-api §4.2 해당 매퍼에 `FOR UPDATE` 명기(테스트 ADM-05까지는 잡지 못하는 레이스 케이스의 근거 문서화). **→ 반영 완료(backend-api §1.4/§4.2)**

### 23. contactPhone에 형식 검증이 없어 MSK-10이 전화 필드에서 성립하지 않음
- backend-api §1.3 `TenantProfileRequest.contactPhone`은 `@Size(20)`뿐 — 마스킹값(`***-****-5678`)을 재제출해도 통과·암호화 저장된다(사업자번호는 `@Pattern`이 걸러줌). 권고: 숫자/하이픈 패턴 추가(`^[0-9\-+ ]{...}$` 수준) 또는 MSK-10의 검증 범위를 사업자번호로 한정 표기. **→ 반영 완료(backend-api §1.3 `@Pattern` 추가 + `validation.contact-phone.format` 키)**

### 24. 테넌트 코드 대소문자 정규화 정책 미정
- frontend §3-1 "대문자 정규화는 서버 정책에 따름(프론트는 trim만)" — 그 서버 정책이 backend-api에 없다. 생성은 `^[A-Z0-9]{2,20}$`이지만 로그인 입력 `acme`의 처리(정규화 vs collation 의존)가 미정의. security-plan §3-1 레이트리밋 키는 소문자 정규화를 전제. 권고: backend-api §1.2 authenticate ①에 "tenantCode는 대문자 정규화 후 조회" 1줄 확정. **→ 반영 완료(backend-api §1.2)**

### 25. `Tenant` record에 updatedAt 부재
- V4 tenant 테이블에는 `updated_at`이 있으나 backend-api §2.1 `Tenant` record에는 없음. 조회 표시(frontend TenantSummary도 createdAt만)에는 지장 없음 — 필요 시만 추가. **→ 반영 완료(변경 불요 확인 — 현행 유지, 필요 시 추가 방침 그대로)**

---

## D. 점검 항목별 결과 요약

| # | 점검 항목 | 결과 |
|---|-----------|------|
| 1 | 화면 코드 체계(W007/W008/W009)·role별 홈 | **충돌** — 발견 1, 2 |
| 2 | 엔드포인트/DTO 1:1, 테스트 케이스의 실엔드포인트 참조 | **충돌/누락** — 발견 5, 13, 17. 그 외 출결 4종·auth·i18n·navigation 경로는 3문서 일치 **확인됨** |
| 3 | V4/V5 정합 | V4 컬럼(tenant_id/role/status/VARBINARY)↔backend 매퍼·엔티티 매핑, §9 영향표↔backend §4 시그니처 표는 정확히 일치 **확인됨**. 단 tenant.status 타입(발견 7), 암호문 포맷(발견 4), V5 파일·키(발견 6) 충돌 |
| 4 | 보안 결정 반영 | toString 마스킹(backend §1.3)·fail-fast 키 검증·마스킹 DTO 팩토리 원칙 **확인됨**. 429(발견 8)·TTL 30분(발견 9)·JCA FieldCipher(발견 4)·운영 프로파일 P1(발견 10) 미반영 |
| 5 | W003 폐기 일치 | 파일 삭제 목록(backend §1.5)↔프론트 삭제(frontend §6)↔테스트(ROLE-06, NAV-06, E2E-REG-01)↔V3 시드 잔존→V5 정리 방침, 상호 모순 없음 **확인됨**(범위 차이만 — 발견 19, 20) |
| 6 | 누락 탐지 | DEFAULT tenant_code 값('DEFAULT')은 V4·backend E2E·test 일치 **확인됨**. 시드 관리자 SYSTEM_ADMIN 승격의 기존 E2E 영향은 backend §8.3·test E2E-REG-01이 커버 **확인됨**, 단 DEFAULT의 TENANT_ADMIN 공백(발견 15)·SA 픽스처 모순(발견 14)은 미해결 |
| + | 격리 원칙(세션 tenantId·2중 조건·같은 이메일 시나리오·크로스 토큰) | 마스터 §5·security T1/T7·V4 §6·backend §4·test ISO/LGN이 전 계층에서 일관 **확인됨** — 이 계획의 가장 견고한 축 |

**정본 지정 요약(권고)**: 권한 판정=security-plan §6-1 / 암호화=security-plan §2 / 화면 코드·랜딩 콘텐츠=frontend-plan §1-2+landing-page.md / API 계약=backend-api §1(단 hasBillingKey·…Masked 필드명은 프론트 안 역반영) / 스키마=data-migration-v4(단 tenant.status VARCHAR 전환). 각 문서 서두에 "정본 참조" 줄을 추가해 재발을 막을 것.
