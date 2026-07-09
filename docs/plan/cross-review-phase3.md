# Phase 3 계획 문서 3종 교차 검증 보고서 (cross-review-phase3)

- 검증일: 2026-07-09
- 대상: `email-onboarding.md`, `work-schedule.md`, `holiday-plan.md`
- 대조: 실코드(user/auth/tenant/navigation/attendance 패키지, V1·V4·V6, frontend App.tsx/types.ts/MembersScreen/TenantsScreen/DetailsScreen), `cross-review.md`(Phase 1 형식), patch-notes D10~D20
- 심각도 정의는 Phase 1 cross-review.md와 동일(구현 차단 / 불일치 / 공백 / 사소). 발견 번호 `CR3-n`은 세 문서의 수정 개소에 표기됨.
- 결정 원칙: 기존 코드 관례 > 보안 보수성 > 단순성. **보류 항목 없음**(전건 결정·반영 완료).

---

## A. 구현 차단 (2건)

### CR3-1. TenantCreateRequest.country — 두 문서가 정반대를 "확정" 기록

- **[holiday-plan §1-1/§4]** tenant.country 승격(tenant_profile.country 제거) + 생성 요청 `country` **필수** — "정본 결정(전 문서 공통)"으로 선언. work-schedule §2-4도 이 승격을 전제.
- **[email-onboarding §4.3(구)]** "생성 요청에 country를 넣는 안은 **기각**(country의 정본은 tenant_profile 단일 소스 — D20)" + "생성 시점엔 country 미등록 → 초대 메일 영어 폴백, 소재국 등록 후 재발송" 시나리오.
- **정본 결정**: **holiday-plan 채택**. 근거: ① 3문서 중 2문서가 승격을 전제, ② tenant_profile은 미등록일 수 있어 공휴일 동기화 출처로 부적합(승격의 존재 이유), ③ D20의 취지(단일 출처)는 출처가 tenant로 이동해도 유지(이원화 아님 — profile 쪽 컬럼은 제거됨). 이메일 문서의 기각 근거는 승격 전 세계관의 낡은 전제.
- **파급 수정**: 생성 시점에 소재국 확정 → 최초 관리자 초대 메일은 **처음부터 소재국 언어**(영어 폴백 후 재발송 시나리오 폐기, admin-invite는 발송 실패 수습용 존속). MailLanguageResolver 입력 = tenant.country. "미등록" 국가 케이스는 NOT NULL DEFAULT 'KR'로 실측 불가(ENG는 방어 전용).
- **수정 문서·절**: email-onboarding 확정 전제(§머리), §4.3(전면), §6.1 MailLanguageResolver, §8.3 W007 행, §11 TPL-04, §13-7 / holiday-plan §4-1(통합 계약 명기).

### CR3-2. MonthlyAttendanceAssembler.assemble 휴일 파라미터 — Set vs Map

- **[work-schedule §6-1(구)]** 확장 시그니처에 `Set<LocalDate> holidayDates` 유지 ↔ **[holiday-plan §6]** `Map<LocalDate, String> holidays`로 교체(holidayName 공급). 같은 메소드의 최종 시그니처가 두 문서에서 모순 — 어느 쪽대로 먼저 구현해도 상대 문서의 테스트(HOL-01 vs CALC-계열)와 충돌.
- **정본 결정**: **Map(holiday-plan 안)**. holidayName 표시에 필수이고, 판정은 `containsKey`로 스케줄 문서의 규칙(휴일 판정 불변)을 그대로 만족 — 정보 손실 없는 상위 호환.
- **수정 문서·절**: work-schedule §6-1(시그니처 교체 + 정본 참조), holiday-plan §6(정본임을 명기, 통합 시그니처는 work-schedule §6-1 참조).

---

## B. 불일치 (5건)

