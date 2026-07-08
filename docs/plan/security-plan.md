# SaaS 멀티테넌시 보안 계획서

- 상위 문서: [`docs/plan-saas-multitenancy.md`](../plan-saas-multitenancy.md) (마스터 계획). 이 문서는 그 §5(격리 강제)·§6-1(암호화/마스킹)·§10(리스크)을 **구현 가능한 스펙 수준**으로 구체화한다.
- 전제(확정): Pool 모델(공유 스키마 + `tenant_id`), 로그인 시 테넌트 코드 입력, 3단계 권한(SYSTEM_ADMIN/TENANT_ADMIN/MEMBER), 테넌트 생성은 운영자 전용, 기업/결제 정보는 AES-256-GCM 암호화 + 응답 마스킹(카드 원본 비저장, PG 빌링키만).
- 현행 코드 기준: 세션 쿠키 인증(`AuthController` — 로그인 시 세션 재발급 있음), `AuthInterceptor`/`AdminInterceptor`(경로 기반), Spring Security 필터체인 없음(`spring-security-crypto`만 사용), MyBatis 어노테이션 매퍼.

---

## 1. 멀티테넌트 위협 모델

위협별 시나리오와 대응 통제. "구현 위치"는 이 코드베이스의 실제 파일/컴포넌트 기준.

| # | 위협 | 구체 시나리오 | 대응 통제 | 구현 위치 | 단계 |
|---|------|---------------|-----------|-----------|:---:|
| T1 | 크로스 테넌트 데이터 접근 | 테넌트 A 세션으로 B 멤버의 출결/월별 상세 조회, B의 user_id를 추측해 API 파라미터에 주입 | ① tenantId는 항상 세션(`SessionUser`)에서만 취득 — 요청 값 불신 ② 모든 테넌트 소유 테이블 쿼리에 `AND tenant_id = #{tenantId}` 2중 조건 ③ 격리 테스트를 CI 게이트로 | 매퍼 전체(`AttendanceMapper`, `ScheduleMapper`, `UserMapper`), `SessionUser`, 격리 테스트 스위트 | P1 |
| T2 | 권한 상승 (수평→수직) | MEMBER가 `/api/v1/tenant/**` 호출, TENANT_ADMIN이 `/api/v1/system/**` 호출, SYSTEM_ADMIN이 테넌트 내부 출결 데이터 열람 | ① `RoleInterceptor` 경로별 허용 role **화이트리스트**(§6) ② SYSTEM_ADMIN은 출결 경로에서 명시적 403 ③ role은 세션에서만 취득(요청으로 role 변경 불가) | `RoleInterceptor`(신규, `AdminInterceptor` 대체), `WebConfig` | P1 |
| T3 | 마지막 관리자 강등을 통한 테넌트 잠금 | TENANT_ADMIN이 자기 자신(유일 관리자)을 MEMBER로 강등/비활성 → 테넌트 관리 불능 | 강등/비활성 트랜잭션 내에서 활성 TENANT_ADMIN 수를 `FOR UPDATE`로 카운트, 1명이면 409 거부(§6-2) | 멤버 관리 서비스(Phase 2 신규) | P2 |
| T4 | 세션 탈취 | XSS로 쿠키 절취, 평문 통신 스니핑, 세션 고정 | ① `HttpOnly`(스크립트 접근 차단) ② 운영 `Secure` + TLS ③ 로그인 시 세션 재발급 — **현행 구현 확인됨**(`AuthController.login`) ④ CSP로 XSS 자체 억제(§5) ⑤ 세션 타임아웃 20분(현행 1200초 유지) | `application.properties`(§4), `SecurityHeadersFilter`(§5) | P1 |
| T5 | CSRF | 세션 쿠키 인증이므로 외부 사이트에서 위조 POST(출결 등록/멤버 조작) | ① `SameSite=Lax` — 크로스 사이트 POST에 쿠키 미전송 ② CORS 화이트리스트(현행 `app.cors.allowed-origins`) + `allowCredentials` 조합 유지 ③ 전 변경 API가 JSON 바디 → 단순 폼 전송으로 재현 불가. CSRF 토큰은 도입하지 않음(Lax로 충분, §4 근거) | `application.properties`, `WebConfig` | P1 |
| T6 | 무차별 로그인 / 크리덴셜 스터핑 | 테넌트 코드+이메일 조합에 비밀번호 대입, 유출 계정 목록 대입 | ① 계정 키 + IP 키 2단 슬라이딩 윈도우 레이트 리밋(§3) ② BCrypt(현행) ③ 실패 사유 단일 메시지 — 테넌트 존재 여부도 비노출(마스터 계획 §6 확정) | `LoginRateLimiter`(신규), `AuthService` | P1 |
| T7 | 체크토큰 재사용 / 크로스 사용 | 타 유저·타 테넌트의 check 토큰으로 confirm 호출, 같은 토큰 반복 confirm | ① 토큰 조회 조건을 `(token, user_id)` → `(token, user_id, tenant_id)`로 확장 ② 단일 사용 보장 — confirm 성공 시 `deleteCheck` (현행 있음) ③ 토큰은 UUIDv4(추측 불가) ④ payload SHA-256 해시로 변조 탐지(현행) ⑤ TTL: 현행 24시간 지연 청소 → **30분으로 단축**(check→confirm은 즉시 UX, 24시간 유효는 과잉) | `AttendanceMapper.findCheckHash`/`deleteExpiredChecks`, V4 마이그레이션(`attendance_check.tenant_id`) | P1 |
| T8 | IDOR | URL/바디의 식별자 조작으로 남의 리소스 접근 | ① 셀프서비스 API는 식별자를 받지 않음 — 현행 출결 API는 전부 `@LoginUser`의 userId만 사용(경로에 id 없음, 확인됨). 이 원칙 유지 ② 관리 API(멤버 조회/수정)는 대상 userId를 받되 **반드시 세션 tenantId와 AND 조건** — 타 테넌트 id는 not found와 동일하게 404 | 컨트롤러 시그니처 규약, 매퍼 2중 조건 | P1–P2 |
| T9 | 정지(SUSPENDED) 테넌트 접근 | 계약 종료/미납 테넌트의 계정이 계속 사용 | 로그인 시 `tenant.status=ACTIVE` 검사(불일치 시 동일한 401). 정지 시점에 살아있는 세션은 최대 20분(타임아웃) 내 소멸 — 즉시 차단(요청별 status 조회)은 비용 대비 과잉으로 도입하지 않음. 즉시 강제 종료가 필요하면 Phase 3 세션 레지스트리와 세트 | `AuthService.authenticate` | P1 |
| T10 | 결제/기업 정보 유출 | DB 덤프 유출, 로그 파일 유출, API 응답 과다 노출 | ① 저장 암호화 AES-256-GCM(§2) ② 응답은 마스킹 값만, 빌링키는 어떤 API로도 미반환 ③ 로그 출력 금지 규약(§8) ④ 카드 원본(PAN/CVC/유효기간) 비저장 — PG 빌링키 + last4만 | `FieldCipher`(신규), 마스킹 유틸, DTO 규약 | P2 |

