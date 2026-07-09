# web-attendance-frontend

웹 출결 시스템 프론트엔드. **Vite + React 19 + TypeScript(strict)**.

구 CRA/JavaScript 프론트(`front_react/`)의 재작성판으로, 백엔드 v2 계약과
**서버 주도 화면 전개** 컨셉을 따른다.

## 핵심 설계

### 서버 주도 화면 전개 (URL 미사용)

- 이 앱에는 **URL 라우터가 없다.** 화면 상태는 `POST /api/v1/navigation`이 돌려주는
  화면 코드(W000~, 실명 은닉)만으로 결정된다.
- `AppContext.navigate(screen?, lang?)`가 유일한 화면 전환 수단. 서버가 세션 상태
  (로그인/권한)를 보고 다른 화면을 돌려줄 수 있고(예: 미로그인 → W001), 클라이언트는
  그 결정을 그대로 렌더링한다 (`App.tsx`의 `ScreenBody` 스위치).
- 세션 만료(401)는 API 클라이언트 훅(`setUnauthorizedHandler`)이 감지해 로그인 화면을 전개.

### 타입 API 클라이언트

- `src/api/types.ts` — 백엔드 record DTO와 1:1 대응하는 타입(스펙 원본: `/v3/api-docs`)
- `src/api/client.ts` — fetch 래퍼(에러 → `ApiError{status, code, fieldErrors}`, 401 공통 처리)
- `src/api/endpoints.ts` — navigation/auth/user/attendance/i18n 엔드포인트

### 출결 체크 → 확정 2단계

`AttendanceScreen`: 타입 버튼 → `navigator.geolocation`으로 위치 취득 → 등록 패널 →
`POST /attendance/check` → `allowed=false`면 메시지 표시 / `requiresConfirmation`이면
인라인 확인 패널(덮어쓰기·재출근) → `POST /attendance`(토큰 + 동일 데이터)로 확정 → 상태 갱신.

### 3개 언어 (한/영/일)

- 언어 선택은 navigation의 `lang`(KOR/ENG/JPN)으로 서버 세션에 저장 → 이후 서버 메시지
  (상태 라벨, 에러, 확인 메시지)가 해당 언어로 응답된다.
- **UI 텍스트의 단일 출처는 DB 언어 마스터**다. 프론트는 텍스트 사전을 갖지 않으며,
  초기 텍스트는 Flyway 시드(`V3__seed_ui_texts.sql` + SaaS 화면 `V5__seed_saas_texts.sql`)로 투입된다.
  관리자 화면에서 등록/수정하면 즉시 반영되고(E2E 검증), 미등록 키는 키 이름이
  그대로 표시되어 번역 누락이 화면에서 드러난다.
- 공통 텍스트(헤더/버튼)는 W999, 화면별 텍스트는 해당 화면 코드에 배치.
  임베드되는 상세 화면(W006)은 자신의 텍스트를 `/api/v1/i18n/W006`로 직접 취득한다.
- 요일·시계 등 로케일 표기는 사전 없이 Intl 표준 API(`localeOf`) 사용.

## 실행

```bash
npm install
npm run dev      # http://localhost:5173 (/api는 9080으로 프록시)
npm run build    # tsc 타입체크 + 프로덕션 빌드(dist/)
```

백엔드가 `http://localhost:9080`에 떠 있어야 한다(저장소 루트에서 `docker compose up -d` + `./mvnw spring-boot:run`).
운영 배포는 `dist/`를 백엔드와 같은 오리진에서 서빙하는 것을 전제로 한다(세션 쿠키).

## 구조

```
src/
├── main.tsx              # 엔트리
├── App.tsx               # 헤더 + 화면 코드 → 컴포넌트 스위치
├── app/AppContext.tsx    # navigate()/화면 상태/언어/텍스트 (서버 주도 전개의 클라이언트 절반)
├── api/                  # 타입 + fetch 클라이언트 + 엔드포인트
├── i18n/lang.ts          # 로케일 매핑(Intl용) + t() 헬퍼 (텍스트 사전 없음)
└── screens/
    ├── LandingScreen.tsx      # W000 랜딩(제품 소개, LANDING_* 키)
    ├── LoginScreen.tsx        # W001 로그인(테넌트 코드 + 이메일 + 비밀번호)
    ├── AdminScreen.tsx        # W004 언어 마스터 관리 (SYSTEM_ADMIN)
    ├── AttendanceScreen.tsx   # W005 출결(체크→확정) (TENANT_ADMIN·MEMBER)
    ├── DetailsScreen.tsx      # W006 월별 상세
    ├── TenantsScreen.tsx      # W007 테넌트 관리 (SYSTEM_ADMIN)
    ├── TenantDetailScreen.tsx # W008 기업/결제 정보 — W007에 임베드 전개
    └── MembersScreen.tsx      # W009 멤버 관리 (TENANT_ADMIN)
```

SaaS 전환(v2.1)에서: 로그인에 테넌트 코드 입력 추가(마지막 성공 코드만 localStorage 기억,
자격 증명은 저장 안 함), 헤더 메뉴가 role별로 달라짐, W003(회원가입) 폐지 — 멤버 등록은 W009에서.
초기 비밀번호·PG 빌링키는 **state에 잔존시키지 않는다**(패널 닫으면 폐기, 빌링키는 제출 즉시 클리어).
파괴적 조작(테넌트 정지, 멤버 비활성/강등)은 인라인 확인 패널을 거치고, 마지막 관리자 보호(409) 등
서버 판단 에러는 해당 행 아래에 그대로 표시한다.

구버전 대비 정리된 것: React Native geolocation 라이브러리 → 표준 `navigator.geolocation`,
수동 달력 계산(윤년 버그) → 백엔드 monthly 응답 사용, `window.location.replace` 전체 리로드 → 상태 기반 전환,
하드코딩 테스트 계정/비밀번호 console.log → 제거.
