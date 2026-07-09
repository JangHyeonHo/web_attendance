# 백엔드 API 상세 설계 — SaaS 멀티테넌시 전환 (Phase 1~2)

- 상위 문서: [plan-saas-multitenancy.md](../plan-saas-multitenancy.md) (마스터 계획, 이하 "마스터")
- 범위: 마스터 Phase 1(코어 멀티테넌시) + Phase 2(테넌트 관리 기능)의 **백엔드 API 상세 설계**.
  DB 마이그레이션(V4/V5)의 DDL 상세와 프론트 화면 설계는 별도 문서로 하되, 계약에 필요한 범위만 여기서 확정한다.
- 확정 전제(마스터 §8):
  - 로그인은 `{tenantCode, email, password}` 3필드.
  - 3단계 role: `SYSTEM_ADMIN` / `TENANT_ADMIN` / `MEMBER`.
  - 테넌트 생성은 SYSTEM_ADMIN만. 공개 회원가입(`POST /api/v1/users`)은 폐기하고 TENANT_ADMIN 등록제로.
  - 기업/결제 정보는 SYSTEM_ADMIN 전용, AES-256-GCM 암호화 + 응답 마스킹(빌링키는 응답 자체에서 제외).
  - 마지막 TENANT_ADMIN 강등/비활성/삭제 차단.
  - SYSTEM_ADMIN은 테넌트 내부의 출결 데이터에 접근하지 않는다(테넌트 메타까지만).

표기: 본문 코드는 실제 컨벤션(record DTO, MyBatis 어노테이션 매퍼, `ApiException` 메시지 키 지연 해석,
Swagger 어노테이션의 설명은 메시지 키)을 그대로 따른 **구현 지시 수준의 설계**다.

---

## 1. 엔드포인트 전체 표

### 1.1 총괄

| 구분 | 개수 | 내용 |
|------|------|------|
| 신규 | 12 | `/api/v1/system/tenants` 계열 8 + `/api/v1/tenant/members` 계열 4 (테넌트명 수정·멤버 단건 조회/일괄 수정/삭제/비밀번호 재발급은 **Phase 3 이연** — §1.3/§1.4) |
| 변경 | 3 | `POST /api/v1/auth/login`(요청+응답), `GET /api/v1/auth/me`(응답), `POST /api/v1/navigation`(응답에 `role` 필드 추가 — 헤더 role 분기용, §8.2) |
| 폐기 | 1 | `POST /api/v1/users` (공개 회원가입) |
| 불변 | 8 | 출결 4(`/api/v1/attendance*` — HTTP 계약 불변, 허용 role은 {TENANT_ADMIN, MEMBER} 화이트리스트로 §3), i18n 공개 1, i18n 관리 2(`/api/v1/admin/i18n` — 요구 role만 SYSTEM_ADMIN으로 상향), logout 1 → §8 |

### 1.2 변경: 인증(auth)

| 메소드/경로 | 요구 role | 요청 DTO | 응답 DTO | 에러(코드 / 메시지 키) |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | 공개 | `LoginRequest` | `LoginResponse` | 401 `UNAUTHORIZED` / `auth.login.failed` — 테넌트 코드 불일치·정지 테넌트·이메일 없음·비밀번호 불일치·`status != ACTIVE` **전부 동일 메시지**(존재 여부 비노출, 마스터 §6). 400 `INVALID_INPUT` / 필드별 `validation.*`. **429 `RATE_LIMITED` / `auth.login.rate-limited`** — 레이트 리밋 차단(security-plan §3, `Retry-After` 헤더 없음) |
| `POST /api/v1/auth/logout` | 로그인 | (없음) | 204 | (불변) |
| `GET /api/v1/auth/me` | 로그인 | (없음) | `LoginResponse` | 401 `UNAUTHORIZED` / `error.unauthorized` (불변) |

`auth/AuthDtos.java` 변경:

```java
@Schema(description = "schema.login-request")
public record LoginRequest(
        @Schema(description = "schema.field.tenant-code", example = "ACME")
        @NotBlank(message = "{validation.tenant-code.required}")
        @Size(max = 20, message = "{validation.tenant-code.size}")
        String tenantCode,

        @Schema(description = "schema.field.email", example = "admin@attendance.local")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @Schema(description = "schema.field.password", example = "Admin123!")
        @NotBlank(message = "{validation.password.required}")
        String password) {
}

@Schema(description = "schema.login-response")
public record LoginResponse(
        @Schema(description = "schema.field.user-id", example = "1") long userId,
        @Schema(description = "schema.field.email") String email,
        @Schema(description = "schema.field.name") String name,
        @Schema(description = "schema.field.role", example = "MEMBER") Role role,
        @Schema(description = "schema.field.tenant-code", example = "ACME") String tenantCode,
        @Schema(description = "schema.field.tenant-name", example = "에이크미(주)") String tenantName) {

    public static LoginResponse from(SessionUser user) {
        return new LoginResponse(user.userId(), user.email(), user.name(),
                user.role(), user.tenantCode(), user.tenantName());
    }
}
```

- 기존 `boolean admin` 필드는 **삭제**하고 `role`로 대체한다(프론트를 같은 PR에서 수정 — 마스터 §10 리스크 표의 방침).
- `AuthController.login`은 ①`LoginRateLimiter` 검사(임계 초과 시 429 `RATE_LIMITED`) → ②`authenticate` → ③실패 시 `recordFailure`, 성공 시 `reset` 순으로 호출한다(security-plan §3-1).
- `AuthService.authenticate(String tenantCode, String email, String rawPassword)`:
  1. **tenantCode는 대문자 정규화(trim + upper) 후** `tenantMapper.findByCode(tenantCode)` — null 또는 `SUSPENDED`면 401 `auth.login.failed`
  2. `userMapper.findByEmail(tenant.tenantId(), email)` — null이면 401
  3. `user.status() != UserStatus.ACTIVE`면 401 (같은 메시지)
  4. BCrypt 검증 실패면 401
  5. `new SessionUser(user.userId(), tenant.tenantId(), tenant.tenantCode(), tenant.name(), user.email(), user.name(), user.role())`
- 세션 재발급(세션 고정 방지)·언어 이어주기 로직은 현행 그대로.

### 1.3 신규: 테넌트 관리 `/api/v1/system/tenants` (SYSTEM_ADMIN 전용)

컨트롤러: `tenant/SystemTenantController.java` (`@RequestMapping("/api/v1/system/tenants")`).
**이 계열만 tenantId를 경로 변수로 받는 예외**다(마스터 §5 — 세션 tenantId를 쓰지 않는 유일한 API군).

| 메소드/경로 | 요청 DTO | 응답 DTO / 상태 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `POST /api/v1/system/tenants` | `TenantCreateRequest` | 201 `TenantCreateResponse` | 409 `TENANT_CODE_DUPLICATED` / `tenant.code.duplicated`, 400 `INVALID_INPUT` |
| `GET /api/v1/system/tenants` | (없음) | 200 `List<TenantResponse>` | — |
| `GET /api/v1/system/tenants/{tenantId}` | (없음) | 200 `TenantResponse` | 404 `TENANT_NOT_FOUND` / `tenant.not-found` |
| `PUT /api/v1/system/tenants/{tenantId}/status` | `TenantStatusRequest` | 200 `TenantResponse` | 404 `TENANT_NOT_FOUND`, 400 `TENANT_SELF_SUSPEND` / `tenant.suspend.self`(자기 소속 테넌트 정지 시도) |
| `GET /api/v1/system/tenants/{tenantId}/profile` | (없음) | 200 `TenantProfileResponse`(마스킹) | 404 `TENANT_NOT_FOUND`, 404 `TENANT_PROFILE_NOT_FOUND` / `tenant.profile.not-found` |
| `PUT /api/v1/system/tenants/{tenantId}/profile` | `TenantProfileRequest`(전체 재입력) | 200 `TenantProfileResponse`(마스킹) | 404 `TENANT_NOT_FOUND`, 400 `INVALID_INPUT` |
| `GET /api/v1/system/tenants/{tenantId}/billing` | (없음) | 200 `TenantBillingResponse`(빌링키 없음) | 404 `TENANT_NOT_FOUND`, 404 `TENANT_BILLING_NOT_FOUND` / `tenant.billing.not-found` |
| `PUT /api/v1/system/tenants/{tenantId}/billing` | `TenantBillingRequest`(전체 재입력) | 200 `TenantBillingResponse` | 404 `TENANT_NOT_FOUND`, 400 `BILLING_CARD_KEY_REQUIRED` / `tenant.billing.card-key.required`(`CARD`인데 빌링키 미입력) |

