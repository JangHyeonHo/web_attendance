# 프론트엔드(React) 점검 보고서

- 대상: `front_react/web-attendance-front/` (React 17 + CRA, 2021년 작성, 약 800줄)
- 기준: 백엔드 v2.0.0 (MariaDB + REST API + Swagger, `claude/attendance-system-upgrade-09t933` 브랜치)
- 결론 요약: **화면전환 로직은 부분 수정이 불가능한 구조라 라우팅 전면 재설계가 필요**하고,
  빌드 체인(CRA)·주요 라이브러리가 모두 수명이 끝났으며, 새 백엔드 API와 요청/응답이 전면 비호환이다.
  규모가 작으므로(컴포넌트 7개) 부분 수정보다 **Vite 기반 신규 재작성 + 화면 단위 이식**을 권장.

---

## 1. 화면전환 로직 (요청하신 중점 점검 항목)

현재 구조는 "서버 주도 화면전환"이다. 프론트가 `/api?win_id=W00x`를 호출하면
서버가 세션 상태를 보고 "실제로 표시할 화면 ID"를 돌려주고, 프론트는 그 값으로 컴포넌트를 스위칭한다.

```
App.js
  windowChange(win_id) → GET /api?win_id=W00x → setData(res.data)
  componentCall(): switch(data.window) { W000→Index, W001→Login, W004→Admin, W005→Attendance }
  <Route path="/" component={componentCall} />   ← 라우터는 있으나 경로가 "/" 하나뿐
```

### 문제점

| # | 문제 | 위치 | 영향 |
|---|------|------|------|
| 1 | **URL과 화면이 분리됨.** 기동시 `window.history.replaceState(null,null,"./")`로 URL을 지우고, 이후 모든 전환이 상태값(`data.window`)으로만 일어남 | `App.js:39-41` | 뒤로가기/앞으로가기 미동작, 새로고침시 초기화면, URL 공유·북마크 불가 |
| 2 | **SPA인데 전환마다 전체 리로드.** 로그인 성공 `window.location.replace('/index')`, 출결 등록 후 `('./')`, 세션 만료 `('/login')` 등 — 리액트 상태가 매번 통째로 날아감 | `Login.js:42`, `Attendance.js:162,173,197` | 성능·UX 저하, 상태 유지 불가 |
| 3 | **React Router를 설치만 하고 사실상 미사용.** `<Route path="/">` 하나에 `component={componentCall}`을 넘기는데, 함수형 컴포넌트를 매 렌더마다 새로 만들어 넘기므로 전환 때마다 하위 트리 전체가 리마운트됨 | `App.js:104` | 라우터의 이점(중첩 라우트, 가드, 코드 스플리팅) 전무 |
| 4 | **세션 만료 감지를 응답의 window ID 비교로 수행.** `checkResp.window !== data.win_id`면 "세션 종료" 알림 | `Attendance.js:160`, `Details.js:61` | 새 백엔드에는 window 개념이 없음. HTTP 401 상태코드 기반으로 바꿔야 함 |
| 5 | **다국어 텍스트가 화면전환 응답에 실려 옴** (`windows`/`headers`) → 화면전환과 i18n이 결합 | `App.js`, 각 컴포넌트 props | 새 백엔드는 `/api/v1/i18n/{windowId}` 별도 API. 결합 해제 필요 |
| 6 | 테스트 잔재: "화면이동 테스트용" 버튼(`App.js:97`), **하드코딩된 관리자 계정으로 로그인하는 버튼**(`Login.js:110-116`, `admin@webatt.com`/`adminadmin1!`이 소스에 노출), 미구현 signup(W003)/비밀번호 찾기 버튼 | | 운영 불가 수준의 잔재. 계정 정보 소스 노출 |
| 7 | `Location.href="./?winId=pwdSearch"` — `Location`은 전역 인터페이스 타입이라 대입 자체가 동작하지 않는 죽은 코드 | `Login.js:104` | 비밀번호 찾기 버튼 클릭시 무반응(콘솔 에러) |

### 권장 방향

window ID(W000~) 개념은 백엔드 v2에서 이미 제거됐다. 표준 URL 라우팅으로 전환한다:

```
/login          → Login          (공개)
/signup         → Signup         (공개, 새로 구현 — 백엔드 POST /api/v1/users 준비됨)
/attendance     → Attendance     (보호)
/attendance/details → Details    (보호)
/admin          → Admin          (관리자)
```

