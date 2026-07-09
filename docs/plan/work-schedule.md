# 근무 스케줄·법정 휴게 상세 설계 — 개인 기본 스케줄 + 국가별 휴게 자동 차감 (Phase 3)

- 상위 문서: [plan-saas-multitenancy.md](../plan-saas-multitenancy.md) (마스터), 결정 기록: [patch-notes-2026-07-saas.md](../patch-notes-2026-07-saas.md) (D10~D20)
- 범위: ① 멤버별 **개인 기본 근무 스케줄**(시업·종업) 등록/수정 ② 테넌트 소재국 기준 **법정 휴게 자동 부여**와
  실휴식(BREAK 스탬프) 초과분 차감 ③ 월별 상세(W006)/출결(W005) 화면 표시. **문서만 — 구현/커밋 없음.**
- 확정 전제(소유자 확정):
  - 법정 휴게 국가 기준은 **테넌트 소재국**. 별도 계획(공휴일 도메인)에서 `tenant.country`로 승격됨을 전제로 하고,
    본 계획은 그 컬럼을 **읽기만** 한다(§2-4).
  - 정본 공식: **총 근무시간 = 체류시간 − max(법정 휴게시간, 실휴식 합계)**. 휴식 미기록이어도 법정 휴게는
    차감한다(법이 보장하는 시간 — 기록 여부와 무관하게 근로가 아니었다고 본다).
  - 개인 기본 스케줄은 `users.default_work_start/default_work_end`(TIME, 기본 09:00/18:00).
    일자별 오버라이드는 기존 `work_schedule` 유지 — **우선순위: work_schedule > 개인 기본값**.
  - 마이그레이션은 **V7 단일 파일 합류**(이메일 온보딩·공휴일 도메인과 공유). 본 문서는 자기 몫 DDL만 기술(§3-2).
  - 신규 화면 코드 W010~W012는 이메일 온보딩 계획이 선점. 스케줄은 **기존 W009 확장으로 해결, 신규 화면 없음**
    (필요해지면 W013부터 — §7-1).

표기: backend-api.md와 동일하게 실제 컨벤션(record DTO, MyBatis 어노테이션 매퍼, `ApiException` 메시지 키,
Swagger 설명은 메시지 키)을 따르는 구현 지시 수준의 설계다.

---

## 1. 용어·모델 정의와 계산 정본

### 1-1. 용어 (전 문서·코드 주석에서 이 명칭만 사용)

| 용어 | 정의 | 산출원 |
|------|------|--------|
| **체류시간** | 그날 채용된 출근 스탬프 시각 ~ 퇴근(조퇴 포함) 스탬프 시각. 야근이면 익일 실시각까지(표기만 24+시) | attendance 스탬프 (기존 페어링 규칙 §6-1 그대로) |
| **실휴식** | 체류 구간 안에서 BREAK 시작→종료 짝의 길이 합계 | BREAK 스탬프 페어링 (§4) |
| **법정휴게** | 그날의 **스케줄 근무구간 길이**(종업−시업)에 국가별 BreakPolicy를 적용해 산출한 최소 휴게 | BreakPolicy(테넌트 소재국) × 스케줄 (§2) |
| **총 근무시간** | `max(0, 체류시간 − max(법정휴게, 실휴식))` | 조립기 계산 (§6) |

- 법정휴게 산출 기반은 **스케줄 구간의 길이(gross)** 다. 휴게의 "배치 시각"은 모델링하지 않으므로(스케줄에 휴게
  시각 컬럼 없음) 구간 길이를 근로시간으로 간주한다 — 휴게를 **과소 부여하지 않는 방향**(법 준수 우선)의 단순화.
  체류시간이 아니라 스케줄 기준인 이유: 같은 스케줄의 구성원에게 같은 휴게가 예측 가능하게 부여되고, 조퇴/야근으로
  법정휴게가 그때그때 출렁이지 않는다(연장근로 중 추가 휴게는 실휴식 기록이 max()로 반영 — §8 엣지 표 참조).
- `max(법정휴게, 실휴식)` 인 이유: 실휴식이 법정휴게 **이내**면 법정휴게만 차감(부여분을 소진한 것),
  **초과**하면 초과분만큼 추가 차감. 결과적으로 "출퇴근 시각이 같아도 실휴식에 따라 총 근무시간이 달라지는" 것이 정상.

### 1-2. 계산 예시 (정본 공식의 수치 검증 — 단위 테스트 CALC-계열과 1:1)