설계 결정:
- **테넌트 삭제(DELETE)는 두지 않는다.** 정지(`SUSPENDED`)로 로그인을 차단하는 것까지가 Phase 2. 물리 삭제/데이터 파기는 Phase 4(퇴출 지원)와 개인정보 파기 정책(마스터 §6-1)에 묶인다.
- **테넌트명 수정(`PUT /{tenantId}`)은 Phase 2 계약에서 제외**(Phase 3 이연 — 교차 검증 최종 결정 D-D의 계약 단일화. 프론트에 대응 UI 없음).
- 테넌트 코드 필드명은 요청/응답 모두 **`tenantCode`로 통일**(D-D — `code` 표기 폐기).
- profile/billing은 1:1 테이블이므로 `PUT` 하나로 create-or-replace(upsert). 마스킹값 표시 + **전체 재입력** 방식(마스터 §6-1 원칙 3)과 맞물린다.
- `TenantStatusRequest`로 정지/재개를 한 엔드포인트에서 처리(정지 전용 DELETE보다 재개까지 대칭으로 표현됨).

`tenant/TenantDtos.java` (발췌 — 필드 전체):

```java
@Schema(description = "schema.tenant-create-request")
public record TenantCreateRequest(
        @Schema(description = "schema.field.tenant-code", example = "ACME")
        @NotBlank(message = "{validation.tenant-code.required}")
        @Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "{validation.tenant-code.format}")
        String tenantCode,

        @Schema(description = "schema.field.tenant-name", example = "에이크미(주)")
        @NotBlank(message = "{validation.tenant-name.required}")
        @Size(max = 100, message = "{validation.tenant-name.size}")
        String name,

        @Schema(description = "schema.tenant-create-request.admin-email", example = "admin@acme.co.kr")
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.format}")
        @Size(max = 100, message = "{validation.email.size}")
        String adminEmail,

        @Schema(description = "schema.tenant-create-request.admin-name", example = "김관리")
        @NotBlank(message = "{validation.name.required}")
        @Size(max = 50, message = "{validation.name.size}")
        String adminName) {
}

@Schema(description = "schema.tenant-create-response")
public record TenantCreateResponse(
        long tenantId, String tenantCode, String name, TenantStatus status,
        long adminUserId, String adminEmail,
        @Schema(description = "schema.field.initial-password") String initialPassword) {
}
// initialPassword는 이 응답에서 단 한 번만 평문 반환(저장은 BCrypt). 초대 링크는 Phase 3.
// 응답은 기존 컨벤션대로 평면(flat) record — 중첩 구조 없음(D-D).

@Schema(description = "schema.tenant-response")
public record TenantResponse(
        long tenantId, String tenantCode, String name, TenantStatus status,
        @Schema(description = "schema.field.member-count") int memberCount,
        LocalDateTime createdAt) {
}

// TenantUpdateRequest(테넌트명 수정)는 Phase 3 이연으로 삭제(§1.3 설계 결정).

@Schema(description = "schema.tenant-status-request")
public record TenantStatusRequest(
        @NotNull(message = "{validation.tenant-status.required}") TenantStatus status) {
}

@Schema(description = "schema.tenant-profile-request")
public record TenantProfileRequest(
        @NotBlank(message = "{validation.biz-reg-no.required}")
        @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "{validation.biz-reg-no.format}")
        String businessRegNo,                                     // [암호화 저장]
        @Size(max = 50, message = "{validation.ceo-name.size}") String ceoName,
        @Size(max = 200, message = "{validation.address.size}") String address,
        @Size(max = 50, message = "{validation.contact-name.size}") String contactName,
        @Email(message = "{validation.email.format}")
        @Size(max = 100, message = "{validation.email.size}") String contactEmail,
        @Pattern(regexp = "^[0-9+][0-9\\- ]{1,19}$", message = "{validation.contact-phone.format}")
        @Size(max = 20, message = "{validation.contact-phone.size}") String contactPhone) { // [암호화 저장] — 형식 검증으로 마스킹값 재제출(`010-****-5678`) 차단(MSK-10)

    @Override
    public String toString() {  // 로그 유출 방지(마스터 §6-1 원칙 3): 민감 필드는 toString에서 제외
        return "TenantProfileRequest[businessRegNo=***, contactPhone=***, ceoName=%s]".formatted(ceoName);
    }
}

@Schema(description = "schema.tenant-profile-response")
public record TenantProfileResponse(
        long tenantId,
        @Schema(description = "schema.field.biz-reg-no-masked", example = "123-**-*****")
        String businessRegNoMasked,                               // 마스킹값 — 필드명으로 마스킹 여부를 드러냄(오독 방지)
        String ceoName, String address, String contactName, String contactEmail,
        @Schema(description = "schema.field.contact-phone-masked", example = "010-****-5678")
        String contactPhoneMasked,                                // 마스킹값
        LocalDateTime updatedAt) {
}

@Schema(description = "schema.billing-method", enumAsRef = true)
public enum BillingMethod { INVOICE, CARD }                       // 기본 INVOICE(마스터 §6-1 원칙 1)

@Schema(description = "schema.tenant-billing-request")
public record TenantBillingRequest(
        @NotNull(message = "{validation.billing-method.required}") BillingMethod billingMethod,
        @Email(message = "{validation.email.format}")
        @Size(max = 100, message = "{validation.email.size}") String billingEmail,
        @Size(max = 200, message = "{validation.pg-key.size}") String pgCustomerKey, // [암호화 저장, CARD 필수]
        @Pattern(regexp = "^\\d{4}$", message = "{validation.card-last4.format}") String cardLast4,
        @Size(max = 20, message = "{validation.card-brand.size}") String cardBrand,
        @Size(max = 20, message = "{validation.plan.size}") String plan,
        LocalDate billedFrom,
        @Size(max = 500, message = "{validation.memo.size}") String memo) {

    @Override
    public String toString() {  // 빌링키 로그 유출 방지
        return "TenantBillingRequest[billingMethod=%s, pgCustomerKey=***]".formatted(billingMethod);
    }
}

@Schema(description = "schema.tenant-billing-response")
public record TenantBillingResponse(
        long tenantId, BillingMethod billingMethod, String billingEmail,
        @Schema(description = "schema.field.has-billing-key")
        boolean hasBillingKey,                                    // 빌링키는 존재 여부만(security-plan §2-4). 원문은 어떤 응답에도 없음
        @Schema(description = "schema.field.card-masked", example = "**** **** **** 1234")
        String cardMasked,                                        // card_last4로 조립. 빌링키 필드는 존재하지 않음
        String cardBrand, String plan, LocalDate billedFrom, String memo,
        LocalDateTime updatedAt) {
}
```

### 1.4 신규: 멤버 관리 `/api/v1/tenant/members` (TENANT_ADMIN **전용** — SYSTEM_ADMIN도 403, §3)

컨트롤러: `user/MemberController.java` (`@RequestMapping("/api/v1/tenant/members")`).
**tenantId는 항상 세션(`@LoginUser SessionUser`)에서 꺼낸다** — 요청에 테넌트 식별자 없음(마스터 §5 원칙).

| 메소드/경로 | 요청 DTO | 응답 DTO / 상태 | 에러(코드 / 메시지 키) |
|---|---|---|---|
| `GET /api/v1/tenant/members` | (없음) | 200 `List<MemberResponse>` | — |
| `POST /api/v1/tenant/members` | `MemberCreateRequest` | 201 `MemberCreateResponse` | 409 `EMAIL_DUPLICATED` / `member.email.duplicated`(테넌트 내 중복) |
| `PUT /api/v1/tenant/members/{userId}/status` | `MemberStatusRequest` | 200 `MemberResponse` | 404 `MEMBER_NOT_FOUND` / `member.not-found`(타 테넌트 userId도 동일 404 — 존재 비노출), 400 `MEMBER_STATUS_INVALID` / `member.status.invalid`(PENDING 지정), 409 `LAST_TENANT_ADMIN` / `member.last-admin`(마지막 관리자 비활성) |
| `PUT /api/v1/tenant/members/{userId}/role` | `MemberRoleRequest` | 200 `MemberResponse` | 404 `MEMBER_NOT_FOUND`, 400 `MEMBER_ROLE_INVALID` / `member.role.invalid`(SYSTEM_ADMIN 지정 시도), 409 `LAST_TENANT_ADMIN`(마지막 관리자 강등) |