## 2. 필드 암호화 유틸 스펙 (AES-256-GCM)

대상 필드(마스터 계획 §6-1): `tenant_profile.business_reg_no`, `tenant_profile.contact_phone`, `tenant_billing.pg_customer_key`.

### 2-1. spring-security-crypto 활용 여부 판단

마스터 계획은 "spring-security-crypto의 AES-GCM 유틸 활용"으로 적었으나, 검토 결과 **필드 암호화는 JCA 직접 구현으로 조정**한다(BCrypt는 계속 spring-security-crypto 사용). 근거:

| 요구 | `AesBytesEncryptor(GCM)` / `Encryptors.stronger()` | 판단 |
|------|-----------------------------------------------------|------|
| 키를 환경변수의 raw 32바이트로 주입 | `Encryptors.stronger()`는 password+salt에서 PBKDF2로 키 유도 — raw 키 직접 주입 경로가 부자연스러움 | 불일치 |
| IV 12바이트 (GCM 표준·NIST SP 800-38D 권장) | 기본 `KeyGenerators.secureRandom(16)` — 16바이트 IV | 불일치 |
| 암호문에 키 버전 프리픽스(`v1:`) — 로테이션 대비 | 포맷이 `IV‖ciphertext` 고정, 버전 개념 없음 | 불일치 |
| 구현 비용 | JCA `Cipher("AES/GCM/NoPadding")` 직접 사용 시 40줄 내외 + 단위 테스트로 커버 가능 | 자체 구현이 더 단순 |

### 2-2. 키 관리

- 환경변수 `APP_CRYPTO_KEY`: **base64 인코딩된 32바이트**(= AES-256 키). 생성: `openssl rand -base64 32`.
- 기동 시 검증: 디코딩 실패 또는 길이 ≠ 32바이트면 `FieldCipher` 빈 생성에서 즉시 예외 → **앱 기동 실패**(잘못된 키로 조용히 운영되는 것 방지). 로컬 개발도 기본값 없음 — 키를 저장소/코드에 두지 않는다(README에 생성 명령만 기재).
- 키 버전: 현행 키 = `v1`. 로테이션 시 `APP_CRYPTO_KEY_V2` 추가 → 복호화는 프리픽스로 키 선택, 신규 암호화는 최신 버전 → 배치로 재암호화 후 구 키 제거. Phase 2에서는 v1 단일 키만 구현하고 **포맷만 로테이션 가능하게** 둔다.

### 2-3. 암호문 포맷과 구현

