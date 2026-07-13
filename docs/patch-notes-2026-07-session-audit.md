# 패치 노트 — 세션 수명(모바일) + 감사 로그

Phase 6 이후 후속. 로그인 세션을 모바일 편의에 맞게 늘리고, 인증·에러 감사 로그를 신설한다.
설계 논의 배경: 기존은 순수 서버 세션(HttpSession) + 20분 idle, 토큰/리프레시토큰·감사 로그 없음.

## D56. 세션 수명 = 최대 1일 슬라이딩(A안 — 세션 유지, 리프레시토큰 미도입)

- `server.servlet.session.timeout` 20분 → **1일**(슬라이딩: 요청마다 갱신, 1일 무활동 시 만료).
  (초기 7일에서 1일로 하향 — 실사용상 이틀 이상 지나면 재로그인이 자연스러워 상한을 낮췄다.)
- `server.servlet.session.cookie.max-age=1d` 부여 → JSESSIONID가 **영속 쿠키**(브라우저/앱 재시작에도 유지).
- **쿠키도 슬라이딩**(`SlidingSessionCookieInterceptor`, D60-b): 서블릿 컨테이너는 max-age 쿠키를 세션 생성 시 한 번만 내려줘 쿠키가 로그인 시점 절대 만료가 된다(매일 쓰는 유저도 만료 유발). 인증된 요청마다 같은 세션 ID로 쿠키를 동일 max-age로 재발급해 **서버 세션과 쿠키가 함께 슬라이딩**(매일 쓰는 유저는 만료 없음, 1일 무활동 시 서버·쿠키 동시 만료). 속성은 컨테이너와 같은 `server.servlet.session.cookie.*`를 읽어 단일 출처(운영 `secure=true`도 반영).
- **리프레시토큰(JWT) 재설계는 하지 않음** — 목표(1일 슬라이딩)는 세션 방식에 이미 내장된 동작이라 설정 변경으로 달성. JWT access+refresh는 네이티브 앱/무상태 수평확장이 필요할 때의 선택지로 보류.
- 안전장치: 기존 **비밀번호 변경 킬스위치** + **단일 세션 강제**(D59)가 긴 세션에서도 즉시 회수를 보장.
- ⚠ **운영 주의**: 세션은 서버 인메모리 → 다중 인스턴스로 확장 시 공유 저장소(Spring Session JDBC/Redis)가 필요하다(이용자 증가 시 Redis 도입 후보). 인메모리 세션·레이트리밋 카운터가 함께 외부화 대상.

## D57. 감사 로그(audit_log, V19)

- 신규 테이블 `audit_log`(FK 없음 — 삭제 후에도 보존, 로그인 실패는 user_id NULL): `category·event·detail·actor_email·ip·user_agent·request_path·created_at`.
- `AuditService.record(...)`는 **본 요청을 절대 깨지 않는다**(예외 삼킴 + 경고 로그). IP/UA/경로는 요청에서 추출, 컬럼 길이로 절단.
- 기록 지점:
  - **AUTH**: `LOGIN_SUCCESS`(AuthController) / `LOGIN_FAIL`(존재 비노출 원칙상 user_id 없음 — 시도 이메일·테넌트·사유코드) / `LOGOUT` / `SESSION_REVOKED`(재검증 회수 사유: HOST_TENANT_MISMATCH·USER_INACTIVE·TENANT_SUSPENDED·PASSWORD_CHANGED).
  - **ERROR**: `APP_ERROR`(GlobalExceptionHandler의 처리되지 않은 예외/500 — 예외 클래스·메시지·경로, 세션 있으면 행위자 식별).
- 기존 로그인 실패는 `LoginRateLimiter` 인메모리라 재시작 시 소실 → 감사용 **영속 기록**을 별도로 남김.

## D58. 감사 로그 조회 화면(W017, SYSTEM_ADMIN)

- `GET /api/v1/admin/audit?category=&limit=` — 운영사(SYSTEM_ADMIN) 전용(`/admin/**` 화이트리스트 편승). 전역(모든 테넌트 + tenant/user가 NULL인 비인증 이벤트) 최신순, `category`(AUTH/ERROR) 선택 필터, limit 1~500 클램프.
- 테넌트명·행위자명을 LEFT JOIN(널 이벤트 보존)해 사람이 읽을 수 있게 표시. 화면 **W017**: 시각·분류(뱃지)·이벤트·테넌트·행위자·IP·경로·상세 + 인증/에러 필터 + 새로고침. SA 헤더에 "감사 로그" 메뉴 추가.

## D59. 단일 세션 강제(session_token, V21)

