# 컴포넌트 카탈로그

신규 화면은 **이 디렉터리의 부품 조립**으로 구현한다. 화면 코드에서 생 `<button>`·일회성 CSS를 새로 만들지 않는다
(스킨 통일은 문맥 CSS가, 구조 반복은 컴포넌트가 맡는다).

이 문서는 **인덱스**다 — 각 부품의 상세 사용 규칙 원문은 해당 컴포넌트 파일의 JSDoc에 있다(그쪽이 정본).
부품을 추가/변경하면 이 표도 함께 갱신한다.

## 공용 UI 부품

| 컴포넌트 | 용도 | 언제 쓰나 |
|---|---|---|
| `Button` | 공용 버튼(variant: primary/default/ghost/danger, size: md/sm) | 폼·페이지 액션. 테이블 안 버튼은 문맥 CSS로 자동 컴팩트(28px)되므로 별도 처리 불요 |
| `IconButton` | 행 액션 아이콘 버튼 + 즉시 툴팁(edit/delete/manage/schedule) | 행 단위 반복 조작 전부. label 필수. **결재성 액션(승인/반려)은 라벨 버튼 유지** |
| `Modal` | 모달 껍데기(제목/닫기/danger) | 입력이 있는 모달(사유 등)의 기본 틀 |
| `ConfirmModal` | 확인 모달 공통 구조(대상+안내+확인/취소) | 결과 확인만 받는 모달. **결과 안내(hint) 필수 — 대상만 덩그러니 금지**, 확인 버튼은 행위 라벨(SUBMIT 재사용 금지) |
| `fields` — `TextField` `TextAreaField` `SelectField` `ModalSubject` | 폼 필드·모달 대상 표시 | 모든 입력엔 라벨 필수(1줄 고정). 서술형 입력은 TextArea. 모달의 대상 표시는 ModalSubject |
| `DateField` / `TimeField` | 날짜·시각 선택(커스텀 팝오버) | 네이티브 date/time input 대신 항상 이것 |
| `SectionHead` | 섹션 제목 + 안내문 슬롯 | 섹션 공통 안내는 제목 아래 hint 한 줄(행 반복 금지). 연속 섹션 2번째부터 `spaced`(인라인 margin 금지) |
| `ScreenGuide` | 관리자 화면 상단 가이드(비개발자용 화면 소개) | 관리자(T/A) 화면 제목 아래 1개. 문구는 언어 마스터 `SCREEN_GUIDE` 키 — 실제 동작 확인 후 작성(추측 금지) |
| `EmptyState` | 빈 목록 표시 | 빈 결과 전용(로딩·잠금 안내는 대상 외). 내용 없는 컨테이너는 렌더링 자체를 생략 |
| `Pagination` | 페이지 번호 방식 목록 이동 | 무한 증가 리스트는 필수(기본 20건/상한 100 — `PageResponse` 계약). 자연 상한 리스트는 근거를 주석으로 |
| `PasswordInput` | 비밀번호 입력(표시 토글) | 비밀번호 입력 전부 |
| `Popover` | 앵커 기준 팝업 배치 | DateField/TimeField류의 내부 기반 |
| `BottomSheet` / `BottomNav` | 모바일 시트·하단 탭 | 모바일 전용 내비/패널 |

## 도메인 컴포넌트(특정 업무 전용)

| 컴포넌트 | 용도 |
|---|---|
| `ScheduleEditor` | 통합 근무 스케줄(정기 패턴+월 달력) 편집 |
| `TimesheetPrintReport` | 근태 보고서 인쇄(PDF) 양식 |
| `InvoiceDocument` | 청구서 문서 양식 |
| `MailPreview` / `MailVarsTable` | 메일 템플릿 미리보기·변수표 |

## 화면 공통 규칙(요약 — 원문은 각 JSDoc·app.css 주석)

- **라벨**: 입력엔 라벨 필수, 라벨은 1줄 고정(`.field-label` nowrap — 임의 개행 금지)
- **안내문 위치**: 폼 전체 안내=제출 버튼 직전 / 섹션 안내=제목 아래 한 줄 / 행마다 반복 금지
- **확인 모달**: 결과 안내 한 줄 필수, 문구는 백엔드 동작 확인 후 작성(추측 금지). 모달 안내문은 13px+균형 개행(`.modal .hint.center`)
- **테이블**: `.detail-table` 사용, 행 버튼은 자동 컴팩트, 행 액션은 IconButton, 무한 증가 목록은 Pagination
- **빈 껍데기 금지**: 표시할 내용이 없으면 컨테이너를 그리지 않는다
- **화면 코드**: 역할 접두사 + 일련번호(M=멤버, T=테넌트 관리, A=운영사, W=공통) — 정본은 백엔드 `Screen` enum