```
포맷:  v1:{base64(iv)}:{base64(ciphertext||tag)}
       - iv: 12바이트, 암호화마다 SecureRandom 생성 (GCM에서 (키,IV) 재사용은 치명적 — 반드시 매회 랜덤)
       - tag: 128비트, ciphertext 뒤에 연접(JCA 기본 출력 그대로)
저장:  위 문자열의 UTF-8 바이트를 VARBINARY 컬럼에 저장
       (business_reg_no/contact_phone VARBINARY(256), pg_customer_key VARBINARY(512) — 오버헤드
        약 base64 4/3배 + 프리픽스 28바이트 이내이므로 여유 충분)
```

```java
@Component
public class FieldCipher {

    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${app.crypto.key}") String base64Key) {  // app.crypto.key=${APP_CRYPTO_KEY}
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("APP_CRYPTO_KEY must be base64 of 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String plain) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        String encoded = VERSION + ":" + Base64.getEncoder().encodeToString(iv)
                + ":" + Base64.getEncoder().encodeToString(ct);
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    public String decrypt(byte[] stored) {
        String[] parts = new String(stored, StandardCharsets.UTF_8).split(":", 3);
        // parts[0] 버전으로 키 선택(현재는 v1만) — 미지 버전이면 예외
        ...
    }
}
```

- 예외 처리: 복호화 실패(태그 불일치 = 변조/키 불일치)는 `ApiException`이 아니라 500 계열로 — 데이터 무결성 사고이므로 사용자 메시지로 가리지 않고 서버 로그에 tenant_id/컬럼명만 남긴다(값은 로그 금지, §8).
- AAD(추가 인증 데이터)는 v1에서는 사용하지 않는다. 암호문 행 바꿔치기는 두 테이블 모두 `tenant_id`가 PK(1:1)이고 쓰기 경로가 SYSTEM_ADMIN 전용이라 위협 모델상 우선순위가 낮음. 도입 시 `tenantId:컬럼명`을 AAD로 묶는 확장 여지만 기록해 둔다.
- 테스트: 라운드트립, 동일 평문 2회 암호화 시 암호문 상이(IV 랜덤 확인), 변조된 태그 복호화 실패, 잘못된 키 길이 기동 실패 — 4건을 단위 테스트로.

### 2-4. 마스킹 규칙

응답 DTO에는 **마스킹된 문자열만** 담는다(평문은 서비스 메소드 지역변수까지만). 수정 화면도 마스킹 값 표시 + 전체 재입력 방식(마스터 계획 §6-1).

| 필드 | 원본 예 | 마스킹 결과 | 규칙 |
|------|---------|-------------|------|
| 사업자등록번호 | `123-45-67890` | `123-**-*****` | 앞 3자리만 노출, 구분자 유지 |
| 카드 | (원본 비저장) | `**** **** **** 1234` | `card_last4` 컬럼(평문 허용 범위)으로 조립 — 복호화 자체가 없음 |
| 전화번호 | `010-1234-5678` | `010-****-5678` | 가운데 블록 마스킹. 하이픈 없는 입력은 정규화 후 적용 |
| 이메일 | `contact@acme.co.kr` | `co*****@acme.co.kr` | 로컬파트 앞 2자 노출 + 나머지 `*`, 도메인 노출. 로컬파트 2자 이하면 전부 `*` |
| PG 빌링키 | — | **미반환** | 어떤 API 응답에도 포함 금지. 존재 여부만 `hasBillingKey: true` 불리언으로 |

구현: `Masking` 유틸 클래스(정적 메소드 4개) + 단위 테스트. 컨트롤러/서비스가 아닌 **DTO 팩토리 메소드에서 마스킹 적용**(마스킹 안 된 값이 DTO에 실릴 경로를 없앤다).

## 3. 로그인 보호 (레이트 리밋)

### 3-1. 설계

Spring Security 필터체인이 없으므로 `AuthService.authenticate()` 진입 전에 검사하는 자체 컴포넌트로 구현한다. 단일 인스턴스 전제(마스터 계획 §10)이므로 인메모리로 충분 — 수평 확장 시 세션 외부화(Redis)와 함께 이 카운터도 Redis로 이동(같은 TODO 항목에 합류).

