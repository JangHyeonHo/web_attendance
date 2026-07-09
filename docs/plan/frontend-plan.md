# 멀티테넌시 전환 — 프론트엔드 상세 설계

- 대상: `frontend/` (Vite + React 19 + TypeScript strict)
- 상위 문서: `docs/plan-saas-multitenancy.md` (마스터 계획, 2026-07-08 확정)
- 전제(확정): 로그인 폼에 테넌트 코드 추가 / 공개 회원가입(W003) 폐기 / role 3단계
  (SYSTEM_ADMIN·TENANT_ADMIN·MEMBER) / 기업·결제 정보는 SYSTEM_ADMIN 화면(마스킹 표시,
  수정은 전체 재입력) / 회사 소개용 랜딩페이지 신설
- 불변 원칙(현 구조 유지):
  - **URL 라우터 없음.** 화면은 navigation API의 W-코드로만 결정, `AppContext.navigate()`가
    유일한 전환 수단, `App.tsx`의 `ScreenBody` 스위치가 코드→컴포넌트 매핑을 보관.
  - **UI 텍스트의 단일 출처는 DB 언어 마스터.** 프론트에 텍스트 사전 없음. 신규 화면 텍스트는
    V5 시드로 투입(§7), 미등록 키는 키 이름 노출로 번역 누락을 드러낸다.
  - 에러/상태 라벨 등 서버 생성 메시지는 서버가 세션 언어로 조립해 내려준다(프론트는 표시만).

---

## 1. 화면 목록 / 전개 표

### 1-1. 랜딩 화면 코드 결정: **W000 개편안 채택** (별도 코드 신설안 기각)

| 안 | 내용 | 판단 |
|----|------|------|
| **A. W000 개편 (채택)** | 기존 W000(인덱스)을 제품 소개 랜딩으로 개편. 비로그인 홈 = 랜딩 | ✅ |
| B. 별도 코드(예: W010 랜딩) 신설 | W000은 잔치(빈 인덱스)로 유지, 랜딩만 새 코드 | ❌ |

**근거** — 현 `NavigationService.decide()`의 동작이 A안과 정확히 일치한다:
- 미지정/알 수 없는 코드 요청 시 **미로그인이면 무조건 W000**으로 전개(기동 시 `navigate()`의
  기본 도착지). 즉 W000이 이미 "비로그인 홈"이며, 랜딩의 요구 정의("비로그인 홈 = 제품 소개")와
  코드 변경 없이 합치한다.
- 로그인 상태에서 W000을 요청하면 서버가 `homeOf(user)`로 강제 전개(ALREADY_LOGGED_IN)하므로,
  **W000은 사실상 비로그인 전용 화면**이다. 랜딩으로 바꿔도 로그인 사용자 동선에 영향이 없다.
- B안은 "미로그인 기본 도착지"를 W000→신규 코드로 옮기는 서버 규칙 변경이 필요하고, 그 후
  W000은 아무도 도달하지 않는 죽은 코드로 남는다. 화면 코드 은닉 체계(W-코드)에 잔재를 만들
  이유가 없다.
- 구현도 최소: `IndexScreen.tsx`를 `LandingScreen.tsx`로 개편(파일 대체)하면 끝. `Screen.java`
  변경 없음(W000 PUBLIC 그대로).

### 1-2. 화면 전개 표 (전환 후 전체)

| 코드 | 화면 | 컴포넌트 (`src/screens/`) | 접근 role | 주요 요소 |
|------|------|---------------------------|-----------|-----------|
| W000 | 랜딩(비로그인 홈) | `LandingScreen.tsx` (기존 `IndexScreen.tsx` 대체) | PUBLIC (로그인 시 서버가 홈으로 전개) | 히어로, 기능 소개 4종, CTA(도입 문의 mailto) — §2 |
| W001 | 로그인 | `LoginScreen.tsx` (수정) | PUBLIC | **tenantCode** + email + password — §3 |
| W002 | 로그아웃(액션) | 없음(서버가 세션 무효화 후 W001 전개) | PUBLIC | 변경 없음 |
| ~~W003~~ | ~~회원가입~~ | ~~`SignupScreen.tsx`~~ **삭제** | — | §6. 코드 영구 결번(재사용 금지) |
| W004 | 언어 마스터 관리 | `AdminScreen.tsx` (유지) | **SYSTEM_ADMIN** | 글로벌 언어 마스터 조회/업서트(현행 그대로). 접근이 관리자→SYSTEM_ADMIN으로 좁아짐 — §4-3 |
| W005 | 출결 | `AttendanceScreen.tsx` (유지) | MEMBER, TENANT_ADMIN | 변경 없음(서버 API가 세션의 tenantId로 스코프) |
| W006 | 출결 상세(월별) | `DetailsScreen.tsx` (유지) | MEMBER, TENANT_ADMIN | 변경 없음. W005에 임베드 + `/i18n/W006` 직접 취득 패턴 유지 |
| **W007** | 테넌트 관리 | `TenantsScreen.tsx` (신규) | **SYSTEM_ADMIN** | 테넌트 목록/생성 폼/정지·재개, 행 선택 시 W008 임베드 — §4-1 |
| **W008** | 테넌트 상세(기업/결제) | `TenantDetailScreen.tsx` (신규) | **SYSTEM_ADMIN** | 기업 정보·결제 정보 조회(마스킹)/전체 재입력 폼 — §4-1. W007에 임베드(W005→W006 패턴), 텍스트는 `/i18n/W008` 직접 취득 |
| **W009** | 멤버 관리 | `MembersScreen.tsx` (신규) | **TENANT_ADMIN** | 멤버 목록/등록/비활성·재활성/역할 변경, 마지막 관리자 보호 에러 표시 — §4-2 |
| W999 | 공통(헤더) | — (텍스트 전용) | PUBLIC | 헤더/공용 버튼 키 |

