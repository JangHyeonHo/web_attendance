# 유연 근무 스케줄 설계 제안 (flexible-schedule-design)

> 대상: 멤버 근무 스케줄 관리를 4가지 실무 케이스(고정 근무 / 월 로타 / 요일별 시간 / 주 단위 로테이션)를
> 동시에 커버하도록 확장. **본 문서는 READ + DESIGN 산출물이며 프로덕션 코드는 변경하지 않는다.**
> 코드/테이블명은 영어, 서술은 한국어(레포 문서 관례 준수).

---

## 1. 현행 구조 요약 (as-is)

### 1-1. 데이터 모델

세 곳에 근무 스케줄 정보가 흩어져 있고, **일자별 해석(per-day resolution)** 시점에 합쳐진다.

| 저장소 | 컬럼/필드 | 의미 | 정의 위치 |
|---|---|---|---|
| `users.default_work_start` / `default_work_end` | `TIME NOT NULL DEFAULT '09:00'/'18:00'` | 개인 기본 시업/종업(고정 1쌍) | `V7__phase3.sql:52-56` |
| `users.work_days` | `CHAR(7) NOT NULL DEFAULT '1111100'` | 요일별 근무 on/off (월화수목금토일, '1'=근무) | `V12__phase5_manual_attendance.sql:17-19` |
| `work_schedule` | `(user_id, work_date)` UNIQUE, `start_time`/`end_time` NULL 허용, `holiday BOOLEAN` | **일자별 오버라이드**(하루짜리) | `V1__init.sql:50-61`, tenant_id는 `V4__multitenancy.sql:160-172` |

도메인 타입:
- `WorkSchedule` record: `(scheduleId, userId, workDate, startTime, endTime, holiday)` — `WorkSchedule.java:9-16`
- `WorkDefaults` record: `(start, end, workDays)` + `worksOn(workDays, dayOfWeek)` 헬퍼 — `WorkDefaults.java:10-19`
  - 결손 방어: `workDays`가 null이거나 길이≠7이면 **전 요일 근무로 해석**(집계가 기록을 버리지 않도록) — `WorkDefaults.java:13-18`

### 1-2. 일자별 해석 우선순위 (per-day resolution precedence)

`MonthlyAttendanceAssembler.assemble()` 가 하루 단위로 아래 순서를 적용 — `MonthlyAttendanceAssembler.java:89-101`:

1. **holiday 판정**: 공휴일 테이블 포함 `OR` 그 날짜 오버라이드의 `holiday()==true` (`:91`)
2. **dayOff 판정**: 휴일이 아니고 **오버라이드가 없고** `work_days` 요일 플래그가 '0' (`:93-94`)
   - 핵심: **일자 오버라이드가 있으면 요일 휴무를 무시하고 근무일로 승격**(오버라이드 우선, `:92` 주석)
3. **시업/종업 해석**(휴일·휴무면 null):
   - `start = 오버라이드 start_time(있으면) → 개인 default_work_start → 상수 09:00` (`:98-99`)
   - `end   = 오버라이드 end_time(있으면)   → 개인 default_work_end   → 상수 18:00` (`:100-101`)
   - **필드 단위 폴백**: 오버라이드가 존재해도 start만 있고 end가 null이면 end는 개인 기본값으로 채워짐.
4. **법정휴게/예정근무**: 근무일이면 `BreakPolicy.requiredBreak(end-start)`로 항상 산출(미퇴근이어도) — `:167-172`. 휴일·휴무는 실체류 기반(`:178-179`).

같은 우선순위가 "오늘 1일" 실시간 경로에도 중복 구현되어 있다 — `AttendanceService.resolveTodaySchedule()` `:392-416`.

데이터 로딩: `AttendanceService.monthly()` `:441-459`
- `scheduleMapper.findBetween(tenantId, userId, from, to)` → `Map<LocalDate, WorkSchedule>` (`ScheduleMapper.java:17-29`)
- `scheduleMapper.findWorkDefaults()` → `WorkDefaults` (`ScheduleMapper.java:35-40`)
- `BreakPolicy.of(country)` — 테넌트 소재국(KR/JP) 법정휴게 (`BreakPolicy.java:46-51`)

### 1-3. 쓰기 경로 (write path) — **중대한 갭**