- 보호 라우트 가드: 앱 진입시 `GET /api/v1/auth/me` → 401이면 `/login`으로. axios 인터셉터(또는 fetch 래퍼)에서 401 공통 처리.
- 전환은 전부 라우터 네비게이션(`useNavigate`)으로. `window.location.replace` 제거.
- 로그인 후 관리자면 `/admin`, 아니면 `/attendance`로 네비게이트(백엔드 로그인 응답의 `admin` 필드 사용).

---

## 2. 새 백엔드 API와의 비호환 (전면)

구 계약(단일 `/api` + win_id/action + Map)이 완전히 바뀌었으므로 API 호출부는 전부 수정 대상이다.

| 현재 프론트 호출 | 새 API | 비고 |
|------------------|--------|------|
| `GET /api?win_id=W001` (로그인 화면 전개) | 불필요 | 화면 전개는 클라이언트 라우팅 |
| `POST /api {win_id:W001, user_email, user_pwd}` | `POST /api/v1/auth/login {email, password}` | 성공=200+유저정보, 실패=401 |
| 응답 `window` 비교로 세션 확인 | `GET /api/v1/auth/me` / 401 인터셉터 | |
| `GET /api?win_id=W002` (로그아웃) | `POST /api/v1/auth/logout` | |
| `GET /api?win_id=W005` → `datas.att_sts/att_time/att_msg` | `GET /api/v1/attendance/status` | `status`(enum)+`statusLabel`+`alert` 구조화 응답 |
| `POST /api {action:1, attendance_type:1..4}` (체크) | `POST /api/v1/attendance/check {type:"GO_TO_WORK"...}` | 타입이 숫자→enum 문자열. 응답은 `allowed`/`requiresConfirmation`/`code`/`token` |
| `POST /api {action:2, result, confirm_cd}` (확정) | `POST /api/v1/attendance {token, ...동일 데이터}` | `result` 해시키 → `token`(UUID). err_cd 1~4 분기 → `requiresConfirmation` 하나로 단순화 |
| `POST /api {action:3, years, months(0-based)}` | `GET /api/v1/attendance/monthly?year=&month=(1~12)` | **month가 0-based→1-based로 변경 주의** |
| 응답 `schedule[].fixScheduleIn` "0900" 문자열 | `days[].scheduleStart` "09:00" | `substr` 변환 코드 불필요. `date` 필드가 포함되어 프론트 달력 계산 자체가 사라짐 |
| (미구현이던 회원가입) | `POST /api/v1/users` | 이제 구현 가능 |
| 응답 `windows`/`headers` 다국어 | `GET /api/v1/i18n/{windowId}?lang=KOR` | 필요 화면에서 1회 취득(또는 react-i18next로 프론트 자체화 검토) |
| `latitude: JSON.stringify(숫자)` 문자열 전송 | `latitude: 숫자(Double)` | `Attendance.js:45-46` 문자열화 제거 |

에러 처리도 변경: 구 `{res:"E", msg:[...]}` → 신 HTTP 상태코드 + `{code, message, fieldErrors[]}`.

---

## 3. 버전/의존성 (2021년 고정 상태)

| 항목 | 현재 | 상태 | 권장 |
|------|------|------|------|
| react-scripts (CRA) | 4.0.3 | **CRA 자체가 공식 종료(2025 sunset).** Node 18+에서 `error:0308010C digital envelope` 로 빌드 실패 | **Vite** |
| React | 17.0.2 | 2개 메이저 뒤(현재 19) | React 19 |
| react-router-dom | 5.2.0 | v5는 유지 종료, API가 v6부터 전면 변경 | v7 |
| @material-ui/core | 4.x | 패키지명 자체가 `@mui/material`로 바뀜(v5부터). v4는 React 18+ 미지원 | MUI v6+ 또는 bootstrap 단독으로 단순화 |
| @mui/x-data-grid | 4.0.0 | @material-ui v4와 혼용 중 | Admin 재설계시 결정 |
| axios | 0.21.1 | **알려진 취약점(CVE-2021-3749 등)** | 1.x |
| moment / moment-timezone | 2.29 | 공식 유지보수 모드(신규 사용 비권장). 실제 사용처도 거의 없음 | 제거(Date/Intl 또는 dayjs) |
| @react-native-community/geolocation | 2.0.2 | **웹 프로젝트에 React Native 라이브러리 사용** | 표준 `navigator.geolocation` |
| express | 4.17.1 | 프론트 dependencies에 포함, 사용처 없음 | 제거 |
| react-spring, react-grid-layout, react-live-clock 등 | | 실험 흔적("사용법을 잘 모르니 테스트해보는거" 주석) | 필요분만 유지/제거 |

