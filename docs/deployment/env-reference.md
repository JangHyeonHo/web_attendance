# 환경변수 레퍼런스

> [인덱스](README.md) · 정본은 `src/main/resources/application.properties`(공통)과
> `application-prod.properties`(운영 프로파일) — 이 표는 그 요약이다.

## prod 프로파일 필수 (미설정 시 기동 실패 — fail-fast)

| 변수 | 예 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | 공개 서버는 항상 prod([01](01-architecture.md) 전제 참조) |
| `APP_CRYPTO_KEY` | `openssl rand -base64 32` 출력 | 민감 필드 AES-256-GCM 키. ★분실=복호화 불가, 백업과 분리 보관([10](10-backup-restore.md))★. dev 기본키로 암호화된 데이터는 운영 반입 금지 |
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3306/attendance` | |
| `DB_USERNAME` | `attendance` | |
| `DB_PASSWORD` | — | |
| `SMTP_HOST` | `smtp.gmail.com` | 초대·비밀번호 재설정 메일 발송(STARTTLS 전제) |
| `SMTP_PORT` | `587` | |
| `SMTP_USER` | — | |
| `SMTP_PASS` | — | Gmail이면 앱 비밀번호 |
| `SMTP_FROM` | `no-reply@<도메인>` | 발신자 표시 주소 |
| `APP_MAIL_LINK_BASE_URL` | `https://app.<도메인>` | 메일 속 링크의 기준 URL. **요청 Host를 쓰지 않는 보안 설계**(Host 헤더 주입 피싱 차단)라 반드시 실제 접속 URL로 |

## 선택

| 변수 | 기본값 | 설명 |
|---|---|---|
| `APP_TENANT_BASE_DOMAIN` | (비움 = 꺼짐) | 테넌트 서브도메인 병행 방식([12](12-tenant-subdomain.md)). 와일드카드 DNS/TLS 준비 후에만 설정 |
| `APP_HOLIDAY_NAGER_BASE_URL` | `https://date.nager.at` | 공휴일 동기화 API. 바꿀 일 없음(스모크 테스트의 스텁 치환용) |

## 자주 틀리는 것

- `APP_MAIL_LINK_BASE_URL`을 `http://localhost:5173`(dev 기본값)로 두면 초대 메일의 링크가
  전부 localhost로 나간다 — 메일은 오는데 링크가 안 열리면 이것부터 확인.
- prod에서 기동 실패 시 로그의 `Caused by`에 어느 변수가 비었는지 나온다
  ([05](05-backend-service.md) 기동 확인 참조).
- 환경변수 파일(`/etc/attendance/attendance.env`)을 고친 뒤에는
  `sudo systemctl restart attendance`까지 해야 반영된다.
