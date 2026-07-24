# 03. 소프트웨어 설치 + MariaDB 초기화

> 이전: [02. OCI 인스턴스](02-oci-instance.md) · 다음: [04. 백엔드 서비스](04-backend-service.md) / [인덱스](README.md)

## 패키지 설치

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk mariadb-server nginx git
# 프론트 빌드용 Node LTS(서버에서 직접 빌드하는 가장 단순한 구성)
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

버전 확인:

```bash
java -version    # 21.x
node -v          # v22.x
mariadb --version
```

## MariaDB 초기화

```bash
sudo mariadb-secure-installation
# → root 비밀번호 설정, 익명 계정 제거, 원격 root 차단, test DB 제거 모두 Yes
```

데이터베이스와 계정 생성(문자셋은 utf8mb4 고정 — 한·일 문자와 이모지):

```bash
sudo mariadb <<'SQL'
CREATE DATABASE attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'attendance'@'localhost' IDENTIFIED BY '<강한 비밀번호>';
GRANT ALL PRIVILEGES ON attendance.* TO 'attendance'@'localhost';
FLUSH PRIVILEGES;
SQL
```

- 계정 호스트는 `localhost` — 외부 접속 계정을 만들지 않는다(02의 포트 정책과 한 쌍).
- **테이블은 만들지 않는다.** 스키마·시드는 백엔드 첫 기동 시 Flyway가 전부 적용한다(04).

## 확인

```bash
mariadb -u attendance -p attendance -e "SELECT 1"
```