### CR3-3. MemberCreateRequest "(불변)" vs workStart/End 추가 + Member DTO 통합 필드 집합 미확정
- email-onboarding §4.2(구)는 `MemberCreateRequest`를 "불변"으로 표기, work-schedule §5-1은 `workStart`/`workEnd` 선택 필드를 추가(스케줄 §11은 "초대 등록 요청에도 동일 적용"을 이미 명시 — 이메일 쪽만 낡음). 응답 DTO도 각자 자기 필드만 기술해 통합 집합이 없었다.
- **결정**: 초대 등록 요청에 스케줄 필드 **포함**. 통합 최종 계약(양 문서 동일 명기):
  - `MemberCreateResponse = userId, email, name, departCd, role, status(=PENDING), workStart, workEnd, mailSent, inviteExpiresAt` (initialPassword 폐지)
  - `MemberResponse = 기존 7필드 + workStart, workEnd + inviteExpiresAt(nullable)`
- 수정: email-onboarding §4.2(표+코드 블록), work-schedule §5-1(표+코드 주석).

### CR3-4. V7 파일명·블록 순서·users ALTER 3분산 미확정
- 파일명은 holiday-plan만 `V7__phase3.sql` 명명, email은 "통합 시 확정", 순서는 work-schedule만 제안. users ALTER가 [S-1]/[E3]/[E4] 3곳.
- **결정**: 파일명 `V7__phase3.sql`, 순서 **[H-1~H-4] → [S-1] → [E1~E4] → 언어 시드(H→S→E)** — tenant.country가 전 도메인 전제라 최선두. users ALTER는 **단일 ALTER로 병합하지 않는다**: 도메인 구획 소유(D17 병렬 워크트리 충돌 최소화)가 리빌드 1회 절약보다 우선하고 각 문이 IF NOT EXISTS 멱등. 단 [E4]의 UNIQUE 신구 교체는 단일문 원자성 유지(V4 [3-4] 관례). 스케줄 몫 구획 주석을 `[S-1]`로 명명.
- 수정: 세 문서의 V7 절 전부(email §2, work-schedule §3-2, holiday §1) + 각 접점 표.

### CR3-5. TenantCreateResponse 통합 계약 + 생성 플로우(초대 메일 × 공휴일 동기) 합성 미정
- 이메일(initialPassword 삭제, adminStatus·mailSent 추가) × 공휴일(country·holidaysSynced 추가)이 같은 DTO를 각자 수정. 커밋 후 후처리 2종의 순서·간섭 여부 미기술.
- **결정**: 통합 응답 = `tenantId, tenantCode, name, country, status, adminUserId, adminEmail, adminStatus(=PENDING), mailSent, holidaysSynced`. 플로우 = Tx(tenant+관리자 PENDING+INVITE 토큰) 커밋 → ①INVITE 메일(mailSent) → ②당해·익년 sync(holidaysSynced) → 응답. 메일 선행 근거: 외부 API 대기가 초대 발송을 지연시키지 않게. 두 후처리는 예외 삼킴·플래그·수습 경로(admin-invite / W013 sync)가 전부 분리라 **간섭 없음**(검증 완료).
- 수정: email-onboarding §4.3, holiday-plan §2-5/§4-1/§4-3/§9.

### CR3-6. W009 최종 레이아웃 — 초대 UX × 스케줄 컬럼 합성 미정
- 이메일: 발송 전 확인 패널·PENDING 행 조작(재발송/삭제/inviteExpiresAt). 스케줄: 폼 time 입력 2개·WORK_START/END 컬럼·행별 스케줄 수정. 같은 폼·같은 테이블.
- **결정**: 폼(이메일/이름/부서/근무 시작/종료) → 확인 패널 → POST(스케줄 필드 동봉). 목록 컬럼 = `이름|이메일|부서|역할|상태|WORK_START|WORK_END|조작`. 스케줄 수정은 PENDING 행에도 허용(입사 전 준비 — 이메일 계획의 조작과 같은 행 공존, §4.2 가드와 무충돌).
- 수정: email-onboarding §8.3(오송신 UX 행), work-schedule §7-1.