| # | 국가/스케줄 | 스탬프 | 체류 | 법정휴게 | 실휴식 | 총 근무시간 |
|---|------------|--------|------|---------|--------|------------|
| E1 | KR 09:00~18:00 | 출근 09:00, 휴식 12:00→13:00, 퇴근 18:00 | 9h00m | 60분(스케줄 9h≥8h) | 60분 | 540−max(60,60) = **480분(8h00m)** |
| E2 | KR 09:00~18:00 (휴식 초과) | 출근 09:00, 휴식 12:00→13:30, 퇴근 18:00 | 9h00m | 60분 | 90분 | 540−max(60,90) = **450분(7h30m)** — E1과 출퇴근 동일, 총계만 다름 |
| E3 | KR 09:00~18:00 (휴식 미기록) | 출근 09:00, 퇴근 18:00 | 9h00m | 60분 | 0분 | 540−max(60,0) = **480분** — 미기록이어도 법정휴게 차감 |
| E4 | JP 09:00~15:00 (6h 경계) | 출근 09:00, 휴식 20분, 퇴근 15:00 | 6h00m | **0분**(6h은 "초과" 아님 — §2-2) | 20분 | 360−max(0,20) = **340분(5h40m)** |
| E5 | JP 09:00~15:30 | 출근 09:00, 퇴근 15:30 | 6h30m | 45분(6h 초과) | 0분 | 390−45 = **345분(5h45m)** |
| E6 | KR 09:00~18:00 (야근) | 출근 09:00, 휴식 12:00→13:00·19:00→19:20, 퇴근 익일 01:10(표기 25:10) | 16h10m | 60분(스케줄 기준) | 80분 | 970−max(60,80) = **890분(14h50m)** |
| E7 | KR 오버라이드 09:00~13:00 (반차) | 출근 09:00, 퇴근 13:00 | 4h00m | 30분(스케줄 4h — "4시간인 경우" 포함) | 0분 | 240−30 = **210분(3h30m)** |

---

## 2. BreakPolicy 규칙 엔진

### 2-1. 설계 — ProfileCountry와 같은 국가 enum 전략 (D20 계승)

```java
// attendance/BreakPolicy.java — 순수 함수 enum. DB/세션 의존 없음(조립기와 동일하게 단위 테스트 대상).
/**
 * 국가별 법정 휴게 산출 전략.
 * 입력은 "스케줄 근무구간 길이"(종업-시업, §1-1), 출력은 그날 부여할 최소 휴게.
 * 경계값 포함 여부는 각 법 조문의 문언("이상/인 경우" vs "초과")을 그대로 따른다(§2-2).
 */
public enum BreakPolicy {

    /** 근로기준법 제54조: 근로시간 4시간인 경우 30분 이상, 8시간인 경우 1시간 이상 — "인 경우"는 경계 포함 */
    KR {
        @Override
        public Duration requiredBreak(Duration scheduledWork) {
            if (scheduledWork.compareTo(Duration.ofHours(8)) >= 0) return Duration.ofMinutes(60);
            if (scheduledWork.compareTo(Duration.ofHours(4)) >= 0) return Duration.ofMinutes(30);
            return Duration.ZERO;
        }
    },

    /** 労働基準法 第34条: 6시간을 "초과"하면 45분 이상, 8시간을 "초과"하면 1시간 이상 — 경계 미포함 */
    JP {
        @Override
        public Duration requiredBreak(Duration scheduledWork) {
            if (scheduledWork.compareTo(Duration.ofHours(8)) > 0) return Duration.ofMinutes(60);
            if (scheduledWork.compareTo(Duration.ofHours(6)) > 0) return Duration.ofMinutes(45);
            return Duration.ZERO;
        }
    };

    public abstract Duration requiredBreak(Duration scheduledWork);

    /** 소재국 → 정책. ProfileCountry와 1:1(switch 완전 매칭 — 국가 추가 시 컴파일 에러로 확장 지점 강제). */
    public static BreakPolicy of(ProfileCountry country) {
        return switch (country) { case KR -> KR; case JP -> JP; };
    }
}
```

- ProfileCountry에 메소드를 얹지 않고 **별도 enum**으로 두는 이유: ProfileCountry는 tenant 패키지의
  "식별번호 체계" 관심사(D20), 휴게는 attendance 패키지의 근태 관심사 — 국가 축은 공유하되 규칙은 도메인별 분리.
- 반올림 없음: 전 계산은 **분 단위 정수**(TIME/스탬프가 분 정밀도, DECIMAL/실수 배제).

### 2-2. 경계값 확정표 (법 조문 문언 기준 — 단위 테스트 BRK-계열과 1:1)

