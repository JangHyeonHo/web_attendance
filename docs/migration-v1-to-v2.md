# v1 → v2 변경 상세 (마이그레이션 문서)

v2.0.0 재설계에서 **v1의 각 구성요소가 어디로 갔는지**를 전부 추적한 문서.
"대체됨 / 형태만 바뀜 / 대체 없이 제거됨"을 구분하며, 특히 마지막 항목(§5)은 반드시 확인할 것.

---

## 1. 백엔드 컴포넌트 매핑

| v1 구성요소 | 처리 | v2 대응 | 비고 |
|-------------|:---:|---------|------|
| `RootController` `GET /api` (화면 전개) | 대체 | `POST /api/v1/navigation` | 서버 주도 화면 결정 + 다국어 + 초기 데이터 동봉 컨셉 유지. 별도 커밋으로 복원 |
| `RootController` `POST /api` (프로세스 분기) | 대체 | 리소스별 REST API (`auth`/`users`/`attendance`) | win_id/action 분기 → 엔드포인트 분리 |
| `RootController.loginAuth()` (화면 인가 규칙) | 대체 | `NavigationService.decide()` | 규칙 동일 계승 + 관리자 화면 보호 활성화(v1은 테스트용 해제 상태였음) |
| `WindowManagement` (화면 ID + 언어 캐시) | 분리 대체 | 화면 ID → `Screen` enum / 언어 → `LanguageService` | static 필드 캐시 제거, DB 직조회로 변경 |
| `UserSessionInfo` (세션 스코프 빈) | 대체 | `SessionUser` record를 HttpSession 속성에 저장 | 프록시 빈 → 표준 세션 속성. 로그인시 세션 재발급(세션 고정 공격 방지) 추가 |
| 로그인 체크 (각 로직에서 수동 확인) | 대체 | `AuthInterceptor` / `AdminInterceptor` | 경로 기반 일괄 적용, 401/403 상태코드로 통일 |
| `UserManagementService` (로그인) | 대체 | `AuthService` + `AuthController` | 검증은 Bean Validation으로 이동 |
| 비밀번호 SHA-512 (무솔트) | 대체 | BCrypt (`spring-security-crypto`) | DB 폐기로 기존 해시 호환 불필요해져 전환 |
| 회원가입 (W003, 미구현) | 신규 구현 | `POST /api/v1/users` | v1에서 화면만 있고 로직이 없던 것 |
| `AttendanceService` (출결 상태머신) | 형태 변경 | `AttendanceService.evaluate()` + `ConfirmCode` enum | err_cd 1~8 규칙 동일 계승, 단위 테스트 추가 |
| 체크→확정 변조 탐지 (`checkRegistSystem`/`isResultDatasEqual`) | 형태 변경 | `attendance_check` 테이블 + UUID 토큰 + SHA-256 | 키가 timestamp+userCd → UUID, 해시가 Map.toString() SHA-512 → 정규화 문자열 SHA-256 |
| 월별 상세 (`getAttendanceDetailDatas`) | 형태 변경 | `MonthlyAttendanceAssembler` | Calendar/SimpleDateFormat → java.time. 페어링 규칙(야근 +24h, 48h 미퇴근, 중복 출근) 계승 |
| `BaseService.objectTo*()` (Map 형변환 유틸) | 제거 | 불필요 | 타입 있는 DTO로 형변환 자체가 사라짐 |
| `BaseService.errorRegistSystem()` (에러 DB 적재) | **부분 제거** | `GlobalExceptionHandler` + 서버 로그 | §5 참조 — DB 적재는 하지 않음 |
| `InitializeConfig` (기동시 언어 적재 + 로그 청소) | 대체 | 언어: DB 직조회 / 청소: 체크시 지연 삭제(`deleteExpiredChecks`) | 기동시 일괄 처리 → 요청 시점 처리 |
| `LogicServiceDao` + 로직 서비스 테이블 | 축소 대체 | `attendance_check` 테이블 | 변조 탐지용 해시만 저장. 성공/에러 이력 축적 기능은 제거(§5) |
| `AdminSettingLogic` (Oracle DDL 자동 생성/컬럼 비교) | 대체 | Flyway 마이그레이션 | 자작 스키마 관리 시스템의 표준 도구 대체 |
| `AdminScanDao`, `DaoManagement` | 제거 | Flyway | 위와 동일 목적의 부속물 |
| `GET /admin_settings` (테이블 생성) | 대체 | 기동시 Flyway 자동 적용 | 수동 URL 호출 불필요 |
| `GET /data_settings` (초기 데이터 투입) | 대체 | `V2__seed_admin.sql` | 초기 관리자 계정 시드 |
| `GET /data_saving` (TSV 백업) | **제거** | 없음 | §5 참조 |
| `/lang_mst` Thymeleaf 화면 (언어 등록) | 대체 | `GET/POST /api/v1/admin/i18n` + Swagger UI | 관리자 인증 필수로 강화(v1은 무인증). 중복 키 등록시 에러 → upsert(갱신)로 개선 |
| `CodeMap` (isEqual 오버로드, 상수) | 제거 | `Objects.equals`, 각 도메인 enum/상수 | |
| `Redirector` | 제거 | 불필요 | Thymeleaf 리다이렉트 전용이었음 |
| DAO XML 매퍼 6종 | 대체 | 어노테이션 매퍼 (`@Select`/`@Insert`) | **MyBatis 자체는 유지** (starter 4.0.1). 복잡 쿼리가 생기면 해당 매퍼만 XML로 복귀 가능 |
| Oracle 달력 쿼리 (`CONNECT BY LEVEL`) | 대체 | 자바 `LocalDate.datesUntil()` | DB 비종속화 |
| DTO 클래스 7종 (getter/setter) | 대체 | record (도메인/요청/응답 분리) | |
| `static/` 정적 자원 1,857개 (sb-admin-2, jquery, fontawesome) | 제거 | 없음 (소비자였던 Thymeleaf 화면이 사라짐) | 필요시 `git checkout 70528e7 -- src/main/resources/static` 로 복구 가능 |
| Thymeleaf 템플릿 (`complete/error/lang_mst/WR_0002`) | 제거 | REST 응답 / Swagger | WR_0002(가입 화면 목업)는 React 재작성시 구현 예정 |
| `WebConfig` CORS (localhost:3000 하드코딩, 미활성) | 대체 | `app.cors.allowed-origins` 프로퍼티 | 환경변수로 제어, 미지정시 비활성 |