## 4. 코드 품질 이슈

- **비밀번호가 콘솔에 출력됨**: `Login.js:38` `console.log(values)` — user_pwd 평문 노출. 전체적으로 console.log 다수.
- **달력 수동 계산의 결함**: `Details.js:33,67-73` — `monthArray` 하드코딩 + 윤년을 `year%4==0`로만 판정(100/400년 규칙 무시), month 0-based 혼용. 새 API는 일자(`date`)를 내려주므로 이 로직은 통째로 삭제 가능.
- select의 문자열 value를 `==`/`parseInt`로 우회 비교(`selectMonth==1` 등) — 타입 불일치가 우연히 동작.
- `substr()` 사용(deprecated), `var` 사용, 미사용 import(`useRef`, `Router` 등), `useEffect` 의존성 경고 다수 예상.
- Admin.js는 더미 데이터 목업 상태(백엔드 관리자 API — i18n 관리 — 와 미연결).
- 응답 스키마에 있던 `remark`(비고) 필드를 표시하지만 새 API에는 없음 → 표시 여부 결정 필요.

## 5. 리뉴얼 권장안

작은 규모(화면 5개)이므로 기존 코드 위에서 의존성만 올리는 것보다 **신규 골격에 화면을 이식**하는 편이 빠르고 안전하다.

1. **골격**: Vite + React 19 (+ TypeScript 권장 — 백엔드 DTO가 record로 정형화되어 타입 매칭이 쉬움)
2. **라우팅**: react-router v7, 위 1장 권장 구조 + 보호 라우트 가드
3. **API 레이어**: `src/api/` 모듈로 분리(auth/attendance/i18n), axios 1.x + 401 인터셉터, 백엔드 `/v3/api-docs`에서 타입 생성(openapi-typescript) 검토
4. **UI**: 화면이 단순하므로 bootstrap 유지가 최소 비용. MUI 도입은 Admin(데이터 그리드) 구현 시점에 결정
5. **위치정보**: `navigator.geolocation.getCurrentPosition` 표준 API로 교체, 좌표는 숫자로 전송
6. **다국어**: 초기엔 `/api/v1/i18n` 호출 유지(백엔드 기능 활용), 장기적으로 react-i18next 병행 검토
7. **정리 대상**: 테스트용 버튼 2종, 하드코딩 관리자 계정, console.log 전부, moment/express/RN geolocation 의존성

### 이식 맵(구 → 신)

| 구 파일 | 신 구성 |
|---------|---------|
| `App.js` (windowChange/componentCall) | `App.tsx` + `router.tsx` (라우트 정의 + 가드) + `Header.tsx` |
| `WindowId.js` | 삭제 (URL 라우팅으로 대체) |
| `Login.js` | `pages/Login.tsx` (Formik+Yup 유지 가능, 새 auth API) |
| `Attendance.js` | `pages/Attendance.tsx` (status API + check→confirm 2단계, `requiresConfirmation` 분기) |
| `Details.js` | `pages/AttendanceDetails.tsx` (monthly API, 달력 계산 삭제) |
| `Admin.js` | `pages/Admin.tsx` (i18n 관리 API 연결로 재설계) |
| `IsNotLang.js` | i18n 헬퍼로 통합 |
| (없음) | `pages/Signup.tsx` 신규 (백엔드 준비 완료) |

## 6. 참고: 현 코드 빌드 가능 여부 (실측)

Node 22.22 환경에서 실제 실행한 결과:

- `npm install`: 성공(다수의 deprecated 경고)
- `npm run build`(react-scripts 4): **실패** — `ERR_OSSL_EVP_UNSUPPORTED` (`error:0308010C:digital envelope routines::unsupported`)

CRA 4가 사용하는 webpack 4의 OpenSSL MD4 해시가 Node 17+에서 제거되어 발생하는 문제.
`NODE_OPTIONS=--openssl-legacy-provider`로 임시 우회는 가능하지만, 이는 보안 레거시 모드를
켜는 미봉책이며 CRA 자체가 종료된 상태라 **빌드 체인 교체(Vite)가 사실상 필수**다.
