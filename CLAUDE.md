# CLAUDE.md

멀티테넌트(SaaS) 웹 근태관리 시스템. 백엔드 Spring Boot + 프론트 Vite/React 모노레포.

## 스택

- 백엔드: Java 21, Spring Boot 4.1.0, MyBatis(spring-boot-starter 4.0.1, 어노테이션 매퍼), MariaDB, Flyway
- Spring Security 미사용 — `spring-security-crypto`(BCrypt)만. 인증/인가는 자체 인터셉터
- 프론트: `frontend/` — React 19 + TypeScript 5.8 + Vite 6. 라우터/상태 라이브러리 없음(의존성은 react/react-dom/pretendard뿐)
- 기타: springdoc(Swagger `/swagger-ui.html`, prod에서는 비활성), Apache POI(xlsx 내보내기), spring-boot-starter-mail(dev는 LoggingMailSender로 로그만)

## 빌드·실행·테스트

```bash
docker compose up -d                  # 로컬 MariaDB 11 (attendance/attendance, DB attendance)
./mvnw spring-boot:run                # 백엔드 :9080 — 플러그인 설정으로 dev 프로파일 자동 적용
cd frontend && npm install && npm run dev   # 프론트 :5173, /api → 9080 프록시
./mvnw test                           # 전체 테스트
./mvnw test -Dtest=AttendanceServiceTest    # 단일 클래스 — @Nested 포함 정상 동작(확인됨)
cd frontend && npm run build          # tsc -b && vite build (타입 체크 겸용)
```

- 환경변수(전부 로컬 기본값 있음): `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, `APP_CRYPTO_KEY`(민감 필드 AES-256-GCM 키 — dev 기본키 커밋됨, prod는 필수·미설정 시 기동 실패)
- 운영은 `SPRING_PROFILES_ACTIVE=prod`: `APP_CRYPTO_KEY`·`DB_*`·`SMTP_*` 필수(fail-fast), Swagger 비활성
- 시드 관리자: `admin@attendance.local` / `Admin123!` (V2 마이그레이션)
- vite 프록시는 `changeOrigin: false` — 테넌트 서브도메인 방식이 Host 헤더로 테넌트를 해석하므로 Host 보존 필수

## 백엔드 구조

- 패키지: `com.attendance.pro.{attendance(.close/.export), audit, auth, billing, common, config, holiday, language, leave, mail, navigation, setting, tenant, user}` — 도메인별 패키지 안에 Controller/Service/Mapper/DTO 동거
- DTO는 record, 도메인별 `XxxDtos.java`에 모음. 예외는 `ApiException` → `GlobalExceptionHandler`
- MyBatis: XML 없이 `@Mapper` 인터페이스 + `@Select` 텍스트 블록. `map-underscore-to-camel-case=true` + **`arg-name-based-constructor-auto-mapping=true`** — record 매핑은 SELECT 컬럼명(snake_case→camelCase)이 record 생성자 파라미터와 일치해야 함. 다르면 `AS` 별칭(예: `type AS type_code`)
- Flyway: `src/main/resources/db/migration/V{n}__{설명}.sql`, 현재 최신 **V87**. 스키마·라벨(language_master) 변경 모두 마이그레이션으로

## 멀티테넌시·인가(핵심 규약)

- **tenantId는 항상 세션(`SessionUser`)에서만 취득** — 요청 파라미터의 테넌트 값 불신
- 매퍼 규약: 모든 메소드 첫 파라미터 `@Param("tenantId")`, user_id 조건에도 `AND tenant_id = #{tenantId}` 병기(2중 조건) — `AttendanceMapper` javadoc 참조
- Role: `MEMBER < HR_ADMIN < TENANT_ADMIN`(회사 내) + `SYSTEM_ADMIN`(운영사). **서열 비교 없음** — 인가는 경로별 허용 role 화이트리스트가 단일 소스(`RoleInterceptor`). SYSTEM_ADMIN은 tenant/attendance 경로에서 오히려 403(운영자의 고객 데이터 접근 차단)
- `RoleInterceptor`는 선언 순서 첫 매칭 + 미매칭 시 fail-closed(403). role 게이트가 필요한 새 경로 프리픽스는 `WebConfig.addInterceptors`에 등록 필수

