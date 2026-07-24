# 11. 테넌트 서브도메인 켜기 (2단계 확장)

> [인덱스](README.md)

기본 상태(1단계)에서 로그인은 테넌트 코드 입력 방식이다. 이 문서는
`{테넌트코드}.app.<도메인>` 접속으로 테넌트가 자동 확정되는 **서브도메인 병행 방식**을
켜는 절차다. 와일드카드 DNS·인증서가 준비된 뒤에만 진행한다.

## 동작 방식 요약

- `APP_TENANT_BASE_DOMAIN=app.<도메인>` 설정 시, 백엔드가 요청 Host의 첫 라벨을
  테넌트 코드로 해석한다: `acme.app.<도메인>` → 테넌트 ACME 확정(로그인 코드 입력 생략)
- 루트 도메인(`app.<도메인>`) 접속은 기존 코드 입력 방식 그대로 — **병행**이므로
  켜도 기존 사용 방식이 깨지지 않는다
- `www`/`admin`/`api`/`app`/`mail`은 예약 라벨 — 테넌트 코드로 등록 자체가 안 되므로
  인프라 용도와 충돌하지 않는다
- 미등록 코드의 서브도메인 접속은 통일된 401(존재 여부 비노출)

## 절차

### 1. DNS — 와일드카드 A 레코드

```
*.app.<도메인>   A   <예약 공인 IP>
```

### 2. 와일드카드 인증서

Let's Encrypt 와일드카드는 **DNS-01 인증 필수**(HTTP-01 불가) — DNS 업체 API로
TXT 레코드를 자동 등록할 수 있어야 한다:

```bash
# 예: Cloudflare 네임서버 사용 시
sudo apt install -y python3-certbot-dns-cloudflare
sudo certbot certonly --dns-cloudflare \
  --dns-cloudflare-credentials /root/.secrets/cloudflare.ini \
  -d 'app.<도메인>' -d '*.app.<도메인>'
```

- 도메인 업체가 certbot 플러그인/API를 제공하지 않으면, 네임서버만 Cloudflare(무료)로
  옮기는 것이 정석 경로다.
- 자동 갱신도 같은 DNS 크리덴셜로 동작하는지 `certbot renew --dry-run`으로 확인.

### 3. nginx — server_name 확장·인증서 교체

```nginx
server_name app.<도메인> *.app.<도메인>;
ssl_certificate     /etc/letsencrypt/live/app.<도메인>/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/app.<도메인>/privkey.pem;
```

`sudo nginx -t && sudo systemctl reload nginx`

### 4. 백엔드 — base-domain 설정

`/etc/attendance/attendance.env`에 추가 후 재기동:

```bash
APP_TENANT_BASE_DOMAIN=app.<도메인>
```

```bash
sudo systemctl restart attendance
```

## 확인

- [ ] `https://<테넌트코드>.app.<도메인>` 접속 → 로그인 화면에 테넌트 코드 입력란이 없음
- [ ] `https://app.<도메인>` → 기존 코드 입력 방식 그대로
- [ ] 존재하지 않는 코드의 서브도메인 → 로그인 시도 시 통일 401
- [ ] 인증서가 두 호스트 모두 유효(브라우저 자물쇠 확인)
