# 이메일 온보딩 · 비밀번호 재설정 상세 설계 — Phase 3

- 상위 문서: [plan-saas-multitenancy.md](../history/plan-saas-multitenancy.md) / 결정 기록: [patch-notes-2026-07-saas.md](../history/patch-notes/patch-notes-2026-07-saas.md)(D10~D20)
- 범위: Phase 2의 **초기 비밀번호 1회 표시 방식(D11)을 이메일 초대로 대체**하고, 같은 토큰 메커니즘 위에
  **비밀번호 찾기**와 **멤버 삭제(오송신 수습)** 를 얹는다 — backend-api.md §1.4의 "Phase 3 이연" 항목의 확정.
- 확정 전제(소유자 확정):
  - 멤버 등록 = TENANT_ADMIN이 이메일로 추가 → 본인 확인 메일 → 링크에서 비밀번호 설정 → 사용 가능.
    발송 전 이메일 재확인 단계(오송신 방지 UX) + 오송신 수습으로 정지(기존)·**삭제(신규)**.
  - 비밀번호 찾기는 같은 메커니즘·다른 본문. 변경 후 기존 세션 전부 무효화(재로그인 필수).
  - 메일 언어 = **테넌트 소재국**(KR→한국어, JP→일본어, 그 외→영어 폴백[방어] — D20의 country 축 재사용.
    소재국의 정본은 **tenant.country** — holiday-plan §1-1의 승격이 전제. NOT NULL DEFAULT 'KR' + 생성 시
    KR/JP 검증이라 "미등록" 상태는 존재하지 않는다 — 교차 리뷰 CR3-1).
    템플릿은 DB 저장 + SYSTEM_ADMIN 관리 화면(미리보기·수정) — 언어 마스터 패턴 재사용, Flyway 시드.
    개발·테스트는 페이크 SMTP(로그 출력형), 운영은 환경변수 `SMTP_HOST/PORT/USER/PASS/FROM`.

표기: backend-api.md와 동일(record DTO, 어노테이션 매퍼, `ApiException` 코드+메시지 키, Swagger 설명은 메시지 키) — 구현 지시 수준의 설계다.

---

## 1. 플로우 다이어그램

### 1-1. 초대(INVITE) — 멤버 등록

```
[TA]  W009 등록 폼(이메일/이름/부서) → 발송 전 확인(인라인 패널에 입력 이메일 강조 재표시 → 확인)
  → POST /api/v1/tenant/members
       [Tx] users INSERT(status=PENDING, password_hash=사용불능 플레이스홀더)
            + user_token INSERT(purpose=INVITE, TTL 72h, SHA-256 해시만)
       [Tx 밖] 메일 발송 — 실패해도 멤버·토큰은 존재(응답 mailSent=false → 에러 표시 + 재발송 버튼)
[멤버] 메일 링크 {테넌트 서브도메인 또는 루트}/?token=...
  → 프론트 기동: 쿼리의 token만 읽고 history.replaceState로 즉시 제거 → navigate('W010')
  → POST /auth/password/verify {token}(이름/회사명/마스킹 이메일/만료 표시) → 비밀번호 입력
  → POST /auth/password {token, password}
       [Tx] BCrypt 저장 + status PENDING→ACTIVE + password_changed_at=NOW + 토큰 used_at=NOW
  → W001 로그인 → 사용 시작
```

상태 전이(UserStatus — V4에서 예약된 PENDING을 이제 실사용):

```
(등록) → PENDING ──(비밀번호 설정)──> ACTIVE <──(정지/재개)──> DISABLED
            │  └─(재발송: 구 토큰 무효 + 신규 발급, PENDING 유지)
            └──(삭제)──> deleted=TRUE  (ACTIVE/DISABLED에서도 삭제 가능)
  ※ PENDING은 로그인 불가(기존 authenticate가 ACTIVE만 허용 — 코드 변경 불요). PENDING 대상의 상태 변경
    API는 400 — 비밀번호 미설정 계정이 ACTIVE가 되는 경로 차단(§4.2)
```

### 1-2. 재설정(RESET) — 비밀번호 찾기

```
[유저] W001 "비밀번호를 잊으셨나요?" → W011
  → POST /auth/password/reset-request {tenantCode?, email}
       테넌트 스코프: 서브도메인 접속이면 호스트가 확정(불일치 400 — 로그인과 동일 규칙 D19), 루트면 코드 필수
       응답은 계정 존재와 무관하게 202 통일(존재 비노출) + 레이트 리밋(§9)
       내부: status=ACTIVE 계정일 때만 — 기존 RESET 토큰 무효화 + 신규 발급(TTL 30m) + 메일 발송
  → 메일 링크 → W010(초대와 동일 화면·동일 set API) → 비밀번호 설정
       [Tx] hash 교체 + password_changed_at=NOW + used_at=NOW (status 불변)
[기존 세션] SessionRevalidationInterceptor: 세션 issuedAt < password_changed_at → 즉시 무효화(§5) → 전 단말 재로그인
```

---

## 2. DB — V7 마이그레이션 (자기 몫의 DDL)

**V7은 단일 파일 `V7__phase3.sql`**로, 스케줄/공휴일 계획의 DDL과 한 파일에 합류한다(V4/V5의 단일 파일 원칙 계승). 아래는 이 문서 몫의 블록만이며, 섹션 주석 `[E1]~[E4]`(Email)로 구분한다. 전 구문 재실행 내성(V4 방식).

**블록 순서(3문서 합의 — 교차 리뷰 CR3-4 확정)**: `[H-1]~[H-4]`(공휴일 — tenant.country 승격이 전 도메인의
전제라 최선두) → `[S-1]`(스케줄 — users 근무 기본값) → `[E1]~[E4]`(본 문서) → **언어 마스터 시드**(H→S→E 순,
각 도메인 구획 주석 유지). users ALTER가 [S-1]/[E3]/[E4] 3문으로 나뉘지만 **단일 ALTER로 병합하지 않는다** —
도메인 구획 소유(D17 병렬 워크트리 충돌 최소화)가 리빌드 1회 절약보다 우선하고, 각 문이 IF NOT EXISTS로
멱등이라 상호 무해하다. 단 [E4]의 UNIQUE 신구 교체는 **단일문 원자성 유지**(V4 [3-4] 관례).