| 스케줄 근무구간 | KR (근로기준법 §54) | JP (労働基準法 §34) | 근거 |
|----------------|--------------------|--------------------|------|
| 3h59m | 0분 | 0분 | 양국 모두 미달 |
| **정확히 4h** | **30분** | 0분 | KR "4시간**인 경우** 30분 이상" — 경계 **포함** |
| **정확히 6h** | 30분 | **0분** | JP "6시간을 **초과**하는 경우 45분" — 경계 **미포함** |
| 6h01m | 30분 | 45분 | JP 초과 성립 |
| **정확히 8h** | **60분** | **45분** | KR "8시간**인 경우** 1시간" 포함 / JP "8시간 **초과**" 미포함 |
| 8h01m | 60분 | 60분 | 양국 상한 구간 |
| 9h (기본 09~18) | 60분 | 60분 | 기본 스케줄의 기대값 |

### 2-3. 산출 기반의 알려진 한계 (의도된 결정 — 리뷰 시 재론 금지 근거)

- KR 스케줄 09:00~13:00(gross 4h)은 휴게 30분을 부여한다. 순근로로 보면 3.5h라 법적 의무는 없지만,
  "구간=근로시간" 단순화의 귀결이며 **법 위반 방지 방향의 과부여**다. 4시간 순근로 반차를 원하는 테넌트는
  스케줄을 09:00~13:30으로 잡으면 된다(운영 가이드에 기재).
- 연장근로(스케줄 밖 야근)는 법정휴게를 늘리지 않는다 — 실휴식 기록이 max()로 차감을 담당(§1-1, E6).
  야근 중 법정 휴게 미부여의 컴플라이언스 경보는 Phase 4 리포트 과제로 이연(본 계획 범위 외).

### 2-4. 소재국 취득 경로 (타 도메인 의존)

- 공휴일 계획이 V7에서 `tenant.country CHAR(2) NOT NULL DEFAULT 'KR'`를 도입(V6 `tenant_profile.country`의
  테넌트 본체 승격)하는 것을 전제한다. `AttendanceService.monthly`는 `tenantMapper.findById(tenantId)`의
  country를 `ProfileCountry.of(...)`로 해석해 `BreakPolicy.of(...)`에 전달한다.
- 방어 규칙: country가 null/미지원 값이면 **KR로 동작**(기동 실패보다 안전한 실패 — 기존 행은 전부 KR backfill이라
  실측 도달 불가, 방어 코드만). 해당 계획이 지연되면 본 기능은 `tenant_profile.country`를 임시 소스로 읽는다(§11).

---

## 3. 개인 기본 스케줄

### 3-1. 우선순위 (일자별 해석 — 조립기 규칙 §6-1에 반영)

| 순위 | 소스 | 비고 |
|------|------|------|
| 1 | `work_schedule` 해당 일자 행 | 기존 유지. `holiday=TRUE`면 개인 휴일(스케줄/스탬프 공란). start/end가 **필드 단위로 null이면 그 필드만** 아래로 폴백(현행 폴백 방식 계승) |
| 2 | `users.default_work_start/default_work_end` | 신규 — 멤버별 기본값 |
| 3 | 코드 상수 09:00/18:00 | `MonthlyAttendanceAssembler.DEFAULT_START/END` 존치(유저 행 결손 등 이론적 방어선) |

### 3-2. V7 DDL — 근무 스케줄 몫 (V7 단일 파일 내 자기 구역, 전 구문 재실행 내성)

```sql
-- =========================================================
-- V7 [work-schedule 몫] 개인 기본 근무 스케줄
--  - 기존 전원 하드코딩(09:00~18:00)을 멤버별 컬럼으로. 기존 행은 DEFAULT로 현행 동작 보존
--  - work_schedule(일자별 오버라이드)은 무변경 — 우선순위: work_schedule > 개인 기본값
-- =========================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS default_work_start TIME NOT NULL DEFAULT '09:00:00'
        COMMENT '개인 기본 시업 시각' AFTER depart_cd,
    ADD COLUMN IF NOT EXISTS default_work_end   TIME NOT NULL DEFAULT '18:00:00'
        COMMENT '개인 기본 종업 시각' AFTER default_work_start;
```

- CHECK(`start < end`)는 두지 않는다 — 검증은 앱 단일 출처(§5-1). V4~V6 관례(IF NOT EXISTS, 앱이 규칙의
  단일 출처 — D20)와 동일. 언어 시드는 §9의 `INSERT IGNORE`로 같은 V7 구역에 포함.