- 멤버 스케줄 수정 = `MemberService.updateSchedule()` `:189-201` → `userMapper.updateWorkSchedule()`
  (`UserMapper.java:119-125`). **`users.default_work_start/end` + `work_days`만** 갱신한다.
- API: `PUT /.../{userId}/schedule`, body `MemberScheduleRequest(workStart, workEnd, workDays)` — `MemberController.java:95-98`, `MemberDtos.java:114-129`.
- 검증: `workDays`는 `^[01]{7}$` + 최소 1일 근무(`:196-198`); `workStart < workEnd` 강제 → **자정 넘김(overnight) 금지** — `MemberService.java:246-250` (주석: "교대제는 별도 Phase").
- **`work_schedule`(일자 오버라이드) 테이블에 INSERT하는 경로가 애플리케이션 어디에도 없다.** 전 소스 grep 결과
  `work_schedule`은 SELECT(읽기)와 스키마 정의에만 등장. 즉 **오버라이드 테이블은 읽히지만 채워지지 않는다**(설계상 존재만 하는 미사용 자산).

### 1-4. 프런트엔드 UX 한계

`MembersScreen.tsx` 스케줄 편집 모달(`:373-424`): 필드는 **시업 1개 + 종업 1개 + 요일 7토글**뿐.
- 요일은 on/off만(다른 요일에 다른 시간 불가) — `:393-407`
- 일자별 로타/월 단위 입력 UI 없음. 타입도 `MemberScheduleUpdateRequest{workStart, workEnd, workDays}`로 동일(`types.ts:388-393`).

### 1-5. 오늘 표현 가능/불가능한 것

**표현 가능**
- 전 근무일 동일한 고정 시업/종업 1쌍 (예: 09:00–18:00)
- 요일별 **근무/휴무 on/off** (예: 토·일 휴무, 주6일 등)
- (이론상) 하루짜리 일자 오버라이드 — **단, 이를 만드는 UI/API가 없어 실사용 불가**

**표현 불가능**
- 요일마다 **다른 시업/종업 시각**(월수금 09–18, 화목 10–19) — 개인 기본은 시각 1쌍뿐
- 월 단위 로타(사람마다 매월 날짜별 다른 시간·휴무)를 **입력하는 수단**
- 주 단위/N주 주기 로테이션(격주 패턴, 교대 순환)
- 재사용 가능한 **교대 템플릿(shift template)** 개념
- 자정 넘김(overnight) 교대 근무(시업>종업)

---

## 2. 요구 4케이스 매핑

| # | 케이스 | 현행 표현 가능? | 어디서 깨지나 |
|---|---|---|---|
| 1 | **고정 일일 시업/종업** (다수) | ✅ 완전 지원 | 없음. `default_work_start/end` + `work_days`로 그대로. 이 경로는 **반드시 단순 유지**. |
| 2 | **월 로타/로스터** (호텔형, 매월 사람별 날짜 지정) | ⚠️ 스키마는 있으나 **입력 불가** | `work_schedule`가 일자별 start/end/holiday를 담을 수 있으나 INSERT 경로·벌크 입력 UI 부재(§1-3). 실질 미지원. |
| 3 | **요일별 시간 차이** (월수금 09–18 / 화목 10–19) | ❌ 불가 | 개인 기본은 시각 **1쌍**. `work_days`는 on/off 비트뿐 시간 정보 없음(`WorkDefaults.java:10`). |
| 4 | **주 단위 / N주 주기 로테이션** | ❌ 불가 | 주기(cycle)·주차(week index) 개념 자체가 데이터 모델에 없음. |

핵심 결론: **케이스 2는 저장 스키마 갱신 없이도 "쓰기 경로만" 만들면 상당 부분 열린다.**
케이스 3·4는 **새 데이터 축(요일→시간 매핑, 주기)** 이 필요하다.

---

## 3. 설계 방향 후보 (3안)

설계 불변식(모든 안 공통):
- **고정 스케줄 다수 경로는 절대 복잡해지지 않는다** — 기존 `default_work_start/end`+`work_days`가 계속 "기본값"으로 남고, 신규 구조는 전부 opt-in.
- **per-day resolver(`MonthlyAttendanceAssembler`)의 출력 계약(하루 = start/end/holiday/dayOff)은 불변.** 새 구조는
  "그 날짜의 `WorkSchedule` 1건을 만들어내는" **상류(upstream) 해석 단계**로 흡수한다 → 야근 25:10·휴게·집계 로직 전부 무변경.