```sql
-- [E1] user_token: 초대/재설정 토큰 — 원문 비저장(SHA-256 해시만), 1회용. TTL은 앱이 부여.
CREATE TABLE IF NOT EXISTS user_token (
    token_hash CHAR(64)    NOT NULL COMMENT '토큰 SHA-256 해시(hex) — 원문은 어디에도 저장하지 않음',
    tenant_id  BIGINT      NOT NULL COMMENT '테넌트 ID',
    user_id    BIGINT      NOT NULL COMMENT '유저 ID',
    purpose    VARCHAR(10) NOT NULL COMMENT '용도(INVITE/RESET)',
    expires_at DATETIME    NOT NULL COMMENT '만료 시각',
    used_at    DATETIME    NULL COMMENT '사용 시각(NULL만 유효 — 1회용)',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각',
    PRIMARY KEY (token_hash),
    KEY idx_user_token_user (tenant_id, user_id),
    CONSTRAINT fk_user_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_user_token_user   FOREIGN KEY (user_id)   REFERENCES users (user_id),
    CONSTRAINT ck_user_token_purpose CHECK (purpose IN ('INVITE', 'RESET'))
) COMMENT '유저 토큰(초대/비밀번호 재설정) — 해시만 저장, 1회용';

-- [E2] mail_template: 메일 템플릿 — 행 집합은 시드 6행(purpose×lang) 고정, 수정만 허용
CREATE TABLE IF NOT EXISTS mail_template (
    purpose     VARCHAR(10)  NOT NULL COMMENT '용도(INVITE/RESET)',
    lang        VARCHAR(5)   NOT NULL COMMENT '언어(KOR/ENG/JPN)',
    subject     VARCHAR(200) NOT NULL COMMENT '제목({변수} 치환)',
    body        TEXT         NOT NULL COMMENT '본문(텍스트 메일, {변수} 치환)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (purpose, lang),
    CONSTRAINT ck_mail_template_purpose CHECK (purpose IN ('INVITE', 'RESET'))
) COMMENT '메일 템플릿(SYSTEM_ADMIN 관리)';
-- 자연키 (purpose, lang)가 그대로 PK — 행 집합이 코드(enum×언어)로 닫혀 있어 대리키 불요
-- 시드(INSERT IGNORE — 관리자가 수정한 행은 덮지 않는다, V3/V5 방식). 실값은 §7 초안.
INSERT IGNORE INTO mail_template (purpose, lang, subject, body) VALUES
('INVITE','KOR','...','...'), ('INVITE','ENG','...','...'), ('INVITE','JPN','...','...'),
('RESET','KOR','...','...'),  ('RESET','ENG','...','...'),  ('RESET','JPN','...','...');

-- [E3] users.password_changed_at: 재로그인 강제의 기준 시각
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at DATETIME NULL
        COMMENT '비밀번호 최종 변경 시각 — 이전 발급 세션은 무효(NULL=이력 없음, 기존 유저)' AFTER password_hash;

-- [E4] 소프트 삭제 후 재등록 허용: UNIQUE(tenant_id,email)이 삭제 행까지 점유하는 문제 해소.
--      email_key = 활성 행만 채워지는 생성 컬럼(NULL은 UNIQUE 비충돌, 앱 유지보수 불요)
--      → 오송신 삭제 후 같은/올바른 이메일 재등록 가능, 삭제 행의 email 원문은 감사용 보존.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_key VARCHAR(100)
        AS (CASE WHEN deleted THEN NULL ELSE email END) PERSISTENT
        COMMENT '활성 행의 이메일 사본(UNIQUE 전용 — 삭제 행은 NULL)' AFTER email,
    ADD UNIQUE KEY IF NOT EXISTS uk_users_tenant_email_key (tenant_id, email_key),
    DROP KEY IF EXISTS uk_users_tenant_email;
```

- **[E4] 결정 근거**: 대안 "삭제 시 email을 `{email}#del{userId}`로 개서"는 컬럼 폭 초과 위험과 감사 원문 훼손으로
  기각. 생성 컬럼은 MariaDB PERSISTENT + UNIQUE 지원으로 원자 적용.
- PENDING 멤버의 `password_hash`는 NOT NULL 유지 + **사용 불능 플레이스홀더**(BCrypt(SecureRandom 64B)) —
  스키마 불변식 완화보다 안전. 로그인은 어차피 `status=ACTIVE`만 통과.
- 만료 토큰 청소: 발급 시 부수 실행(`DELETE ... WHERE expires_at < NOW() - INTERVAL 30 DAY` —
  `deleteExpiredChecks` 패턴 계승. 30일 보존은 오송신 감사 추적용).

---

## 3. 토큰 설계 (정본)

| 항목 | 결정 | 근거 |
|---|---|---|
| 생성 | `SecureRandom` 32바이트 → Base64URL(43자, 패딩 없음) | 256bit 엔트로피 — 추측·브루트포스 불가 |
| 저장 | **SHA-256 hex 해시만**(원문 비저장) | DB 덤프 유출 시에도 링크 재구성 불가 — attendance_check가 원문 대신 해시를 남기는 것과 동일한 "원문 비저장" 원칙 |
| 검증 | 입력 토큰을 해시 → PK 조회. 부존재/만료/사용 완료 **전부 동일 404** | 사유 비노출(열거 방지). 해시 조회라 타이밍 균일 |
| 1회용 | 사용 시 `used_at=NOW()` (같은 Tx) | 재사용 차단 + 성공 감사 흔적 |
| TTL | INVITE **72h** / RESET **30m** | 초대는 수신자 부재 허용, 재설정은 탈취 창 최소화 |
| 무효화 | ①재발송 시 같은 (user, purpose)의 기존 행 DELETE ②정지·삭제 시 그 유저의 전 토큰 DELETE ③설정 성공 시 잔여 토큰 전부 DELETE — 즉 (user, purpose)당 유효 토큰은 항상 1개 이하 + 재설정 요청 레이트 리밋(§9) | "살아있는 링크는 최신 1개 이하" 불변식, 메일 폭탄 방지 |

신규 파일: `user/TokenPurpose.java`(enum), `user/UserToken.java`(record), `user/UserTokenMapper.java`(`insert`/`findByHash`/
`markUsed`/`deleteByUser`/`deleteByUserAndPurpose`/`deleteExpired` — 매퍼 규약대로 tenantId 첫 `@Param`. 단 `findByHash`는 해시가
전역 유일 PK이므로 규약의 예외로 명기: 토큰 행이 tenant_id를 보유), `user/UserTokenService.java`(발급·검증·무효화 + SHA-256).

---

## 4. API 계약 전량

### 4.1 공개: 비밀번호 설정/재설정 `/api/v1/auth/password` (`auth/PasswordController.java`)

WebConfig의 authInterceptor excludePathPatterns에 `/api/v1/auth/password/**` 추가(공개 4번째 경로). 토큰은 URL이 아닌 **바디로만** 받는다(액세스 로그·Referer 유출 방지).

| 메소드/경로 | 요청 DTO | 응답 / 상태 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `POST /auth/password/verify` | `TokenVerifyRequest{token}` | 200 `TokenVerifyResponse` | 404 `TOKEN_INVALID` / `auth.token.invalid`(부존재·만료·사용 완료 통일), 429 `RATE_LIMITED` / `auth.login.rate-limited` |
| `POST /auth/password` | `PasswordSetRequest{token, password}` | 204 | 404 `TOKEN_INVALID`(동일 통일), 400 `INVALID_INPUT` / `validation.password.*`(기존 `PASSWORD_PATTERN` 재사용), 429 `RATE_LIMITED` |
| `POST /auth/password/reset-request` | `PasswordResetRequest{tenantCode?, email}` | **202**(바디 없음 — 계정 존재와 무관하게 통일) | 400 `TENANT_CODE_REQUIRED` / `validation.tenant-code.required`(루트 접속 + 코드 없음), 400 `TENANT_CODE_MISMATCH` / `auth.login.tenant-mismatch`(호스트 불일치 — `AuthController.resolveTenantCode` 재사용), 429 `RATE_LIMITED` |