설계 결정:
- **Phase 2 멤버 API는 위 4개가 전부**(교차 검증 최종 결정 D-D — frontend-plan §5-3과 1:1). 멤버 단건 조회(`GET /{userId}`)·이름/부서 일괄 수정(`PUT /{userId}`)·soft delete(`DELETE`)·비밀번호 재발급(`password-reset`)은 **Phase 3 이연**(비활성으로 접근 차단 가능하므로 Phase 2 운영에 공백 없음).
- 초기 비밀번호는 **서버 생성**(SecureRandom, `PASSWORD_PATTERN` 충족 12자)하여 등록 응답에 1회만 평문 반환. 메일 인프라 없는 Phase 2에서 TENANT_ADMIN이 구두/사내 채널로 전달하는 운영을 전제. 셀프 재설정·초대 링크는 Phase 3(마스터 §6).
- **등록 시 role은 받지 않는다** — 신규 멤버는 항상 `MEMBER`로 생성하고, 관리자 지정/해제는 `PUT /{userId}/role`로만(프론트의 PROMOTE/DEMOTE 조작과 1:1).
- 마지막 TENANT_ADMIN 판정: `role = TENANT_ADMIN AND status = ACTIVE AND deleted = FALSE` 인원이 1명이고 그 대상을 강등/비활성하려 하면 409 **`LAST_TENANT_ADMIN`**(에러 코드 전 문서 통일 — D-D). 서비스 레이어에서 `@Transactional` 안에 `FOR UPDATE` 카운트 후 차단(§4.2, security-plan §6-2).
- 등록 즉시 `status = ACTIVE`(승인제 미채택 — 마스터 §6. PENDING은 스키마 대비용으로만 존재하고 이 API로는 만들 수 없다).

`user/MemberDtos.java` (필드 전체):

```java
public final class MemberDtos {

    /** 기존 UserDtos.PASSWORD_PATTERN을 이관(§2 참조) */
    public static final String PASSWORD_PATTERN = "...(현행 그대로)...";

    @Schema(description = "schema.member-create-request")
    public record MemberCreateRequest(
            @Schema(description = "schema.field.email", example = "hong@acme.co.kr")
            @NotBlank(message = "{validation.email.required}")
            @Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}")
            String email,

            @Schema(description = "schema.field.name", example = "홍길동")
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 50, message = "{validation.name.size}")
            String name,

            @Schema(description = "schema.field.depart-cd", example = "DEV01")
            @Size(max = 50, message = "{validation.depart.size}")
            String departCd) {    // role 필드 없음 — 등록은 항상 MEMBER, 관리자 지정은 PUT /{userId}/role (§1.4)
    }

    @Schema(description = "schema.member-create-response")
    public record MemberCreateResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status,
            @Schema(description = "schema.field.initial-password") String initialPassword) {
    }

    @Schema(description = "schema.member-response")
    public record MemberResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status, LocalDateTime createdAt) {

        public static MemberResponse from(User user) {
            return new MemberResponse(user.userId(), user.email(), user.name(),
                    user.departCd(), user.role(), user.status(), user.createdAt());
        }
    }

    @Schema(description = "schema.member-status-request")
    public record MemberStatusRequest(
            @NotNull(message = "{validation.member-status.required}") UserStatus status) { // ACTIVE|DISABLED
    }

    @Schema(description = "schema.member-role-request")
    public record MemberRoleRequest(
            @NotNull(message = "{validation.member-role.required}") Role role) { // TENANT_ADMIN|MEMBER (SYSTEM_ADMIN은 400)
    }

    // MemberUpdateRequest(이름/부서 일괄 수정)·PasswordResetResponse(관리자 재발급)는 Phase 3 이연으로 삭제(§1.4).
}
```

### 1.5 폐기: 공개 회원가입

| 항목 | 방침 |
|---|---|
| `POST /api/v1/users` | **삭제**(410 유지 없음 — 외부 소비자 없음, 프론트 동일 PR 수정. 마스터 §10) |
| 삭제 파일 | `user/UserController.java`, `user/UserService.java`, `user/UserDtos.java`(PASSWORD_PATTERN은 `MemberDtos`로 이관) |
| `Screen.SIGNUP`(W003) | enum에서 제거. `fromCode("W003")` → null → 기존 규칙대로 `UNKNOWN_SCREEN` 사유와 함께 인덱스(W000) 전개 |
| WebConfig | authInterceptor의 excludePathPatterns에서 `/api/v1/users` 제거 |
| 메시지/시드 | `api.user.*`, `schema.signup-request*` 키 삭제(백엔드 메시지 번들). V3 시드의 `W003`/`INDEX_*` 텍스트 행은 **잔존 허용(무해 — 어떤 화면도 참조하지 않는 죽은 행, D-A 확정)**. V5에서도 DELETE하지 않는다 — W003 코드는 영구 결번 |

---

## 2. 패키지 배치

### 2.1 신규 패키지 `tenant/` (파일 12)

| 파일 | 내용 |
|---|---|
| `Tenant.java` | record: `(Long tenantId, String tenantCode, String name, TenantStatus status, LocalDateTime createdAt)` |
| `TenantStatus.java` | enum `ACTIVE, SUSPENDED` (users의 `deleted`와 달리 status enum — DB는 VARCHAR(10), MyBatis 기본 이름 매핑) |
| `TenantDtos.java` | §1.3의 DTO 전부 + `BillingMethod` enum(중첩 — `AttendanceDtos`의 `WorkStatus` 배치 방식 계승) |
| `TenantMapper.java` | tenant 테이블: `findByCode`, `findById`, `findAllWithMemberCount`, `insert(TenantCreate)`, `updateStatus`, `existsByCode` (`updateName`은 테넌트명 수정 API와 함께 Phase 3 이연) |
| `TenantCreate.java` | `UserCreate`와 동일한 이유(useGeneratedKeys 회수)로 가변 클래스 |
| `TenantProfile.java` | record: `(long tenantId, String businessRegNoEnc, String ceoName, String address, String contactName, String contactEmail, String contactPhoneEnc, LocalDateTime createdAt, LocalDateTime updatedAt)` — `v1:` 텍스트 암호문(String)만 보관, DB는 VARCHAR(§6.1, D-C) |
| `TenantBilling.java` | record: `(long tenantId, BillingMethod billingMethod, String billingEmail, String pgCustomerKeyEnc, String cardLast4, String cardBrand, String plan, LocalDate billedFrom, String memo, ...)` |
| `TenantProfileMapper.java` | tenant_profile: `findById(tenantId)`, `upsert(...)` (`ON DUPLICATE KEY UPDATE` — LanguageMapper.upsert 방식 계승) |
| `TenantBillingMapper.java` | tenant_billing: `findById(tenantId)`, `upsert(...)` |
| `TenantService.java` | 테넌트 CRUD/정지 + **최초 TENANT_ADMIN 발급**(`@Transactional`로 tenant INSERT + `MemberService.registerInitialAdmin()` 호출 — user 패키지에 위임해 UserMapper 접근을 user 패키지에 유지) |
| `TenantProfileService.java` | profile/billing 암·복호화 + 마스킹 조립(§6), `SystemTenantController`가 사용 |
| `SystemTenantController.java` | §1.3 엔드포인트 8개 |

### 2.2 기존 파일별 변경 요약표

