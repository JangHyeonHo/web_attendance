# Phase 4 — 디자인 업그레이드 계획 (모바일 최적화 + 사계절 테마 + 등록 모달화 + 테이블 통일)

## 1. 목표

| # | 요구 | 구현 방향 |
|---|------|-----------|
| 1 | 모바일 최적화(출결은 모바일로 찍는다) | 모바일 퍼스트 미디어쿼리, 출결 화면 대형 터치 버튼(2×2 그리드), 반응형 헤더 |
| 2 | 봄/여름/가을/겨울 테마 + SYSTEM_ADMIN이 설정 | `app_setting` 테이블(V11) + `/api/v1/admin/ui-theme` + navigation 응답에 확정 테마 동봉 |
| 3 | 최신 디자인 | CSS 디자인 토큰 전면 재작성(카드·그림자·라운드·포커스 링·타이포) — 외부 리소스 없이 |
| 4 | 등록 버튼 → 별도 모달 | 재사용 `Modal` 컴포넌트(PC=중앙 다이얼로그 / 모바일=바텀시트), 등록·확인 패널 이전 |
| 5 | 테이블 사이즈 들쭉날쭉 해소 | 공통 `.table-wrap`(가로 스크롤) + 통일된 행 높이·패딩·헤더 스타일 |

## 2. 테마 설계

- 설정값: `AUTO | SPRING | SUMMER | AUTUMN | WINTER` (기본 AUTO — 서버 날짜 기준 3–5월=봄, 6–8월=여름, 9–11월=가을, 12–2월=겨울)
- 저장: `app_setting('UI_THEME')` — 시스템 전역(테넌트별 아님. 요구가 "시스템 관리자가 설정").
- 전달: `NavigateResponse.theme`(확정값, AUTO는 서버가 해석) — 공개 화면(랜딩/로그인)도 테마 적용.
- SA 화면: W004(관리)에서 라디오 카드로 선택, `GET/PUT /api/v1/admin/ui-theme` (RoleInterceptor `/api/v1/admin/**` = SYSTEM_ADMIN 기존 규칙에 편승).
- 프론트: `document.documentElement.dataset.theme` 반영 → CSS `[data-theme=...]` 토큰 오버라이드.

## 3. 프론트 구조 변경

- `components/Modal.tsx` 신설: `<Modal title onClose>` — PC 중앙(최대 520px)/모바일 바텀시트, ESC·배경 클릭 닫기, `role="dialog"`.
- 등록 폼 모달 이전: W009 멤버 초대, W007 테넌트 생성, W013 회사 공휴일 등록, W004 언어 마스터 등록.
  목록 상단은 `.toolbar`(제목 + [등록] 버튼)로 통일.
- 파괴적 확인·발송 재확인 패널(`stamp-box confirm`)도 Modal로 이전(모바일에서 테이블 내 확장 행이 깨지는 문제 해소).
  행 아래 인라인 에러 표시는 유지.
- 테이블: `detail-table` → 스타일 재작성 + 각 화면에서 `.table-wrap`으로 감싼다.
- 출결(W005): 스탬프 버튼 4개를 대형 그리드로, 시계·상태 카드화.

## 4. 검증

- 단위: `UiThemeTest`(AUTO 월 경계), NavigationServiceTest(생성자 변경 반영), `mvn test` 그린.
- 통합: 실기동 후 SA로 PUT 테마 → navigation 응답 theme 변화 확인(curl).
- UI: Playwright로 데스크톱(1280)/모바일(390) 뷰포트 × 4테마 스크린샷 육안 확인 + 주요 플로우(로그인→출결 스탬프→모달 등록) 동작 확인.

## 5. 비범위

- 다크 모드, 테넌트별 테마, 사용자별 테마(후속 후보).
- URL 라우팅 도입 없음 — 서버 주도 화면 전개 유지.
