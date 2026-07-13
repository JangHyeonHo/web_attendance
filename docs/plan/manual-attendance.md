# Phase 5 — 수동 출결 정정 + 조작 이력 노출 + 주말·공휴일 근무 처리

## 1. 요구 → 결정 요약

| 요구 | 결정 |
|------|------|
| 수동 출결(정정)에 이유 필수, 일부 선택 + 직접 입력 | 사유 코드 4종(자주 있는 "찍는 것을 잊음" 선두) + 자유 텍스트. OTHER는 텍스트 필수 |
| 수동입력과 버튼(자동)입력 구분 | `attendance.source` 컬럼(AUTO/MANUAL) — 등록 경로가 다르면 데이터도 다르게 남는다 |
| 출결 조작이 이력으로 남아야(출근 2번 등) | **attendance는 원래 append-only** — 중복 스탬프도 전부 행으로 존재. 부족한 건 노출이므로 일자 상세 API(`GET /attendance/daily`)로 그 날의 전 스탬프(수동/자동·사유 포함)를 보여준다 |
| 조회에서 사유 표시(테이블 좁음) | 월별 테이블에는 "수동" 마커만, **행 클릭 → 일자 상세 모달**에서 사유·전체 이력 표시 |
| 공휴일에도 출퇴근 가능 | 상태머신(evaluate)은 원래 날짜 무관 — 이미 가능. **집계(어셈블러)가 휴일 스탬프를 버리던 것을 채용하도록 수정** |
| 토·일 출근 유무는 멤버별 설정(관리자) | `users.work_days` CHAR(7) 월~일 플래그(기본 1111100) — W009 스케줄 모달에서 TA가 설정 |

## 2. 데이터 (V12)

- `attendance` ALTER: `source VARCHAR(6) NOT NULL DEFAULT 'AUTO'`, `reason_code VARCHAR(10) NULL`, `reason_text VARCHAR(200) NULL` (기존 행 = AUTO)
- `users` ALTER: `work_days CHAR(7) NOT NULL DEFAULT '1111100'` (월화수목금토일, '1'=근무)
- 언어 마스터 시드: W006(일자 상세·정정 등록·사유 4종·수동/자동·휴무·타입 라벨), W009(근무 요일)

## 3. API

### POST /api/v1/attendance/manual — 수동 정정 등록(본인)
- `{date, time, type, reasonCode, reasonText?}`
- type ∈ GO_TO_WORK/OFF_WORK/EARLY_DEPARTURE — **BREAK 제외**(시작/종료 페어링 정합성은 후속. 문서화)
- 검증: 미래 불가 / 오늘−90일 이내 / OTHER는 reasonText 필수(≤200)
- 상태머신 검사 없음(정정 목적) — append-only라 원래 값도 행으로 남는다. 채용 규칙(마지막 값 우선)은 기존 어셈블러 규칙 그대로
- 좌표/단말 없음(terminal='manual') — 위치는 자동 스탬프의 무결성 장치이지 정정의 입력이 아님

### GET /api/v1/attendance/daily?date= — 일자 스탬프 이력(본인)
- 그 달력 날짜의 전 스탬프 오름차순: `{stampedAt, type, breakEnd, source, reasonCode, reasonText}`
- 거부된 체크(allowed=false)는 확정 전 단계이므로 기록·노출 대상이 아니다(계약 명시)

### PUT /api/v1/tenant/members/{id}/schedule — workDays 추가
- `{workStart, workEnd, workDays}` — workDays는 `[01]{7}`, 최소 1일은 '1'
- MemberResponse에 workDays 동봉

## 4. 집계(어셈블러) 변경

- 판정 순서: 공휴일/개인휴일(기존) > **요일 휴무(dayOff: work_days 플래그 0 && 일자 오버라이드 없음)** > 근무일
- **공휴일·휴무 날에도 스탬프 채용**(기존은 공란 스킵): 스케줄 표시 null, 법정휴게는 스케줄이 없으므로 **실체류 기반** `policy.requiredBreak(체류)`, 총근무 = max(0, 체류 − max(법정, 실휴식)) — 월 합계 포함
- `DailyAttendance` 추가 필드: `dayOff`(요일 휴무), `manual`(그 날 MANUAL 스탬프 존재)
- 오늘 스케줄(W005 표시): 휴무일도 공휴일과 동일하게 null

## 5. 화면

- **W006**: 행 클릭 → 일자 상세 모달(스탬프 이력: 시각·구분·수동/자동 뱃지·사유 / 공휴일명·휴무 라벨 / [정정 등록] 버튼). 툴바에도 [정정 등록]. 수동 스탬프가 있는 날은 날짜 셀에 "수동" 마커. 휴일·휴무 행은 스탬프가 있으면 시각·근무시간 표시(없으면 기존 통합 셀)
- **정정 등록 모달**: 날짜(기본 오늘 또는 클릭한 날)·시각·구분(출근/퇴근/조퇴)·사유 select(잊음/단말·통신/외근·출장/기타)·직접 입력 텍스트(기타 필수)
- **W009 스케줄 모달**: 요일 체크박스 7개(Intl 요일명 — 언어 마스터 불요) 추가

## 6. 비범위(후속 후보)

- BREAK 수동 정정, 관리자 승인 워크플로, 관리자에 의한 타인 정정, 거부된 체크 시도 로깅