- **back-compat**: 기존 테넌트/멤버는 신규 테이블에 행이 0건 → resolver가 예전과 정확히 동일하게 동작(zero disruption).

### 안 A — 일자 오버라이드 확장 + 월 로타 벌크 에디터

가장 얇은 안. **새 테이블 없음**. 이미 존재하는 `work_schedule`에 **쓰기 경로**를 만든다.

- 데이터: `work_schedule` 그대로 활용(필요 시 `note VARCHAR` 정도만 추가).
  ```sql
  -- (선택) 로타 셀 메모/템플릿 출처 추적용, 필수 아님
  ALTER TABLE work_schedule ADD COLUMN source VARCHAR(16) NULL COMMENT 'MANUAL|ROTA';
  ```
- API: 벌크 upsert
  ```
  PUT /tenant/members/{userId}/rota?month=YYYY-MM
  body: [{ workDate, startTime|null, endTime|null, holiday }...]  // 그 달 셀 전체 대체
  ```
  - 매퍼: `INSERT ... ON DUPLICATE KEY UPDATE`(UNIQUE(user_id, work_date) 활용) + 그 달 범위 밖 행 보존.
- resolver 소비: **무변경**. `findBetween`이 이미 오버라이드를 읽어 `:98-101`에서 반영.
- BreakPolicy: 무변경(오버라이드 start/end 구간으로 자동 산출).
- UI: 멤버별 **월 달력 그리드**(날짜 셀에 시업/종업/휴무). 고정 스케줄 멤버는 이 화면을 열지 않아도 됨.
- 커버: 케이스 1(유지)·2(✅ 정면 해결). 케이스 3은 "요일 대량 채우기" 헬퍼로 흉내 가능하나 **패턴이 아니라 셀 나열**이라 매월 재입력 부담. 케이스 4는 사실상 미해결.

**장점**: 최소 변경, 스키마 안정, 위험 극소. **단점**: 케이스 3·4를 "반복 패턴"으로 표현 못 함(매 기간 수기 입력).

### 안 B — 재사용 교대 템플릿 + 패턴/로테이션 배정

정공법. **shift template + assignment(요일맵 / N주 주기)** 도입.

```sql
-- 재사용 교대 정의 (예: 'DAY' 09-18, 'EVE' 14-23, 'OFF')
CREATE TABLE shift_template (
    shift_id     BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT NOT NULL,
    code         VARCHAR(32) NOT NULL,          -- 화면 표시/식별
    start_time   TIME NULL,                     -- NULL+is_off=1 => 휴무 교대
    end_time     TIME NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,  -- overnight 지원(§4 위험)
    is_off       BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (shift_id),
    UNIQUE KEY uk_shift_tenant_code (tenant_id, code)
);

-- 멤버에게 붙는 패턴 배정(기간 + 주기)
CREATE TABLE schedule_assignment (
    assignment_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    effective_from DATE NOT NULL,               -- 이 날부터 유효
    effective_to   DATE NULL,                   -- NULL=무기한
    cycle_weeks    TINYINT NOT NULL DEFAULT 1,  -- 1=매주 동일(요일맵), N=N주 주기
    anchor_monday  DATE NOT NULL,               -- 주기 0주차의 기준 월요일(주차 계산 원점)
    PRIMARY KEY (assignment_id),
    KEY idx_assign_user_from (tenant_id, user_id, effective_from)
);

-- (주차 index, 요일) -> 교대. cycle_weeks * 7 개의 셀
CREATE TABLE schedule_assignment_slot (
    assignment_id BIGINT NOT NULL,
    week_index    TINYINT NOT NULL,             -- 0..cycle_weeks-1
    day_of_week   TINYINT NOT NULL,             -- 1..7 (월..일)
    shift_id      BIGINT NOT NULL,
    PRIMARY KEY (assignment_id, week_index, day_of_week),
    CONSTRAINT fk_slot_assign FOREIGN KEY (assignment_id) REFERENCES schedule_assignment(assignment_id)
);
```