### CR3-7. W006 표시 충돌 — 신규 3열 × holidayName 휴일 셀
- 실코드의 휴일 행은 `colSpan=4` 통합 셀. 스케줄이 데이터 열을 4→7로 늘리고 공휴일이 그 셀의 내용을 바꾸는데 colSpan 조정을 양쪽 다 미기술. DailyAttendance 통합 record도 미확정.
- **결정**: 데이터 열 7열, 휴일 행 `colSpan=7` + `{day.holidayName ?? t('HOLIDAY')}` 폴백(공휴일=명칭, 개인 휴일=기존 라벨 — W006/HOLIDAY 키는 V3 시드 존재 확인). 휴일 행은 신규 3필드 null(X8)이라 규칙 간 충돌 없음. 통합 `DailyAttendance` = 현행 6필드 + holidayName + breakMinutes/statutoryBreakMinutes/workMinutes = 10필드.
- 수정: work-schedule §5-2/§7-2, holiday-plan §5-2.

---

## C. 공백 (2건)

### CR3-8. UserMapper.existsByEmail의 deleted 필터 누락 — 이메일 문서 자신의 DEL-02를 깨는 미기재
- 실코드 `existsByEmail`은 `WHERE tenant_id AND email`뿐(**deleted 무필터**). [E4]로 UNIQUE는 활성 행 기준(email_key)이 되지만 앱 레벨 중복 검사가 삭제 행에 계속 걸려 "삭제 후 재등록 201"(DEL-02)이 409로 실패한다. 문서는 "활성 행 기준"이라고만 쓰고 매퍼 변경을 누락.
- **결정**: `AND deleted = FALSE` 추가를 명시(§4.2에 기재). 수정: email-onboarding §4.2.

### CR3-9. 소프트 삭제 유저의 잔존 work_schedule/holiday 데이터 표시 정책 — 위임받은 문서에 부재
- email-onboarding §13-5가 "표시 정책은 스케줄 문서 몫"으로 넘겼으나 work-schedule에 해당 절이 없었다.
- **결정**(work-schedule §3-4 신설): **잔존 보존 + Phase 3 표시 경로 없음**. 근거: 스케줄/출결 조회는 전부 `@LoginUser` 셀프서비스(삭제 유저 로그인 불가 — findByEmail/findById deleted 제외), 멤버 목록도 deleted 제외, 재등록은 신규 user_id라 구 데이터 비승계. holiday 데이터는 테넌트 소유라 유저 삭제와 무관. 관리자용 타인 조회 API 도입 Phase에서 재결정(Phase 1 발견 13의 이연 과제와 연동).
- 수정: work-schedule §3-4 신설·§11, email-onboarding §13-5, holiday-plan §9.

---

## D. 사소 (2건)

### CR3-10. RoleInterceptor RULES 일반화(`/api/v1/admin/**`→SYSTEM_ADMIN) — 채택 확정 + 타 문서 각주
- 실코드 검증: `/api/v1/admin/**` 하위 실경로는 LanguageController(i18n)뿐이고 WebConfig는 이미 `/api/v1/admin/**`로 등록 — 일반화는 기존 거동 불변 + W012 API 보호. 공휴일/스케줄 경로(`/api/v1/tenant/**`)와 무간섭. holiday-plan §6의 "RoleInterceptor 무변경" 표기가 오독 소지라 각주만 추가.
- 수정: holiday-plan §6(각주), email-onboarding §13-4(검증 완료 표기).

### CR3-11. 스케줄 스모크의 멤버 로그인 경로가 초대 전환으로 변경됨
- WSC-S-01 등 "멤버 등록 → 그 멤버로 monthly"는 초기 비밀번호 로그인 전제였음 — 초대 전환 후에는 메일 로그의 토큰으로 비밀번호 설정을 경유해야 한다.
- 수정: work-schedule §10 WSC-S-01(사전 단계 명기), §11.

---

## E. 점검 항목별 결과 요약

