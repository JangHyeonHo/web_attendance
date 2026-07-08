# web_attendance — 웹 출결 시스템

Spring Boot 4 + MyBatis + **MariaDB** 기반의 웹 출결(근태) 관리 시스템 백엔드.

- **REST API + Swagger**: 리소스별 엔드포인트와 타입 있는 DTO(record). API 문서는 `/swagger-ui.html`
- **3개 언어 대응(한/영/일)**: 에러·검증·출결 메시지와 Swagger 문서가 요청 언어로 응답
  (우선순위: navigation의 `lang`(세션 저장) → `Accept-Language` 헤더 → 한국어)
- **세션 쿠키 인증**: 로그인 후 세션 쿠키로 호출, 관리자 API 권한 분리
- **출결 2단계 처리**: 체크(사전 검사 + 토큰 발급) → 확정(동일 데이터 + 토큰, SHA-256 해시로 변조 탐지)
- **Flyway 마이그레이션**: 기동시 스키마 자동 생성/버전 관리

## 실행 방법

요구사항: **JDK 21+**, MariaDB (또는 Docker)

```bash
# 1. DB 기동 (로컬에 MariaDB가 없다면)
docker compose up -d

# 2. 백엔드 기동 (스키마는 Flyway가 자동 생성)
./mvnw spring-boot:run

# 3. 프론트엔드 기동 (개발 모드, /api는 9080으로 프록시)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

- 서버: http://localhost:9080
- Swagger UI: http://localhost:9080/swagger-ui.html (스펙: `/v3/api-docs`)
- 초기 관리자 계정: `admin@attendance.local` / `Admin123!` — **운영 배포 전 반드시 변경/삭제**

DB 접속 정보는 환경변수로 주입한다(미지정시 로컬 기본값 `localhost:3306/attendance`, attendance/attendance).

```bash
export DB_URL="jdbc:mariadb://db-host:3306/attendance"
export DB_USERNAME="..."
export DB_PASSWORD="..."
```

프론트를 별도 포트/도메인으로 띄우는 경우 CORS 허용 오리진을 지정한다:
`APP_CORS_ALLOWED_ORIGINS=http://localhost:3000` (프로퍼티 `app.cors.allowed-origins`)

## API 개요

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/navigation` | **서버 주도 화면 전개** (화면 코드 + 텍스트 + 초기 데이터) | - |
| POST | `/api/v1/auth/login` | 로그인(세션 발급) | - |
| POST | `/api/v1/auth/logout` | 로그아웃 | 세션 |
| GET | `/api/v1/auth/me` | 내 정보 | 세션 |
| POST | `/api/v1/users` | 회원가입 | - |
| GET | `/api/v1/attendance/status` | 현재 출결 상태(출근 대기/출근 중/휴식/퇴근 완료 + 24시간 경과 알림) | 세션 |
| POST | `/api/v1/attendance/check` | 출결 체크(사전 검사 + 확정 토큰 발급) | 세션 |
| POST | `/api/v1/attendance` | 출결 확정(스탬프 등록, 변조 탐지) | 세션 |
| GET | `/api/v1/attendance/monthly?year=&month=` | 월별 출결 상세(일자별 스케쥴/출퇴근 시각) | 세션 |
| GET | `/api/v1/i18n/{windowId}?lang=` | 화면 다국어 텍스트 조회 | - |
| GET/POST | `/api/v1/admin/i18n` | 언어 마스터 목록/등록(갱신) | 관리자 |

상세 스키마와 응답 예시는 Swagger UI 참조.

### 서버 주도 화면 전개 (Navigation)

이 프로젝트의 프론트는 **URL 라우팅 없이 서버가 결정한 화면 코드로만 화면을 전환**하는
Server-Driven Navigation 컨셉을 사용한다(v1의 화면 전개 방식 계승).
실제 화면 명은 은닉 코드(W000~)로만 노출된다.

```
POST /api/v1/navigation  {screen: "W005", lang: "KOR"}
→ {
    screen: "W001",            // 서버가 결정한 실제 표시 화면(여기선 미로그인이라 로그인으로)
    reason: "LOGIN_REQUIRED",  // 요청과 다른 화면이 된 사유
    userName: null,
    texts:   { ... },          // 해당 화면의 다국어 텍스트(language_master)
    headers: { ... },          // 공통(W999) 텍스트
    data:    { ... }           // 화면 초기 데이터(W005면 출결 상태)
  }