- `users.session_token` 신설(V21). **로그인 성공 시마다 새 토큰(UUID) 발급·저장**(`AuthService.authenticate`), `SessionUser` 스냅샷에 포함.
- `SessionRevalidationInterceptor`가 매 요청 스냅샷 토큰 vs DB `session_token`을 비교(비밀번호 킬스위치와 동일 위치·패턴, `Objects.equals` 널 안전) → 불일치면 세션 회수(`SESSION_REVOKED` / detail `SESSION_SUPERSEDED`).
- 효과: **새 기기 로그인이 이전 기기 세션을 다음 요청에 자동으로 밀어냄(마지막 로그인만 유효)** — 7일 장기 세션의 필수 안전장치. 로그인 실패는 토큰을 바꾸지 않아 기존 세션을 밀어내지 않는다(비밀번호 검증 통과 후에만 교체).
- 최소 침습: `User` 레코드는 미변경, 재검증은 `findSessionToken`(PK 조회 1건)만 추가.
- ⚠ "모든 기기 로그아웃"은 `session_token`을 임의 값으로 바꾸면 즉시 전 세션 회수 — 후속 버튼으로 노출 가능.

## D60. 코드 리뷰 후속 수정(세션 변경 하드닝, 8건)

세션·감사 변경은 인증 경로라 강도 높은 코드 리뷰를 돌려 8건을 전부 반영했다.

- **(a) 감사 폭주 방지(#1)**: 처리되지 않은 예외를 무조건 `APP_ERROR`로 남기면 **비인증 요청**(잘못된 JSON·미지원 메소드 405·미지원 미디어타입·핸들러 없음 404 등)으로도 누구나 `audit_log`를 무한정 부풀릴 수 있었다. `GlobalExceptionHandler`가 이 Spring MVC 프로토콜 예외 계열을 각자의 4xx로 반환하고(대부분 `org.springframework.web.ErrorResponse`를 구현 → 정확한 상태코드) **감사에 남기지 않는다**. 진짜 서버 결함(5xx)만 `APP_ERROR` 유지. (부수 효과: 잘못된 JSON·405가 이전엔 500이던 것도 올바른 4xx로 정정.)
- **(b) 세션 쿠키 슬라이딩(#4)**: `SlidingSessionCookieInterceptor` 신설 — D56 참조. 쿠키 절대만료 vs 서버 세션 슬라이딩 불일치 해소.
- **(c) 레이트리밋 차단 감사(#2)**: 429로 걸린 무차별 시도가 감사에 전혀 안 남던 공백 보완. `LoginRateLimiter.recordFailure`가 **차단 발동(임계 도달) 시점에만 true**를 반환하고, `AuthController`가 그때 `LOGIN_BLOCKED`를 1회 기록한다. 차단 중 반복 시도는 `check()`에서 429로 걸러져 재기록되지 않아 **감사 자체가 폭주하지 않는다**(무차별 정황: `LOGIN_FAIL`×N + `LOGIN_BLOCKED`×1).
- **(d) 재검증 쿼리 결합(#5)**: 요청마다 `findById`(전체 User) + `findSessionToken` 2회 조회하던 것을 `UserMapper.findRevalidationState`(status·role·password_changed_at·session_token) **1건**으로 결합. `User` 레코드는 미변경(호출부 파급 회피).
- **(e) 로그아웃 토큰 정리(#6)**: 로그아웃 시 `AuthService.clearSession`으로 DB `session_token`을 비운다 → 어떤 잔존 스냅샷도 불일치해 다음 요청에 회수(로그아웃=전 기기 로그아웃 정합).
- **(f) 정리(#7·#8)**: 미사용 `AuditLogMapper.findRecentByTenant` + `AuditLog` 레코드 제거(데드코드). `SessionUser`에 `serialVersionUID` 명시(직렬화 세션 저장소 확장 대비).

## 검증

- `mvn test` **370건 그린**(레이트리밋 차단 발동 신호 1건 신규 포함, SessionRevalidation은 결합 쿼리로 재작성).
- 단일 세션 라이브 스모크: 기기 A 로그인→/me 200 → 기기 B 로그인(토큰 교체) → **A 재요청 401(킥)·B 200(유지)** → 감사 `SESSION_REVOKED/SESSION_SUPERSEDED` 기록.
- 세션·감사 라이브 스모크(D60): 로그인 → 쿠키 **`Max-Age=86400`(1일)**, `/me` 재요청 시 **쿠키 재발급(슬라이딩)** / `GET /navigation`(405)·잘못된 JSON(400) → **`APP_ERROR` 미증가** / 실패 5회 → `LOGIN_FAIL`×5 + `LOGIN_BLOCKED`×1(6·7번째 429는 재기록 없음) / 로그아웃 → DB `session_token` `NULL` 정리.
- V1→V21 마이그레이션 적용.