### 1-3. 서버 전개 규칙에 대한 프론트 기대치 (백엔드 Screen/NavigationService 확장과의 계약)

- `Screen.Access`가 `PUBLIC / LOGIN_REQUIRED / ADMIN_ONLY` → **`PUBLIC / LOGIN_REQUIRED / 요구 role 집합`**
  으로 일반화된다(마스터 계획 §4·§10 "Screen 레지스트리에 요구 role 선언적 추가").
- `homeOf(user)` (로그인 직후·W000/W001 요청 시 도착지):
  - `SYSTEM_ADMIN` → **W007**, `TENANT_ADMIN` → **W005**(일상 업무가 출결), `MEMBER` → **W005**.
  - SYSTEM_ADMIN은 출결 비대상(운영사 계정, 마스터 계획 §4 데이터 접근 경계)이므로 W005/W006
    요청 시 W007로 전개된다.
- 권한 부족 전개 사유는 `ADMIN_ONLY` → **`ROLE_DENIED`**로 개명(`RoleInterceptor` 일반화에 맞춤).
- W003 요청은 `Screen.fromCode()`가 null을 돌려 기존 폴백(UNKNOWN_SCREEN → W000)으로 흡수 —
  프론트 추가 처리 불요.

### 1-4. 헤더(App.tsx) — role별 메뉴

`NavigateResponse`에 `role` 필드가 추가되어(§5) 헤더가 이를 스위치한다. 서버가 최종 게이트이므로
헤더는 "보이는 메뉴"만 담당(현행과 동일한 이중 구조).

| 상태 | 메뉴 버튼(왼쪽부터) |
|------|---------------------|
| 비로그인 | HOME(W000) · LOGIN(W001) — **SIGNUP 버튼 삭제** |
| MEMBER | HOME · ATTEND(W005) · LOGOUT(W002) |
| TENANT_ADMIN | HOME · ATTEND(W005) · MEMBERS(W009) · LOGOUT |
| SYSTEM_ADMIN | HOME · TENANTS(W007) · ADMIN(W004, 언어 마스터) · LOGOUT |

HOME은 전 상태 공통(로그인 시 서버가 각자의 홈으로 전개하므로 안전). 언어 셀렉터는 현행 유지.
`header-right`에 `userName` 옆 **테넌트명 뱃지**(`LoginResponse.tenantName`, SYSTEM_ADMIN은 미표시)를 추가한다.

---

## 2. 랜딩페이지(W000) 설계

### 2-1. 컴포넌트: `src/screens/LandingScreen.tsx`

`useApp()`의 `t`/`userName`/`navigate`만 사용, 자체 API 호출 없음(텍스트는 navigation 응답의
W000 texts로 동봉 — 현행 인덱스와 동일 경로라 추가 왕복 0회).

**섹션 구성과 카피·키의 정본은 landing-page.md**다(교차 검증 최종 결정 D-E — 동일 키 상이 카피
충돌은 landing-page.md 버전 채택). 6섹션: 히어로 → 핵심 기능 4카드 → 신뢰 3카드 → 도입 3단계 →
CTA → 푸터(landing-page.md §1).

```
<div className="landing">
  <section className="hero">            // 히어로 (landing-page §2-1)
    <span className="badge">{t('LANDING_HERO_BADGE')}</span>
    <h1>{t('LANDING_HERO_TITLE')}</h1>
    <p>{t('LANDING_HERO_SUB')}</p>
    <a className="cta" href={`mailto:${t('CONTACT_EMAIL')}?subject=…`}>{t('LANDING_CTA_CONTACT')}</a>
    <button onClick={→ navigate('W001')}>{t('LANDING_CTA_LOGIN')}</button>   // 보조 CTA
  </section>
  <section className="features">        // 기능 4카드 (LANDING_FEAT1_*~FEAT4_*, §2-2)
  <section className="trust">           // 신뢰 3카드 (LANDING_TRUST1_*~TRUST3_*, §2-3)
  <section className="steps">           // 도입 3단계 (LANDING_STEP1_*~STEP3_*, §2-4)
  <section className="cta-band">        // CTA 반복 (LANDING_CTA_TITLE/SUB/CONTACT, §2-5)
  <footer>                              // 푸터 (LANDING_FOOTER_*, §2-5)
</div>
```

- **mailto 주소는 언어 마스터 키(`CONTACT_EMAIL`, W000, 3언어 동일 값)로 관리**한다(이 키만
  본 문서 안 채택 — landing-page §2-5의 `{CONTACT_EMAIL}` 플레이스홀더를 이 키 값으로 렌더 시
  치환). "UI 텍스트는 DB 단일 출처" 원칙의 연장으로, 문의 주소 변경을 배포 없이 처리한다.
- 로그인 상태로 W000이 렌더될 일은 원칙적으로 없으나(서버가 홈으로 전개), 방어적으로
  `userName` 존재 시 주 CTA를 "출결 화면으로" 버튼(헤더 ATTEND와 동일 키)으로 대체한다.
- 기존 `INDEX_TITLE`/`INDEX_SUB` 키는 랜딩에서 **미사용**. V5에서 삭제하지 않고 무해 잔존
  허용(D-A 확정 — §7). E2E의 타이틀 단언은 `LANDING_HERO_TITLE` 기준(test-plan 반영, D-E).

### 2-2. 스타일: `src/app.css` 확장 (신규 파일·라이브러리 없음)

기존 변수(`--accent` 등)·클래스 컨벤션을 그대로 확장한다.

```css
.landing { }                                   /* 래퍼 */
.hero { text-align:center; padding:48px 16px; }/* .panel 변형: 상하 여백 확대 */
.hero h1 { font-size:32px; }
.cta { /* button.primary와 동일 시각(배경 --accent, 흰 글자) — a 태그용 재정의 */ }
.features { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin-top:20px; }
.feature-card { /* .panel 축소판: 흰 배경, --border, radius 10px, padding 16px */ }
@media (max-width: 720px) { .features { grid-template-columns:repeat(2,1fr); } }
@media (max-width: 480px) { .features { grid-template-columns:1fr; } }
```

