# 06. DNS·TLS (1단계 — 단일 도메인)

> 이전: [05. 프론트·nginx](05-frontend-nginx.md) · 다음: [07. 초기 설정](07-initial-setup.md) / [인덱스](README.md)

1단계는 **일반 인증서 하나로 단일 호스트만** 연다. 이 상태에서 로그인은 테넌트 코드 입력
방식으로 동작한다. 테넌트 서브도메인(와일드카드)은 [11](11-tenant-subdomain.md)에서 확장한다.

## DNS

도메인 관리 콘솔에서 A 레코드 1건:

```
app.<도메인>   A   <예약 공인 IP>
```

- 호스트 라벨은 `app` 권장 — `www`/`admin`/`api`/`app`/`mail`은 시스템이 테넌트 코드로
  쓸 수 없게 예약한 라벨이라, 나중에 서브도메인 방식을 켜도 충돌하지 않는다.
- 전파 확인: `dig +short app.<도메인>` 이 IP를 돌려주면 다음 단계로.

## TLS (Let's Encrypt / certbot)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d app.<도메인>
# → 05에서 만든 server 블록을 찾아 TLS 설정과 80→443 리다이렉트를 자동 삽입한다
```

- 갱신은 systemd 타이머로 자동(`systemctl list-timers | grep certbot`으로 확인).
- 백엔드 prod 프로파일은 HSTS·Secure 쿠키를 켜므로 **TLS 없이는 정상 동작하지 않는다** —
  IP 직접 접속(http)에서 로그인이 안 되는 것은 정상이다.

## 확인

```bash
curl -sI https://app.<도메인> | head -3          # HTTP/2 200
curl -sI http://app.<도메인> | head -3           # 301 → https
```