- 그 날짜 해석: `weekIndex = floor(daysBetween(anchor_monday, day)/7) % cycle_weeks`, `dow = day.getDayOfWeek()` →
  slot → `shift_template` → `(start,end,is_off)`. **이 결과를 기존 `WorkSchedule` 1건으로 투영**한다.
- resolver 소비: 우선순위를 **한 단계 상류에서 병합**하는 어댑터를 둔다:
  `work_schedule 일자 오버라이드 > assignment(패턴) > 개인 default > 상수`.
  구현은 `AttendanceService.monthly()` `:441-459`에서 `schedules` Map을 만들 때, 오버라이드가 없는 날짜를
  assignment 해석 결과 `WorkSchedule`로 채워 넣는 것으로 충분 → **Assembler 자체는 무변경**.
- BreakPolicy: 무변경(투영된 start/end로 산출). overnight는 §4 참조.
- API: `shift_template` CRUD + `PUT /members/{userId}/assignment`(요일맵/주기 그리드).
- UI: (1) 테넌트 교대 마스터 관리, (2) 멤버에 패턴 배정(1주 요일맵 = 케이스 3, N주 그리드 = 케이스 4).
- 커버: 케이스 1(유지, 배정 없으면 기본값)·2(월 로타는 여전히 일자 오버라이드로, 안 A 병행)·3(✅ 요일맵)·4(✅ 주기).

**장점**: 4케이스 전부 "패턴"으로 표현·재사용, 매월 재입력 불필요, overnight 정식 지원 지점 확보.
**단점**: 테이블 3개·해석 로직·UI 3화면 → 구현·QA 비용 큼. 우선순위 병합 어댑터가 새 복잡도.

### 안 C — 하이브리드(권장 골격)

**안 A를 Phase 1로, 안 B를 Phase 2+로** 단계 결합. 두 축을 공존시킨다:
- **패턴 축**(안 B) = 반복되는 정규 스케줄(요일별 시간·주기 로테이션)의 "기본 그림".
- **일자 오버라이드 축**(안 A, 기존 `work_schedule`) = 그 위에 찍는 예외(특정일 대타·연장·개인휴가).
- 최종 우선순위(불변):
  `공휴일/개인휴일 > work_schedule 일자 오버라이드 > schedule_assignment 패턴 > users 개인 기본값 > 상수`.

이는 기존 `:97` 우선순위 주석의 자연스러운 확장이며, 실무(호텔 로타 + 급 대타)를 모두 담는다.

---

## 4. 추천안 + 근거 + 단계 롤아웃

### 추천: **안 C(하이브리드)**, 단 반드시 단계적으로.

근거(복잡도 대 커버리지 trade-off):
- 케이스 2(월 로타)는 **오늘 가장 가시적인 결핍이자 스키마가 이미 준비된 곳**이라, 쓰기 경로만으로 최대 효과(안 A).
- 케이스 3·4는 "반복 패턴"이 본질이므로 셀 나열(안 A)로는 지속 불가 → 결국 안 B가 필요. 그러나 이를 1차에
  같이 넣으면 위험(§4 위험목록)이 커진다. → **분리**.
- 안 C는 resolver 계약을 건드리지 않고(상류 병합) 확장하므로 야근 25:10·휴게·집계 회귀 위험이 낮다.

### Phase 1 (최소 변경으로 로타 + 요일별 시간 일부 확보)
목표: **케이스 2 정면 해결 + 케이스 3의 실무 needs 즉시 완화**, 새 테이블 최소.
1. `work_schedule` **벌크 upsert 쓰기 경로** 신설(안 A). 매퍼 `ON DUPLICATE KEY UPDATE`.
2. 멤버 **월 달력 로타 에디터** UI. "요일 일괄 채우기"(월수금=09-18, 화목=10-19를 한 달치 셀로 펼치는 헬퍼)로
   케이스 3을 **입력 편의**로 커버(저장은 일자 오버라이드).
3. 기존 고정 경로·`updateSchedule`·resolver·Assembler **전부 무변경**. 신규 테넌트/멤버는 로타 미사용 시 예전과 동일.
- 산출: 케이스 1 ✅, 2 ✅, 3 ⚠️(패턴 아닌 입력 편의로 충족), 4 ❌(Phase 2).