- V7 파일 내 배치 순서: 공휴일 몫(tenant.country) → **본 몫(users 컬럼)** → 이메일 온보딩 몫 → 각 몫의 언어 시드.
  구역 주석(`-- [도메인 몫]`)으로 경계 명시(§11 충돌 방지).

### 3-3. 자바 측 반영

| 파일 | 변경 |
|------|------|
| `user/User.java` | `LocalTime defaultWorkStart, LocalTime defaultWorkEnd` 필드 추가 (record) |
| `user/UserMapper.java` | `findByTenant`/`findById`/`findByEmail` SELECT에 두 컬럼 추가. `insert`는 UserCreate의 값 사용. 신규 `int updateWorkSchedule(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("workStart") LocalTime, @Param("workEnd") LocalTime)` — 2중 조건(테넌트 전파 규약) |
| `user/UserCreate.java` | 두 필드 추가(미지정 시 서비스에서 09:00/18:00 채움 — DB DEFAULT에 맡기지 않고 응답 조립에 즉시 사용) |
| `attendance/ScheduleMapper.java` | 신규 `WorkDefaults findWorkDefaults(tenantId, userId)` — users 테이블 SELECT지만 "그날의 스케줄 해석"이라는 근태 질의이므로 스케줄 매퍼 소유(attendance→user 패키지 의존 회피). `WorkDefaults`는 `record(LocalTime start, LocalTime end)` (attendance 패키지) |

---

## 4. 휴식(BREAK) 스탬프 페어링 명세

BREAK 스탬프는 등록 시점에 이미 시작/종료가 확정돼 있다(`AttendanceService.confirm`의 토글 —
`status 0=시작(STATUS_ACTIVE), 1=종료(STATUS_BREAK_ENDED)`). 조립기는 이를 짝지어 실휴식을 합산한다.

1. **창(window)**: 그날 채용된 출근 스탬프 시각(inAt) ~ 퇴근 스탬프 시각(outAt)의 **실시각 구간**.
   달력일이 아니라 창 기준이므로 자정 넘김 야근의 새벽 휴식도 그 근무일의 실휴식이다(E6).
2. **짝 매칭**: 창 안의 BREAK를 시각 순으로 훑어 시작(0)이 열고 다음 종료(1)가 닫는다. 여러 짝은 전부 합산.
3. **미종료 휴식**(시작만 있고 창이 닫힘): **outAt까지 휴식한 것으로 간주**(초과분 차감).
   - 근거 ①: 상태머신이 휴식 중 퇴근을 거부(NOT_ON_DUTY)하므로 정상 경로에서는 발생 불가 — 발생했다면
     비정상 데이터이며, 0으로 보면 "휴식 시작만 찍고 방치"가 근무시간을 부풀리는 악용 통로가 된다.
     기록된 사실(휴식 시작 이후 근무 복귀 증적 없음)에 부합하는 쪽은 "퇴근까지 휴식"이다.
   - 근거 ②: max(법정, 실휴식) 공식에서 과대 추정은 근무시간을 줄일 뿐 법정 최소선 아래의 값을 만들지 않는다.
4. **시작 없는 종료**: 무시(0 처리) — 상태머신상 도달 불가, 방어 규칙.
5. **창 밖 BREAK**: 무시. 재출근 덮어쓰기(마지막 출근 채용)로 이전 구간이 버려지면 그 구간의 휴식도 함께
   버려진다 — 기존 "마지막 값 채용" 표시 규칙(§6-1)과 동일 귀결로 일관.
6. **퇴근이 없는 날**(미퇴근 48h 규칙 포함): 창이 닫히지 않으므로 실휴식·총 근무시간 모두 **null**(산출 불가).

---

## 5. API 계약

### 5-1. 멤버 등록/수정 — `/api/v1/tenant/members` 계열 확장 (TENANT_ADMIN 전용, 신규 화면·경로 없음)

| 메소드/경로 | 변경 | 에러(코드 / 메시지 키) |
|---|---|---|
| `POST /api/v1/tenant/members` | `MemberCreateRequest`에 `workStart`/`workEnd` **선택** 필드(미지정 시 09:00/18:00) | 400 `INVALID_INPUT` / `validation.work-time.format`, 400 `WORK_TIME_INVALID_RANGE` / `member.work-time.invalid-range` |
| `PUT /api/v1/tenant/members/{userId}/schedule` | **신규** — 속성별 PUT(기존 `/status`·`/role` 패턴 계승) | 404 `MEMBER_NOT_FOUND`(타 테넌트 동일 404 — 존재 비노출), 400 위와 동일 |
| `GET /api/v1/tenant/members` | `MemberResponse`에 `workStart`/`workEnd` 추가 | — |