앱 전체 `max-width: 860px` 컨테이너는 유지(랜딩도 같은 폭 — 별도 풀블리드 레이아웃은 도입하지
않는다. 사내 도구 톤을 유지하고 헤더/언어 셀렉터를 공유하기 위함).

---

## 3. 로그인 화면(W001) 변경

### 3-1. `LoginScreen.tsx`

- 필드 순서: **회사 코드 → 이메일 → 비밀번호**. 라벨 키 `TENANT_CODE`(W999 — 공용 배치, §7),
  보조 문구 `TENANT_CODE_HINT`(W001, "회사에서 안내받은 코드를 입력하세요").
- input 속성: `autoComplete="organization"`, `autoCapitalize="none"`, `spellCheck={false}`,
  `required`. 대문자 정규화는 서버 정책에 따름(프론트는 trim만).
- **UX: 마지막 성공 테넌트 코드를 `localStorage('wa.tenantCode')`에 기억**하고 초기값으로 채운다.
  코드는 비밀이 아니고(로그인 실패 메시지도 테넌트 존재를 비노출 — 마스터 계획 §6), 매일
  타이핑하는 값이라 기억이 UX상 필수. 이메일/비밀번호는 저장하지 않는다.
- 제출: `authApi.login({ tenantCode, email, password })` → 성공 시 localStorage 갱신 후
  `navigate('W000')`(서버가 role별 홈 결정 — 현행 주석 그대로 유효).
- 실패는 현행처럼 단일 메시지(`ApiError.message`) 표시. 테넌트 오류/계정 오류를 구분해 보여주지
  않는다(서버 계약). **429(`RATE_LIMITED`) 수신 시에도 서버 메시지를 그대로 표시**한다(별도 분기
  불요 — security-plan §3, 교차 검증 발견 8).

### 3-2. 타입 변경 (발췌 — 전체는 §5)

```ts
export interface LoginRequest {
  tenantCode: string   // 추가
  email: string
  password: string
}

export interface LoginResponse {
  userId: number
  email: string
  name: string
  role: Role           // admin: boolean 제거 → role 단일 필드 (D-D)
  tenantCode: string   // 백엔드 계약과 1:1 (SYSTEM_ADMIN은 'DEFAULT')
  tenantName: string   // 헤더 뱃지용 — 비null (SYSTEM_ADMIN은 소속 테넌트명. 뱃지 표시는 role로 분기해 SYSTEM_ADMIN 미표시)
}
```

---

## 4. 신규 관리 화면 설계

공통 컨벤션(기존 `AdminScreen` 답습): `.panel` 래퍼, 상단 `.inline-form` 등록 폼, 하단
`.detail-table` 목록, 에러는 `ApiError.message`를 `<p className="error" role="alert">`로,
성공은 `.success`. 목록 재조회는 `useCallback(reload)` + 조작 후 `await reload()`.

### 4-1. SYSTEM_ADMIN — W007 테넌트 관리 (`TenantsScreen.tsx`)

**목록** (`systemTenantApi.list()`): 코드 / 회사명 / 상태 / 멤버 수 / 생성일 / 조작.
- 상태 라벨: `TENANT_STATUS_ACTIVE` `TENANT_STATUS_SUSPENDED` 키로 렌더(enum→키 매핑 객체,
  `TYPE_LABEL_KEYS` 패턴).
- 조작: 정지(`SUSPEND`)/재개(`RESUME`) 버튼 — `systemTenantApi.updateStatus()` 후 reload.
  정지는 파괴적이므로 `.stamp-box.confirm` 스타일의 인라인 확인 패널(출결 확정 패턴 재사용)을
  거친다. `window.confirm`은 언어 마스터를 못 쓰므로 사용하지 않는다.
- 행의 상세(`DETAIL`) 버튼 → 해당 행 아래에 **W008 임베드 전개**(`<TenantDetailScreen tenantId={...}/>`,
  W005가 W006을 토글 임베드하는 기존 패턴).

