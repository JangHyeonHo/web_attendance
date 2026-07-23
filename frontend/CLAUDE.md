# CLAUDE.md — 프론트엔드

멀티테넌트(SaaS) 웹 근태관리 시스템의 **프론트엔드**. 현재는 백엔드(Spring Boot, `:9080`)와 모노레포 동거 중이나 별도 레포 분리 예정 — 이 문서는 프론트만으로 자립하도록 쓴다. 백엔드 규약은 루트 `CLAUDE.md` 참조.

## 스택

- React 19 + TypeScript 5.8 + Vite 6
- **라우터/상태 라이브러리 없음** — 의존성은 react/react-dom/pretendard뿐. 화면 전환은 서버 주도(아래), 상태는 컴포넌트 로컬 + `AppContext`

## 빌드·실행

```bash
npm install
npm run dev      # :5173 — /api → localhost:9080 프록시
npm run build    # tsc -b && vite build (타입 체크 겸용 — 검증은 항상 이걸로)
```

- vite 프록시는 `changeOrigin: false` **고정** — 백엔드가 테넌트를 서브도메인(Host 헤더)으로 해석하므로 Host 보존 필수. 레포 분리 후 프록시/게이트웨이를 새로 짜도 이 조건은 유지

## 백엔드 계약(API)

- 인증은 **세션 쿠키**(1일 슬라이딩, 단일 세션 — 다른 곳에서 로그인하면 기존 세션 회수). 토큰 저장/갱신 로직 없음
- 모든 요청에 `X-Requested-With: XMLHttpRequest` 필수(CSRF) — `api/client.ts`가 항상 부여하므로 **fetch를 직접 쓰지 말고 client 경유**
- API 정의는 `api/endpoints.ts`, 타입은 `api/types.ts`에 집약. 에러는 `ApiError`(서버 메시지·fieldErrors 포함)
- 목록 페이지네이션은 `PageResponse<T>`(items/page/size/totalCount/totalPages, 기본 20건·상한 100)

## 서버 주도 내비게이션·i18n

- **URL 라우팅 없음**: `POST /api/v1/navigation` 응답의 화면 코드로만 화면 결정(`app/AppContext.tsx`), 화면 코드↔컴포넌트 매핑은 `App.tsx`
- 화면 코드 정본은 **백엔드** `Screen` enum: `M###` 멤버 본인 업무 / `T###` 테넌트 관리 / `A###` 운영사 / `W###` 공통. 프론트 `api/types.ts`의 코드 목록은 그 사본 — 새 화면은 백엔드에서 코드 발급 후 반영
- **프론트는 텍스트 사전을 갖지 않는다**: 라벨은 전부 서버 언어 마스터에서 수신(`i18n/lang.ts`의 `makeT` — 현재 화면 코드 + `W999` 공통). 미등록 키는 키 이름 그대로 표시되므로 화면에서 바로 드러남. **라벨 추가/수정은 백엔드 Flyway 마이그레이션 작업**(레포 분리 후에도 동일)
- 시각·요일 등 Intl 표기는 `localeOf(lang)`(ko/en/ja)

## 구조·컴포넌트 카탈로그

- `src/{api, app, components, hooks, i18n, screens, util}` — 화면은 `screens/`
- **신규 화면은 `components/` 카탈로그 부품 조립으로** — 생 `<button>`·일회성 CSS 금지(스킨 통일은 문맥 CSS, 구조 반복은 컴포넌트). 카탈로그 인덱스는 `components/README.md`, 각 부품의 상세 규칙 정본은 해당 컴포넌트 JSDoc. 부품 추가/변경 시 README 표도 갱신
- 주요 부품: Button / IconButton(행 액션+툴팁) / Modal / ConfirmModal(**결과 안내 hint 필수**, 확인 버튼은 행위 라벨) / fields(TextField 등 — 라벨 필수) / DateField·TimeField(네이티브 date/time input 금지) / SectionHead / EmptyState / Pagination(무한 증가 목록 필수)

## 화면 공통 규칙(요약 — 원문은 components/README.md와 각 JSDoc)

- 입력엔 라벨 필수(1줄 고정), 폼 전체 안내는 제출 버튼 직전, 섹션 안내는 제목 아래 한 줄(행마다 반복 금지)
- 확인 모달은 결과 안내 한 줄 필수 — 문구는 백엔드 동작 확인 후 작성(추측 금지)
- 테이블은 `.detail-table`(행 버튼 자동 컴팩트), 행 액션은 IconButton
- 표시할 내용 없는 컨테이너는 렌더링 자체를 생략(빈 껍데기 금지)

## 주의점

- 주석 언어는 한국어. 규약·의도가 주석에 촘촘히 적혀 있으니 수정 전 해당 파일 JSDoc부터 읽을 것
- 전역 CSS(`app.css`)는 클래스 누수 주의 — 범용 이름의 전역 클래스 신설 금지(과거 `.today` 충돌 사례), 화면 한정 스타일은 화면 프리픽스로