```java
// user/MemberDtos.java 추가/변경분
public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";   // "HH:mm" — DailyAttendance 표기와 동일 포맷

public record MemberCreateRequest(
        /* email, name, departCd 현행 그대로 */
        @Schema(description = "schema.field.work-start", example = "09:00")
        @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}") String workStart,  // null 허용 → 09:00
        @Schema(description = "schema.field.work-end", example = "18:00")
        @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}") String workEnd) {  // null 허용 → 18:00
}

@Schema(description = "schema.member-schedule-request")
public record MemberScheduleRequest(
        @NotBlank(message = "{validation.work-time.required}")
        @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}") String workStart,
        @NotBlank(message = "{validation.work-time.required}")
        @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}") String workEnd) {
}
// MemberResponse / MemberCreateResponse: String workStart, String workEnd 추가 ("HH:mm")
```

- 교차 검증(`workStart < workEnd`)은 `MemberService`에서 — 위반 시 400 `WORK_TIME_INVALID_RANGE`.
  **개인 기본 스케줄의 자정 넘김(예: 22:00~06:00)은 비지원**: 조립기의 일자 귀속·법정휴게 산출이 1일 1구간
  전제라, 야간 교대제는 별도 Phase(교대 근무제)로. 일자별 특이 근무는 work_schedule 오버라이드로 흡수.
- 대상이 SYSTEM_ADMIN이면 기존 보호 규칙 그대로 404(D18 계승 — 멤버 목록 비노출 대상은 조작도 불가).

### 5-2. 월별 상세 응답 확장 — `GET /api/v1/attendance/monthly` (경로·파라미터 불변, 응답 필드 추가만)

```java
public record DailyAttendance(
        /* date, holiday, scheduleStart, scheduleEnd, stampIn, stampOut 현행 그대로 */
        @Schema(description = "schema.daily-attendance.break-minutes", example = "70")
        Integer breakMinutes,          // 실휴식 합(분). 출근·퇴근 미확정이면 null
        @Schema(description = "schema.daily-attendance.statutory-break-minutes", example = "60")
        Integer statutoryBreakMinutes, // 법정휴게(분). 휴일이면 null, 근무일은 항상 산출(스케줄 기반)
        @Schema(description = "schema.daily-attendance.work-minutes", example = "470")
        Integer workMinutes) {         // 총 근무시간(분). 출근·퇴근 미확정이면 null
}

public record MonthlyResponse(
        int year, int month, List<DailyAttendance> days,
        @Schema(description = "schema.monthly-response.total-work-minutes", example = "9600")
        int totalWorkMinutes) {        // 월 합계 = workMinutes non-null 합
}
```

- 시간 수치는 전부 **분 단위 정수**(프론트가 `h:mm`으로 조립 — 서버는 로케일 무관 수치만, 표기는 화면 책임).
  기존 문자열 필드(stampIn 등)는 표기 규칙(24+시 포함)이 굳어 있어 불변.

### 5-3. 상태 조회 확장 — `GET /api/v1/attendance/status` (확장 **한다**)

```java
public record StatusResponse(
        /* status, statusLabel, stampedAt, alert, alertLabel 현행 그대로 */
        @Schema(description = "schema.status-response.today-schedule-start", example = "09:00")
        String todayScheduleStart,     // 오늘의 해석된 스케줄(§3-1 우선순위 적용 후). 휴일이면 null
        String todayScheduleEnd) {
}
```

- 근거: W005가 "오늘 근무 09:00~18:00"을 보여줘야 개인 스케줄 반영을 당사자가 즉시 확인 가능(요구 3).
  W005는 navigation `loadScreenData`로 status를 동봉받으므로 추가 API 호출 없이 표시된다.
- check/confirm 계약은 **불변**(휴게는 사후 집계이지 타각 차단 조건이 아님 — 상태머신 무변경).

## 6. MonthlyAttendanceAssembler 변경 명세 (순수 로직)

### 6-1. 시그니처·규칙

```java
public List<DailyAttendance> assemble(List<LocalDate> monthDays,
        Map<LocalDate, WorkSchedule> schedules,
        Set<LocalDate> holidayDates,
        List<AttendanceStamp> stamps,          // 변경: BREAK 포함 전 타입 조회로 확대(현행 매퍼는 이미 전 타입 반환)
        LocalTime defaultStart,                // 신규: 개인 기본값(ScheduleMapper.findWorkDefaults)
        LocalTime defaultEnd,
        BreakPolicy breakPolicy)               // 신규: 테넌트 소재국 정책
```