## 인증 모델

- 세션 쿠키(서버 인메모리, 1일 슬라이딩). 인터셉터 순서: CSRF(`X-Requested-With` 헤더 요구, 상태변경 요청 전부) → 세션 재검증 → 쿠키 슬라이딩 → 인증(공개 4종 제외: login/i18n/navigation/password) → 인가
- 단일 세션: 로그인 시 `users.session_token` 발급, DB 값과 다르면 세션 회수. 비밀번호 변경(`passwordChangedAt` 스냅샷 불일치)도 즉시 회수 — `SessionRevalidationInterceptor`
- `SessionUser` 필드 변경 시 serialVersionUID 증가 = 전 유저 재로그인. 프론트 fetch는 `client.ts`가 `X-Requested-With: XMLHttpRequest`를 항상 부여

## 화면·i18n

- **서버 주도 내비게이션**: 프론트에 URL 라우팅 없음. `POST /api/v1/navigation` 응답의 화면 코드로만 화면 결정(`AppContext.tsx`)
- 화면 코드 정본은 백엔드 `navigation/Screen.java` enum: `M###` 멤버 본인 업무 / `T###` 테넌트 관리 / `A###` 운영사 / `W###` 공통. 새 화면은 해당 접두사의 다음 번호. W003은 영구 결번
- 화면 텍스트는 DB `language_master`(window_id=화면 코드, `W999`=공통 라벨), 라벨 추가는 Flyway 마이그레이션으로
- 코드 메시지는 `messages/messages{,_en,_ja}.properties`(ko 기본/en/ja). **키 추가 시 3개 파일 동시에** — `MessagesTest.bundlesHaveSameKeys`가 키 집합 일치를 강제

## 프론트 구조

- `frontend/src/{api, app, components, hooks, i18n, screens, util}` — 화면은 `screens/`, 화면 코드↔컴포넌트 매핑은 `App.tsx`
- **신규 화면은 `components/` 카탈로그 부품 조립으로** — 생 `<button>`·일회성 CSS 금지. 카탈로그 인덱스는 `frontend/src/components/README.md`(상세 규칙 정본은 각 컴포넌트 JSDoc), 부품 추가/변경 시 이 표도 갱신
- 주요 공용 부품: Button/IconButton/Modal/ConfirmModal(결과 안내 hint 필수)/fields(TextField 등, 라벨 필수)/DateField·TimeField(네이티브 input 금지)/SectionHead/EmptyState/Pagination(무한 증가 목록 필수, 기본 20건)

## 도메인 핵심

- 실효 근무 스케줄 우선순위: **상세 로타 오버라이드(work_schedule 행) > 정기 패턴(SchedulePattern: cycleWeeks·anchorMonday·주차×요일 슬롯 투영)**, 둘 다 없으면 **미설정=휴무**(개인 기본값은 V84에서 폐지) — `ScheduleAdminService`/`SchedulePatternResolver`. 야간 교대는 `crossesMidnight`(종업 익일)
- 출결 스탬프는 2단계: check(사전 검사+토큰 발급) → 확정(SHA-256 해시로 변조 탐지)
- 휴가: 내부 수량은 **분(minutes)** 단위, 표시만 `standardDayMinutes` 기준 일(days) 환산 — `LeaveDtos`
- 근태 마감: 멤버 월 마감 신청 `REQUESTED → APPROVED/REJECTED`, 취소는 REQUESTED 행 삭제 — `attendance/close/`

## 주의점

- 문서(`docs/`, README)보다 **코드가 정본** — docs는 패치 노트/계획 위주라 낡았을 수 있음
- 주석 언어는 한국어. 주석에 규약·의도가 촘촘히 적혀 있으니 수정 전 해당 파일 javadoc부터 읽을 것
- 민감 필드는 `FieldCipher`(AES-256-GCM) 암호화 저장 + 응답 마스킹(`Masking`). 카드 원본(PAN/CVC)은 어떤 형태로도 저장 금지, PG 빌링키는 응답 비노출
- 공휴일 외부 API(Nager.Date)의 치환 지점은 `app.holiday.nager.base-url` 하나뿐
- 세션은 서버 인메모리 — 다중 인스턴스 확장 시 공유 세션 저장소 필요(application.properties 주석)