DTO(`auth/AuthDtos.java`에 추가 — 필드 전체):

- `TokenVerifyRequest(String token @NotBlank)` / `PasswordSetRequest(String token @NotBlank, String password @NotBlank @Pattern(MemberDtos.PASSWORD_PATTERN))` — 둘 다 **`toString()` 오버라이드 필수**(`token=***` — D-F 규약 확장).
- `TokenVerifyResponse(TokenPurpose purpose /* W010 안내 분기 */, String name, String emailMasked /* Masking.email() 신규 — 로컬파트 첫 글자만 h***@acme.co.kr */, String tenantName, LocalDateTime expiresAt)`.
- `PasswordResetRequest(String tenantCode /* 서브도메인 접속이면 생략 가능(호스트 우선 — D19) */, String email @NotBlank)`.

- `verify`/`set`은 **테넌트 스코프를 요구하지 않는다** — 토큰 행이 (tenant_id, user_id)를 보유하므로 토큰이 곧 스코프.
- `set` 트랜잭션: 토큰 검증 → 유저 로드(`deleted=FALSE`, INVITE면 PENDING·RESET이면 ACTIVE 기대 — 어긋나면 통일 404)
  → hash 교체 + INVITE는 `status='ACTIVE'` + `password_changed_at=NOW()` + `markUsed` + 잔여 토큰 DELETE.
- `reset-request` 내부: 테넌트 해석(호스트 우선) → `findByEmail` → **ACTIVE일 때만** 발급·발송. PENDING/DISABLED/
  부존재/테넌트 부존재/SMTP 실패 — 전부 동일 202(실패는 ERROR 로그만. 오류 응답 자체가 존재/상태 오라클이 된다).

### 4.2 변경·신규: 멤버 관리 `/api/v1/tenant/members` (TENANT_ADMIN 전용 — 화이트리스트 불변)

| 메소드/경로 | 요청 DTO | 응답 / 상태 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `POST /tenant/members` **(변경)** | `MemberCreateRequest`(기존 필드 불변 + 스케줄 계획의 `workStart`/`workEnd` 선택 필드 — work-schedule.md §5-1과 병합, CR3-3) | 201 `MemberCreateResponse` — **initialPassword 삭제**, `status=PENDING`, `mailSent`·`inviteExpiresAt` 추가 | 409 `EMAIL_DUPLICATED`(활성 행 기준 — email_key) |
| `POST /tenant/members/{userId}/invite` **(신규, 재발송)** | (없음) | 200 `InviteResponse` | 404 `MEMBER_NOT_FOUND`(타 테넌트·SYSTEM_ADMIN — `requireManageableMember` 재사용), 409 `MEMBER_NOT_PENDING` / `member.invite.not-pending`(ACTIVE/DISABLED 대상) |
| `DELETE /tenant/members/{userId}` **(신규, 소프트 삭제)** | (없음) | 204 | 404 `MEMBER_NOT_FOUND`(동상), 400 `MEMBER_SELF_DELETE` / `member.delete.self`(자기 자신), 409 `LAST_TENANT_ADMIN` / `member.last-admin`(기존 guard 재사용) |
| `GET /tenant/members` **(응답 확장)** | (없음) | 200 `List<MemberResponse>` — `inviteExpiresAt`(nullable) 추가 | — |
| `PUT /{userId}/status` **(가드 추가)** | `MemberStatusRequest` | 200 `MemberResponse` | 기존 + **대상이 PENDING이면 400 `MEMBER_STATUS_INVALID`**(비밀번호 미설정 계정의 ACTIVE화 차단 — 수습은 재발송/삭제로) |

```java
// ▼ 통합 최종 계약(이메일 × 스케줄 병합 — 교차 리뷰 CR3-3 확정. work-schedule.md §5-1과 동일 필드 집합)
public record MemberCreateResponse(              // initialPassword 필드 삭제 — 초기 비밀번호 방식 폐지
        long userId, String email, String name, String departCd,
        Role role, UserStatus status,            // 항상 MEMBER / PENDING
        String workStart, String workEnd,        // 스케줄 몫("HH:mm") — 미지정 등록은 09:00/18:00
        boolean mailSent,                        // 발송 실패해도 201(멤버는 생성됨) — false면 재발송 유도
        LocalDateTime inviteExpiresAt) {
}
public record InviteResponse(long userId, String email, boolean mailSent, LocalDateTime inviteExpiresAt) {
}
// MemberResponse 최종 = 기존 필드(userId,email,name,departCd,role,status,createdAt)
//   + workStart/workEnd(스케줄 몫) + inviteExpiresAt(nullable — 이 문서 몫:
//     PENDING+유효 INVITE 토큰이면 그 만료시각, 아니면 null(만료/실패 → "재발송 필요" 표시))
```

- **삭제 = 소프트 삭제**(`users.deleted=TRUE` — 출결 기록 보존, FK user_id 잔존) + 같은 Tx에서 `deleteByUser`(토큰 전멸).
  활성 세션은 기존 `SessionRevalidationInterceptor`가 `findById`(deleted 제외) null → 즉시 회수 — 추가 장치 불요.
  가드 순서: 자기 자신 400 → `requireManageableMember`(타 테넌트·SYSTEM_ADMIN 404) → 대상이 ACTIVE TENANT_ADMIN이면
  `guardLastTenantAdmin`(FOR UPDATE 카운트) 409.
- 메일 발송은 **등록 트랜잭션과 분리**: `MemberService.create` Tx(users+user_token INSERT) 커밋 후 `MemberInviteService`가
  발송. 발송 예외는 삼키고 `mailSent=false`로 응답(멤버·토큰은 유효 — 재발송이 수습 경로).
  재발송 = 기존 INVITE 토큰 DELETE + 신규 발급(72h 리셋) + 발송 — 구 링크는 즉시 무효(오송신 수습 겸용).
- **`UserMapper.existsByEmail` 변경 필수(CR3-8)**: 현행 쿼리는 `deleted` 필터가 없어 삭제 행이 앱 레벨 중복
  검사에 계속 걸린다 — [E4] 후에도 재등록(DEL-02)이 409로 막히는 누락. `AND deleted = FALSE`(또는
  `email_key IS NOT NULL` 기준)로 교체해 UNIQUE(email_key)와 판정 기준을 일치시킨다.

### 4.3 변경: 테넌트 생성 `/api/v1/system/tenants` (최초 관리자도 초대로 통일)

초기 비밀번호 방식을 **양쪽 경로 모두** 폐지해야 `generateInitialPassword`와 평문 반환 코드가 완전히 사라진다(멤버만 전환하면 위험 코드 경로 잔존). D11의 "Phase 3에서 초대 링크로 대체" TODO의 이행.