- 기존 규칙(마지막 출근/퇴근 채용, 야근 24+시 표기, 48h 미퇴근, 출근 연속 미퇴근 처리, 휴일 공란) **전부 불변**.
  기존 페어링이 확정한 (inAt, outAt) 실시각을 내부 보존해 §4 창을 만들고, §1-1 공식으로 세 필드를 계산해
  DailyAttendance에 추가한다. 표기 문자열 경로와 계산 경로를 분리(표기 25:10 ↔ 계산은 실 LocalDateTime).
- `statutoryBreakMinutes = breakPolicy.requiredBreak(Duration.between(시업, 종업))` — §3-1 우선순위로 해석된
  그날의 스케줄. `workMinutes = max(0, 체류 − max(법정, 실휴식))` (음수 클램프 — 극단 오버라이드 방어).
- 호출부 변경: `AttendanceService.monthly`가 `scheduleMapper.findWorkDefaults` + 테넌트 country(§2-4)를 조회해
  전달. `attendanceMapper.findBetween`은 현행 그대로(BREAK도 이미 반환됨 — type 필터 없음 확인 완료).

### 6-2. 단위 테스트 케이스 목록 (`MonthlyAttendanceAssemblerTest` 확장 — 기존 형식: DisplayName + AssertJ)

| ID | DisplayName(요지) | 검증 |
|----|-------------------|------|
| CALC-01 | KR 정시: 휴식 60분이면 총 8h | E1 — `workMinutes=480, breakMinutes=60, statutoryBreakMinutes=60` |
| CALC-02 | 휴식 초과: 90분이면 초과 30분 추가 차감 | E2 — 출퇴근 동일한 CALC-01과 총계만 다름(450) |
| CALC-03 | 휴식 미기록도 법정휴게 차감 | E3 — `breakMinutes=0, workMinutes=480` |
| CALC-04 | JP 6h 정각 스케줄은 법정휴게 0 | E4 — `statutoryBreakMinutes=0, workMinutes=340` |
| CALC-05 | 야근: 새벽 휴식 포함, 창 기준 합산 | E6 — 표기 25:10 유지 + `workMinutes=890` |
| CALC-06 | 반차 오버라이드: 스케줄 4h → KR 30분 | E7 — work_schedule 우선 적용 확인 |
| CALC-07 | 개인 기본값: 오버라이드 없는 날은 default 10:00~19:00로 산출 | scheduleStart/statutory 모두 개인값 기준 |
| CALC-08 | 미퇴근(48h)·미출근 일자는 break/work 모두 null | 기존 noOffWorkOver48h 확장 |
| CALC-09 | 월 합계는 null 제외 합산 | `MonthlyResponse.totalWorkMinutes` |
| PAIR-01 | 다중 휴식 합산 | 2짝 30+40=70 |
| PAIR-02 | 미종료 휴식은 퇴근까지로 간주 | §4-3 |
| PAIR-03 | 시작 없는 종료는 무시 | §4-4 |
| PAIR-04 | 창 밖(재출근 덮어쓰기로 버려진 구간) 휴식은 미포함 | §4-5 |
| PAIR-05 | 자정 넘김 휴식(23:50→00:20)은 그 근무일 30분 | §4-1 |
| BRK-01~07 | BreakPolicy 경계 7행(§2-2 표 그대로, `BreakPolicyTest` 신설) | 3h59/4h/6h/6h01/8h/8h01/9h × KR·JP |

---

## 7. 프론트 변경

### 7-1. W009 멤버 관리 (`MembersScreen.tsx` 확장 — 신규 화면 없음, W013은 미사용 예약)

- 등록 폼: 근무 시작/종료 `<input type="time">` 2개(기본값 09:00/18:00 프리필). 등록 응답의 workStart/End 표시.
- 목록: `WORK_START`/`WORK_END` 컬럼 추가. 행별 "스케줄 수정" 조작 → 인라인 time 입력 2개 + 저장
  (`PUT /members/{userId}/schedule`) — 기존 status/role 인라인 조작과 같은 상호작용 패턴.
- 검증 에러(400)는 기존 ApiError 배너 경로 그대로(서버 메시지 표시).

### 7-2. W006 월별 상세 (`DetailsScreen.tsx`)

- 테이블 열 추가: 실휴식(`BREAK_ACTUAL`), 법정 휴게(`BREAK_STATUTORY`), 총 근무시간(`TOTAL_WORK`).
  분→`h:mm` 조립은 프론트 유틸(예: `470 → "7:50"`), null은 `-`(현행 공란 표기 관례).