| # | 점검 항목 | 결과 |
|---|-----------|------|
| 1 | V7 단일 파일(파일명·순서·users ALTER) | **불일치** — CR3-4 확정 반영 |
| 2 | TenantCreateRequest/Response 동시 수정 | **구현 차단 + 불일치** — CR3-1, CR3-5 |
| 3 | MemberDtos/MemberService/W009 동시 수정 | **불일치** — CR3-3, CR3-6 |
| 4 | RoleInterceptor 일반화 | **채택 확정** — CR3-10(코드 대조: admin 하위 실경로 i18n 단일, WebConfig 기등록 확인) |
| 5 | 소프트 삭제 잔존 데이터 표시 정책 | **공백** — CR3-9로 신설 |
| 6 | W005/W006 표시 충돌 | **불일치** — CR3-7(colSpan 7 + 폴백 규칙 합의) |
| 7 | 화면 코드 W010~W013 / Screen enum / decide() / App.tsx | **확인됨** — 상호 결번 존중 일치. decide()의 "로그인 중 공개 화면 허용은 LOGIN/INDEX 리다이렉트뿐" 주장은 실코드와 일치(NavigationService.decide — 코드 변경 불요 검증). W013 헤더 메뉴와 W012 SA 메뉴 무충돌 |
| 8 | 테넌트 생성 플로우(공휴일 sync × 초대 메일) 간섭 | **무간섭 확정** — CR3-5(순서·플래그·수습 경로 분리) |
| 9 | 언어 마스터 키 충돌 | **확인됨** — 동일 키 다른 값 없음. W009/W013의 `DELETE_CONFIRM`은 window 분리로 공존, W999 신규 키(HOLIDAYS/MAIL_TEMPLATES) 상호 무충돌, W007 COUNTRY 키는 V6의 W008 키와 window 분리(공휴일 문서가 이미 명시). W009/STATUS_PENDING·W006/HOLIDAY·W999/SUBMIT·CANCEL 재사용 키는 V3/V5 시드 존재 확인 |
| 10 | 백엔드 메시지 키 | **확인됨** — 3문서 신규 키 무중복, 재사용 키(member.status.invalid / member.last-admin / auth.login.rate-limited / auth.login.tenant-mismatch / validation.tenant-code.required / validation.country.* / COUNTRY_UNSUPPORTED) 전부 실번들·실코드에 존재 |
| 11 | DTO·API 경로 명명 관례 | **확인됨** — `/members/{id}/schedule`·`/invite`·`/admin-invite`는 기존 `/status`·`/role` 속성별 PUT/POST 패턴, record DTO·@Pattern 메시지 키 방식 일치 |
| 12 | 테스트 ID 체계 | **확인됨** — INV/RST/TOK/DEL/SES/TPL × CALC/PAIR/BRK/SCH/WSC × HOL 상호 무충돌, 기존 test-plan 접두사(ISO/LGN/ROLE/NAV/ADM/CRY/MSK/SEC/REG/TTL)와도 무충돌. 스케줄 ISO-15는 기존 ISO-14 다음 번호로 정합 |
| 13 | V6 ProfileCountry ↔ tenant.country 승격 정합 | **확인됨(CR3-1 반영 후)** — ProfileCountry enum 유지·출처만 이동(holiday §6), TenantProfileService의 COUNTRY_UNSUPPORTED 분기 이동 계획은 실코드 구조와 일치 |
| 14 | "기존 코드 무변경" 가정 전수 | **확인됨** — authenticate는 ACTIVE만 통과(PENDING 401, AuthService L46), UNIQUE 인덱스명 `uk_users_tenant_email`(V4 [3-4]), findById/findByEmail deleted 제외(세션 회수·로그인 차단 전제 성립), attendanceMapper.findBetween type 무필터(BREAK 반환), 공개 exclude 3종(4번째 추가 여지), Spring Boot 4.1(RestClient), LoginRateLimiter·Masking·guardLastTenantAdmin(FOR UPDATE)·requireManageableMember 존재. 단 existsByEmail은 예외 — CR3-8 |

**정본 지정 요약**: 소재국 축(tenant.country·생성 계약) = holiday-plan / assemble 휴일 파라미터(Map) = holiday-plan / Member·Tenant DTO 통합 필드 집합 = email-onboarding §4.2·§4.3(스케줄 필드 포함) = work-schedule §5-1 / V7 구조 = 3문서 공통 표기(CR3-4) / 화면 코드 = email-onboarding(W010~12)·holiday-plan(W013) / 소프트 삭제 표시 정책 = work-schedule §3-4.