| 파일 | 변경 내용 |
|---|---|
| `auth/SessionUser.java` | `tenantId`, `tenantCode`, `tenantName`, `role` 필드 추가, `admin` 삭제(§3.1) |
| `auth/AuthDtos.java` | `LoginRequest`에 `tenantCode`, `LoginResponse` `admin` → `role`/`tenantCode`/`tenantName`(§1.2) |
| `auth/AuthService.java` | `authenticate(tenantCode, email, rawPassword)` — 테넌트 해석·정지/상태 검사 추가(§1.2). `TenantMapper` 의존 추가 |
| `auth/AuthController.java` | `authenticate` 호출부 인자만 변경. 세션 처리 불변 |
| `auth/AdminInterceptor.java` | **삭제** → `RoleInterceptor`로 대체(§3.2) |
| `auth/AuthInterceptor.java` | 불변(`currentUser` 정적 헬퍼는 RoleInterceptor가 재사용) |
| `auth/LoginUser.java`, `LoginUserArgumentResolver.java` | 불변 |
| `auth/RoleInterceptor.java` | **신규**(§3.2) |
| `config/WebConfig.java` | AdminInterceptor → RoleInterceptor 2건 등록, exclude 목록 갱신(§3.3) |
| `user/User.java` | `tenantId`, `role`(Role), `status`(UserStatus) 추가, `admin` 삭제 |
| `user/Role.java` | **신규**: `MEMBER, TENANT_ADMIN, SYSTEM_ADMIN` — 서열 비교(`atLeast`) 없음, 인가는 화이트리스트(§3.1, D-B) |
| `user/UserStatus.java` | **신규**: `PENDING, ACTIVE, DISABLED` |
| `user/UserCreate.java` | `tenantId`, `role`, `status` 추가, `admin` 삭제 |
| `user/UserMapper.java` | 전 메소드 tenantId 스코프 + 멤버 관리용 메소드 추가(§4.2) |
| `user/UserController.java` / `UserService.java` / `UserDtos.java` | **삭제**(§1.5) |
| `user/MemberController.java` / `MemberService.java` / `MemberDtos.java` | **신규**(§1.4). `MemberService`에 `registerInitialAdmin(long tenantId, String email, String name)`(TenantService용)과 초기 비밀번호 생성기(SecureRandom) 포함 |
| `attendance/AttendanceMapper.java` | 전 메소드 첫 파라미터 `@Param("tenantId")` + 2중 조건(§4.3) |
| `attendance/ScheduleMapper.java` | 동상 + holiday 테넌트화(§4.4) |
| `attendance/AttendanceService.java` | 공개 메소드 시그니처 `(long tenantId, long userId, ...)`로. 상태머신 로직 불변. **payloadHash 계산 입력에 tenantId 포함**(심층 방어 — data-migration-v4 §6-4 채택, 교차 검증 발견 12) |
| `attendance/AttendanceController.java` | `user.userId()` → `user.tenantId(), user.userId()` 전달만 변경. **HTTP 계약 불변**(§8) |
| `navigation/Screen.java` | `Access` enum → **허용 role 집합**(`Set<Role>`) 필드, W003 제거, W007/W008/W009 추가(§5, D-A) |
| `navigation/NavigationService.java` | `decide`/`homeOf` role 기반으로, `loadScreenData`에 tenantId 전달(§5.3) |
| `navigation/NavigationDtos.java` | `NavigationReason.ADMIN_ONLY` → **`ROLE_DENIED`로 개명**(3-role 체계 맞춤 — D-A. 프론트 동일 PR 수정). `NavigateResponse`에 `role: Role`(nullable) 필드 추가(§8.2) |
| `language/*` | **불변**(글로벌 리소스 — 마스터 §3). `/api/v1/admin/i18n`의 요구 role만 WebConfig에서 SYSTEM_ADMIN으로 상향 |
| `common/ApiException.java`, `ErrorResponse.java`, `GlobalExceptionHandler.java`, `Messages.java` | 불변 |
| `auth/LoginRateLimiter.java` | **신규**: 계정·IP 2단 슬라이딩 윈도우 레이트 리밋(security-plan §3 — P1 필수, 429 `RATE_LIMITED`) |
| `common/FieldCipher.java` | **신규**: AES-256-GCM 암·복호화 — JCA 직접 구현, `v1:` 텍스트 포맷(§6.1, D-C) |
| `common/Masking.java` | **신규**: 마스킹 정적 유틸(§6.3) |
| `common/SecurityHeadersFilter.java` | **신규**: CSP/nosniff/X-Frame 등 보안 헤더 필터(security-plan §5 — P1 필수) |
| `resources/application.properties` | 쿠키 명시 설정 2줄(`http-only`, `same-site=lax` — security-plan §4-2) + `app.crypto.key` **개발 기본값**(D-C) |
| `resources/application-prod.properties` | **신규**: Swagger off·Secure 쿠키·HSTS·info 로그·`app.crypto.key` 기본값 없음(security-plan §7-2 — P1 격상) |
| `resources/db/migration/V4__multitenancy.sql` | **신규**: tenant/tenant_profile/tenant_billing 생성, tenant_id·role·status 컬럼, DEFAULT 테넌트 backfill, 시드 관리자 SYSTEM_ADMIN 승격(DDL 상세는 DB 설계 문서) |
| `resources/db/migration/V5__seed_saas_texts.sql` | **신규**: 멤버/테넌트 관리 화면(W007/W008/W009/W999/W001) + 랜딩(W000 `LANDING_*`) 텍스트 시드 통합 단일 파일(§7.4, D-E) |
| `resources/messages/messages*.properties` | §7의 키 추가(3언어) |

신규 자바 파일 합계: tenant 12 + user 5(Role, UserStatus, MemberController, MemberService, MemberDtos) + auth 2(RoleInterceptor, LoginRateLimiter) + common 3(FieldCipher, Masking, SecurityHeadersFilter) = **22** (+ SQL 2 + properties 2).
삭제: UserController, UserService, UserDtos, AdminInterceptor = 4.

---

## 3. SessionUser / 인터셉터

### 3.1 SessionUser 확장과 Role

```java
// user/Role.java
public enum Role {
    MEMBER, TENANT_ADMIN, SYSTEM_ADMIN;
    // 서열 비교(atLeast)는 두지 않는다 — 인가는 경로별 허용 role "화이트리스트"가 단일 소스(§3.2, D-B).
    // SYSTEM_ADMIN이 TENANT_ADMIN의 상위가 아니다: 출결·멤버 데이터 접근 차단 원칙(마스터 §4 보완정책 2).
}

// auth/SessionUser.java
public record SessionUser(
        long userId,
        long tenantId,
        String tenantCode,
        String tenantName,
        String email,
        String name,
        Role role) implements Serializable {

    public static final String SESSION_KEY = "LOGIN_USER";
}
```

- `admin()` 헬퍼는 두지 않는다 — 호출부가 전부 role 기반으로 다시 쓰이므로 남기면 오용 통로가 된다.
- `tenantCode`/`tenantName`은 `LoginResponse.from(SessionUser)` 조립(현행 패턴)과 헤더 표시용. 세션 무게 영향 미미.
- **SYSTEM_ADMIN의 `/api/v1/tenant/**`·`/api/v1/attendance/**` 접근은 화이트리스트에서 명시적으로 403**이다(D-B — security-plan §6-1 정본). "세션 tenantId 격리로 충분하니 허용" 구상은 폐기: 경로 차단(1차) + 매퍼 tenant_id 2중 조건(2차) + API 표면 차단(3차)의 3중 방어(security-plan §6-3).

### 3.2 RoleInterceptor (AdminInterceptor 대체) — 경로별 허용 role 화이트리스트

security-plan §6-1의 `RouteRule` 테이블을 **단일 소스**로 그대로 구현한다(요구 role 서열 비교 방식 기각 — D-B).

```java
// auth/RoleInterceptor.java — 단일 인스턴스. 규칙 테이블이 인가의 단일 소스.
public class RoleInterceptor implements HandlerInterceptor {

    private record RouteRule(String pattern, Set<Role> allowed) {}

    private static final List<RouteRule> RULES = List.of(   // 선언 순서대로 첫 매칭 규칙 적용
        new RouteRule("/api/v1/system/**",     Set.of(Role.SYSTEM_ADMIN)),
        new RouteRule("/api/v1/admin/i18n/**", Set.of(Role.SYSTEM_ADMIN)),               // 언어 마스터는 글로벌 제품 자산
        new RouteRule("/api/v1/tenant/**",     Set.of(Role.TENANT_ADMIN)),               // SYSTEM_ADMIN도 403
        new RouteRule("/api/v1/attendance/**", Set.of(Role.TENANT_ADMIN, Role.MEMBER))   // SYSTEM_ADMIN 명시 배제
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionUser user = AuthInterceptor.currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized();      // 401 error.unauthorized
        }
        for (RouteRule rule : RULES) {
            if (matcher.match(rule.pattern(), request.getRequestURI())) {
                if (!rule.allowed().contains(user.role())) {
                    throw ApiException.forbidden(); // 403 error.forbidden
                }
                return true;
            }
        }
        return true;   // 규칙에 없는 인증 필수 경로(auth/me 등)는 role 무관 통과
    }
}
```