- 하단 합계 행: `MONTH_TOTAL` + `totalWorkMinutes` 표기.
- 실휴식이 법정 휴게를 초과한 날은 실휴식 셀 강조(기존 상태 색상 토큰 재사용) — "왜 총계가 줄었는지" 시인성.

### 7-3. W005 출결 (`AttendanceScreen.tsx`)

- 상태 카드에 `TODAY_SCHEDULE` 라벨 + `todayScheduleStart~todayScheduleEnd` 표시(휴일이면 비표시).
- `api/types.ts`: StatusResponse/DailyAttendance/MonthlyResponse/MemberResponse 필드 추가,
  `endpoints.ts`: `memberApi.updateSchedule(userId, body)` 추가.

## 8. 엣지 케이스 표 (조립 규칙의 정본 — 구현·리뷰 시 이 표를 기준으로)

| # | 케이스 | 동작 | 근거 |
|---|--------|------|------|
| X1 | 자정 넘김 근무 + 자정 넘김 휴게(23:50 시작→익일 00:20 종료, 퇴근 익일 01:10) | 휴식 30분 전액 그 근무일 귀속(창 기준), 표기는 기존 25:10 규칙 | §4-1 |
| X2 | 휴식만 있고 퇴근 없음(48h 경과 미퇴근 포함) | `workMinutes=null, breakMinutes=null`, `statutoryBreakMinutes`는 스케줄 기반이므로 표시 | §4-6 |
| X3 | 다중 휴식(오전 15분 + 점심 60분 + 오후 20분) | 합산 95분 → KR 9h 스케줄이면 540−95=445분 | §4-2 |
| X4 | 반차: work_schedule 09:00~13:00 오버라이드 | 법정휴게 30분(KR — 스케줄 우선순위 1) | §1-2 E7 |
| X5 | 오버라이드 행에 start만 null | start는 개인 기본값, end는 오버라이드 값 — 필드 단위 폴백 | §3-1 |
| X6 | 미종료 휴식 상태의 비정상 데이터 + 그날 퇴근 존재 | 퇴근까지 휴식 간주(차감 극대화 — 부풀리기 방지) | §4-3 |
| X7 | 조퇴로 체류 3h < 스케줄 9h | 540분 기준 법정휴게 60분 차감, `max(0, …)` 클램프로 음수 방지. 스케줄 기준 산출의 의도된 귀결(짧은 근무 예정은 오버라이드로 표현) | §1-1, §6-1 |
| X8 | 개인 휴일(work_schedule holiday=TRUE)·공휴일에 스탬프 존재 | 현행대로 전부 공란(신규 3필드도 null) — 휴일 근무 정산은 Phase 4 | 현행 계승 |
| X9 | 극단 오버라이드(start=end, 예: 00:00~00:00) | 스케줄 0분 → 법정휴게 0 — 저장은 앱 검증(§5-1)이 차단하므로 조립기는 방어만 | §5-1 |
| X10 | 테넌트 country 미설정/미지원 | KR로 동작(안전한 실패) | §2-4 |

## 9. 신규 메시지·언어 마스터 키 (3개국어 — V7 자기 몫 `INSERT IGNORE` + messages*.properties)

언어 마스터(V7 시드, V5/V6 관례):

| window | key | KOR | ENG | JPN |
|--------|-----|-----|-----|-----|
| W009 | WORK_START | 근무 시작 | Work start | 始業時刻 |
| W009 | WORK_END | 근무 종료 | Work end | 終業時刻 |
| W009 | EDIT_SCHEDULE | 스케줄 수정 | Edit schedule | スケジュール編集 |
| W006 | BREAK_ACTUAL | 실휴식 | Break (actual) | 休憩（実績） |
| W006 | BREAK_STATUTORY | 법정 휴게 | Statutory break | 法定休憩 |
| W006 | TOTAL_WORK | 총 근무시간 | Total work | 総労働時間 |
| W006 | MONTH_TOTAL | 월 합계 | Monthly total | 月合計 |
| W005 | TODAY_SCHEDULE | 오늘 근무 | Today's schedule | 本日の勤務 |