```

| 코드 | 화면 | 접근 |
|------|------|------|
| W000 | 인덱스 | 공개 |
| W001 | 로그인 | 공개 |
| W002 | 로그아웃(처리 후 W001로) | 공개 |
| W003 | 회원가입 | 공개 |
| W004 | 관리자 | 관리자 |
| W005 | 출결 | 로그인 |
| W006 | 출결 상세 | 로그인 |
| W999 | 공통(헤더 텍스트용) | - |

전환 규칙: 보호 화면+미로그인 → W001 / 비관리자의 W004 → W005 /
로그인 상태의 W000·W001·W003 → 홈(관리자 W004, 일반 W005) / 알 수 없는 코드 → W000.
`lang`은 세션에 저장되어 이후 요청에도 적용된다.

프론트는 이 응답의 `screen` 값으로만 컴포넌트를 스위칭하고(브라우저 URL 미사용),
개별 액션(로그인, 출결 등록 등)은 아래의 REST API를 사용한다.

### 출결 등록 흐름 (체크 → 확정)

```
POST /api/v1/attendance/check  {type: "GO_TO_WORK", latitude, longitude, placeInfo, terminal}
  → allowed=false                          : 처리 불가(예: 출근 전 퇴근). 메시지 표시
  → allowed=true, requiresConfirmation=true: 덮어쓰기/재출근 확인 필요. 사용자 확인 후 확정
  → allowed=true + token                   : 확정 가능

POST /api/v1/attendance  {token, ...체크와 동일한 데이터}
  → 201 등록 완료
  → 400 CHECK_MISMATCH (체크 시점과 데이터가 다름 = 변조)
```

출결 타입: `GO_TO_WORK`(출근) / `OFF_WORK`(퇴근) / `EARLY_DEPARTURE`(조퇴) / `BREAK`(휴식, 시작/종료 토글)

상태머신 규칙(구버전의 err_cd 1~8 계승):
- 최근 48시간 내 기록이 없으면 **출근만 허용**
- 같은 타입 반복(출근/퇴근/조퇴)은 **덮어쓰기 확인** 후 허용
- 같은 날 퇴근/조퇴 후 출근은 **재출근 확인** 후 허용
- 휴식 기록 상태에서는 재출근 불가, 퇴근/조퇴는 출근 중(또는 휴식 종료 후)에만 가능

## 아키텍처

도메인 패키지 구조(레이어 혼합 대신 기능 단위):

```
src/main/java/com/attendance/pro/
├── WebAttendanceApplication.java
├── config/
│   ├── WebConfig.java              # 인터셉터/CORS/ArgumentResolver 등록
│   └── OpenApiConfig.java          # Swagger 문서 정보
├── common/
│   ├── ApiException.java           # 서비스 예외(HTTP 상태 + 코드)
│   ├── ErrorResponse.java          # 공통 에러 응답(record)
│   └── GlobalExceptionHandler.java # 전역 예외 -> ErrorResponse 변환
├── auth/                           # 세션 인증
│   ├── AuthController/Service      # 로그인/로그아웃/내정보
│   ├── SessionUser.java            # 세션 보관 유저(record)
│   ├── AuthInterceptor.java        # 로그인 검사
│   ├── AdminInterceptor.java       # 관리자 검사
│   └── @LoginUser + Resolver       # 컨트롤러에 세션 유저 주입
├── user/                           # 회원
│   ├── UserController/Service/Mapper
│   └── User(record), UserCreate, UserDtos
├── attendance/                     # 출결(핵심 도메인)
│   ├── AttendanceController/Service
│   ├── AttendanceMapper, ScheduleMapper (MyBatis 어노테이션 매퍼)
│   ├── AttendanceType, ConfirmCode (enum 상태머신 코드)
│   ├── MonthlyAttendanceAssembler  # 월별 스케쥴 x 스탬프 페어링(순수 로직, 단위테스트 대상)
│   └── AttendanceStamp, WorkSchedule(record), AttendanceDtos
└── language/                       # 다국어 텍스트
    └── LanguageController/Service/Mapper, LanguageEntry, LanguageDtos