**생성 폼** (`systemTenantApi.create({tenantCode, name, adminEmail, adminName})`):
- 성공 시 응답의 **`initialPassword`를 1회성 패널로 표시**:
  - `.stamp-box`에 코드/관리자 이메일/초기 비밀번호 + 복사 버튼(`navigator.clipboard.writeText`,
    실패 시 텍스트 선택 폴백) + 안내문 `INITIAL_PWD_NOTE`("이 비밀번호는 지금만 표시되며 다시
    조회할 수 없습니다. 고객사 관리자에게 안전한 경로로 전달하세요.").
  - 패널을 닫으면 상태에서 즉시 폐기(비밀번호를 어떤 전역 상태/스토리지에도 남기지 않음).
- 필드 검증 에러는 `ApiError.fieldErrors`를 필드별 `<span className="error">`로(기존
  SignupScreen의 fieldErrors 처리 코드를 이식 — 삭제 전에 이 패턴만 회수한다).

**W008 테넌트 상세 — 기업/결제 정보 (`TenantDetailScreen.tsx`, props: `tenantId`)**

임베드 컴포넌트이므로 W006과 동일하게 **자기 텍스트를 `/api/v1/i18n/W008`로 직접 취득**
(`languageApi.texts('W008', lang)`, 언어 변경 시 재취득).

두 카드(기업 정보 / 결제 정보) 각각 "조회 뷰 ⇄ 재입력 폼" 2모드:

- **조회 뷰(기본)** — 서버가 내려준 마스킹 문자열을 **그대로 표시**(마스킹은 서버 책임, 프론트는
  가공·복원 시도 금지):
  - 사업자등록번호: `123-**-*****` (`businessRegNoMasked`)
  - 담당자 연락처: `010-****-5678` (`contactPhoneMasked`)
  - 카드: `**** **** **** 1234` + 브랜드 (`cardMasked`, `cardBrand`)
  - **빌링키: 값 자체는 어떤 API로도 오지 않는다.** `hasBillingKey: boolean`만 받아
    `BILLING_KEY_SET`("등록됨") / `BILLING_KEY_UNSET`("미등록") 라벨로 표시.
  - 미등록(404 또는 null) 시 `NOT_REGISTERED` 문구 + 등록 버튼.
- **재입력 폼(수정)** — `EDIT` 버튼으로 진입. **모든 필드가 빈 값에서 시작**하며(마스킹 값을
  초기값/placeholder로도 넣지 않는다 — 부분 노출·마스킹 문자열 재제출 사고 방지), 저장은 전체
  필드 재입력 후 upsert 1회. 안내문 `REENTER_NOTE`("수정 시 모든 항목을 다시 입력합니다. 저장된
  값은 표시되지 않습니다.")를 폼 상단에 고정.
- **빌링키 재입력 UX**: 결제 방식 select(`METHOD_INVOICE` 기본 / `METHOD_CARD`)에서 CARD 선택
  시에만 빌링키·카드 마지막 4자리·브랜드 입력란이 열린다. Phase 2에는 PG 결제위젯이 없으므로
  운영자가 PG 콘솔에서 발급한 빌링키를 수동 입력하는 흐름임을 라벨/힌트로 명시. 빌링키 input은
  `type="password"` + `autoComplete="off"`(화면 어깨너머 노출 방지), 제출 후 즉시 state 클리어.
  INVOICE로 저장하면 카드 필드는 서버에서 무시(계약)되므로 프론트도 비활성화만 하고 값은 보내지
  않는다(`pgCustomerKey: null`).

### 4-2. TENANT_ADMIN — W009 멤버 관리 (`MembersScreen.tsx`)

**목록** (`tenantMemberApi.list()`): 이름 / 이메일 / 부서 / 역할 / 상태 / 조작.
- 역할 라벨 `ROLE_TENANT_ADMIN`·`ROLE_MEMBER`, 상태 라벨 `STATUS_ACTIVE`·`STATUS_DISABLED`·
  `STATUS_PENDING`(PENDING은 스키마 대비용 — Phase 2에선 표시만).
- 조작: 비활성(`DISABLE`)/재활성(`ENABLE`) — `tenantMemberApi.updateStatus()`,
  관리자 지정(`PROMOTE`)/해제(`DEMOTE`) — `tenantMemberApi.updateRole()`. 비활성·해제는
  인라인 확인 패널 경유(§4-1과 동일 패턴).
- **마지막 관리자 보호 에러 표시**: 유일한 TENANT_ADMIN을 강등/비활성하려 하면 서버가 409 +
  `code: 'LAST_TENANT_ADMIN'`(전 문서 통일 — D-D. 메시지는 서버가 세션 언어로 조립)를 반환한다. 프론트는
  `ApiError.message`를 **해당 행 바로 아래 인라인 `.error`로 표시**하고 목록은 그대로 둔다
  (전역 배너가 아니라 행 컨텍스트에 붙여 원인을 즉시 알게 함). 별도 프론트 사전 판단(관리자 수
  세기)은 하지 않는다 — 판정은 서버 단일 책임.
- 자기 자신 행의 강등/비활성 버튼은 렌더하지 않는다(세션 파괴 사고 방지 — `auth/me`의 userId와 비교).

**등록 폼** (`tenantMemberApi.create({email, name, departCd?})`):
- 성공 시 W007과 동일한 **초기 비밀번호 1회 표시 패널**(복사 버튼 + `INITIAL_PWD_NOTE`).
- fieldErrors 필드별 표시(§4-1과 동일).

### 4-3. 기존 언어 마스터 화면(W004)의 위치

- **화면·컴포넌트·API 모두 현행 유지**(`AdminScreen.tsx`, `/api/v1/admin/i18n`). 언어 마스터는
  Phase 1~3 동안 "제품 글로벌"(마스터 계획 §3)이므로 테넌트 개념이 없다.
- 접근만 **SYSTEM_ADMIN 전용**으로 좁아진다(글로벌 문구 관리는 운영사 권한 — 마스터 계획 §4).
  프론트 변화는 헤더 메뉴 노출 조건뿐(§1-4). TENANT_ADMIN이 W004를 요청하면 서버가
  `ROLE_DENIED`로 홈 전개.
- 테넌트별 문구 오버라이드는 Phase 4 — 이 문서 스코프 밖.

---

## 5. `api/types.ts` / `endpoints.ts` 변경 전체

### 5-1. types.ts — 변경 타입

| 타입 | 변경 |
|------|------|
| `ScreenCode` | `'W003'` 제거, `'W007' | 'W008' | 'W009'` 추가 |
| `NavigationReason` | `'ADMIN_ONLY'` → `'ROLE_DENIED'` |
| `NavigateResponse` | `role: Role | null` 필드 추가 |
| `LoginRequest` | `tenantCode: string` 추가 |
| `LoginResponse` | `admin: boolean` → `role: Role`, `tenantCode: string`·`tenantName: string`(비null) 추가 — 백엔드 계약 1:1(D-D) |
| `SignupRequest` | **삭제** |
| `UserResponse` | `admin: boolean` → `role: Role`, `status: UserStatus` 추가 |

### 5-2. types.ts — 신규 타입 (백엔드 record와 1:1)

```ts
// ---- tenancy 공통 ----
export type Role = 'SYSTEM_ADMIN' | 'TENANT_ADMIN' | 'MEMBER'
export type UserStatus = 'PENDING' | 'ACTIVE' | 'DISABLED'
export type TenantStatus = 'ACTIVE' | 'SUSPENDED'
export type BillingMethod = 'INVOICE' | 'CARD'

// ---- system: 테넌트 (W007) ----
export interface TenantSummary {
  tenantId: number
  tenantCode: string
  name: string
  status: TenantStatus
  memberCount: number
  createdAt: string
}

export interface TenantCreateRequest {
  tenantCode: string
  name: string
  adminEmail: string
  adminName: string
}

export interface TenantCreateResponse {   // 평면(flat) record — 백엔드 컨벤션 1:1 (D-D, 중첩 구조 폐기)
  tenantId: number
  tenantCode: string
  name: string
  status: TenantStatus
  adminUserId: number
  adminEmail: string
  initialPassword: string   // 1회성 — 응답 이후 어디에도 저장하지 않는다
}

export interface TenantStatusUpdateRequest {
  status: TenantStatus
}

// ---- system: 기업/결제 정보 (W008) ----
export interface TenantProfileUpsertRequest {   // 전체 재입력(평문) — 부분 수정 없음
  businessRegNo: string
  ceoName: string | null
  address: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
}

export interface TenantProfileResponse {        // 조회는 마스킹 필드만 (…Masked 필드명 — 백엔드 DTO에 역반영됨)
  tenantId: number
  businessRegNoMasked: string    // 예: 123-**-*****
  ceoName: string | null
  address: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhoneMasked: string | null   // 예: 010-****-5678
  updatedAt: string
}

export interface TenantBillingUpsertRequest {   // 전체 재입력
  billingMethod: BillingMethod
  billingEmail: string | null
  pgCustomerKey: string | null   // CARD일 때만. 응답으로는 절대 돌아오지 않는다
  cardLast4: string | null
  cardBrand: string | null
  plan: string
  billedFrom: string | null      // ISO date
  memo: string | null
}

export interface TenantBillingResponse {
  tenantId: number
  billingMethod: BillingMethod
  billingEmail: string | null
  hasBillingKey: boolean         // 빌링키는 존재 여부만
  cardMasked: string | null      // 예: **** **** **** 1234
  cardBrand: string | null
  plan: string
  billedFrom: string | null
  memo: string | null
  updatedAt: string
}

// ---- tenant: 멤버 (W009) ----
export interface MemberSummary {
  userId: number
  email: string
  name: string
  departCd: string | null
  role: Role                     // TENANT_ADMIN | MEMBER (SYSTEM_ADMIN은 등장 안 함)
  status: UserStatus
  createdAt: string
}

export interface MemberCreateRequest {
  email: string
  name: string
  departCd?: string | null
}

export interface MemberCreateResponse {   // 평면(flat) record — 백엔드 컨벤션 1:1 (D-D)
  userId: number
  email: string
  name: string
  departCd: string | null
  role: Role                     // 등록 시 항상 MEMBER (role은 별도 PUT /role로 변경)
  status: UserStatus
  initialPassword: string        // 1회성
}

export interface MemberStatusUpdateRequest {
  status: UserStatus             // ACTIVE | DISABLED
}

export interface MemberRoleUpdateRequest {
  role: Role                     // TENANT_ADMIN | MEMBER
}
```

에러 계약 추가: `ErrorResponse.code`에 `'LAST_TENANT_ADMIN'`(409 — 전 문서 통일, D-D) 등장 —
타입은 string 그대로(코드 유니온은 만들지 않음, 현행 컨벤션 유지).

### 5-3. endpoints.ts — 추가/삭제

갱신 계열 메소드는 백엔드 계약대로 **PUT**이다(D-D — 종전 `post()` 표기 정정. `client.ts`에
`put()` 헬퍼 1개 추가).

```ts
/** SYSTEM_ADMIN 전용 — 테넌트/기업/결제 */
export const systemTenantApi = {
  list: () => get<TenantSummary[]>('/api/v1/system/tenants'),
  detail: (tenantId: number) =>
    get<TenantSummary>(`/api/v1/system/tenants/${tenantId}`),
  create: (request: TenantCreateRequest) =>
    post<TenantCreateResponse>('/api/v1/system/tenants', request),
  updateStatus: (tenantId: number, request: TenantStatusUpdateRequest) =>
    put<TenantSummary>(`/api/v1/system/tenants/${tenantId}/status`, request),
  profile: (tenantId: number) =>
    get<TenantProfileResponse | null>(`/api/v1/system/tenants/${tenantId}/profile`),
  upsertProfile: (tenantId: number, request: TenantProfileUpsertRequest) =>
    put<TenantProfileResponse>(`/api/v1/system/tenants/${tenantId}/profile`, request),
  billing: (tenantId: number) =>
    get<TenantBillingResponse | null>(`/api/v1/system/tenants/${tenantId}/billing`),
  upsertBilling: (tenantId: number, request: TenantBillingUpsertRequest) =>
    put<TenantBillingResponse>(`/api/v1/system/tenants/${tenantId}/billing`, request),
}

/** TENANT_ADMIN 전용 — 멤버 (tenantId는 항상 서버 세션에서 — 파라미터로 보내지 않는다) */
export const tenantMemberApi = {
  list: () => get<MemberSummary[]>('/api/v1/tenant/members'),
  create: (request: MemberCreateRequest) =>
    post<MemberCreateResponse>('/api/v1/tenant/members', request),
  updateStatus: (userId: number, request: MemberStatusUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/status`, request),
  updateRole: (userId: number, request: MemberRoleUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/role`, request),
}
```

- 위 목록이 **Phase 2 계약의 전부**(D-D 단일화 — backend-api §1.3/§1.4와 1:1). 멤버 단건
  조회/삭제/비밀번호 재발급, 테넌트명 수정은 Phase 3 이연으로 양쪽 문서에서 제외.
- **삭제**: `userApi`(signup 단일 함수만 있던 객체) 통째 제거.
- 변경: `client.ts`에 `put()` 헬퍼 추가(401 처리·에러 매핑은 그대로).
- 변경 없음: `navigationApi`, `attendanceApi`, `languageApi`
  (멀티테넌시는 세션 스코프라 클라이언트 계층 무변경 —
  tenantId를 프론트가 실어 보내는 API는 SYSTEM_ADMIN용 경로 파라미터뿐이라는 점이
  마스터 계획 §5 원칙의 프론트 대응).

### 5-4. `AppContext.tsx` 변경

- `AppState`에 `role: Role | null` 추가, `navigate()`에서 `response.role` 반영.
- 그 외(언어/텍스트/401 훅/기동 흐름) 무변경.

---

## 6. W003(회원가입) 폐기 처리

| 대상 | 처리 |
|------|------|
| `src/screens/SignupScreen.tsx` | **파일 삭제** (fieldErrors 표시 패턴은 §4 신규 폼에 이식) |
| `src/App.tsx` | `case 'W003'` 분기, `SignupScreen` import, **헤더 SIGNUP 버튼 삭제** — 비로그인 헤더는 HOME·LOGIN 2개가 된다 |
| `src/api/types.ts` | `ScreenCode`에서 `'W003'` 제거, `SignupRequest` 삭제 |
| `src/api/endpoints.ts` | `userApi.signup` 제거(→ `userApi` 객체 자체 삭제) |
| 언어 마스터 | V3 시드의 `W003` 전 행·`W999/SIGNUP`은 **잔존 허용(무해 — V5에서 삭제하지 않음, D-A 확정)**. `NAME`·`DEPART`는 W009 키로 신규 시드(§7) |
| 백엔드(참고) | `Screen.SIGNUP` enum 제거 → W003 요청은 `fromCode()=null` → 기존 UNKNOWN_SCREEN 폴백으로 W000(랜딩) 전개. 프론트에 별도 방어 코드 불요 |

URL 라우터가 없으므로 딥링크/북마크 호환 문제는 원천적으로 없다. W003 코드는 **영구 결번**
(과거 의미와 혼동 방지 — 신규 화면은 W007부터 부여한 이유).

---

## 7. 언어 마스터 신규 키 전체 목록 (V5 시드)

**`V5__seed_saas_texts.sql` 단일 파일**(멤버/테넌트/랜딩 키 통합 — D-E, 파일 구성 확정 기록은
data-migration-v4 §7). V3와 동일하게 `INSERT IGNORE`(운영자 수정 존중).
**DELETE 없음**: V3 시드의 `W003` 전 행·`W999/SIGNUP`·`W000/INDEX_*`는 잔존 허용(무해한 죽은 행 — D-A 확정).

### W999 공통 (신규 7키)

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| TENANT_CODE | 회사 코드 | Company code | 会社コード |
| TENANTS | 테넌트 | Tenants | テナント |
| MEMBERS | 멤버 | Members | メンバー |
| EDIT | 수정 | Edit | 編集 |
| CLOSE | 닫기 | Close | 閉じる |
| COPY | 복사 | Copy | コピー |
| STATUS | 상태 | Status | 状態 |

### W000 랜딩 (신규 34키 — **카피·키의 정본은 landing-page.md §2**, D-E)

종전의 본 문서 W000 키 13종(`FEATURE_GEO_*` 등 2섹션 설계)은 **폐기**하고 landing-page.md §2의
`LANDING_*` 키 체계(6섹션 33키)를 채택한다: `LANDING_HERO_BADGE/TITLE/SUB`,
`LANDING_FEATURES_TITLE`·`LANDING_FEAT1~4_TITLE/DESC`, `LANDING_TRUST_TITLE`·`LANDING_TRUST1~3_TITLE/DESC`,
`LANDING_STEPS_TITLE`·`LANDING_STEP1~3_TITLE/DESC`, `LANDING_CTA_TITLE/SUB/CONTACT/LOGIN`,
`LANDING_FOOTER_PRODUCT/CONTACT/COPYRIGHT` — 카피 전문(한/영/일)은 landing-page.md §2 표를
그대로 시드한다(동일 키 상이 카피는 landing-page.md 버전 채택).

본 문서 안에서 유지하는 키는 1개뿐:

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| CONTACT_EMAIL | sales@webatt.example | sales@webatt.example | sales@webatt.example |

(3언어 동일 값 — landing-page §2-5의 `{CONTACT_EMAIL}` 플레이스홀더 치환용. `INDEX_TITLE`/`INDEX_SUB`는
미사용 잔존, `LANDING_GO_ATTEND`는 폐기 — 로그인 상태 방어 분기는 헤더 ATTEND 키 재사용.)

### W001 로그인 (신규 1키)

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| TENANT_CODE_HINT | 회사에서 안내받은 코드를 입력하세요. | Enter the code provided by your company. | 会社から案内されたコードを入力してください。 |

### W007 테넌트 관리 (신규 14키)

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| TENANTS_TITLE | 테넌트 관리 | Tenant management | テナント管理 |
| TENANT_CREATE | 테넌트 생성 | Create tenant | テナント作成 |
| TENANT_NAME | 회사명 | Company name | 会社名 |
| ADMIN_EMAIL | 관리자 이메일 | Admin email | 管理者メール |
| ADMIN_NAME | 관리자 이름 | Admin name | 管理者名 |
| MEMBER_COUNT | 멤버 수 | Members | メンバー数 |
| CREATED_AT | 생성일 | Created | 作成日 |
| TENANT_STATUS_ACTIVE | 사용 중 | Active | 利用中 |
| TENANT_STATUS_SUSPENDED | 정지 | Suspended | 停止 |
| SUSPEND | 정지 | Suspend | 停止 |
| RESUME | 재개 | Resume | 再開 |
| DETAIL | 상세 | Detail | 詳細 |
| INITIAL_PWD | 초기 비밀번호 | Initial password | 初期パスワード |
| INITIAL_PWD_NOTE | 이 비밀번호는 지금만 표시되며 다시 조회할 수 없습니다. 안전한 경로로 전달하세요. | This password is shown only once and cannot be retrieved again. Share it securely. | このパスワードは今回のみ表示され、再照会できません。安全な方法で共有してください。 |

### W008 기업/결제 정보 (신규 23키)

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| PROFILE_TITLE | 기업 정보 | Company profile | 企業情報 |
| BILLING_TITLE | 결제 정보 | Billing | 請求情報 |
| BIZ_REG_NO | 사업자등록번호 | Business reg. no. | 事業者登録番号 |
| CEO_NAME | 대표자명 | CEO name | 代表者名 |
| ADDRESS | 주소 | Address | 住所 |
| CONTACT_NAME | 담당자 | Contact name | 担当者 |
| CONTACT_EMAIL | 담당자 이메일 | Contact email | 担当者メール |
| CONTACT_PHONE | 담당자 연락처 | Contact phone | 担当者電話番号 |
| BILLING_METHOD | 결제 방식 | Billing method | 支払方法 |
| METHOD_INVOICE | 세금계산서/계좌이체 | Invoice / bank transfer | 請求書/口座振込 |
| METHOD_CARD | 카드(빌링키) | Card (billing key) | カード（ビリングキー） |
| BILLING_EMAIL | 청구서 수신 이메일 | Billing email | 請求書送付先メール |
| BILLING_KEY | PG 빌링키 | PG billing key | PGビリングキー |
| BILLING_KEY_SET | 등록됨 | Registered | 登録済み |
| BILLING_KEY_UNSET | 미등록 | Not registered | 未登録 |
| CARD_LAST4 | 카드 마지막 4자리 | Card last 4 digits | カード下4桁 |
| CARD_BRAND | 카드 브랜드 | Card brand | カードブランド |
| PLAN | 플랜 | Plan | プラン |
| BILLED_FROM | 과금 시작일 | Billed from | 課金開始日 |
| MEMO | 메모 | Memo | メモ |
| NOT_REGISTERED | 아직 등록된 정보가 없습니다. | Nothing registered yet. | まだ登録された情報はありません。 |
| REENTER_NOTE | 수정 시 모든 항목을 다시 입력합니다. 저장된 값은 표시되지 않습니다. | Editing requires re-entering every field. Stored values are never shown. | 修正時はすべての項目を再入力します。保存済みの値は表示されません。 |
| UPDATED_AT | 수정일 | Updated | 更新日 |

### W009 멤버 관리 (신규 16키)

| 키 | KOR | ENG | JPN |
|----|-----|-----|-----|
| MEMBERS_TITLE | 멤버 관리 | Member management | メンバー管理 |
| MEMBER_CREATE | 멤버 등록 | Add member | メンバー登録 |
| NAME | 이름 | Name | 名前 |
| DEPART | 부서 코드(선택) | Department code (optional) | 部署コード（任意） |
| ROLE | 역할 | Role | 役割 |
| ROLE_TENANT_ADMIN | 관리자 | Admin | 管理者 |
| ROLE_MEMBER | 멤버 | Member | メンバー |
| STATUS_ACTIVE | 사용 중 | Active | 利用中 |
| STATUS_DISABLED | 비활성 | Disabled | 無効 |
| STATUS_PENDING | 대기 | Pending | 保留 |
| DISABLE | 비활성 | Disable | 無効化 |
| ENABLE | 활성화 | Enable | 有効化 |
| PROMOTE | 관리자 지정 | Make admin | 管理者に設定 |
| DEMOTE | 관리자 해제 | Revoke admin | 管理者を解除 |
| INITIAL_PWD | 초기 비밀번호 | Initial password | 初期パスワード |
| INITIAL_PWD_NOTE | 이 비밀번호는 지금만 표시되며 다시 조회할 수 없습니다. 본인에게 안전하게 전달하세요. | This password is shown only once and cannot be retrieved again. Share it with the member securely. | このパスワードは今回のみ表示され、再照会できません。本人へ安全に共有してください。 |

합계: **신규 95키 × 3언어 = 285행**(W999 7 + W000 34 + W001 1 + W007 14 + W008 23 + W009 16).
삭제 행 없음 — 폐기 키(W003 전 행, W999/SIGNUP, W000/INDEX_*)는 무해 잔존 허용(D-A).
(INITIAL_PWD 계열은 W007/W009에 의도적 중복 — 화면 단위 텍스트 배치 컨벤션 유지.
기존 W005/W006의 ATTDETAILS 중복과 같은 원칙.)

### 서버 메시지(언어 마스터 아님 — 참고)

마지막 관리자 보호(409 LAST_TENANT_ADMIN), 로그인 실패(401)·레이트리밋(429), 검증 에러 등 **서버 생성 메시지는
백엔드 메시지 번들**에서 세션 언어로 조립되어 `ErrorResponse.message`로 내려온다(현행 D5 체계).
프론트는 표시만 하므로 V5 시드 대상이 아니다.

---

## 8. E2E 시나리오 변경/추가

기존 12단계 스모크(백엔드 실기동 + 브라우저/curl)에 대한 변경분과 신규 시나리오.

### 변경 (기존 단계 수정)

| # | 기존 | 변경 |
|---|------|------|
| E-01 | 로그인(email/password) | **tenantCode 포함 로그인**. 기존 시드 계정은 `DEFAULT` 테넌트 코드로 로그인 |
| E-02 | 회원가입(W003) 후 로그인 | **삭제** → 신규 T3(멤버 등록 경유)로 대체 |
| E-03 | 비로그인 홈 = 빈 인덱스 확인 | 비로그인 홈 = **랜딩 렌더** 확인(신규 T4로 흡수) |
| E-04 | 관리자 로그인 → W004 전개 | **SYSTEM_ADMIN 로그인 → W007 전개**(홈 변경), W004는 헤더 메뉴로 진입 확인 |
| 기타 | 출결 체크→확정, 월별 조회, 언어 전환, 언어 마스터 즉시 반영, 로그아웃, 401 전개 | 절차 불변 — tenantCode 로그인 이후 수행으로만 변경 |

### 추가

| # | 시나리오 | 확인 포인트 |
|---|----------|-------------|
| T1 | **테넌트 코드 로그인** | 올바른 코드 → 성공·role별 홈 전개(SYSTEM_ADMIN→W007, TENANT_ADMIN/MEMBER→W005). 틀린 코드/틀린 비밀번호 → **동일한 단일 에러 메시지**(테넌트 존재 비노출). 재방문 시 회사 코드 필드가 localStorage 값으로 선입력 |
| T2 | **2테넌트 격리** | SYSTEM_ADMIN이 테넌트 A·B 생성(각 초기 관리자 발급) → A 관리자 세션으로 W009 목록에 B 멤버 미표시, `POST /api/v1/tenant/members/{B의 userId}/status` 직접 호출 시 403/404. **같은 이메일**을 A·B에 각각 등록 후 tenantCode만 바꿔 로그인 → 각자 자기 테넌트 데이터만 보임 |
| T3 | **멤버 등록 → 그 멤버로 출결** | TENANT_ADMIN이 W009에서 멤버 등록 → 화면에 1회 표시된 초기 비밀번호로 로그아웃 후 로그인 → W005 전개 → 출근 체크→확정 → 상태 WORKING, W006 월별에 스탬프 표시. 그 멤버를 비활성 후 재로그인 시도 → 로그인 거부 |
| T4 | **랜딩 표시** | 비로그인 접속 → W000 랜딩: 히어로 타이틀(**`LANDING_HERO_TITLE` 텍스트** — D-E), 기능 카드 4종·신뢰 3종·도입 3단계·푸터 렌더, CTA가 `mailto:` href(언어 마스터의 CONTACT_EMAIL)로 렌더. 헤더에 **SIGNUP 버튼 없음**. 언어 3종 전환 시 랜딩 텍스트 전량 교체(키 노출 없음). `navigate('W003')` 강제 호출 → W000 폴백. 로그인 상태에서 W000 요청 → 각자 홈으로 전개 |
| T5 | **마지막 관리자 보호** | 관리자 1명뿐인 테넌트에서 그 관리자 강등/비활성 시도 → 409 에러가 해당 행 아래 인라인 표시, 목록 불변. 관리자 2명으로 늘린 뒤 1명 강등 → 성공 |
| T6 | **기업/결제 마스킹** | W008에서 기업/결제 정보 등록 → 조회 뷰에 `123-**-*****`, `**** **** **** 1234`, 빌링키 "등록됨"만 표시. 네트워크 응답 JSON에 원본/빌링키 필드 부재 확인. 수정 진입 시 폼이 전량 빈 값(마스킹 값 미주입) |
| T7 | **role 전개 가드** | MEMBER 세션으로 W009/W007/W004 전개 요청 → 서버가 W005로(ROLE_DENIED). TENANT_ADMIN으로 W007/W004 요청 → W005로. SYSTEM_ADMIN으로 W005 요청 → W007로 |

T2·T5·T6은 마스터 계획 §7 격리 테스트(CI 게이트)의 화면 레벨 대응이다.

---

## 부록 — 구현 파일 요약

| 구분 | 파일 |
|------|------|
| 신규 | `screens/LandingScreen.tsx`(W000 개편), `screens/TenantsScreen.tsx`(W007), `screens/TenantDetailScreen.tsx`(W008), `screens/MembersScreen.tsx`(W009) |
| 수정 | `App.tsx`(스위치·헤더 role 분기), `app/AppContext.tsx`(role 상태), `api/types.ts`, `api/endpoints.ts`, `api/client.ts`(`put()` 헬퍼 추가), `screens/LoginScreen.tsx`, `app.css` |
| 삭제 | `screens/IndexScreen.tsx`(LandingScreen으로 대체), `screens/SignupScreen.tsx` |
| 무변경 | `i18n/lang.ts`, `main.tsx`, `AttendanceScreen.tsx`, `DetailsScreen.tsx`, `AdminScreen.tsx` |
| 시드 | `V5__seed_saas_texts.sql` — 신규 95키×3언어(관리 화면 키 §7 + 랜딩 카피 landing-page.md §2), DELETE 없음 |

구현 순서 권장: Phase 1(§3 로그인 + types의 Login·Navigate 변경 + W003 폐기 + 랜딩) →
Phase 2(W007/W008/W009 + V5 시드) — 마스터 계획 로드맵과 정합.

---

## 교차 검증 반영 이력(2026-07-08)

- D-A 확정 사항(W007/W008/W009 3분할, TENANT_ADMIN 홈=W005, `ROLE_DENIED` 개명)은 본 문서 안이 정본으로 채택됨 — §1 유지.
- D-D: endpoints의 갱신 메소드를 POST → **PUT**으로 정정(client.ts `put()` 헬퍼 추가), 응답 타입을 평면(flat) record로 정정(TenantCreateResponse/MemberCreateResponse), `LoginResponse`에 `tenantCode`·비null `tenantName` 반영, 에러 코드 `LAST_ADMIN_PROTECTED` → **`LAST_TENANT_ADMIN`**. Phase 2 계약은 테넌트 8·멤버 4 엔드포인트로 확정(단건 조회/삭제/재발급은 Phase 3).
- D-E: W000 랜딩의 섹션 구성·키·카피를 landing-page.md §2(`LANDING_*` 33키) 정본으로 교체, `CONTACT_EMAIL` 키만 본 문서 안 유지. V5는 `V5__seed_saas_texts.sql` 단일 파일(95키), 폐기 키 DELETE 없음(잔존 허용 — D-A).
- 발견 8: 로그인 429(`RATE_LIMITED`) 수신 시 서버 메시지 표시 1줄 추가(§3-1).
