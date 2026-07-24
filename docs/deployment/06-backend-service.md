# 06. 백엔드 빌드·환경변수·systemd

> 이전: [05. 소프트웨어 설치](05-software-install.md) · 다음: [07. 프론트·nginx](07-frontend-nginx.md) / [인덱스](README.md)

## 빌드와 배치

```bash
# 전용 실행 계정(로그인 불가)과 디렉터리
sudo useradd -r -m -s /usr/sbin/nologin attendance
sudo mkdir -p /opt/attendance /etc/attendance

# 소스 취득·빌드
cd /home/ubuntu && git clone <레포 URL> web_attendance
cd web_attendance && ./mvnw -q package -DskipTests
sudo cp target/*.jar /opt/attendance/app.jar
```

**비공개 레포라 clone에서 인증 오류가 나면** — 서버에 읽기 전용 열쇠(Deploy Key)를 등록한다:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N ""   # 서버 안에서 키 생성
cat ~/.ssh/id_ed25519.pub                            # 출력된 한 줄을 복사
```

GitHub에서: 레포 → Settings → Deploy keys → **Add deploy key** → 복사한 내용 붙여넣기
(Allow write access는 **체크하지 않는다** — 서버는 읽기만). 그 후 SSH 주소로 clone:

```bash
git clone git@github.com:<소유자>/web_attendance.git web_attendance
# 처음 접속 시 github.com fingerprint 질문 → yes
```

## 환경변수 파일

`/etc/attendance/attendance.env` — **root 소유, chmod 600** (암호화 키·DB 비밀번호가 들어간다):

```bash
SPRING_PROFILES_ACTIVE=prod
# ★암호화 키 — openssl rand -base64 32 로 생성. 분실하면 암호화 필드 복호화 불가(09 참조)★
APP_CRYPTO_KEY=<생성값>
DB_URL=jdbc:mariadb://127.0.0.1:3306/attendance
DB_USERNAME=attendance
DB_PASSWORD=<03에서 정한 비밀번호>
SMTP_HOST=<smtp 호스트>
SMTP_PORT=587
SMTP_USER=<계정>
SMTP_PASS=<비밀번호>
SMTP_FROM=<발신 주소>
APP_MAIL_LINK_BASE_URL=https://app.<도메인>
```

각 변수의 의미·미설정 시 동작은 [env-reference.md](env-reference.md) 참조.
`APP_TENANT_BASE_DOMAIN`은 이 단계에서 넣지 않는다(서브도메인은 [13](13-tenant-subdomain.md)에서).

## systemd 서비스

`/etc/systemd/system/attendance.service`:

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
sudo systemctl daemon-reload
sudo systemctl enable --now attendance
```

## 기동 확인

```bash
journalctl -u attendance -f
```

- 첫 기동은 Flyway 마이그레이션(V1부터 전부)이 돌아 수십 초 걸린다.
  `Migrating schema ...` 나열 후 `Started WebAttendanceApplication`이 나오면 성공.
- **기동 실패 시**: 대부분 환경변수 누락이다 — prod 프로파일은 APP_CRYPTO_KEY·DB·SMTP가
  없으면 의도적으로 기동을 거부한다(fail-fast). 로그의 Caused by에서 어느 변수인지 확인.

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9080/api/v1/navigation   # 401이면 정상(미인증 응답)
```