| 메소드/경로 | 변경 내용 | 에러 |
|---|---|---|
| `POST /system/tenants` | `TenantCreateResponse`에서 **initialPassword 삭제** → `adminStatus`(=PENDING)·`mailSent` 추가. 관리자 계정은 TENANT_ADMIN/PENDING으로 생성 + INVITE 발송({inviterName}=SA 세션 name) | (기존과 동일 + 공휴일 계획의 400 `COUNTRY_UNSUPPORTED`) |
| `POST /system/tenants/{tenantId}/admin-invite` **(신규, 재발송)** | 대상 = 그 테넌트의 PENDING인 TENANT_ADMIN **1명**일 때 재발송. 0명/2명 이상이면 409(모호성 거부) | 404 `TENANT_NOT_FOUND`, 409 `TENANT_ADMIN_INVITE_INVALID` / `tenant.admin-invite.invalid` |

- **소재국(country) — 정본 정정(교차 리뷰 CR3-1)**: 이 문서 초안은 "생성 요청에 country를 넣는 안 기각
  (tenant_profile 단일 소스)"이었으나, **공휴일 계획(holiday-plan §1-1/§4)이 tenant.country 승격 +
  `TenantCreateRequest.country` 필수 + tenant_profile.country 제거를 확정**했고 스케줄 계획도 이를 전제한다
  — 공휴일 문서가 정본(단일 출처는 tenant.country로 이동, 이원화 없음 — D20의 취지 유지). 따라서:
  - 생성 시점에 소재국이 확정되므로 **최초 관리자 초대 메일도 처음부터 소재국 언어**(KR→KOR, JP→JPN)로
    발송된다. "영어 폴백 후 소재국 등록 시 재발송" 시나리오는 폐기. `admin-invite` 재발송은
    `mailSent=false`(SMTP 실패)·미수신 수습 용도로 존속.
- **통합 최종 계약(이메일 × 공휴일 병합 — CR3-5)**:
  `TenantCreateResponse(tenantId, tenantCode, name, country, status, adminUserId, adminEmail,
  adminStatus /*=PENDING*/, mailSent, holidaysSynced)` — country·holidaysSynced는 공휴일 몫(holiday-plan §4-1).
- **생성 플로우 합성(CR3-5)**: `TenantService.create` Tx(tenant INSERT + 관리자 users INSERT(PENDING) +
  INVITE 토큰 INSERT) 커밋 → ① INVITE 메일 발송(실패 시 `mailSent=false`) → ② 당해·익년 공휴일 sync
  (실패 시 `holidaysSynced=false` — holiday-plan §2-5) → 응답. 두 후처리는 **상호 독립**(각자 예외 삼킴,
  플래그·수습 경로 분리: 메일=admin-invite 재발송, 공휴일=W013 수동 동기화)이라 간섭 없음. 메일을 먼저
  두는 이유: 외부 API 대기(최악 수 초×4)가 초대 발송을 지연시키지 않게.

### 4.4 신규: 메일 템플릿 관리 `/api/v1/admin/mail-templates` (SYSTEM_ADMIN — 글로벌 제품 자산)

컨트롤러: `mail/MailTemplateController.java`. 행 집합은 시드 6행 고정 — **생성/삭제 API는 두지 않는다**(purpose는
코드 enum이 단일 출처, 언어 마스터와 달리 키가 열려 있지 않다). 수정 + 미리보기만.

| 메소드/경로 | 요청 DTO | 응답 / 상태 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `GET /admin/mail-templates` | (없음) | 200 `List<MailTemplateResponse>` | — |
| `PUT /admin/mail-templates/{purpose}/{lang}` | `MailTemplateUpdateRequest{subject, body}` | 200 `MailTemplateResponse` | 404 `MAIL_TEMPLATE_NOT_FOUND` / `mail-template.not-found`(미지 purpose/lang), 400 `MAIL_TEMPLATE_UNKNOWN_VAR` / `mail-template.unknown-var`(허용 외 `{...}`, 인자로 변수명), 400 `MAIL_TEMPLATE_ACTION_URL_REQUIRED` / `mail-template.action-url.required`(본문에 `{actionUrl}` 부재 — 링크 없는 메일은 기능 불능) |
| `POST /admin/mail-templates/preview` | `MailTemplatePreviewRequest{purpose, lang, subject, body}` | 200 `{subject, body}` — **저장하지 않고** 샘플 값으로 치환한 결과 | 400 동상(저장과 같은 검증을 미리보기에서 먼저 통과 가능) |

- **RoleInterceptor RULES 변경(중요)**: 현행 `/api/v1/admin/i18n/**` 규칙을 **`/api/v1/admin/**` 로 일반화**.
  RULES는 "미등록 경로 = 통과"라 개별 등록은 다음 admin API 추가 때 같은 함정을 반복한다 — admin 하위는 전부
  SYSTEM_ADMIN 글로벌 자산으로 선언(WebConfig 등록 경로는 이미 `/api/v1/admin/**`).
- 치환 누락 검증(2층): ①**저장/미리보기 시** — 허용 변수 INVITE = `{memberName, tenantName, actionUrl, expiresAt,
  inviterName}`, RESET = inviterName 제외 4종. `\{[a-zA-Z]+\}` 전 매치가 집합 밖이면 400, `{actionUrl}`은 본문 필수
  (모든 변수를 다 쓸 필요는 없다). ②**발송 시(방어)** — 치환 후 잔존 플레이스홀더 검출 시 발송 중단 + ERROR 로그(DB 직수정 대비).

---

## 5. 재로그인 강제 — SessionUser.issuedAt + 재검증 확장

```java
// auth/SessionUser.java — issuedAt(세션 발급 시각) 필드 추가
public record SessionUser(long userId, long tenantId, String tenantCode, String tenantName,
        String email, String name, Role role, LocalDateTime issuedAt) implements Serializable { ... }
// auth/SessionRevalidationInterceptor.java — 유저/테넌트 상태 검사 다음에 1검사 추가
if (current.passwordChangedAt() != null
        && sessionUser.issuedAt().isBefore(current.passwordChangedAt())) {
    invalidateKeepingLang(request, session);   // 비밀번호 변경 이전 발급 세션 → 즉시 회수
    return true;
}
```

- 발급 지점: `AuthController.login`. 재검증의 role 갱신 경로는 **원래 issuedAt을 보존**한 새 스냅샷으로 교체(재검증이
  세션을 "연장"하지 않게). `User`에 `passwordChangedAt` 추가, `UserMapper.updatePassword(tenantId, userId, passwordHash)`
  신규 — SQL에서 `password_changed_at = NOW()` 동시 세팅(시각 이원화 방지). 정밀도는 DATETIME 초 단위 — 변경과
  같은 초에 발급된 세션은 생존 가능하나 그 세션은 변경 직후의 본인 로그인뿐(무해).
