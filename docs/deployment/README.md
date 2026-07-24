# 구축 문서 인덱스

개발(테스트) 서버는 **OCI 단일 서버**(nginx + Spring Boot + MariaDB 동거)로 구축한다.
처음 구축한다면 01→07을 순서대로 따라가면 서버가 열리고, 08 이후는 운영하면서 찾아 읽는 문서다.

## 구축 순서 (01 → 07)

| # | 문서 | 내용 |
|---|---|---|
| 01 | [architecture.md](01-architecture.md) | 전체 구성도·구성요소·전제(왜 테스트 서버도 prod 프로파일인가) |
| 02 | [oci-instance.md](02-oci-instance.md) | OCI 인스턴스·네트워크 준비(셰이프 선택, 보안 목록, iptables 함정) |
| 03 | [software-install.md](03-software-install.md) | JDK·Node·nginx 설치 + MariaDB 초기화 |
| 04 | [backend-service.md](04-backend-service.md) | 백엔드 빌드·환경변수·systemd 서비스 등록 |
| 05 | [frontend-nginx.md](05-frontend-nginx.md) | 프론트 빌드·nginx 설정(Host 보존이 왜 필수인가) |
| 06 | [dns-tls.md](06-dns-tls.md) | 도메인 A 레코드·HTTPS 인증서(certbot) |
| 07 | [initial-setup.md](07-initial-setup.md) | 초기 계정(★비밀번호 즉시 변경★)·첫 검증 시나리오 |

## 운영·유지보수

| # | 문서 | 내용 |
|---|---|---|
| 08 | [deploy-update.md](08-deploy-update.md) | 코드 갱신 배포 절차·롤백 원칙 |
| 09 | [backup-restore.md](09-backup-restore.md) | DB 백업·복원·★암호화 키 분리 보관★ |
| 10 | [security-checklist.md](10-security-checklist.md) | 공개 전·정기 보안 점검 목록 |
| 11 | [tenant-subdomain.md](11-tenant-subdomain.md) | 테넌트 서브도메인 켜기(와일드카드 DNS/TLS — 2단계 확장) |
| 12 | [test-operation-policy.md](12-test-operation-policy.md) | 테스트 서버 운영 정책(실판매 없음 고지·데이터 취급) |

## 참조

| 문서 | 내용 |
|---|---|
| [env-reference.md](env-reference.md) | 환경변수 전체 레퍼런스(한 표) |
| [production-options.md](production-options.md) | 운영 서버 구성 선택지 비교와 권장 로드맵(의사결정 대기) |

## 표기 규칙

- 명령은 Ubuntu 24.04 기준. 아직 정해지지 않은 값은 `<괄호>`로 표기 — 현재 미정 입력은
  **도메인**과 **SMTP 계정** 두 가지.
- ★표시는 사고로 직결되는 항목(비밀번호·암호화 키)이다.