- `AdminInterceptor.java`는 **삭제**. 검사 흐름(미로그인 401 → 권한 403)과 `AuthInterceptor.currentUser` 재사용은 그대로 계승.
- `error.forbidden` 문구는 "관리자 권한이 필요합니다" → "접근 권한이 없습니다"로 일반화(키 불변, 3언어 문구만 수정).

### 3.3 WebConfig 경로 매핑 표

| 인터셉터 | addPathPatterns | excludePathPatterns | 효과 |
|---|---|---|---|
| `authInterceptor` | `/api/**` | `/api/v1/auth/login`, `/api/v1/i18n/**`, `/api/v1/navigation` | 로그인 필수(공개 3종 제외) — 인증 단일 책임(security-plan §6-1). `/api/v1/users` 제외 항목은 삭제 |
| `roleInterceptor` (단일, §3.2 RULES 내장) | `/api/v1/system/**`, `/api/v1/admin/**`, `/api/v1/tenant/**`, **`/api/v1/attendance/**`** | — | 인가 단일 책임. **attendance 경로 등록 필수**(SYSTEM_ADMIN 403 — D-B. 누락 시 ISO-11/ROLE-03~05 실패) |

WebConfig 생성자에서 `AdminInterceptor` 의존 제거. RoleInterceptor는 규칙 테이블을 내장한 단일 인스턴스로 등록한다(경로↔허용 role이 코드에 선언적으로 드러남).

---

## 4. 테넌트 전파 규약 (매퍼 시그니처)

### 4.1 규약 (마스터 §5의 구체화)

1. 테넌트 소유 테이블(users, attendance, attendance_check, work_schedule, holiday)을 만지는 **모든 매퍼 메소드는 `@Param("tenantId") long tenantId`를 첫 파라미터**로 받는다.
2. user_id 조건 쿼리에도 `AND tenant_id = #{tenantId}`를 **병기**(2중 조건).
3. tenantId는 컨트롤러에서 `@LoginUser SessionUser`의 값을 서비스에 명시 전달. 서비스 공개 메소드도 `(long tenantId, long userId, ...)` 형태로 통일.
4. 예외 2건: `AttendanceMapper.deleteExpiredChecks()`(시간 조건만의 전역 청소 — 데이터 반환이 없어 유출 불가), `TenantMapper`/`LanguageMapper`(테넌트 소유 테이블이 아님. system API는 경로의 tenantId를 명시 전달).

### 4.2 UserMapper — 시그니처 변경 전수

| 현행 | 변경 후 | 비고 |
|---|---|---|
| `User findByEmail(@Param("email") String email)` | `User findByEmail(@Param("tenantId") long tenantId, @Param("email") String email)` | SELECT에 `tenant_id, role, status` 컬럼 추가, `WHERE tenant_id = #{tenantId} AND email = #{email} AND deleted = FALSE` |
| `int insert(UserCreate user)` | (시그니처 동일) `UserCreate`에 tenantId/role/status 추가 | INSERT 컬럼 `tenant_id, role, status` 추가, `is_admin` 제거 |
| `boolean existsByEmail(@Param("email") String email)` | `boolean existsByEmail(@Param("tenantId") long tenantId, @Param("email") String email)` | `UNIQUE(tenant_id, email)` 대응 |
| — (신규) | `List<User> findByTenant(@Param("tenantId") long tenantId)` | 멤버 목록(`deleted = FALSE`, name 순) |
| — (신규) | `User findById(@Param("tenantId") long tenantId, @Param("userId") long userId)` | 2중 조건 — 타 테넌트 userId는 null → 404 |
| — (신규) | `int countActiveTenantAdmins(@Param("tenantId") long tenantId)` | `role='TENANT_ADMIN' AND status='ACTIVE' AND deleted=FALSE` — 마지막 관리자 보호. **`FOR UPDATE` 명기**(동시 상호 강등 레이스에서 0명 테넌트 방지 — security-plan §6-2. ADM-05까지는 잡지 못하는 레이스의 근거 문서화) |
| — (신규) | `int updateRole(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("role") Role role)` | `PUT /{userId}/role` 대응 |
| — (신규) | `int updateStatus(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("status") UserStatus status)` | |

(`updateMember`(이름/부서)·`updatePasswordHash`·`softDelete`는 해당 API의 Phase 3 이연과 함께 제외 — §1.4)

### 4.3 AttendanceMapper — 시그니처 변경 전수

| 현행 | 변경 후 |
|---|---|
| `findLatest(userId)` | `findLatest(@Param("tenantId") long tenantId, @Param("userId") long userId)` |
| `findLatestGoToWork(userId)` | `findLatestGoToWork(tenantId, userId)` |
| `findBetween(userId, from, to)` | `findBetween(tenantId, userId, from, to)` |
| `insert(userId, typeCode, status, stampedAt, latitude, longitude, placeInfo, terminal)` | `insert(tenantId, userId, ...)` — INSERT 컬럼에 `tenant_id` 추가 |
| `insertCheck(token, userId, payloadHash, confirmCode)` | `insertCheck(tenantId, token, userId, payloadHash, confirmCode)` |
| `findCheckHash(token, userId)` | `findCheckHash(tenantId, token, userId)` — **격리 테스트 ④(크로스 테넌트 토큰) 방어 지점** |
| `deleteCheck(token)` | `deleteCheck(tenantId, token, userId)` — `WHERE token = #{token} AND user_id = #{userId} AND tenant_id = #{tenantId}` **3중 조건**(같은 테넌트 내 타 유저 토큰 무효화 허점까지 봉쇄 — data-migration-v4 §6-3) |
| `deleteExpiredChecks()` | 시그니처 불변(§4.1 예외 — 전 테넌트 청소). 단 **시간 조건 상수 24h → 30분**으로 변경(체크토큰 TTL 30분 — security-plan §1 T7 확정, D-F) |

2중 조건 SQL 예시(`findLatest`):

```java
@Select("""
        SELECT attendance_id, user_id, type AS type_code, status, stamped_at
        FROM attendance
        WHERE tenant_id = #{tenantId}
          AND user_id = #{userId}
          AND stamped_at > NOW() - INTERVAL 48 HOUR
        ORDER BY stamped_at DESC, attendance_id DESC
        LIMIT 1
        """)
AttendanceStamp findLatest(@Param("tenantId") long tenantId, @Param("userId") long userId);
```

### 4.4 ScheduleMapper — 시그니처 변경 전수

| 현행 | 변경 후 | 비고 |
|---|---|---|
| `findBetween(userId, from, to)` | `findBetween(tenantId, userId, from, to)` | 2중 조건 |
| `findHolidayDates(from, to)` | `findHolidayDates(@Param("tenantId") long tenantId, @Param("from") ..., @Param("to") ...)` | **holiday 테넌트화**(마스터 §3): `WHERE tenant_id = #{tenantId} AND ...` |

### 4.5 서비스 시그니처 변경 전수 (attendance)

| 현행 | 변경 후 |
|---|---|
| `AttendanceService.check(long userId, CheckRequest)` | `check(long tenantId, long userId, CheckRequest)` |
| `confirm(long userId, ConfirmRequest)` | `confirm(long tenantId, long userId, ConfirmRequest)` |
| `status(long userId)` | `status(long tenantId, long userId)` |
| `monthly(long userId, int year, int month)` | `monthly(long tenantId, long userId, int year, int month)` |

호출부: `AttendanceController`(4곳)와 `NavigationService.loadScreenData`(1곳)가 `user.tenantId()`를 함께 전달. `evaluate(...)` 등 순수 로직·`MonthlyAttendanceAssembler`는 불변(기존 단위 테스트 로직 불변 확인 — 마스터 §7).

---

## 5. navigation / Screen 확장

### 5.1 화면 코드 체계 (결정)

