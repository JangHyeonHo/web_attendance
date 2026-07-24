# 구축 문서 인덱스

개발(테스트) 서버는 **AWS EC2 단일 서버**(nginx + Spring Boot + MariaDB 동거)로 구축한다.
처음 구축한다면 01→08을 순서대로 따라가면 서버가 열리고, 09 이후는 운영하면서 찾아 읽는 문서다.

**대상 독자: 클라우드 서버를 처음 만들어 보는 사람.** 계정 가입부터 화면·명령 단위로 쓰며,
사전 지식을 가정하지 않는다. 낯선 단어는 [용어집](glossary.md)에 모아 둔다.
문서를 따라가다 질문이 생겼다면 그것은 읽는 사람 탓이 아니라 **문서의 결함**이다 —
그 지점을 알려주면 문서를 보강한다.

## 구축 순서 (01 → 08)

| # | 문서 | 내용 |
|---|---|---|
| 01 | [architecture.md](01-architecture.md) | 전체 구성도·구성요소·전제(왜 테스트 서버도 prod 프로파일인가) |
| 02 | [aws-iam.md](02-aws-iam.md) | AWS 루트 보호(MFA)·관리자 IAM 사용자·예산 알림 |
| 03 | [aws-ec2.md](03-aws-ec2.md) | EC2 인스턴스 생성(키 페어·보안 그룹·Elastic IP·SSH 접속) |
| 04 | [software-install.md](04-software-install.md) | 스왑·JDK·Node·nginx 설치 + MariaDB 초기화 |
| 05 | [backend-service.md](05-backend-service.md) | 백엔드 빌드·환경변수·systemd 서비스 등록 |
| 06 | [frontend-nginx.md](06-frontend-nginx.md) | 프론트 빌드·nginx 설정(Host 보존이 왜 필수인가) |
| 07 | [dns-tls.md](07-dns-tls.md) | 도메인 구매·A 레코드·HTTPS 인증서(certbot) |
| 08 | [initial-setup.md](08-initial-setup.md) | 초기 계정(★비밀번호 즉시 변경★)·첫 검증 시나리오 |

## 운영·유지보수

| # | 문서 | 내용 |
|---|---|---|
| 09 | [deploy-update.md](09-deploy-update.md) | 코드 갱신 배포 절차·롤백 원칙 |
| 10 | [backup-restore.md](10-backup-restore.md) | DB 백업·복원·★암호화 키 분리 보관★ |
| 11 | [security-checklist.md](11-security-checklist.md) | 공개 전·정기 보안 점검 목록 |
| 12 | [tenant-subdomain.md](12-tenant-subdomain.md) | 테넌트 서브도메인 켜기(와일드카드 DNS/TLS — 2단계 확장) |
| 13 | [test-operation-policy.md](13-test-operation-policy.md) | 테스트 서버 운영 정책(실판매 없음 고지·데이터 취급) |

## 참조

| 문서 | 내용 |
|---|---|
| [glossary.md](glossary.md) | 용어집 — 문서에 나오는 낯선 단어 전부 |
| [env-reference.md](env-reference.md) | 환경변수 전체 레퍼런스(한 표) |
| [production-options.md](production-options.md) | 운영 서버 구성 선택지 비교와 권장 로드맵(의사결정 대기) |

## 표기 규칙

- 명령은 Ubuntu 24.04 기준. 아직 정해지지 않은 값은 `<괄호>`로 표기 — 현재 미정 입력은
  **도메인**과 **SMTP 계정** 두 가지.
- `내 PC에서` 라고 적힌 명령만 내 컴퓨터에서 실행하고, 나머지는 전부 SSH로 들어간 서버 안에서 실행한다.
- "파일을 만든다/고친다"는 것은 `sudo nano <파일경로>`로 열어 내용을 붙여넣고
  Ctrl+O → Enter(저장) → Ctrl+X(닫기) 하는 것이다.
- ★표시는 사고로 직결되는 항목(비밀번호·암호화 키·변경 불가 선택)이다.