## 2. DB 스키마 매핑 (Oracle → MariaDB)

| v1 테이블 | v2 테이블 | 주요 변경 |
|-----------|-----------|-----------|
| `USER_MST` | `users` | PK: 이메일 → `user_id`(AUTO_INCREMENT), 이메일은 UNIQUE로. `USER_RANK`(-1=관리자) → `is_admin`(BOOLEAN) — **등급(숫자 랭크) 개념 제거**(§5). `USER_CD` 제거(user_id로 통합) |
| `LANGAUGE_MST` (오타) | `language_master` | 오타 정리. `MASTER_NAME` → `lang_key`, `OPTION_VALUE`(키 접미 숫자) 제거(§5). UNIQUE(window_id, lang_key, lang) |
| `ATTENDANCE` | `attendance` | 복합키(날짜+SEQ) → `attendance_id` 단일 PK. 위경도 VARCHAR → DECIMAL(10,7). `ERROR_CD`/`ERROR_MSG`/`REMARK` 컬럼 제거(§5) |
| 로직 서비스 테이블 | `attendance_check` | 변조 탐지 토큰 전용으로 축소. 상태(S/E)·에러코드·요청덤프 컬럼 제거(§5) |
| `SCHEDULE_MANAGEMENT` | `work_schedule` + `holiday` | 개인 스케쥴과 전사 공휴일 분리. 시각 VARCHAR("0900") → TIME 타입. `HOLIDAY_SEQ` → `holiday`(BOOLEAN)/`holiday` 테이블. `ADMIN_APPROVE`(승인 플래그) 제거(§5). **기본 근무시간 캐리포워드 제거**(§5) |
| 공통 컬럼 (`REGIST_USER/DATE`, `UPDATE_USER/DATE/CNT`, `DEL_FLG`) | `created_at`, `updated_at`, (users만 `deleted`) | 수정 유저/수정 횟수 추적 제거(§5). 논리삭제는 users만 유지, 나머지는 물리 데이터 그대로 |

## 3. API 계약 매핑

| v1 | v2 |
|----|----|
| `GET /api?win_id=W00x&lang=` | `POST /api/v1/navigation {screen, lang}` |
| `POST /api {win_id:W001, user_email, user_pwd}` | `POST /api/v1/auth/login {email, password}` |
| `GET /api?win_id=W002` (로그아웃) | `POST /api/v1/navigation {screen:"W002"}` 또는 `POST /api/v1/auth/logout` |
| `POST /api {win_id:W005, action:1, attendance_type:1~4}` | `POST /api/v1/attendance/check {type:"GO_TO_WORK"~}` |
| `POST /api {action:2, result, confirm_cd}` | `POST /api/v1/attendance {token, ...}` |
| `POST /api {win_id:W006, action:3, years, months(0-based)}` | `GET /api/v1/attendance/monthly?year&month(1~12)` |
| 응답 `{res:"S"/"E", msg:[...]}` | HTTP 상태코드 + `{code, message, fieldErrors[]}` |
| 응답 `windows`/`headers` (다국어) | navigation 응답의 `texts`/`headers`, 또는 `GET /api/v1/i18n/{windowId}` |
| 시각 표현 "0900", "2510"(HHmm) | "09:00", "25:10"(HH:mm) |
| 세션 만료 판정: 응답 window 비교 | HTTP 401 |