| 코드 | 화면 | 허용 role | 비고 |
|---|---|---|---|
| W000 | 인덱스(랜딩) | 공개 | **공개 유지** |
| W001 | 로그인 | 공개 | 폼에 회사 코드 필드 추가(프론트) |
| W002 | 로그아웃(액션) | 공개 | 불변 |
| W003 | ~~회원가입~~ | — | **폐기·영구 결번**(enum 제거 → UNKNOWN_SCREEN 처리). V3 시드 텍스트 행은 잔존 허용(§1.5) |
| W004 | 언어 마스터 관리 | {SYSTEM_ADMIN} | 글로벌 리소스이므로 SYSTEM_ADMIN으로 **상향**(현행 ADMIN_ONLY의 승계) |
| W005 | 출결 | {MEMBER, TENANT_ADMIN} | 불변. **SYSTEM_ADMIN은 전개 거부**(출결 비대상 — D-B). TENANT_ADMIN·MEMBER의 홈 |
| W006 | 출결 상세 | {MEMBER, TENANT_ADMIN} | 불변 |
| W007 | **테넌트 목록/생성** | {SYSTEM_ADMIN} | 신규 — SYSTEM_ADMIN의 홈 (D-A) |
| W008 | **테넌트 상세(기업정보/결제정보)** | {SYSTEM_ADMIN} | 신규 — W007에 임베드 전개, 텍스트는 `/i18n/W008` 직접 취득(W005→W006 패턴 — D-A) |
| W009 | **멤버 관리** | {TENANT_ADMIN} | 신규 — 헤더 MEMBERS 메뉴로 진입(TENANT_ADMIN의 홈은 W005 출결 — D-A) |
| W999 | 공통(헤더) | 공개 | 불변 |

W004를 role별로 재해석해 분기하는 안(마스터 §4) 대신 **신규 코드 추가**를 택한다: 화면 코드가 곧 프론트 컴포넌트 매핑 키이므로, 한 코드에 두 컴포넌트를 매다는 것보다 코드 분리가 서버 주도 전개(D7)의 "코드→화면 1:1" 원칙에 맞다. 같은 원칙으로 테넌트 목록(W007)·테넌트 상세(W008)·멤버 관리(W009)를 **3분할**한다(frontend-plan §1-2 정본 채택 — D-A).

### 5.2 Screen enum — 허용 role 집합 설계

요구 role 단일 값 + 서열(atLeast) 방식은 기각(D-B) — API 화이트리스트(§3.2)와 동일하게 **허용 role 집합**으로 선언한다(frontend-plan §1-2의 "접근 role" 열과 1:1).

```java
public enum Screen {

    INDEX("W000", null),
    LOGIN("W001", null),
    LOGOUT("W002", null),
    ADMIN("W004", Set.of(Role.SYSTEM_ADMIN)),
    ATTENDANCE("W005", Set.of(Role.MEMBER, Role.TENANT_ADMIN)),
    ATT_DETAILS("W006", Set.of(Role.MEMBER, Role.TENANT_ADMIN)),
    SYSTEM_TENANTS("W007", Set.of(Role.SYSTEM_ADMIN)),
    TENANT_DETAIL("W008", Set.of(Role.SYSTEM_ADMIN)),
    MEMBERS("W009", Set.of(Role.TENANT_ADMIN)),
    COMMON("W999", null);

    private final String code;
    private final Set<Role> allowed;   // null = 공개, 그 외 = 집합에 든 role만

    Screen(String code, Set<Role> allowed) { ... }

    public boolean isPublic() { return allowed == null; }
    public Set<Role> allowed() { return allowed; }
    // code(), fromCode(String)는 현행 유지
}
```

기존 `Access` enum(PUBLIC/LOGIN_REQUIRED/ADMIN_ONLY)은 삭제 — `null`(공개)/허용 role 집합이 그대로 상위 호환한다.

### 5.3 NavigationService 결정 규칙 변경

```java
public Decision decide(String requestedCode, SessionUser user) {
    // (알 수 없는 코드 → 인덱스 or homeOf, 로그아웃 처리: 현행 그대로)
    ...
    if (requested.isPublic()) {
        if (loggedIn && (requested == Screen.LOGIN || requested == Screen.INDEX)) { // SIGNUP 분기 삭제
            return new Decision(homeOf(user), NavigationReason.ALREADY_LOGGED_IN, false);
        }
        return new Decision(requested, null, false);
    }
    if (!loggedIn) {
        return new Decision(Screen.LOGIN, NavigationReason.LOGIN_REQUIRED, false);
    }
    if (!requested.allowed().contains(user.role())) {
        return new Decision(homeOf(user), NavigationReason.ROLE_DENIED, false); // 허용 role 집합 미포함
    }
    return new Decision(requested, null, false);
}

/** 로그인 유저의 홈 화면 (D-A 확정). */
private Screen homeOf(SessionUser user) {
    return switch (user.role()) {
        case SYSTEM_ADMIN -> Screen.SYSTEM_TENANTS;   // W007
        case TENANT_ADMIN -> Screen.ATTENDANCE;       // W005 — 일상 업무는 출결, 멤버 관리(W009)는 헤더 진입
        case MEMBER -> Screen.ATTENDANCE;             // W005
    };
}
```

- `NavigationReason.ADMIN_ONLY`는 **`ROLE_DENIED`로 개명**(D-A — 3-role 체계에서 "관리자 전용"이라는 이름이 부정확. 값 교체는 프론트 타입과 같은 PR에서 수정 — 마스터 §10 방침).
- 권한 미달 시 목적지가 현행 `ATTENDANCE` 고정에서 `homeOf(user)`로 바뀐다(예: SYSTEM_ADMIN이 W005 요청 → W007).
- `loadScreenData`: `attendanceService.status(user.tenantId(), user.userId())`로 변경. W007/W008/W009의 초기 데이터 동봉(테넌트 목록/멤버 목록)은 Phase 2 프론트 구현 시 같은 자리에서 확장 가능하나, **초기 데이터 없이 시작**(프론트가 각 API를 호출)으로 확정 — 화면 전개 응답 비대화 방지.
- `NavigationServiceTest`: SIGNUP 케이스 삭제, role 3종 × 화면별 매트릭스로 재작성.

---

## 6. 암호화/마스킹 적용 지점

### 6.1 common/FieldCipher (AES-256-GCM)

**security-plan §2를 정본으로 하는 JCA 직접 구현**이다(spring-security-crypto `AesBytesEncryptor` 방식은 기각 — IV 16B·버전 개념 부재·raw 키 주입 부자연, security-plan §2-1의 기각 근거표. D-C 확정). 코드 스케치 전문은 security-plan §2-3.

```java
/**
 * 민감 필드의 애플리케이션 레벨 암호화(AES-256-GCM, JCA Cipher 직접 사용).
 * 암호문은 텍스트 포맷 "v1:{base64(iv12)}:{base64(ct||tag)}" — 키 버전 프리픽스로 로테이션 대비.
 */
@Component
public class FieldCipher {

    // 키: 환경변수 APP_CRYPTO_KEY = base64 인코딩된 32바이트 (openssl rand -base64 32)
    public FieldCipher(@Value("${app.crypto.key}") String base64Key) {
        // 디코딩 실패 또는 길이 != 32바이트면 즉시 예외 → 앱 기동 실패 (security-plan §2-2)
    }

    /** 평문 → "v1:{b64(iv)}:{b64(ct||tag)}" 텍스트 암호문. null은 null. */
    public String encrypt(String plain) { ... }   // Cipher("AES/GCM/NoPadding"), IV 12B 매회 SecureRandom, 태그 128bit

    /** 복호화. "v1:" 프리픽스로 키 버전 선택(현재 v1 단일, 미지 버전은 예외). null은 null. */
    public String decrypt(String stored) { ... }
}
```

- 키 주입(D-C): `application.properties`에 **개발 기본키를 기본값으로 허용**(`app.crypto.key=${APP_CRYPTO_KEY:<dev 기본키>}`), **`application-prod.properties`는 기본값 없음** — prod 프로파일에서 `APP_CRYPTO_KEY` 미설정이면 기동 실패(fail-fast).
- 대상 컬럼(**VARCHAR** — 텍스트 암호문 그대로 저장, D-C): `tenant_profile.business_reg_no VARCHAR(128)`, `tenant_profile.contact_phone VARCHAR(128)`, `tenant_billing.pg_customer_key VARCHAR(1024)`. 이 3개 외 확대 금지(검색·정렬 불가 비용). 크기 산정은 data-migration-v4 §4.