| 항목 | 값 | 근거 |
|------|-----|------|
| 키 1 (계정) | `acct:{tenantCode소문자}:{email소문자}` | 특정 계정 표적 공격 차단. **계정 실존 여부와 무관하게 동일 적용**(존재 오라클 방지) |
| 키 2 (IP) | `ip:{클라이언트 IP}` | 계정을 바꿔가며 대입하는 스터핑 차단 |
| 윈도우 | 슬라이딩 5분 (키별 실패 타임스탬프 덱 보관, 접근 시 만료분 제거) | 고정 윈도우의 경계 회피(윈도우 끝+시작 몰아치기) 방지 |
| 임계 | 계정 키: 5분 내 실패 5회 / IP 키: 5분 내 실패 30회 | 계정은 오타 여유 수준, IP는 NAT 공용망(사내에서 여러 직원이 동일 IP) 고려해 느슨하게 |
| 차단 시간 | 계정: 5분 / IP: 15분 | 자동화 공격의 실효 속도를 무력화하면서 정당한 사용자 대기를 최소화 |
| 성공 시 | 해당 계정 키 카운터 초기화 (IP 키는 유지) | 정상 사용자 복귀를 빠르게 |
| 메모리 상한 | 키 수 상한(예: 10만) 도달 시 가장 오래된 키부터 제거 + 매 접근 시 만료 항목 정리 | 키 스프레이로 인한 메모리 고갈 방지 |

- 클라이언트 IP: 리버스 프록시 뒤 배포 시 `server.forward-headers-strategy=framework` 설정 후 `request.getRemoteAddr()` 사용. 프록시가 없는 구성에서 `X-Forwarded-For`를 직접 읽지 않는다(위조 가능).
- 구현 형태: `LoginRateLimiter` 컴포넌트(`ConcurrentHashMap<String, Deque<Long>>` + 차단 만료 시각 맵). `AuthController.login`이 ①limiter 검사 → ②`authenticate` → ③실패 시 `recordFailure`, 성공 시 `reset` 순으로 호출. 단위 테스트: 임계 도달/차단 해제/성공 초기화/윈도우 슬라이딩 4건.

### 3-2. 응답: 401 위장 vs 429 — **429로 확정**

| 선택지 | 장점 | 단점 |
|--------|------|------|
| 401로 위장(구분 불가) | 공격자에게 차단 발동 사실 은폐 | 비밀번호가 **맞는** 정당한 사용자도 "인증 실패"를 보게 됨 → 불필요한 비밀번호 재설정/문의 유발. 공격자는 어차피 타이밍·성공률로 차단을 감지 |
| **429 반환 (채택)** | 사용자에게 "잠시 후 재시도" 안내 가능, 프론트가 구분 처리 가능, 시맨틱 정확 | 차단 발동이 노출 — 그러나 아래와 같이 정보 가치 없음 |

**429가 계정 정보를 누설하지 않는 근거**: 카운팅이 자격 증명 검증 **이전에** 수행되고, 존재하지 않는 tenantCode/email 조합에도 동일하게 적용된다. 따라서 429는 "그 조합으로 시도가 많았다"만 알려줄 뿐 계정 실존 여부를 구분하는 신호가 되지 않는다. 응답 바디는 기존 `ErrorResponse` 형식으로 `code=RATE_LIMITED`, 메시지는 잔여 시간을 포함하지 않는 일반 문구("잠시 후 다시 시도해 주세요" — 3개 언어 메시지 키 추가). `Retry-After`/`X-RateLimit-*` 헤더는 붙이지 않는다(공격 자동화에 정확한 재개 시각을 주지 않기 위해).

인증 실패 자체는 현행 유지: 단일 메시지 `auth.login.failed` 401 — 테넌트 부존재/계정 부존재/비밀번호 불일치/`status≠ACTIVE`/테넌트 SUSPENDED 전부 동일 응답(마스터 계획 §6 확정 사항의 재확인).

### 3-3. 비밀번호 정책 — 현행 유지

현행 `UserDtos.PASSWORD_PATTERN`(4종 문자군 중 3종 조합, 8~30자, Bean Validation) 을 **유지**한다. 근거: 이미 구현·테스트되어 있고, BCrypt 72바이트 제한 내(최대 30자)이며, 관리자 등록제 전환 후에도 멤버 등록/초기 비밀번호 발급 DTO에서 동일 상수를 재사용하면 된다. 변경 시 얻는 것이 없다. 추가 사항 2건만:

- SYSTEM_ADMIN이 발급하는 초기 비밀번호는 서버가 패턴을 충족하는 랜덤 12자 이상으로 **생성**(`SecureRandom`)해 반환 — 운영자가 약한 비밀번호를 임의 입력하는 경로를 만들지 않는다.
- 최초 로그인 시 강제 변경은 비밀번호 재설정 인프라(메일)와 함께 Phase 3(마스터 계획 §6과 동일 판단).

## 4. 세션/쿠키 하드닝

### 4-1. 현행 확인