- 배포 주의: SessionUser 직렬화 형태 변경으로 **배포 시점의 기존 세션은 전원 재로그인**(역직렬화 실패 = 세션 무효,
  안전한 실패). 릴리즈 노트에 명기.

---

## 6. 메일 발송 구성

### 6.1 구성 요소 (`mail/` 신규 패키지)

| 파일 | 내용 |
|---|---|
| `MailSender.java` | 인터페이스 `void send(String to, String subject, String body)` — 텍스트 메일 단일 메소드(스프링 `JavaMailSender`를 직접 노출하지 않아 페이크 치환점이 됨) |
| `SmtpMailSender.java` | `@Profile("prod")` — spring-boot-starter-mail의 `JavaMailSender` 위임, From은 `app.mail.from` |
| `LoggingMailSender.java` | `@Profile("!prod")` — 발송 대신 INFO 로그로 to/subject/body 전문 출력(dev 전용이므로 actionUrl 포함 — 로컬 수동 테스트·E2E가 로그에서 링크를 취득). 발송 내용을 노출하는 조회 API는 **두지 않는다** |
| `MailTemplate.java` / `MailTemplateMapper.java` / `MailTemplateService.java` | record + 매퍼(`findAll`/`find(purpose, lang)`/`update` — 글로벌 테이블, tenantId 규약 예외로 LanguageMapper와 동급) + 서비스(로드·검증(§4.4)·치환 렌더·미리보기 샘플 값: 홍길동/에이크미(주)/예시 URL/발송 시각+TTL/김관리) |
| `MailLanguageResolver.java` | **tenant.country**(holiday-plan §1-1 승격 후의 정본 — CR3-1) → KR=KOR, JP=JPN, **그 외=ENG**(방어 — NOT NULL DEFAULT 'KR' + 생성 검증으로 실측 도달 불가). 미지원 국가 문자열이 와도 ENG |
| `user/MemberInviteService.java` | 초대·재설정 발송 오케스트레이션: 토큰 발급(UserTokenService) → 링크 조립 → 언어 해석 → 렌더 → MailSender. 등록 Tx 밖에서 호출 |

### 6.2 링크 조립 (프론트 진입점 계약)

```
서브도메인 활성(app.tenant.base-domain 설정): https://{tenantCode 소문자}.{base-domain}/?token={원문}
그 외:                                      {app.mail.link-base-url}/?token={원문}
```

- 신규 설정 `app.mail.link-base-url`(예: `https://webatt.example`, dev 기본값 `http://localhost:5173`).
  **요청 Host를 링크 조립에 쓰지 않는다**(Host 헤더 주입 피싱 링크 차단 — 서버 구성값만).
- `?token=` 쿼리 파라미터가 유일한 진입 계약. **URL 라우팅 도입이 아니다** — 프론트가 이 파라미터만 읽고 즉시
  제거 후 서버 주도 화면 전개(navigate)로 합류(§8.1).

### 6.3 환경변수 / 프로파일

```properties
# application-prod.properties 추가분 (미설정 시 기동 실패 — APP_CRYPTO_KEY와 동일한 fail-fast)
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT}
spring.mail.username=${SMTP_USER}
spring.mail.password=${SMTP_PASS}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.mail.from=${SMTP_FROM}
app.mail.link-base-url=${APP_MAIL_LINK_BASE_URL}
# application.properties(공통) — dev는 LoggingMailSender라 SMTP 설정 불요
app.mail.link-base-url=${APP_MAIL_LINK_BASE_URL:http://localhost:5173}
```

- pom.xml: `spring-boot-starter-mail` 추가. dev에서는 host 미설정으로 자동구성이 빈을 만들지 않으므로
  `SmtpMailSender`만 `@Profile("prod")`로 가두면 충돌 없음.

---

## 7. 메일 템플릿 시드 초안 (V7 [E2]의 실값 — 간결한 텍스트 메일)

변수 위치 포함 확정 초안. `{expiresAt}`은 서버 시각대 `yyyy-MM-dd HH:mm` 렌더(언어별 포맷 분화는 후속 과제). **비밀번호·평문 개인정보는 본문에 싣지 않는다** — 토큰 링크가 유일한 비밀(§9).

| purpose/lang | subject |
|---|---|
| INVITE/KOR | `[{tenantName}] 출결 시스템 초대 — 비밀번호를 설정해 주세요` |
| INVITE/ENG | `[{tenantName}] You're invited — set your password` |
| INVITE/JPN | `[{tenantName}] 勤怠システムへのご招待 — パスワード設定のお願い` |
| RESET/KOR | `[{tenantName}] 비밀번호 재설정 안내` |
| RESET/ENG | `[{tenantName}] Password reset request` |
| RESET/JPN | `[{tenantName}] パスワード再設定のご案内` |

INVITE/KOR 본문(전 템플릿 공통 구조 — 인사/안내/링크/만료·1회용/오송신 무시 안내):

```
{memberName}님, 안녕하세요.

{inviterName}님이 {tenantName}의 출결 시스템에 회원님을 초대했습니다.
아래 링크에서 비밀번호를 설정하면 바로 이용하실 수 있습니다.

{actionUrl}

이 링크는 {expiresAt}까지 1회만 사용할 수 있습니다.
본인에게 온 메일이 아니라면 무시해 주세요.
```

- INVITE/ENG: `Hello {memberName}, / {inviterName} has invited you to {tenantName}'s attendance system. Set your password at the link below to get started. / {actionUrl} / This link can be used once and expires at {expiresAt}. / If you did not expect this email, please ignore it.`
- INVITE/JPN: `{memberName} 様 / {inviterName} 様が {tenantName} の勤怠システムにあなたを招待しました。以下のリンクからパスワードを設定すると、すぐにご利用いただけます。 / {actionUrl} / このリンクは {expiresAt} まで、1回のみ有効です。 / お心当たりのない場合は、このメールを破棄してください。`

RESET 본문(단락 구분은 INVITE와 동일 — `/`가 빈 줄):

- RESET/KOR: `{memberName}님, 안녕하세요. / {tenantName} 출결 시스템의 비밀번호 재설정 요청을 받았습니다. 아래 링크에서 새 비밀번호를 설정해 주세요. / {actionUrl} / 이 링크는 {expiresAt}까지 1회만 사용할 수 있습니다. / 요청하신 적이 없다면 이 메일을 무시해 주세요. 비밀번호는 변경되지 않습니다.`
- RESET/ENG: `Hello {memberName}, / We received a request to reset your password for {tenantName}. Set a new password at the link below. / {actionUrl} / This link can be used once and expires at {expiresAt}. / If you did not request this, please ignore this email. Your password will not change.`
- RESET/JPN: `{memberName} 様 / {tenantName} 勤怠システムのパスワード再設定のリクエストを受け付けました。以下のリンクから新しいパスワードを設定してください。 / {actionUrl} / このリンクは {expiresAt} まで、1回のみ有効です。 / お心当たりのない場合は破棄してください。パスワードは変更されません。`

---

## 8. 프론트 변경

### 8.1 진입점 — 토큰 캡처 (AppContext)

