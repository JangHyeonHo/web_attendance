# 01. 전체 구성 — 개발(테스트) 서버

> 다음: [02. AWS 계정 준비](02-aws-iam.md) / [인덱스](README.md)

AWS EC2 인스턴스 1대에 전부 올리는 단일 서버 구성이다. 테스트 공개용(실판매 없음)이며,
운영 서버의 형태는 [production-options.md](production-options.md)에서 별도로 판단한다.

```
[브라우저]
   │ HTTPS(443)
   ▼
[nginx] ──────────────── 정적 파일: frontend/dist (React 빌드 산출물)
   │ /api/* 만 프록시     ※ Host 헤더 보존 필수(테넌트 해석) — 05 참조
   ▼
[Spring Boot jar :9080] ─ SPRING_PROFILES_ACTIVE=prod
   ▼
[MariaDB :3306(로컬만)] ─ Flyway가 기동 시 스키마+시드 자동 적용

외부 아웃바운드: SMTP(초대·재설정 메일), date.nager.at(공휴일 동기화)
```

## 구성요소와 역할

| 구성요소 | 역할 | 비고 |
|---|---|---|
| nginx | TLS 종단, 정적 프론트 서빙, `/api` 프록시 | 프록시 시 Host 보존 + X-Forwarded-* 부여 |
| Spring Boot | 전 업무 API(:9080) | jar 단일 파일, systemd로 상주 |
| MariaDB | 데이터 저장 | 외부 비공개(127.0.0.1만). 스키마는 Flyway 전담 — 수동 DDL 없음 |

## 전제 두 가지 (구축 전에 이해할 것)

**1. 테스트 서버도 `prod` 프로파일로 기동한다.**
dev 프로파일은 로컬 개발 전용이다 — 메일을 발송하지 않고 로그로만 남기고, Swagger가 노출되며,
debug 로그에 출결 파라미터가 남는다. 외부에 공개하는 서버는 테스트 목적이라도 prod로 띄운다.
prod는 필수 환경변수(암호화 키·DB·SMTP)가 없으면 **기동 자체가 실패**하는 fail-fast 설계다
→ [env-reference.md](env-reference.md).

**2. 테넌트 서브도메인은 나중에 켤 수 있다.**
`APP_TENANT_BASE_DOMAIN`을 설정하지 않으면 로그인 화면에서 테넌트 코드를 입력하는 방식으로
동작한다. 따라서 **일반 인증서 하나로 먼저 열고**, 와일드카드 DNS/TLS가 준비되면
[12. 테넌트 서브도메인](12-tenant-subdomain.md)으로 확장한다.

## 왜 단일 서버인가

- 테스트 단계 목적은 "실사용 피드백"이지 가용성이 아니다 — 구성요소를 최소로 유지해
  구축·운영 비용을 낮춘다.
- 세션이 서버 인메모리라 어차피 앱 다중화에는 코드 변경(세션 공유)이 선행된다.
  단일 서버는 현재 코드 그대로 동작하는 구성이다.
