# 개발(테스트) 서버 구축 가이드 — OCI 단일 서버 (2026-07)

> 대상: **테스트 공개용 개발 서버**(실판매 없음). OCI(Oracle Cloud Infrastructure) 인스턴스 1대에
> nginx + Spring Boot + MariaDB를 모두 올리는 단일 서버 구성.
> 운영 서버의 형태는 별도 문서(`production-options.md`) — 이 가이드는 개발 서버 한정으로 상세히 쓴다.
> 이 문서의 명령은 Ubuntu 24.04 기준. 값이 정해지지 않은 곳은 `<괄호>`로 표기한다.

## 0. 전체 구성

```
[브라우저]
   │ HTTPS(443)
   ▼
[nginx] ──────────────── 정적 파일: frontend/dist (React 빌드 산출물)
   │ /api/* 만 프록시     ※ Host 헤더 보존 필수(테넌트 해석), X-Forwarded-* 부여
   ▼
[Spring Boot jar :9080] ─ SPRING_PROFILES_ACTIVE=prod (테스트 서버도 prod 프로파일 사용)
   │                      ※ dev 프로파일은 메일을 로그로만 남기는 등 공개 서버에 부적합
   ▼
[MariaDB :3306(로컬만)] ─ Flyway가 기동 시 스키마+시드 자동 적용
   외부 통신: SMTP(메일 발송), date.nager.at(공휴일 동기화) — 아웃바운드 HTTPS 필요
```

핵심 전제 두 가지:

- **테스트 서버도 `prod` 프로파일로 기동한다.** dev 프로파일은 로컬 개발용(메일 미발송, Swagger 노출,
  debug 로그에 출결 파라미터 기록)이라 외부 공개에 쓰면 안 된다. prod는 필수 환경변수가 없으면
  기동 자체가 실패(fail-fast)하도록 설계돼 있다 — 아래 4장의 환경변수를 전부 채워야 한다.
- **테넌트 서브도메인은 나중에 켤 수 있다.** `APP_TENANT_BASE_DOMAIN` 미설정이면 로그인 화면에서
  테넌트 코드를 입력하는 방식으로 동작하므로, **1단계(단일 도메인·일반 인증서)로 먼저 열고**
  와일드카드 DNS/TLS가 준비되면 2단계로 서브도메인을 켠다(8장).

## 1. OCI 인스턴스 준비

| 항목 | 권장값 | 이유 |
|---|---|---|
| Shape | **VM.Standard.A1.Flex** (Ampere ARM) 2~4 OCPU / 12~24GB | Always Free 범위(최대 4 OCPU/24GB). JVM+MariaDB 동거에 x86 Micro(1GB)는 부족 |
| 이미지 | Ubuntu 24.04 (aarch64) | JDK21·MariaDB·nginx 모두 ARM 패키지 제공 |
| 부트 볼륨 | 50GB~ | 기본 47GB로도 충분(로그·백업 감안해 여유) |
| 공인 IP | **예약(Reserved) 공인 IP** | 인스턴스 재생성에도 IP 유지(DNS 재설정 불요) |

- VCN 보안 목록(Security List) 인그레스: **22(SSH — 가급적 내 IP만), 80, 443**. 그 외 전부 차단.
  3306은 열지 않는다(DB는 로컬 접속 전용).
- **OCI 함정**: Oracle 제공 Ubuntu 이미지는 OS 안에 iptables 거부 규칙이 미리 들어 있다.
  보안 목록에서 80/443을 열어도 접속이 안 되면 이것이 원인 —
  `sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT`(443 동일) 후
  `sudo netfilter-persistent save`로 영속화한다.

## 2. DNS·TLS — 1단계(단일 도메인)

1. 도메인의 A 레코드: `app.<도메인>` → 예약 공인 IP
   (`app`/`www`/`admin`/`api`/`mail`은 테넌트 코드로 쓸 수 없는 예약 라벨이므로 접속 호스트로 안전)