| 항목 | 현행 | 비고 |
|------|------|------|
| 세션 고정 방지 | **구현됨** — `AuthController.login`이 기존 세션 invalidate 후 재발급(LANG 승계 포함) | 유지. 격리 테스트에 "로그인 전후 JSESSIONID 상이" 케이스 추가 |
| tracking-mode | `cookie` 명시(URL 세션 ID 없음) | 유지 |
| 타임아웃 | 1200초(20분) | 유지 — 출결 앱 사용 패턴(짧은 조작)에 적정 |
| HttpOnly | 미설정 — Tomcat 기본값 true로 동작 중 | 기본값 의존 제거, **명시 설정** |
| SameSite | 미설정 — Boot/Tomcat은 기본적으로 SameSite 속성을 붙이지 않음(브라우저 기본 동작에 의존) | **`lax` 명시** — T5(CSRF)의 1차 방어 |
| Secure | 미설정(기본 false) | 운영 프로파일에서 **true 명시**(TLS 전제) |

### 4-2. 설정 스펙

```properties
# application.properties (공통)
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax

# application-prod.properties (운영, §7)
server.servlet.session.cookie.secure=true
server.forward-headers-strategy=framework   # 리버스 프록시 뒤 배포 시
```

- `SameSite=Strict`가 아닌 `Lax`인 이유: 이 앱은 크로스 사이트 GET 진입(북마크/링크)이 있을 수 있고, 상태 변경은 전부 POST이므로 Lax로 크로스 사이트 POST 쿠키 전송이 전면 차단된다. Strict의 추가 이득(크로스 사이트 GET 차단)은 GET API가 전부 조회 전용이라 실익 없음.
- CSRF 토큰 미도입 근거: ①Lax ②CORS 화이트리스트+credentials ③전 변경 API JSON 바디(단순 폼으로 재현 불가) 3중이면 세션 쿠키 CSRF는 실질 차단. Spring Security 미도입 상태에서 토큰 인프라를 자작할 비용 대비 이득 없음.

### 4-3. 동시 세션 정책 — 제한하지 않음(Phase 1)

동일 계정의 다중 기기 로그인은 **허용**한다. 근거: 출결 스탬프는 PC/모바일 병용이 정상 사용 패턴이고, 단일 세션 강제는 세션 레지스트리(전 세션 추적)가 필요한데 현재 컨테이너 내장 세션 저장소에는 그 인프라가 없다. "새 로그인 시 기존 세션 강제 종료"가 필요해지면 Phase 3 세션 외부화(Redis, 마스터 계획 §10)와 함께 도입 — T9(정지 테넌트 즉시 차단)도 같은 인프라에 합류. Phase 3까지는 로그인 이벤트 로깅(§8)으로 이상 로그인(동시 다지역 등)의 사후 추적만 확보한다.

## 5. HTTP 보안 헤더 (서블릿 필터)

Spring Security 없이 `OncePerRequestFilter` 하나로 구현한다. 등록은 `@Component`(전 경로 적용) — 인터셉터가 아닌 필터인 이유는 에러 응답·정적 자원에도 헤더가 붙어야 하기 때문.

```java
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    private final boolean hstsEnabled;  // @Value("${app.security.hsts:false}") — 운영(TLS)에서만 true

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=()");
        if (!req.getRequestURI().startsWith("/swagger-ui")) {  // 개발용 Swagger UI만 CSP 제외(운영은 §7에서 Swagger 자체 비활성)
            res.setHeader("Content-Security-Policy", CSP);
        }
        if (req.getRequestURI().startsWith("/api/")) {
            res.setHeader("Cache-Control", "no-store");        // 출결/멤버/결제 조회 응답의 중간 캐시·디스크 캐시 방지
        }
        if (hstsEnabled) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(req, res);
    }
}
```

값 선정 근거(이 앱 특성 반영):