### Phase 2 (요일별 시간 = 재사용 패턴)
`shift_template` + `schedule_assignment`(cycle_weeks=1 = 요일맵) 도입. 상류 병합 어댑터.
- 케이스 3을 **재사용 패턴**으로 승격(매월 재입력 제거).

### Phase 3 (주 단위/N주 주기 로테이션)
`cycle_weeks=N` + `anchor_monday` 활성화, N주 그리드 UI. overnight(`crosses_midnight`) 정식 지원.
- 케이스 4 ✅.

### 위험(risks) — 반드시 사전 검토
1. **overtime/야근 25:10 표시**: `MonthlyAttendanceAssembler.java:154-158`은 출근일 기준 일수차×24로 표기.
   overnight 교대(시업>종업, `crosses_midnight`)를 도입하면 "예정 근무 구간"의 end가 다음날이 되어
   `Duration.between(start,end)`가 음수/오해석될 수 있음 → 예정근무·법정휴게 산출(`:167-172`)이 깨진다.
   **Phase 3 전까지 overnight는 저장·해석 모두 금지 유지**(현행 `validateWorkRange` `:246-250`와 정합).
2. **휴게/예정근무 상호작용**: 법정휴게는 "스케줄 구간 길이" 입력(`BreakPolicy.java:9`). 패턴 투영 결과가
   그대로 이 입력이 되므로, 투영 시 start/end가 항상 유효(start<end, non-null)해야 함.
3. **기존 데이터 마이그레이션**: 신규 테이블 전부 **추가만**(ALTER 없음, `work_schedule`은 컬럼 추가 optional).
   기존 행 0건 → resolver 동일 동작. 마이그레이션 리스크 최소.
4. **일자 오버라이드가 요일 휴무를 승격시키는 규칙**(`:92-94`): 패턴이 "휴무 교대(is_off)"를 만들 때
   이를 `work_schedule.holiday`로 투영할지, 아니면 dayOff로 투영할지 **의미 충돌** 주의(§5 Q3).
5. **동시성/UNIQUE**: `work_schedule`의 `uk_work_schedule_user_date`(V1:59)와 패턴 투영이 같은 날짜를 노릴 때
   투영은 DB에 쓰지 않고 **메모리 병합**만 하도록 해 충돌 회피(오버라이드만 실제 행).
6. **timezone**: 전 계산이 `LocalDate`/`LocalTime`(테넌트 소재국 가정). 다국 테넌트 확장 시 별도 과제(현행도 동일).

---

## 5. 오픈 questions (구현 전 오너 확정 필요)

1. **케이스 3 우선순위**: 요일별 시간을 Phase 1에서 "로타 입력 편의"로 임시 충족해도 되는가, 아니면
   처음부터 재사용 패턴(Phase 2)이 필수인가? (1차 범위 결정에 직결)
2. **월 로타 편집 단위**: 관리자가 사람별로 월 그리드를 채우는가, 아니면 부서/팀 단위 로타 보드(여러 명 한 화면)가
   필요한가? 후자면 UI 비용이 크게 다르다.
3. **휴무의 표현**: 패턴/로타의 "휴무일"을 개인휴일(`work_schedule.holiday=true`)로 볼 것인가,
   단순 요일 휴무(dayOff)로 볼 것인가? 집계·휴게 산출(`:167`, `:178`)과 UI 색상에 영향.
4. **overnight/교대 근무 시급성**: 야간 교대(예: 22:00–06:00)가 실제 고객 요구에 포함되는가?
   포함이면 Phase 3(25:10 로직 재검토)을 앞당겨야 하며, 아니면 계속 금지.
5. **주기 원점(anchor)**: N주 로테이션의 "1주차" 기준을 배정마다 지정(`anchor_monday`)할지, 테넌트 공통(ISO week)로 할지.
6. **로타 확정/잠금**: 지난 달 로타를 나중에 수정하면 과거 집계·정정 기록과 어긋날 수 있다. 로타 마감(lock) 정책 필요 여부.
7. **기존 `work_schedule` 오버라이드**: 지금은 비어 있으나, 향후 패턴과 공존 시 "오버라이드가 항상 이긴다"(§3-C 우선순위)로 확정해도 되는가?