### 6.2 서비스 레이어 암·복호화 흐름 (TenantProfileService)

```
[등록/수정]  PUT profile/billing
  Controller → TenantProfileService.upsertProfile(tenantId, TenantProfileRequest)
    ① tenantMapper.findById(tenantId) 존재 확인(없으면 404 tenant.not-found)
    ② 평문 필드를 fieldCipher.encrypt() → "v1:" 텍스트 암호문(String)
    ③ tenantProfileMapper.upsert(tenantId, businessRegNoEnc, ceoName, ...)
    ④ 응답 조립: "저장한 평문"을 다시 쓰지 않고 ⑤의 조회 경로를 재사용(마스킹 일원화)
[조회]  GET profile/billing
  Controller → TenantProfileService.getProfile(tenantId)
    ⑤ mapper.findById → entity(String 암호문) → fieldCipher.decrypt() → Masking.* 적용 → Response record
    ⑥ 빌링키: 복호화하지 않는다. TenantBillingResponse에는 존재 여부 hasBillingKey(boolean)만(응답 제외 원칙).
       빌링키 복호화 호출부는 Phase 4의 PG 청구 배치가 유일해질 예정.
```

- 로그 정책(**구현 규약** — D-F): 위 두 서비스에서 요청/엔티티를 로그로 출력하지 않는다. **민감 필드를 받는 요청 DTO는 `toString()` 오버라이드가 필수**(`TenantProfileRequest`/`TenantBillingRequest` — §1.3, `businessRegNo=***` 형태) — record의 자동 toString에 의한 사고 차단. 신규 민감 DTO 추가 시 리뷰 체크리스트 항목.
- `MemberService`/`TenantService`의 초기 비밀번호도 동일: 로그 금지, 응답 1회 반환 후 어디에도 보관하지 않음.

### 6.3 마스킹 규칙 (common/Masking — 정적 유틸)

| 대상 | 메소드 | 입력(복호 평문) | 응답 표시 |
|---|---|---|---|
| 사업자등록번호 | `bizRegNo(String)` | `123-45-67890` | `123-**-*****` (앞 3자리만) |
| 연락처 전화 | `phone(String)` | `010-1234-5678` | `010-****-5678` (가운데 블록 마스킹 — security-plan §2-4 포맷으로 통일, 교차 검증 발견 16) |
| 카드 | `card(String last4)` | `1234` (card_last4 평문 컬럼) | `**** **** **** 1234` |
| PG 빌링키 | — | — | **어떤 API로도 반환하지 않음**(DTO에 필드 없음) |

마스킹은 **응답 DTO 조립 시점에만** 적용(저장값은 항상 암호문 원본). 수정 화면은 마스킹값 표시 + 전체 재입력(PUT) — 부분 노출 API 없음.

---

## 7. 메시지 키 목록 (신규/변경 — 3언어 번역은 구현 단계)

### 7.1 에러/도메인 키 (`messages*.properties`)

```properties
# 공통 에러 — 문구만 변경(키 불변)
error.forbidden=접근 권한이 없습니다.

# 로그인 레이트 리밋 (429 — security-plan §3)
auth.login.rate-limited=잠시 후 다시 시도해 주세요.

# 테넌트 (tenant.*)
tenant.not-found=존재하지 않는 테넌트입니다.
tenant.code.duplicated=이미 사용 중인 테넌트 코드입니다.
tenant.suspend.self=자기 소속 테넌트는 정지할 수 없습니다.
tenant.profile.not-found=등록된 기업 정보가 없습니다.
tenant.billing.not-found=등록된 결제 정보가 없습니다.
tenant.billing.card-key.required=카드 결제는 PG 빌링키가 필요합니다.

# 멤버 (member.*)
member.not-found=존재하지 않는 멤버입니다.
member.email.duplicated=이미 사용 중인 이메일입니다.
member.role.invalid=지정할 수 없는 역할입니다.
member.status.invalid=지정할 수 없는 상태입니다.
member.last-admin=테넌트의 마지막 관리자는 변경/비활성/삭제할 수 없습니다.
```

삭제: `user.email.duplicated`(→ `member.email.duplicated`로 대체), `api.user.*`, `schema.signup-request*`.
불변 재사용: `auth.login.failed`(테넌트 불일치 포함 전 실패 사유), `error.unauthorized`.

### 7.2 검증 키 (validation.*)

```properties
validation.tenant-code.required=테넌트 코드를 입력해 주세요.
validation.tenant-code.size=테넌트 코드는 20자 이하로 해주세요.
validation.tenant-code.format=테넌트 코드는 영대문자/숫자 2~20자로 해주세요.
validation.tenant-name.required=회사명을 입력해 주세요.
validation.tenant-name.size=회사명은 100자 이하로 해주세요.
validation.tenant-status.required=테넌트 상태를 입력해 주세요.
validation.member-role.required=역할을 입력해 주세요.
validation.member-status.required=상태를 입력해 주세요.
validation.biz-reg-no.required=사업자등록번호를 입력해 주세요.
validation.biz-reg-no.format=사업자등록번호는 000-00-00000 형식으로 입력해 주세요.
validation.ceo-name.size=대표자명은 50자 이하로 해주세요.
validation.address.size=주소는 200자 이하로 해주세요.
validation.contact-name.size=담당자명은 50자 이하로 해주세요.
validation.contact-phone.size=연락처는 20자 이하로 해주세요.
validation.contact-phone.format=연락처는 숫자와 하이픈으로 입력해 주세요.
validation.billing-method.required=결제 방식을 입력해 주세요.
validation.pg-key.size=PG 빌링키는 200자 이하로 해주세요.
validation.card-last4.format=카드 마지막 4자리는 숫자 4자리로 입력해 주세요.
validation.card-brand.size=카드 브랜드는 20자 이하로 해주세요.
validation.plan.size=플랜은 20자 이하로 해주세요.
validation.memo.size=메모는 500자 이하로 해주세요.
```

기존 재사용: `validation.email.*`, `validation.name.*`, `validation.depart.size`, `validation.password.*`.

### 7.3 Swagger 키 (api.* / schema.*)

```properties
# api.* — 태그/요약/설명/응답코드 (네이밍: api.<태그>.<오퍼레이션>.<속성>)
api.system-tenant.tag / api.system-tenant.create.summary|description|201|409
api.system-tenant.list.summary|description / api.system-tenant.get.summary|404
api.system-tenant.status.summary|400
api.system-tenant.profile-get.summary|404 / api.system-tenant.profile-upsert.summary|description
api.system-tenant.billing-get.summary|404 / api.system-tenant.billing-upsert.summary|description|400
api.member.tag / api.member.list.summary / api.member.create.summary|description|201|409
api.member.status.summary|409 / api.member.role.summary|409
# 변경: api.auth.login.description(테넌트 코드 포함으로), api.auth.login.401(문구에 테넌트 포함)
# 변경: api.language.list/upsert.summary 의 "[관리자]" → "[시스템 관리자]"

# schema.* — 모델/필드
schema.field.tenant-code / schema.field.tenant-name / schema.field.tenant-status
schema.field.role / schema.field.member-status / schema.field.member-count
schema.field.initial-password / schema.field.biz-reg-no-masked / schema.field.contact-phone-masked
schema.field.card-masked
schema.tenant-create-request / schema.tenant-create-request.admin-email / schema.tenant-create-request.admin-name
schema.tenant-create-response / schema.tenant-response / schema.tenant-update-request / schema.tenant-status-request
schema.tenant-profile-request / schema.tenant-profile-response
schema.tenant-billing-request / schema.tenant-billing-response / schema.billing-method
schema.member-create-request / schema.member-create-response / schema.member-response
schema.member-update-request / schema.member-status-request / schema.password-reset-response
```

### 7.4 화면 텍스트 시드 (V5 — language_master, 3언어)

파일: **`V5__seed_saas_texts.sql` 단일 파일**(멤버/테넌트/랜딩 키 통합 — D-E. `INSERT IGNORE`, V3 방식).