`AppProvider` 기동 시(첫 navigate 전에 1회):

```
① token = new URLSearchParams(location.search).get('token') — 있으면 ref(메모리)에만 보관
② history.replaceState(null, '', location.pathname) — 주소창·히스토리에서 즉시 제거
③ token 있으면 navigate('W010'), 없으면 기존대로 navigate()
```

토큰은 state가 아닌 ref(리렌더 무관·직렬화 없음)로 W010에 전달, 화면 이탈/완료 시 즉시 클리어(D18의 민감 입력 클리어 정책). 서버 주도 전개 규칙은 불변 — W010은 공개 화면이라 서버가 그대로 전개한다.

### 8.2 화면 3종 + Screen enum / decide()

| 코드 | 화면 / 컴포넌트 | 허용 role | 명세 |
|---|---|---|---|
| **W010** | 비밀번호 설정 `PasswordSetupScreen` | 공개(토큰 필요) | 진입 즉시 verify → 성공: purpose별 안내(초대 환영/재설정) + 이름·회사명·마스킹 이메일·만료 표시, 새 비밀번호+확인 입력(불일치 인라인 에러), 제출 → 완료 패널 + 로그인 버튼(navigate('W001')). verify/set 404: 무효 안내 + "재설정 다시 요청"(navigate('W011')). 토큰 없이 전개되면 W011 안내로 폴백 |
| **W011** | 비밀번호 재설정 요청 `PasswordResetRequestScreen` | 공개 | `hostTenantName` 있으면 코드란 숨김·회사명 표시(LoginScreen 패턴), 아니면 tenantCode 입력. email 입력 → 202 후 **입력값과 무관하게 동일 완료 문구**. W001에 "비밀번호를 잊으셨나요?" 링크 추가 |
| **W012** | 메일 템플릿 관리 `MailTemplatesScreen` | {SYSTEM_ADMIN} | AdminScreen(W004) 패턴 — purpose/lang 선택 → subject/body 편집 + 허용 변수 힌트, [미리보기]=preview API 결과를 우측 패널에, [저장]=PUT. 400(미지 변수/actionUrl 누락)은 폼 인라인 표시. 헤더 SYSTEM_ADMIN 메뉴에 W012 추가 |

```java
// navigation/Screen.java 추가(3행)
PASSWORD_SETUP("W010", null),          // 공개 — 토큰 유효성은 API가 판정(화면 전개는 무조건)
PASSWORD_RESET("W011", null),          // 공개
MAIL_TEMPLATES("W012", Set.of(Role.SYSTEM_ADMIN)),
```

- `NavigationService.decide()`: 로그인 상태의 공개 화면 홈 리다이렉트는 현행대로 **LOGIN/INDEX만** — W010/W011은
  로그인 중에도 전개 허용(메일 링크는 기존 세션과 무관하게 동작해야 함 — 예: 다른 계정으로 로그인된 브라우저에서
  초대 링크 클릭). `homeOf` 불변. W012는 기존 role 게이트에 자동 편입(ROLE_DENIED → homeOf).
- 프론트: `types.ts` ScreenCode에 3코드 추가, `App.tsx` 스위치 3케이스, `endpoints.ts`에 `passwordApi`(verify/set/
  resetRequest)·`mailTemplateApi`(list/update/preview)·`tenantMemberApi.invite/remove` 추가.

### 8.3 MembersScreen(W009) 개편 — 초기 비밀번호 패널 제거 + 초대 운영

| 항목 | 변경 |
|---|---|
| 초기 비밀번호 패널 | **삭제**(`created`/`copyPassword`/`passwordRef` 제거) — 평문 비밀번호가 프론트 state에 실리는 일 자체가 없어진다 |
| 오송신 방지 UX | 등록 폼(이메일/이름/부서 + 스케줄 계획의 근무 시작/종료 time 입력 2개 — work-schedule.md §7-1과 병합, CR3-6) 제출 → 즉시 발송하지 않고 **인라인 확인 패널**(기존 pending 확인 패널 스타일): 입력 이메일을 강조 재표시 + "이 주소로 초대 메일을 발송합니다" + [발송]/[취소]. [발송]에서 비로소 POST(스케줄 필드 동봉). 오타 발견 시 취소 → 폼 값 유지 |
| 등록 결과 | `mailSent=true`: "초대 메일 발송됨" 안내. `mailSent=false`: 행 에러 + 재발송 버튼 유도(멤버는 목록에 PENDING으로 존재) |
| 목록 행(PENDING) | 상태 뱃지 "초대 대기" + `inviteExpiresAt` 표시(경과 시 "만료 — 재발송 필요"), [재발송] 버튼(즉시 실행 — 파괴적 조작 아님), **[삭제]** 버튼(인라인 확인 패널 — PendingAction에 `'DELETE'` 추가). 비활성 버튼은 비표시(§4.2 400과 정합) |
| 목록 행(ACTIVE/DISABLED) | 기존 조작 유지 + [삭제] 추가(자기 자신 행은 비표시 — 기존 self 가드 패턴) |
| W007 TenantsScreen | 초기 비밀번호 패널 삭제 → "관리자 초대 메일 발송됨"(소재국 언어로 발송 — CR3-1) 안내 + `mailSent=false`면 에러 표시, 테넌트 행에 [관리자 초대 재발송] 버튼(admin-invite API). 생성 폼의 소재국 셀렉트·`holidaysSynced=false` 안내는 공휴일 문서 몫(holiday-plan §4-3)과 같은 화면에서 합성 |

---

## 9. 보안 고려사항

| 위협 | 대책 | 지점 |
|---|---|---|
| 토큰 유출(DB 덤프)·추측·재사용 | 해시만 저장(링크 재구성 불가) + 256bit SecureRandom + 1회용(used_at) + TTL(72h/30m) | §3 |
| 오송신 잔존 링크 | 재발송·정지·삭제 시 토큰 DELETE — 잘못 간 링크의 즉시 무력화 수단 제공 | §4.2 |
| 계정/토큰 상태 열거 | reset-request 202 통일(부존재·PENDING·DISABLED·발송 실패 무관 — 로그인 401 통일과 동일 사상) + verify/set 404 단일 코드(부존재=만료=사용 완료) | §4.1 |
| 메일 폭탄/브루트포스 | `PasswordResetRateLimiter`(LoginRateLimiter 계승 — 인메모리 2단 슬라이딩 윈도우): reset-request 계정 키 5분 3회·IP 5분 30회, verify/set IP 5분 30회 — 초과 429 `RATE_LIMITED`. 카운팅은 계정 실존 여부와 무관(존재 오라클 방지) | auth/ |
| URL 토큰 유출(로그/Referer/히스토리) | API는 토큰을 바디로만 수수 + 프론트 replaceState 즉시 제거 + `SecurityHeadersFilter`의 Referrer-Policy 확인(미설정 시 `same-origin` 추가) | §4.1, §8.1 |
| Host 헤더 주입 피싱 | 링크 조립에 요청 Host 불사용 — 서버 구성값만 | §6.2 |
| 메일·로그의 민감정보 | 본문에 비밀번호·평문 개인정보 미탑재(토큰 링크만), verify 응답 이메일 마스킹. 토큰 포함 DTO는 `toString()` 오버라이드 필수(D-F 규약 확장). 운영(SmtpMailSender)은 본문 로그 금지 — 전문 로그는 LoggingMailSender(dev) 한정 | §4.1, §6.1, §7 |
| PENDING 계정 악용 | 로그인 불가(authenticate ACTIVE-only, 기존) + 상태 변경 API로 ACTIVE화 불가(400) — ACTIVE 전이는 토큰 경유 단일 경로 | §4.2 |
| 삭제 후/탈취 후 세션 잔존 | 삭제는 SessionRevalidationInterceptor의 findById null → 즉시 회수(기존 메커니즘, D18). 비밀번호 변경은 password_changed_at 이전 발급 세션 전부 무효(재설정이 곧 세션 킬 스위치) | §5 |
| 관리자 잠금/자폭 | 자기 자신 삭제 400, 마지막 활성 TENANT_ADMIN 삭제 409, SYSTEM_ADMIN 대상 404(전부 기존 가드 재사용) | §4.2 |