백엔드 메시지(messages / _en / _ja — 검증·Swagger 키만 발췌):
`validation.work-time.required`(근무 시각을 입력해 주세요 / Work time is required / 勤務時刻を入力してください),
`validation.work-time.format`(시각은 HH:mm 형식으로 입력해 주세요 / Use HH:mm format / HH:mm形式で入力してください),
`member.work-time.invalid-range`(근무 시작은 종료보다 빨라야 합니다 / Start must be before end / 始業は終業より前である必要があります),
`schema.field.work-start`, `schema.field.work-end`, `schema.member-schedule-request`,
`schema.daily-attendance.break-minutes`, `schema.daily-attendance.statutory-break-minutes`,
`schema.daily-attendance.work-minutes`, `schema.monthly-response.total-work-minutes`,
`schema.status-response.today-schedule-start`, `schema.status-response.today-schedule-end`,
`api.member.schedule.summary`(멤버 근무 스케줄 수정) 각 3개국어.

## 10. 테스트 계획 (test-plan.md 규약 계승 — U=단위/S=스모크, 케이스 ID를 코드 주석에 표기)

| ID | 레벨 | 내용 | 기대 |
|----|------|------|------|
| BRK-01~07 | U | §2-2 경계 7행 × KR/JP (`BreakPolicyTest`) | 표와 완전 일치 |
| PAIR-01~05 | U | §6-2 페어링 5건 | §4 규칙 |
| CALC-01~09 | U | §6-2 계산 9건 (E1~E7 포함) | §1-2 수치 |
| SCH-U-01 | U | MemberService: workStart≥workEnd 등록/수정 400 `WORK_TIME_INVALID_RANGE` | 검증 위치 서비스 단일 |
| SCH-U-02 | U | 등록 시 미지정 → 09:00/18:00 기본값으로 insert + 응답 반영 | UserCreate 캡처 검증 |
| SCH-U-03 | U | schedule 수정 대상이 타 테넌트/미존재/SYSTEM_ADMIN → 404 | 존재 비노출 계승 |
| ISO-15 | U | monthly가 defaults/country 조회에 세션 tenantId 전달(`verify(scheduleMapper).findWorkDefaults(eq(TENANT_A), …)`) | 전파 규약(ISO-14 계열 확장) |
| WSC-S-01 | S | TA로 멤버 등록(10:00~19:00) → 그 멤버 monthly의 무오버라이드 일자 scheduleStart=10:00 | 개인 기본값 관통 |
| WSC-S-02 | S | 출근→휴식 90분→퇴근(9h 체류, KR) → monthly `workMinutes=450` | 초과 차감 실기동 |
| WSC-S-03 | S | 동일 출퇴근·휴식 미기록 멤버 → `workMinutes=480` | E3 vs E2 대비쌍 |
| WSC-S-04 | S | JP 테넌트(country=JP)에서 6h 스케줄 → `statutoryBreakMinutes=0` | 국가 분기 실기동 |
| WSC-S-05 | S | `PUT /members/{id}/schedule` 후 status 응답 todaySchedule 갱신 확인 | 5-3 계약 |
| WSC-S-06 | S | MEMBER 세션으로 `PUT /members/{id}/schedule` → 403 | RoleInterceptor 화이트리스트 회귀 |
| E2E | E | 기존 시나리오에 1스텝 삽입: TA가 멤버 스케줄 수정 → 멤버 로그인 → W005 오늘 근무·W006 총계 확인 | 3화면 관통 |

기존 회귀: `MonthlyAttendanceAssemblerTest` 기존 8건은 시그니처 변경만 반영하고 **기대값 불변**
(신규 3필드는 기존 케이스에서 값 검증 추가). `mvn test` 그린이 머지 게이트.

---

## 11. 타 도메인 경계·충돌 주의 (교차 리뷰 요청 지점)

| 상대 | 접점 | 조정 |
|------|------|------|
| 공휴일 계획 | `tenant.country` 승격이 본 계획의 **전제**(§2-4). V7 내 구역 순서: country가 먼저 | 승격 지연 시 본 기능은 `tenant_profile.country` 임시 소스(코드 1곳 스위치) |
| 이메일 온보딩 계획 | ① V7 단일 파일 합류 — users ALTER가 양쪽에 있으면 **구역 분리 + IF NOT EXISTS**로 상호 무해 ② `MemberDtos`/`MemberService`/W009 동시 수정(초대 흐름 vs 스케줄 필드) — 같은 파일 병합 충돌 예상, 초대 플로우의 등록 요청에도 workStart/End 선택 필드 규칙 동일 적용 ③ 화면 코드 W010~W012 선점 존중(본 계획 신규 화면 없음, W013 미사용) | |
| 상태머신/출결 API | check/confirm/evaluate **무변경**. 휴게는 집계이지 차단 조건이 아님 | 상태머신 변경 계획이 생기면 §4 페어링 전제(휴식 중 퇴근 불가) 재검토 필요 |