| 헤더 | 값 | 근거 |
|------|-----|------|
| `Content-Security-Policy` | 위 CSP | Vite 프로덕션 빌드는 외부 `.js`/`.css` 파일만 출력(인라인 스크립트 없음) → `script-src 'self'` 가능 = 반사/저장 XSS로 주입된 인라인 스크립트 실행 차단(T4의 핵심 억제). 프론트는 `app.css` 정적 CSS 사용이라 `style-src 'self'`도 성립 — CSS-in-JS 라이브러리를 도입하게 되면 그때 `style-src`만 재검토. API 호출은 동일 오리진(`connect-src 'self'`), 외부 CDN/폰트 없음 |
| `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'` | 임베드 필요 없음 | 클릭재킹(출결 버튼 오버레이) 차단. 구형 브라우저 겸용으로 둘 다 |
| `X-Content-Type-Options: nosniff` | 고정 | MIME 스니핑 차단 |
| `Referrer-Policy: no-referrer` | 고정 | 내부 URL 구조를 외부로 흘리지 않음. SPA라 리퍼러 의존 기능 없음 |
| `Permissions-Policy` | `geolocation=(self)` | 출결 스탬프가 브라우저 위치정보(위경도)를 사용하므로 self 허용, 나머지 차단 |
| `Cache-Control: no-store` (API만) | 고정 | 개인 출결·마스킹된 결제 정보의 브라우저/프록시 캐시 잔존 방지. 정적 자원(해시 파일명)은 캐시 허용해야 하므로 API 경로 한정 |
| `HSTS` | 운영 한정, 프로퍼티 토글 | 로컬(http://localhost)에서 켜면 개발 방해. `app.security.hsts=true`를 prod 프로파일에만 |

## 6. 권한 통제

### 6-1. RoleInterceptor 설계

`AdminInterceptor`를 폐기하고 단일 `RoleInterceptor`로 대체한다(마스터 계획 §4). 현행 `WebConfig`가 `/api/v1/admin/**`를 `AuthInterceptor`에서 제외하고 `AdminInterceptor`가 인증+인가를 겸하는 이중 구조도 이때 정리한다 — **인증은 `AuthInterceptor` 단일 책임, 인가는 `RoleInterceptor` 단일 책임**으로.

규칙은 선언적 테이블로 정의(코드에 그대로 옮김):

```java
// RoleInterceptor 내부 — 선언 순서대로 첫 매칭 규칙 적용
private record RouteRule(String pattern, Set<Role> allowed) {}

private static final List<RouteRule> RULES = List.of(
    new RouteRule("/api/v1/system/**",     Set.of(Role.SYSTEM_ADMIN)),
    new RouteRule("/api/v1/admin/i18n/**", Set.of(Role.SYSTEM_ADMIN)),                    // 언어 마스터는 제품 글로벌
    new RouteRule("/api/v1/tenant/**",     Set.of(Role.TENANT_ADMIN)),
    new RouteRule("/api/v1/attendance/**", Set.of(Role.TENANT_ADMIN, Role.MEMBER))        // SYSTEM_ADMIN 명시 배제
);
// 매칭: AntPathMatcher. 규칙에 없는 인증 필수 경로(auth/me, navigation 등)는 role 무관 통과.
```

| 경로 | SYSTEM_ADMIN | TENANT_ADMIN | MEMBER | 비고 |
|------|:---:|:---:|:---:|------|
| `/api/v1/system/**` (테넌트 CRUD, 기업/결제 정보) | ✅ | ❌ 403 | ❌ 403 | Phase 2 신규 |
| `/api/v1/admin/i18n/**` (언어 마스터) | ✅ | ❌ 403 | ❌ 403 | 현행 `admin` → SYSTEM_ADMIN으로 승격 |
| `/api/v1/tenant/**` (멤버 관리, 스케쥴/공휴일) | ❌ 403 | ✅ | ❌ 403 | SYSTEM_ADMIN도 차단 — 테넌트 내부 관리는 고객사 소관 |
| `/api/v1/attendance/**` (체크/확정/상태/월별) | **❌ 403** | ✅ | ✅ | §6-3 |
| `/api/v1/auth/me`, `/api/v1/navigation` | ✅ | ✅ | ✅ | 인증만 |
| `/api/v1/auth/login`, `/api/v1/i18n/**` | 인증 불요 | 인증 불요 | 인증 불요 | 현행 exclude 유지. **`POST /api/v1/users`(공개 가입)는 exclude 목록과 함께 폐기**(마스터 계획 §6 확정) |

- 응답: 미인증 401(`AuthInterceptor`), role 불일치 403(`error.forbidden` — 현행 `ApiException.forbidden()` 재사용).
- `SessionUser`는 `record SessionUser(long userId, long tenantId, String email, String name, Role role)`로 확장. `admin()` 불리언 제거 — 호출부 전수 수정(컴파일 에러로 누락 탐지).

### 6-2. 마지막 TENANT_ADMIN 보호

멤버 강등(`role` 변경)·비활성(`status=DISABLED`) 서비스 로직에서, 대상이 TENANT_ADMIN이면:

```sql
SELECT COUNT(*) FROM users
WHERE tenant_id = #{tenantId} AND role = 'TENANT_ADMIN' AND status = 'ACTIVE' AND deleted = FALSE
FOR UPDATE
```

- 같은 트랜잭션 안에서 카운트가 1이고 그 1명이 대상 본인이면 `ApiException.conflict("LAST_TENANT_ADMIN", ...)` → 409. `FOR UPDATE`인 이유: 두 관리자가 동시에 서로를 강등하는 레이스에서 0명 테넌트가 되는 것을 행 잠금으로 방지.
- SYSTEM_ADMIN의 테넌트 정지(SUSPENDED)는 이 검사와 무관(테넌트 단위 조치이므로).

### 6-3. SYSTEM_ADMIN의 출결 데이터 접근 차단 (코드 레벨 3중)

마스터 계획 §4 보완 정책 2("운영자는 테넌트 메타까지만")를 정책 문구가 아닌 코드로 보장한다:

1. **경로 차단**: `RoleInterceptor`가 `/api/v1/attendance/**`에서 SYSTEM_ADMIN을 403 처리(§6-1 표). 허용 role 화이트리스트 방식이므로 "깜빡하고 열어주는" 실수가 구조적으로 어렵다.
2. **데이터 차단**: SYSTEM_ADMIN 계정은 운영사 테넌트(`tenant_id=운영사`) 소속이다. 만약 1이 뚫려도 매퍼의 `tenant_id` 2중 조건 때문에 고객 테넌트의 출결 행은 조회 결과에 나타날 수 없다.
3. **API 표면 차단**: SYSTEM_ADMIN 전용 `/api/v1/system/**` API의 응답 DTO에 출결 데이터를 담는 필드를 만들지 않는다(테넌트 메타: 기업정보/결제 마스킹값/멤버 수/사용량 집계까지만). 리뷰 체크리스트 항목으로 고정.

격리 테스트에 "SYSTEM_ADMIN 세션으로 `/api/v1/attendance/status` 호출 → 403" 케이스를 포함한다. 장애 지원용 열람이 필요해지는 시점에는 별도 API + 감사 로그(§8) 세트로만 연다.

## 7. 시크릿 / 설정 분리

### 7-1. 환경변수 목록

| 변수 | 용도 | 형식 | 도입 시점 |
|------|------|------|:---:|
| `DB_URL` | MariaDB 접속 URL | JDBC URL | 현행 |
| `DB_USERNAME` / `DB_PASSWORD` | DB 계정 | 문자열 | 현행 |
| `APP_CRYPTO_KEY` | 필드 암호화 키(§2) | base64(32바이트) | P2 |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 화이트리스트(기존 `app.cors.allowed-origins` 바인딩) | 콤마 구분 URL | 현행 |

- 현행 `application.properties`의 DB 기본값(`attendance`/`attendance`)은 로컬 편의용으로 유지하되, **운영 프로파일에는 기본값 없이** `${DB_PASSWORD}` 필수로 — 기본 크리덴셜로 운영에 뜨는 사고 방지.
- `APP_CRYPTO_KEY`는 기본값 금지(§2-2, 기동 실패가 올바른 동작).

### 7-2. 운영 프로파일 (`application-prod.properties`, 신규)

```properties
# Swagger 완전 비활성 — 내부 API 구조/스키마 설명(3개 언어) 노출 방지
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

# devtools (패키징된 jar에서는 자동 비활성이나 명시)
spring.devtools.livereload.enabled=false

# 로그 레벨 강하 — 현행 debug는 출결 파라미터가 로그에 남음(§8)
logging.level.com.attendance.pro=info

# 쿠키/헤더 (§4, §5)
server.servlet.session.cookie.secure=true
app.security.hsts=true
```

- devtools는 `spring-boot-maven-plugin`의 `excludeDevtools` 기본값(true)으로 운영 jar에서 제외됨 — pom 변경 불요, 확인만.
- 기동 방법: `SPRING_PROFILES_ACTIVE=prod`. 마스터 계획 Phase 3 "운영 프로파일 분리" TODO가 이 파일이다 — 단 Swagger 비활성과 쿠키 Secure는 **운영 배포 전 필수**이므로 Phase 1로 앞당긴다(§9).

## 8. 로깅 / 감사

### 8-1. 결제·민감 필드 로그 금지 (구현 규약)

| 규약 | 구현 |
|------|------|
| 평문 민감값을 필드로 갖는 객체를 만들지 않는다 | 복호화 값은 서비스 메소드 **지역변수**까지만. DTO에는 마스킹 값만(§2-4의 팩토리 메소드 규약) |
| 민감값을 받는 요청 DTO는 toString을 가리기 | record는 toString 자동 생성 → `TenantProfileRequest`/`TenantBillingRequest`는 `toString()`을 오버라이드해 `businessRegNo=[PROTECTED]` 형태로. (record도 toString 오버라이드 가능) |
| 예외/검증 로그에 요청 바디를 싣지 않는다 | 현행 `GlobalExceptionHandler`는 예외 스택만 로깅(확인됨) — 이 상태 유지. 요청 로깅 필터를 추가하게 될 경우 `/api/v1/system/**` 바디 제외를 조건으로 |
| 운영 로그 레벨 info | 현행 `logging.level.com.attendance.pro=debug`는 `AttendanceService.confirm`의 스탬프 디버그 로그 등이 남으므로 운영은 info(§7-2). 민감값이 아닌 userId 수준이라 debug 자체는 유지 가능하나 운영에서 볼 이유 없음 |
| 로그인 로그에 비밀번호 절대 미포함 | 로그인 실패 로그는 `tenantCode, email, IP, 사유분류(BAD_CREDENTIAL/RATE_LIMITED/SUSPENDED)`만. rawPassword는 어떤 로그 문에도 인자로 넘기지 않는다 |

### 8-2. 감사 대상 목록 (Phase 3 예고)

Phase 3에서 `audit_log` 테이블(`audit_id, tenant_id, actor_user_id, action, target_type, target_id, detail, ip, created_at`)로 적재할 이벤트를 미리 확정해 두고, Phase 1~2에서는 동일 항목을 **서버 로그(info)로만** 남긴다(스키마 없이 시작, 나중에 소급 불가한 것은 로그로라도 남는 상태 유지):

| 이벤트 | 기록 항목 | 시점 |
|--------|-----------|:---:|
| 로그인 성공/실패/레이트리밋 차단 | tenantCode, email, IP, 결과 | P1(로그) → P3(테이블) |
| 멤버 등록/비활성/role 변경 | 행위자, 대상 userId, 변경 내용 | P2(로그) → P3 |
| 테넌트 생성/정지/재개 | 행위자(SYSTEM_ADMIN), tenantCode | P2(로그) → P3 |
| 기업/결제 정보 등록·수정 | 행위자, tenant_id, **변경 필드명만**(값 금지) | P2(로그) → P3 |
| SYSTEM_ADMIN의 테넌트 메타 조회 | 행위자, tenant_id | P3 |
| 마지막 관리자 보호 발동(409) | 행위자, 대상 | P2(로그) → P3 |

## 9. 구현 우선순위

| 항목 | 근거 | Phase |
|------|------|:---:|
| 매퍼 tenant_id 2중 조건 + 세션 tenantId 전파 (T1) | Pool 모델의 생명선 — 이것 없이는 멀티테넌시 자체가 불성립 | **P1 필수** |
| `RoleInterceptor` + SYSTEM_ADMIN 출결 차단 (T2, §6) | 3단계 권한 도입과 동시가 아니면 나중에 못 끼워 넣음 | **P1 필수** |
| 격리 테스트 스위트 (CI 게이트) | 마스터 계획 §7 최우선 항목 | **P1 필수** |
| 체크토큰 tenant_id 조건 + TTL 30분 (T7) | V4 마이그레이션에 `attendance_check.tenant_id`가 포함될 때 같이 | **P1 필수** |
| 로그인 레이트 리밋 (§3) | 테넌트 코드 도입으로 로그인 표면이 공개 멀티테넌트화되는 시점과 동시 | **P1 필수** |
| 쿠키 명시 설정(HttpOnly/SameSite=Lax) (§4) | properties 2줄 — 비용 제로 | **P1 필수** |
| `SecurityHeadersFilter` (§5) | 클래스 1개 — SPA 서빙 전 경로에 즉효 | **P1 필수** |
| 운영 프로파일(Swagger off, Secure 쿠키, HSTS, info 로그) (§7) | "운영 배포가 일어나는 첫 시점" 전 필수 — Phase 3 TODO에서 앞당김 | **P1 필수** |
| 공개 가입 `POST /api/v1/users` 폐기 | 마스터 계획 §6 확정 — 열린 표면 제거 | **P1 필수** |
| `FieldCipher` + 마스킹 유틸 (§2) | 대상 테이블(`tenant_profile`/`tenant_billing`)이 Phase 2 신규 | P2 |
| 마지막 TENANT_ADMIN 보호 (§6-2) | 멤버 관리 API(Phase 2)와 동시 | P2 |
| 감사 이벤트 서버 로그 (§8-2) | 해당 기능 구현 시 로그 문장만 추가 | P1~P2 |
| `audit_log` 테이블 적재 | 마스터 계획 Phase 3와 정합 | P3 |
| 동시 세션 제한 / 정지 테넌트 세션 즉시 종료 | 세션 외부화(Redis) 인프라 필요 | P3+ |
| 비밀번호 재설정/최초 로그인 강제 변경 | 메일 인프라 필요 | P3 |
| MyBatis 테넌트 가드 인터셉터(누락 감지) | 마스터 계획 §5 — 명시 규약 우선, 필요 시 보강 | P3 검토 |
| 키 로테이션 실적용(v2 키), AAD 도입 | 포맷만 P2에서 대비(§2-3) | 필요 시 |

## 10. 이 문서가 마스터 계획에서 조정한 것

| 항목 | 마스터 계획 | 이 문서 | 근거 |
|------|-------------|---------|------|
| AES-GCM 구현 | spring-security-crypto 유틸 활용 | JCA 직접 구현(BCrypt만 spring-security-crypto 유지) | §2-1 — IV 12바이트·버전 프리픽스 요구와 불일치 |
| 운영 프로파일 분리 | Phase 3 | Swagger off·Secure 쿠키 등 보안 항목만 Phase 1로 앞당김 | §7-2 — 운영 배포 전 필수 |
| 체크토큰 TTL | (언급 없음, 현행 24h) | 30분 | §1 T7 — check→confirm 즉시 UX에 24h는 과잉 |
