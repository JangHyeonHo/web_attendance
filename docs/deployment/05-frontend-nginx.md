# 05. 프론트 빌드·nginx 설정

> 이전: [04. 백엔드 서비스](04-backend-service.md) · 다음: [06. DNS·TLS](06-dns-tls.md) / [인덱스](README.md)

## 프론트 빌드·배치

```bash
cd /home/ubuntu/web_attendance/frontend
npm ci && npm run build          # tsc 타입체크 겸 vite 빌드
sudo mkdir -p /var/www/attendance
sudo rsync -a --delete dist/ /var/www/attendance/
```

## nginx 설정

`/etc/nginx/sites-available/attendance`:

```nginx
server {
    listen 80;
    server_name app.<도메인>;
    root /var/www/attendance;
    index index.html;

    # SPA — 어떤 경로도 index.html (화면 전환은 서버 주도 내비게이션이 처리)
    location / { try_files $uri /index.html; }

    location /api/ {
        proxy_pass http://127.0.0.1:9080;
        proxy_set_header Host $host;                                    # ★필수 — 아래 설명
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;    # 실제 클라이언트 IP
        proxy_set_header X-Forwarded-Proto $scheme;                     # https 인지
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/attendance /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

(TLS 블록은 06에서 certbot이 자동으로 덧붙인다 — 여기서는 80만으로 구성해 둔다.)

## ★왜 `Host $host` 보존이 필수인가★

백엔드는 **테넌트를 요청 Host 헤더의 서브도메인으로 해석**한다(서브도메인/코드 병행 방식).
프록시가 Host를 `127.0.0.1:9080` 따위로 바꿔 보내면 테넌트 해석이 깨진다.
로컬 개발의 vite 프록시가 `changeOrigin: false`로 고정된 것과 같은 이유이며,
**앞단에 어떤 프록시·LB·CDN을 두더라도 이 원칙은 유지**해야 한다.

`X-Forwarded-For/Proto`는 prod 프로파일의 `forward-headers` 처리와 한 쌍이다 —
없으면 로그인 레이트리밋·감사 로그의 IP가 전부 프록시 IP(127.0.0.1)로 붕괴된다.

## 확인

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://<공인IP>/            # 200 (랜딩)
curl -s -o /dev/null -w "%{http_code}\n" http://<공인IP>/api/v1/navigation  # 401 (프록시 정상)
```