src/main/resources/
├── application.properties
├── messages/                       # 서버 메시지 번들(ko 기본/en/ja)
└── db/migration/                   # Flyway 마이그레이션
    ├── V1__init.sql                # 스키마
    └── V2__seed_admin.sql          # 초기 관리자

frontend/                           # 프론트엔드 (Vite + React 19 + TypeScript)
└── src/                            # 서버 주도 화면 전개 기반 SPA (frontend/README.md 참조)
```

- **Map 남용 제거**: 요청/응답/도메인 모두 record 기반 타입. MyBatis는 생성자 인자명 자동매핑(`arg-name-based-constructor-auto-mapping`) 사용
- **매퍼**: XML 대신 어노테이션 매퍼(`@Select`/`@Insert`). 복잡했던 월 달력 SQL(Oracle `CONNECT BY`)은 자바(java.time)로 이동
- **비밀번호**: BCrypt (spring-security-crypto만 사용, 전체 Spring Security 미도입)

## DB 스키마 (MariaDB, Flyway 관리)

| 테이블 | 용도 |
|--------|------|
| `users` | 회원(이메일 UNIQUE, BCrypt 해시, 관리자 플래그) |
| `attendance` | 출결 스탬프(타입/상태/시각/위치/단말) |
| `attendance_check` | 체크→확정 사이의 변조 방지 토큰(+요청 해시) |
| `work_schedule` | 일자별 근무시간 오버라이드/개인 휴일 (미등록 일자는 09:00~18:00) |
| `holiday` | 전사 공휴일 |
| `language_master` | 다국어 텍스트(화면 그룹 + 키 + 언어) |

## 테스트

```bash
./mvnw test
```

- `AttendanceServiceTest` — 출결 상태머신(체크 규칙 8종), 체크→확정 변조 탐지, 상태 조회 매핑
- `MonthlyAttendanceAssemblerTest` — 월별 페어링(정상/야근 25:10 표기/미퇴근/중복 출근/휴일/스케쥴 오버라이드)
- `WebAttendanceApplicationTests` — 컨텍스트 기동(DB 필요, 기본 비활성)

## 버전 이력

### v2.0.0 (2026-07) — MariaDB 전환 + REST API 재설계
- Spring Boot `3.5` → `4.1.0`, MyBatis Starter `4.0.1`, springdoc `3.0.3`
- **Oracle → MariaDB** 전환: Flyway 마이그레이션 도입, `docker-compose.yml` 제공, Oracle 전용 DDL 자동 생성기(AdminSettingLogic 등) 제거
- **API 재설계**: 단일 `/api`(win_id/action + Map) → 리소스별 REST 엔드포인트 + record DTO + Bean Validation. **구 API와 호환되지 않음**
- **Swagger(springdoc)** 도입: 전 엔드포인트/스키마 문서화
- 비밀번호 SHA-512(무솔트) → **BCrypt**, 로그인 401 통일 메시지(이메일 존재 여부 비노출), 세션 고정 공격 방지
- 화면 ID(W000~) 개념 제거, 다국어는 `/api/v1/i18n` API로 단순화(테이블명 오타 `LANGAUGE_MST` → `language_master` 정리)
- Thymeleaf 템플릿/sb-admin-2 정적 자원 등 프론트 잔재 제거(백엔드 전용화)
- 출결 상태머신/월별 페어링 로직을 java.time 기반으로 재작성 + 단위 테스트 26건

### v1.1.0 (2026-07) — 1차 현대화
- Spring Boot 2.5→3.5, Java 8→21, 명명규칙 정리, 생성자 주입, 매퍼 XML 비공개화, DB 접속정보 외부화 등
- 상세 내역은 git 히스토리 참조

## 남은 과제 (TODO)

- [ ] 근무 스케쥴/공휴일 등록 API (현재는 SQL로 직접 입력)
- [ ] 관리자용 회원 관리 API (권한 부여, 비활성화)
- [ ] 출결 데이터 조회 API의 페이징/기간 검색
- [ ] 운영 프로파일 분리(devtools/Swagger 비활성, 세션 저장소 외부화)
- [ ] 인증을 세션에서 토큰(JWT) 방식으로 전환할지 검토(모바일 클라이언트 대응시)
