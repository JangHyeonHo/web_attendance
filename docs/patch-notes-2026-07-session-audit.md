# 패치 노트 — 세션 수명(모바일) + 감사 로그

Phase 6 이후 후속. 로그인 세션을 모바일 편의에 맞게 늘리고, 인증·에러 감사 로그를 신설한다.
설계 논의 배경: 기존은 순수 서버 세션(HttpSession) + 20분 idle, 토큰/리프레시토큰·감사 로그 없음.

## D56. 세션 수명 = 최대 7일(A안 — 세션 유지, 리프레시토큰 미도입)

- `server.servlet.session.timeout` 20분 → **7일**(슬라이딩: 요청마다 갱신, 7일 무활동 시 만료).
- `server.servlet.session.cookie.max-age=7d` 부여 → JSESSIONID가 **영속 쿠키**(브라우저/앱 재시작에도 유지). 로그인 시점 기준 절대 7일 상한(활동으로 서버 세션은 슬라이딩되나 쿠키는 7일 후 재로그인 유도).
- **리프레시토큰(JWT) 재설계는 하지 않음** — 목표(7일 슬라이딩)는 세션 방식에 이미 내장된 동작이라 설정 변경으로 달성. JWT access+refresh는 네이티브 앱/무상태 수평확장이 필요할 때의 선택지로 보류.
- 안전장치: 기존 **비밀번호 변경 킬스위치**(`SessionRevalidationInterceptor`, `password_changed_at` 스냅샷 비교)가 긴 세션에서도 즉시 회수를 보장. (단일 세션 강제·전체 로그아웃은 이번 범위 밖 — 필요 시 `session_version` 컬럼으로 확장 가능.)
- ⚠ **운영 주의**: 세션은 서버 인메모리 → 다중 인스턴스로 확장 시 공유 저장소(Spring Session JDBC/Redis)가 필요하고, 7일 세션은 메모리 누적을 고려해야 한다(현재 단일 인스턴스 전제).

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
- (테넌트 관리자용 자사 감사 화면은 매퍼 `findRecentByTenant`로 후속 확장 가능.)

## 검증

- `mvn test` **361건 그린**(AuditService 절단·실패삼킴·컨텍스트 3건 신규, SessionRevalidation 감사 인자 반영).
- 실 DB 라이브 스모크: 로그인 성공 시 **쿠키 `Max-Age=604800`(7일)** + `LOGIN_SUCCESS` 기록 / 실패 → `LOGIN_FAIL`(user NULL, 사유코드) / 로그아웃 → `LOGOUT` / 잘못된 JSON → 500 + `APP_ERROR`(예외명·경로) 기록. 전부 IP·UA·경로 포함.
- V1→V19 마이그레이션 적용.