- **키 목록의 정본은 두 문서**: 관리 화면(W999/W001/W007/W008/W009)은 frontend-plan §7, 랜딩(W000 `LANDING_*`)은 landing-page.md §2. 여기서는 window_id 배치만 확정한다:
  - `W007`(테넌트 목록/생성): 목록·생성 폼·정지/재개·초기 비밀번호 키
  - `W008`(테넌트 상세): 기업 정보(`PROFILE_TITLE`, `BIZ_REG_NO` 등)·결제 정보(`BILLING_*`, `METHOD_*`) 키 — **W007이 아닌 W008에 배치**(D-A 3분할)
  - `W009`(멤버 관리): 멤버 목록/등록/역할·상태 라벨 키 — **W008이 아닌 W009에 배치**(D-A)
- **DELETE 없음**: V3 시드의 `W003`/`INDEX_*`(및 `W999/SIGNUP`) 행은 잔존 허용 — 어떤 화면도 참조하지 않는 무해한 죽은 행(D-A 확정, §1.5)

---

## 8. 하위 호환 / E2E 영향

### 8.1 시그니처 불변 API (W005/W006 포함)

다음 API는 **HTTP 계약(경로·요청·응답 스키마) 완전 불변** — 내부 서비스/매퍼에만 tenantId 조건이 추가된다:

| API | 내부 변경 |
|---|---|
| `GET /api/v1/attendance/status` (W005) | `status(tenantId, userId)` |
| `POST /api/v1/attendance/check` (W005) | `check(tenantId, userId, ...)` — 토큰 발급/검증 tenant 스코프 |
| `POST /api/v1/attendance` (W005) | `confirm(tenantId, userId, ...)` |
| `GET /api/v1/attendance/monthly` (W006) | `monthly(tenantId, userId, ...)` — holiday가 테넌트별로 바뀌지만 응답 형태 동일 |
| `GET /api/v1/i18n/{windowId}` | 없음(글로벌) |
| `GET/POST /api/v1/admin/i18n` | 계약 불변, 요구 role만 admin → SYSTEM_ADMIN |
| `POST /api/v1/auth/logout` | 없음 |

(navigation은 응답에 `role` 필드가 추가되므로 **계약 불변 목록에서 제외** — §8.2로 이동. 출결 4종은 허용 role이 {TENANT_ADMIN, MEMBER}로 좁아지나 HTTP 계약 자체는 불변.)

### 8.2 계약이 깨지는 지점 (프론트 동일 PR 수정 — 마스터 §10)

1. `POST /api/v1/auth/login`: 요청 `tenantCode` 필수 추가, 응답 `admin`(boolean) 제거 → **`role` 단일 필드**(D-D) + `tenantCode`/`tenantName`.
2. `GET /api/v1/auth/me`: 응답 동상.
3. `POST /api/v1/users` 삭제(프론트 `userApi.signup`/`SignupScreen`/W003 제거).
4. navigation: ①응답에 `role: Role`(비로그인 null) 필드 추가(헤더 role 분기의 전제 — frontend-plan §1-4) ②로그인 홈이 role별 분기(SYSTEM_ADMIN→W007, TENANT_ADMIN/MEMBER→W005) ③reason `ADMIN_ONLY` → `ROLE_DENIED` — `admin ? W004 : W005` 가정 코드 제거.

### 8.3 E2E(12단계)에 미치는 영향 목록

| # | 영향 | 대응 |
|---|---|---|
| 1 | 로그인 단계: 요청 바디에 `tenantCode` 필드 필요 | 시드 관리자는 V4에서 `DEFAULT` 테넌트 소속 SYSTEM_ADMIN으로 승격 → `{tenantCode:"DEFAULT", ...}` |
| 2 | 회원가입 단계 소멸(W003/`POST /users`) | "SYSTEM_ADMIN 테넌트 생성 → 응답의 초기 비밀번호로 TENANT_ADMIN 로그인 → 멤버 등록 → 그 멤버로 출결" 시나리오로 대체(마스터 §7) |
| 3 | 로그인 후 홈 화면 검증: 시드 관리자 홈이 W004 → **W007** | 화면 코드 기대값 수정 |
| 4 | 관리자 화면 접근 게이팅: MEMBER/TENANT_ADMIN의 W004 요청 → 각자 홈(W005)으로 | reason은 `ROLE_DENIED`(개명 반영) |
| 5 | 출결 체크→확정→상태→월별 단계 | **무변경**(계약 불변, 세션에서 tenant 자동 결정) |
| 6 | `auth/me`·화면 헤더의 유저 표기 | `admin` → `role` 필드로 검증식 변경 |
| 7 | 언어 텍스트 검증 | W003 키는 잔존하나 미사용(단언 대상 아님), W999 `TENANT_CODE`·W000 `LANDING_*` 등 추가분 반영 |
| 8 | 신규 격리 시나리오 추가 | 테넌트 A/B 세션 교차 접근 4종(마스터 §7) — CI 게이트 |

### 8.4 기존 단위 테스트 영향

- `AttendanceServiceTest`(상태머신/해시): 메소드 호출에 tenantId 인자만 추가, 검증 로직 불변(마스터 §7의 "로직 불변 확인"이 목적이므로 기대값 수정 금지).
- `MonthlyAttendanceAssemblerTest`: 무변경(어셈블러는 tenant 무관).
- `NavigationServiceTest`: §5.3에 따라 재작성(케이스 증가).
- 신규: `RoleInterceptor` 단위(화이트리스트 매트릭스 파라미터라이즈드), `LoginRateLimiter`(임계/차단 해제/성공 초기화/윈도우 4건), `TenantService`(코드 중복/최초 관리자 발급), `MemberService`(마지막 관리자 보호 4경로), `FieldCipher`(라운드트립/`v1:` 프리픽스/IV 랜덤/변조 태그), `Masking`.

---

## 부록 A. 테넌트 생성 트랜잭션 흐름

```
POST /api/v1/system/tenants  (SYSTEM_ADMIN)
  TenantService.create(request)  @Transactional
    ① tenantMapper.existsByCode(code) → true면 409 tenant.code.duplicated
    ② tenantMapper.insert(TenantCreate)  → tenantId 회수(useGeneratedKeys)
    ③ memberService.registerInitialAdmin(tenantId, adminEmail, adminName)
         - 초기 비밀번호 생성(SecureRandom) → BCrypt 해시 → userMapper.insert
           (role=TENANT_ADMIN, status=ACTIVE)
         - 반환: (userId, 평문 초기 비밀번호)
    ④ TenantCreateResponse 조립(initialPassword는 이 응답이 유일한 노출)
```

## 부록 B. 구현 순서 (마스터 로드맵과의 대응)

1. V4 + Role/UserStatus/SessionUser/AuthService/RoleInterceptor/WebConfig + LoginRateLimiter/SecurityHeadersFilter/운영 프로파일 (Phase 1)
2. 매퍼/서비스 tenantId 전파(§4) + 격리 테스트 (Phase 1)
3. Screen/NavigationService 개편 + 프론트 로그인 폼 (Phase 1)
4. tenant 패키지(FieldCipher 포함) + member API + V5 시드 (Phase 2)
5. signup 폐기·정리 + E2E 갱신 (Phase 2 마감)

---

## 교차 검증 반영 이력(2026-07-08)

- D-A/D-B: 화면 코드 3분할(W007 목록/W008 상세/W009 멤버) 채택, TENANT_ADMIN 홈=W005, `ADMIN_ONLY`→`ROLE_DENIED` 개명, `Role.atLeast` 서열 폐기 → RoleInterceptor를 security-plan §6-1 화이트리스트(attendance 포함)로 재작성, Screen을 허용 role 집합으로 전환.
- D-C: FieldCipher를 JCA 직접 구현 + `v1:` 텍스트 포맷 + `APP_CRYPTO_KEY`(base64 32B, dev 기본값/prod 필수)로 교체, 암호화 컬럼 VARCHAR 전환.
- D-D: 테넌트 8·멤버 4 엔드포인트로 계약 단일화(`tenantCode` 통일, 평면 record, `hasBillingKey` 추가, `…Masked` 필드명, `LAST_TENANT_ADMIN` 통일, NavigateResponse `role` 추가). 단건 조회/삭제/재발급/이름 수정은 Phase 3 이연.
- 발견 8~12·16·20~24 반영: 429 레이트리밋, 체크토큰 TTL 30분, SecurityHeadersFilter·prod 프로파일, deleteCheck 3중 조건, payload_hash에 tenantId 포함, 전화 마스킹 `010-****-5678`, W003 시드 잔존 허용, FOR UPDATE 명기, contactPhone 형식 검증, tenantCode 대문자 정규화.