---

## 10. 신규 메시지 키 / 언어 마스터 키

### 10.1 백엔드 메시지 키 (`messages*.properties` — 한국어 대표값, 영/일 번역은 구현 단계. 기존 관례)

```properties
auth.token.invalid=유효하지 않거나 만료된 링크입니다.
validation.token.required=토큰이 필요합니다.
member.invite.not-pending=초대 대기 상태의 멤버가 아닙니다.
member.delete.self=자기 자신은 삭제할 수 없습니다.
tenant.admin-invite.invalid=재발송할 초대 대기 관리자가 없습니다.
mail-template.not-found=존재하지 않는 템플릿입니다.
mail-template.unknown-var=사용할 수 없는 변수입니다: {0}
mail-template.action-url.required=본문에 {actionUrl} 변수가 필요합니다.
```

재사용: `member.not-found`/`member.last-admin`/`member.status.invalid`/`auth.login.rate-limited`/
`auth.login.tenant-mismatch`/`validation.tenant-code.required`/`validation.email.*`/`validation.password.*`.
Swagger 키는 기존 네이밍으로 추가: `api.password.*`, `api.member.invite.*|delete.*`, `api.mail-template.*`,
`api.system-tenant.admin-invite.*`, `schema.field.email-masked|mail-sent|invite-expires-at` 등.

### 10.2 언어 마스터 시드 (V7 — `INSERT IGNORE`, 3개국어. 주요 키)

| window/key | KOR | ENG | JPN |
|---|---|---|---|
| W001/FORGOT_PWD | 비밀번호를 잊으셨나요? | Forgot your password? | パスワードをお忘れですか？ |
| W010/SETUP_TITLE | 비밀번호 설정 | Set your password | パスワード設定 |
| W010/SETUP_INVITE_DESC | 초대를 확인했습니다. 사용할 비밀번호를 설정해 주세요. | Invitation confirmed. Choose your password. | 招待を確認しました。パスワードを設定してください。 |
| W010/SETUP_DONE | 비밀번호가 설정되었습니다. 로그인해 주세요. | Password set. Please sign in. | パスワードを設定しました。ログインしてください。 |
| W010/TOKEN_INVALID_DESC | 링크가 유효하지 않거나 만료되었습니다. | This link is invalid or has expired. | リンクが無効か、期限切れです。 |
| W011/RESET_TITLE | 비밀번호 재설정 | Reset password | パスワード再設定 |
| W011/RESET_SENT | 계정이 있다면 재설정 메일을 보냈습니다. 받은편지함을 확인해 주세요. | If an account exists, we sent a reset email. Check your inbox. | アカウントが存在する場合、再設定メールを送信しました。受信箱をご確認ください。 |
| W012/TPL_TITLE | 메일 템플릿 관리 | Mail templates | メールテンプレート管理 |
| W009/CONFIRM_SEND_DESC | 이 주소로 초대 메일을 발송합니다. | An invitation will be sent to this address. | このアドレスに招待メールを送信します。 |
| W009/MAIL_FAILED | 메일 발송에 실패했습니다. 재발송해 주세요. | Failed to send email. Please resend. | メール送信に失敗しました。再送信してください。 |
| W009/INVITE_EXPIRED | 만료 — 재발송 필요 | Expired — resend needed | 期限切れ — 再送信が必要 |
| W009/DELETE_CONFIRM | 이 멤버를 삭제합니다. 출결 기록은 보존됩니다. | This member will be deleted. Attendance records are kept. | このメンバーを削除します。勤怠記録は保持されます。 |

(보조 키 — W010/NEW_PWD·NEW_PWD_CONFIRM·PWD_MISMATCH·GO_RESET, W011/RESET_SUBMIT·RESET_DESC, W012/TPL_SUBJECT·
TPL_BODY·TPL_VARS_HINT·PREVIEW, W009/SEND_INVITE·INVITE_SENT·RESEND·DELETE, W999/MAIL_TEMPLATES, W007 안내 문구 —
는 시드 작성 시 같은 표기 규칙으로 3개국어 추가. 확인/취소/제출은 기존 W999 `SUBMIT`/`CANCEL` 재사용.)

---

## 11. 테스트 계획

ID 체계: INV 초대 / RST 재설정 / TOK 토큰 / DEL 삭제 / SES 세션 / TPL 템플릿. 레벨 U/S/E는 test-plan §0-3.