2. 인증서: certbot 일반(HTTP-01) 발급 — 와일드카드가 아니므로 DNS API 없이 가능
   ```bash
   sudo apt install -y certbot python3-certbot-nginx
   sudo certbot --nginx -d app.<도메인>
   ```
3. 이 단계에서는 로그인 시 테넌트 코드 입력 방식으로 동작한다(서브도메인 진입은 8장에서).

## 3. 소프트웨어 설치

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk mariadb-server nginx git
# 프론트 빌드용 Node LTS (서버에서 빌드하는 가장 단순한 구성)
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs
```

MariaDB 초기화:

```bash
sudo mariadb-secure-installation   # root 비밀번호·익명 계정 제거 등
sudo mariadb <<'SQL'
CREATE DATABASE attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'attendance'@'localhost' IDENTIFIED BY '<강한 비밀번호>';
GRANT ALL PRIVILEGES ON attendance.* TO 'attendance'@'localhost';
FLUSH PRIVILEGES;
SQL
```

스키마·초기 데이터는 앱이 기동하면서 Flyway로 전부 만든다(수동 DDL 없음).

## 4. 백엔드 배치

```bash
sudo useradd -r -m -s /usr/sbin/nologin attendance
sudo mkdir -p /opt/attendance /etc/attendance
cd /home/ubuntu && git clone <레포 URL> web_attendance
cd web_attendance && ./mvnw -q package -DskipTests
sudo cp target/*.jar /opt/attendance/app.jar
```

환경변수 파일 `/etc/attendance/attendance.env` (**chmod 600, root 소유**):

```bash
SPRING_PROFILES_ACTIVE=prod
# 민감 필드 암호화 키 — openssl rand -base64 32 로 생성. ★분실하면 암호화 필드 복호화 불가★
APP_CRYPTO_KEY=<생성값>
DB_URL=jdbc:mariadb://127.0.0.1:3306/attendance
DB_USERNAME=attendance
DB_PASSWORD=<3장에서 정한 비밀번호>
# SMTP — prod는 미설정 시 기동 실패. 테스트 서버는 Gmail 앱 비밀번호/SES 등 간단한 것으로
SMTP_HOST=<smtp 호스트>
SMTP_PORT=587
SMTP_USER=<계정>
SMTP_PASS=<비밀번호>
SMTP_FROM=<발신 주소>
# 초대/재설정 메일의 링크 기준 URL (요청 Host를 쓰지 않는 보안 설계 — 반드시 실제 접속 URL로)
APP_MAIL_LINK_BASE_URL=https://app.<도메인>
# 테넌트 서브도메인 — 1단계에서는 비워 둔다(8장에서 켤 때 값 부여)
#APP_TENANT_BASE_DOMAIN=
```

systemd 유닛 `/etc/systemd/system/attendance.service`:

```ini
[Unit]
Description=Web Attendance backend
After=network.target mariadb.service
Wants=mariadb.service

[Service]
User=attendance
EnvironmentFile=/etc/attendance/attendance.env
ExecStart=/usr/bin/java -jar /opt/attendance/app.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now attendance
journalctl -u attendance -f    # Flyway 마이그레이션 → "Started WebAttendanceApplication" 확인
```

## 5. 프론트엔드 배치 + nginx

```bash
cd /home/ubuntu/web_attendance/frontend && npm ci && npm run build
sudo mkdir -p /var/www/attendance && sudo rsync -a --delete dist/ /var/www/attendance/
```

`/etc/nginx/sites-available/attendance` (certbot이 TLS 블록을 덧붙인다):

```nginx
server {
    server_name app.<도메인>;
    root /var/www/attendance;
    index index.html;

    # SPA — 어떤 경로도 index.html (라우팅은 앱이 서버 주도로 처리)
    location / { try_files $uri /index.html; }

    location /api/ {
        proxy_pass http://127.0.0.1:9080;
        # ★ Host 보존 필수 — 백엔드가 테넌트를 Host 헤더로 해석한다(개발 vite 프록시와 동일 원칙)
        proxy_set_header Host $host;
        # prod의 forward-headers 처리(레이트리밋·감사 로그의 실제 IP)용
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/attendance /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## 6. 초기 계정과 첫 작업

- Flyway 시드로 **운영사 관리자 `admin@attendance.local` / `Admin123!`** 이 만들어진다.
  ★서버를 열자마자 가장 먼저 이 비밀번호를 변경한다★ (공개 저장소에 적힌 크리덴셜이다.)
- 이후 흐름: 운영사 관리자로 로그인 → 테넌트 생성(관리자 초대 메일 발송) → 초대받은 관리자가
  비밀번호 설정 → 멤버 등록. 메일이 실제로 도착하는지가 SMTP 설정의 검증 지점이다.

## 7. 갱신(배포)·백업

갱신 절차(다운타임 수 초):

```bash
cd /home/ubuntu/web_attendance && git pull
./mvnw -q package -DskipTests && sudo cp target/*.jar /opt/attendance/app.jar
cd frontend && npm ci && npm run build && sudo rsync -a --delete dist/ /var/www/attendance/
sudo systemctl restart attendance   # 새 마이그레이션은 기동 시 Flyway가 자동 적용
```

- 롤백: 직전 jar를 `app.jar.prev`로 보관해 두고 되돌린다. 단 **이미 적용된 마이그레이션은 되돌리지
  않는다**(전방 수정 원칙 — 되돌릴 일이 생기면 새 V버전으로 정정).

백업(cron, 매일):

```bash
# /etc/cron.daily/attendance-backup (chmod 700)
#!/bin/sh
mysqldump --single-transaction attendance | gzip > /var/backups/attendance-$(date +%F).sql.gz
find /var/backups -name 'attendance-*.sql.gz' -mtime +14 -delete
```

- ★`APP_CRYPTO_KEY`는 DB 백업과 **별도 장소**에 보관★ — 키 없이는 백업을 복원해도 암호화 필드
  (마스킹 대상 개인정보 등)를 읽을 수 없다. 반대로 키+백업이 함께 유출되면 전부 읽힌다.
- 복원 리허설을 최소 1회 해 본다(백업은 복원해 본 것만 백업이다).

## 8. 2단계 — 테넌트 서브도메인 켜기 (준비되면)

1. DNS에 와일드카드 A 레코드 추가: `*.app.<도메인>` → 같은 IP
2. 와일드카드 인증서 발급 — Let's Encrypt는 와일드카드에 **DNS-01 인증 필수**
   (DNS 업체의 certbot 플러그인 필요. 업체가 API를 제공하지 않으면 Cloudflare로 네임서버 이전이 정석)
3. nginx `server_name`에 `*.app.<도메인>` 추가, 인증서 교체
4. `/etc/attendance/attendance.env`에 `APP_TENANT_BASE_DOMAIN=app.<도메인>` 설정 후 재기동
   → `{테넌트코드}.app.<도메인>` 접속 시 테넌트가 호스트로 확정된다(코드 입력 생략).
   `www`/`admin`/`api`/`app`/`mail` 라벨은 예약어라 테넌트 코드로 등록되지 않는다.

## 9. 점검 체크리스트

- [ ] `https://app.<도메인>` 접속 → 랜딩 표시, HTTP→HTTPS 리다이렉트
- [ ] admin@attendance.local 로그인 → **비밀번호 즉시 변경**
- [ ] Swagger 비노출 확인: `/swagger-ui.html` 404
- [ ] 테넌트 생성 → 초대 메일 실제 수신 → 비밀번호 설정 링크가 `APP_MAIL_LINK_BASE_URL` 기준인지
- [ ] 멤버 출근/퇴근 스탬프 → 출결 조회 반영
- [ ] 공휴일 동기화 실행(아웃바운드 date.nager.at 도달 확인)
- [ ] 백업 파일 생성 확인 + 복원 리허설
- [ ] `journalctl -u attendance`에 debug 파라미터 로그가 없는지(프로파일 확인)