## 4. 유지된 것 (컨셉 동일, 형태만 현대화)

- **MyBatis** (XML → 어노테이션)
- **세션 쿠키 인증** (JWT 등으로 바꾸지 않음)
- **서버 주도 화면 전개 + 화면 코드 은닉(W000~)** (navigation API로 복원)
- **출결 체크→확정 2단계 변조 탐지**
- **출결 상태머신 규칙** (확인 코드 1~8 의미 동일)
- **월별 페어링 규칙** (야근 +24h 표기, 48h 미퇴근, 중복 스탬프 덮어쓰기, 휴일 공란)
- **DB 언어 마스터 기반 다국어** (KOR/ENG, 화면 코드 단위)
- **위치정보/단말 기록** (위경도/장소/단말)

## 5. ⚠ 대체 없이 제거된 기능 (복원 원하면 요청할 것)

재설계 과정에서 의도적으로 걷어냈지만, v1에 존재했던 기능들이다.
필요하다면 각각 독립적으로 복원 가능하다.

| 제거된 기능 | v1에서의 동작 | 제거 사유 | 복원 난이도 |
|-------------|---------------|-----------|:---:|
| **에러 이력 DB 적재** | 서비스 예외 발생시 요청 덤프+에러코드를 로직 테이블에 INSERT | 서버 로그(GlobalExceptionHandler)로 대체. 운영 로그 수집 체계가 있으면 DB 적재는 중복 | 낮음 |
| **체크 이력 축적** | 모든 출결 체크가 상태 "S"로 테이블에 남음(일주일 후 삭제) | 변조 탐지 목적만 남기고 감사(audit) 용도는 제거 | 낮음 |
| **TSV 데이터 백업** (`/data_saving`) | 테이블 데이터를 서버 로컬에 .tsv로 저장 | DB 표준 백업(mariadb-dump)이 정석 | - |
| **위치취득 실패 기록** (`error_cd`/`error_msg`) | 프론트 위치정보 취득 실패시 에러코드를 출결 데이터에 저장 | 스탬프 데이터와 에러의 결합 제거. 필요시 컬럼 2개 추가로 복원 | 낮음 |
| **비고(`REMARK`)** | 월별 상세에 표시되던 비고 컬럼 | 입력 수단이 없던 컬럼(표시만 존재) | 낮음 |
| **유저 등급(`USER_RANK`)** | 숫자 랭크(-1=관리자) | 관리자 여부만 실사용이라 boolean으로 축소. 직급 체계가 필요해지면 별도 컬럼/테이블로 | 중간 |
| **스케쥴 승인(`ADMIN_APPROVE`)** | 스케쥴에 관리자 승인 플래그 | 승인 프로세스 자체가 미구현이었음 | 중간 |
| **기본 근무시간 캐리포워드** | `HOLIDAY_SEQ=-1` 행을 "이 날부터 기본 근무시간 변경" 마커로 사용, 이후 날짜에 자동 적용 | 일자별 오버라이드 방식으로 단순화(미등록일=09:00~18:00 고정). 유연 근무제가 필요하면 `user_default_schedule` 테이블로 재설계 권장 | 중간 |
| **언어 키 접미 숫자(`OPTION_VALUE`)** | 같은 키를 숫자 붙여 여러 개 등록(ATTSTS1, ATTSTS2...) | 키 문자열에 직접 포함하면 됨(기능 손실 아님, 관례 변경) | - |
| **수정 이력 컬럼** (`UPDATE_USER/CNT` 등) | 누가 몇 번 수정했는지 컬럼으로 추적 | 실제 갱신 로직이 거의 없었음. 감사 추적이 필요해지면 이력 테이블 방식 권장 | 중간 |
| **재출근시 이전 데이터 삭제** | 주석 처리된 미완성 코드(`reAttendingToDeleteStatus`) | v1에서도 비활성 상태였음. 월별 페어링의 "마지막 스탬프 채용" 규칙이 같은 효과 | - |

## 6. 이 문서 이후의 변경

- 서버 주도 화면 전개(navigation) 복원 — §1에 반영됨 (커밋 a0bf283)
- **3개 언어(한/영/일) 대응** — 서버 메시지(에러/검증/출결)와 Swagger 문서를 MessageSource 번들로 국제화.
  v1의 "일본어 → 한국어 폴백"이 실제 일본어 지원으로 대체됨. 결정 배경은
  [patch-notes-2026-07.md](./patch-notes-2026-07.md) D8 참조
- 프론트엔드 점검 보고서: `claude/frontend-review-09t933` 브랜치 `docs/frontend-review.md`
  (라우팅 권장안은 navigation 방식 확정에 따라 갱신 예정)
