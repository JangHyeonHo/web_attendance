# 12. 보안 체크리스트

> [인덱스](README.md)

## 공개 전 1회

- [ ] AWS 루트·IAM 사용자 모두 MFA 등록, 루트 미사용 상태 ([02](02-aws-iam.md))
- [ ] AWS 예산 알림 1건 존재 ([02](02-aws-iam.md))
- [ ] 시드 관리자(admin@attendance.local) 비밀번호 변경 완료 ([09](09-initial-setup.md))
- [ ] `/swagger-ui.html`·`/v3/api-docs` 404 (prod 프로파일 — API 구조 비노출)
- [ ] SSH: 비밀번호 로그인 비활성(`PasswordAuthentication no`), 키 인증만
- [ ] 보안 그룹 인바운드가 22(내 IP)/80/443만인지 — 특히 **3306이 닫혀 있는지** 재확인
- [ ] `/etc/attendance/attendance.env` 권한 600·root 소유
- [ ] DB 계정이 `'attendance'@'localhost'`뿐인지(원격 계정 없음), root 원격 차단
- [ ] https 강제(80→443 리다이렉트), HSTS 응답 헤더 확인: `curl -sI https://... | grep -i strict`
- [ ] 세션 쿠키에 `Secure; HttpOnly; SameSite=Lax`가 붙는지(브라우저 개발자도구)

## 정기 (월 1회 권장)

- [ ] `sudo apt update && sudo apt upgrade` — 커널 갱신 시 재부팅(다운타임 수십 초, 시간대 골라서)
- [ ] `sudo certbot renew --dry-run` — 인증서 자동 갱신 경로 확인
- [ ] `journalctl -u attendance | grep -i "429\|RATE"` — 로그인 무차별 시도 흔적 훑기
  (감사 로그 화면(A003)의 인증/에러 필터로도 확인 가능)
- [ ] 백업 파일 생성·크기 확인 ([11](11-backup-restore.md))
- [ ] OS 로그인 실패 로그 훑기: `sudo lastb | head`

## 시스템이 이미 갖추고 있는 것 (중복 구축 불요)

구축자가 따로 안 해도 되는 것들 — 켜져 있는지 확인만 한다:

- CSRF 방어: 모든 API가 `X-Requested-With` 헤더 요구(프론트 client가 자동 부여)
- 로그인/재설정 레이트리밋: 계정·IP 기준 임계(429) 내장
- 단일 세션: 다른 곳에서 로그인하면 기존 세션 자동 회수
- 민감 필드 DB 암호화(AES-256-GCM) + 마스킹 응답(원문은 어떤 API로도 안 나감)
- 감사 로그: 인증 이벤트·에러가 A003 화면에서 조회 가능

## 하지 않기로 한 것 (테스트 단계)

- fail2ban·WAF·침입 탐지: 단일 테스트 서버에는 과잉. 운영 이전 시 재검토
- 2단계 인증: 관리자급 한정 도입이 백로그에 있음(기능 개발 항목)