| ID | 레벨 | 시나리오 | 기대 |
|----|------|----------|------|
| INV-01 | S | TA-A 멤버 등록 | 201, `status=PENDING`, `mailSent=true`, initialPassword 부재. dev 로그에 메일 전문(링크 포함) |
| INV-02 | S | PENDING 멤버로 로그인 | 401 단일 메시지(기존 authenticate 재사용 확인) |
| INV-03 | S | 링크 토큰 verify → set → 로그인 | verify 200(이름/마스킹 이메일), set 204 후 ACTIVE, 로그인 200 |
| INV-04 | S | set 후 재사용 / 재발송 후 구 토큰 / 만료 토큰(DB 시각 조작) | 전부 404 `TOKEN_INVALID` 단일 코드 |
| INV-05 | S | 재발송 — PENDING 대상 / ACTIVE 대상 | 200(신규 만료시각) / 409 `MEMBER_NOT_PENDING` |
| INV-06 | U | 메일 발송 예외 시 create(MailSender mock throw) | 멤버·토큰 생성 유지 + `mailSent=false`(Tx 분리 검증) |
| INV-07 | S | PENDING 대상 status 변경(ACTIVE/DISABLED 지정) | 400 `MEMBER_STATUS_INVALID` |
| INV-08 | S | 테넌트 생성(initialPassword 부재) → admin-invite 재발송 → 설정 → TA 로그인 | 전 구간 그린 |
| INV-09 | S | 크로스 테넌트: TA-A가 B 멤버 userId로 invite/DELETE | 404(ISO 매트릭스 확장 — requireManageableMember) |
| RST-01 | S | reset-request를 존재/부존재 이메일/부존재 테넌트/PENDING/DISABLED로 | **모두 202, 응답 동일**(존재 계정만 메일 로그) |
| RST-02 | S | 재설정 set 후 구 비밀번호 / 신 비밀번호 로그인 | 401 / 200 |
| RST-03 | S | 서브도메인 접속(코드 생략/불일치) / 루트 접속 + 코드 생략 | 202 호스트 확정 / 400 `TENANT_CODE_MISMATCH` / 400 `TENANT_CODE_REQUIRED`(D19 병행 규칙) |
| RST-04 | S | reset-request 계정 키 4회째 / verify IP 31회째 | 429 `RATE_LIMITED` |
| RST-05 | U | INVITE 72h/RESET 30m TTL 부여, ACTIVE만 발송 | UserTokenService/PasswordService 단위 |
| TOK-01 | U/S | 발급 토큰: DB 저장값 = SHA-256(원문), 원문 43자 Base64URL. S에서는 DB의 token_hash 값을 그대로 토큰으로 제출 | U pass / S 404(해시의 해시 불일치 — 원문 비저장 증명) |
| TOK-02 | S | 정지·삭제된 유저의 발급 완료 토큰으로 verify/set | 404(무효화 확인) |
| DEL-01 | S | 멤버 삭제 → 목록/로그인/활성 세션 | 204, 목록 제외, 로그인 401, 세션 다음 요청부터 401(REV 계승) |
| DEL-02 | S | 삭제 후 같은 이메일 재등록 + 출결 기록 잔존 확인 | 201(email_key UNIQUE — 오송신 수습 경로 증명), 기록은 구 user_id로 보존 |
| DEL-03 | S | 자기 자신 / 마지막 활성 TENANT_ADMIN / SYSTEM_ADMIN 대상 삭제 | 400 / 409 `LAST_TENANT_ADMIN` / 404 |
| SES-01 | S | 세션 2개 발급 → 재설정 완료 → 두 세션으로 API 호출 | 둘 다 401(issuedAt < password_changed_at 회수) |
| SES-02 | U | 재검증 role 갱신 경로의 issuedAt 보존 | SessionRevalidationInterceptor 단위 |
| TPL-01 | S | 템플릿 목록/수정/미리보기 — SA / TA·MEMBER | 200 / 403(**RULES `/api/v1/admin/**` 일반화 검증**) |
| TPL-02 | S | 미지 변수 `{foo}` 저장 / `{actionUrl}` 누락 | 400 `MAIL_TEMPLATE_UNKNOWN_VAR`(변수명 포함) / 400 `MAIL_TEMPLATE_ACTION_URL_REQUIRED` |
| TPL-03 | U | 렌더 치환(5변수) + 잔존 플레이스홀더 시 발송 중단 | MailTemplateService |
| TPL-04 | S | country=KR/JP 테넌트의 초대 메일 언어(tenant.country — CR3-1. "미등록"은 NOT NULL DEFAULT로 실측 불가, ENG 폴백은 U에서 미지원 값으로 방어 검증) | KOR/JPN 제목 분기(메일 로그 단언) + U: 미지원 값→ENG |
| E2E | E | 기존 16스텝의 "초기 비밀번호 로그인" 스텝을 → 로그에서 actionUrl 추출 → `/?token=` 진입 → W010 설정 → 로그인으로 교체 + 오송신 시나리오(등록→삭제→재등록) 1스텝 추가 | 그린 |

단위 테스트 신규: `UserTokenServiceTest`/`PasswordServiceTest`/`MailTemplateServiceTest`/`MailLanguageResolverTest`/
`PasswordResetRateLimiterTest`(LoginRateLimiter 4건 구성 계승), `MemberServiceTest` 확장(삭제 가드 3경로·PENDING
상태 변경 400), `SessionRevalidationInterceptorTest`(SES-02), `NavigationServiceTest`(W010~W012 role 매트릭스 행 추가).

---

## 12. 구현 순서

1. V7([E1]~[E4] — 스케줄/공휴일 몫과 파일 합류) + TokenPurpose/UserToken/매퍼 + UserTokenService (TOK)
2. mail 패키지(starter-mail, MailSender 프로파일 분기, 템플릿 서비스·시드) + MailLanguageResolver (TPL)
3. PasswordController + PasswordResetRateLimiter + WebConfig exclude + RULES `/api/v1/admin/**` 일반화 (RST)
4. MemberService/TenantService 초대 전환(initialPassword 폐지)·재발송·삭제 + SessionUser.issuedAt/재검증 확장 (INV/DEL/SES)
5. Screen 3종 + 프론트(토큰 캡처, W010~W012, W009/W007 개편) + V7 언어 시드 → 스모크 전량 + E2E 개편(릴리즈 게이트)

## 13. 타 계획 문서와의 접점 (교차 리뷰 확인 대상)

| # | 접점 | 확정 결과(교차 리뷰 cross-review-phase3.md 반영 완료) |
|---|---|---|
| 1 | **V7 단일 파일 합류** — 스케줄/공휴일 DDL과 한 파일 | **확정(CR3-4)**: 파일명 `V7__phase3.sql`, 순서 [H]→[S]→[E]→언어 시드. users ALTER는 도메인 구획별 3문 유지(§2) |
| 2 | **화면 코드** W010/W011/W012 선점(정본) | 확정 — 스케줄은 신규 화면 없음, 공휴일은 W013(상호 무모순 확인 — CR3 확인 항목) |
| 3 | **MemberResponse/MemberCreateRequest·Response 병합** | **확정(CR3-3)**: 통합 필드 집합은 §4.2 코드 블록 = work-schedule.md §5-1과 동일. 등록 요청에 workStart/End 선택 필드 포함 |
| 4 | **RoleInterceptor RULES 일반화**(`/api/v1/admin/**`→SYSTEM_ADMIN) | **채택 확정(CR3-10)**: 현행 admin 하위 실경로는 i18n뿐, WebConfig는 이미 `/api/v1/admin/**` 등록 — 스케줄/공휴일 경로(`/api/v1/tenant/**`)와 무간섭 검증 완료 |
| 5 | **소프트 삭제 유저의 잔존 데이터** — work_schedule 표시/제외 정책 | **확정(CR3-9)**: work-schedule.md §3-4 — 잔존 보존 + Phase 3 표시 경로 없음(전 조회가 셀프서비스, 목록은 deleted 제외) |
| 6 | 동시 수정 파일: Screen.java/WebConfig/App.tsx/types.ts/V7/언어 시드 | 워크트리 병렬 구현 시 충돌 예상 지점(D17 프로세스의 통합 단계에서 조정) |
| 7 | **TenantCreateRequest/Response** — country(공휴일)×초대 전환(본 문서) | **확정(CR3-1/5)**: §4.3 통합 계약·플로우 = holiday-plan §4-1/§2-5와 동일 |
