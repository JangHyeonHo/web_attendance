# 14. 온디맨드 운용 — 테스트 날에만 켜기

> [인덱스](README.md) · 전제: [03. EC2](03-aws-ec2.md)에서 Elastic IP 없이 구축한 상태

테스트 날에만 서버를 켜서 비용을 최소화하는 운용 방식. 요금 동작과 켜고 끄는 절차,
그리고 "켤 때마다 바뀌는 IP"를 DNS가 자동으로 따라가게 하는 설정을 다룬다.

## 14-1. 요금이 어떻게 움직이는가

| 상태 | 나가는 돈 |
|---|---|
| **정지(Stopped)** | 디스크 15GB ≈ **$1.4/월** 뿐 (컴퓨팅 0, IP는 반납되어 0) |
| **실행(Running)** | 컴퓨팅 ~$0.021/h + 공인 IPv4 $0.005/h ≈ **시간당 $0.026(약 37원)** |

- 예: 월 40시간 테스트 → 합계 약 $2.5/월
- "종료(Terminate)"는 인스턴스 삭제다 — **절대 누르지 말 것.** 켜고 끄는 것은 항상
  중지(Stop)/시작(Start)이다.

## 14-2. 켜는 절차 (테스트 날)

1. EC2 콘솔 → 인스턴스 선택 → **인스턴스 시작(Start instance)**
   (AWS Console 모바일 앱에서도 가능 — 외출 중 대응용으로 설치해 두면 편하다)
2. 2~3분 대기: OS 부팅 → DNS 자동 갱신(14-4) → 백엔드 systemd 자동 기동
3. 열렸는지 확인: `https://app.<도메인>` 접속 → 로그인까지 한 번
   - 인증서 만료 화면이 뜨면(90일 이상 꺼둔 경우): 몇 분 내 certbot이 자동 갱신하므로
     잠시 후 새로고침

## 14-3. 끄는 절차 (테스트 종료)

1. EC2 콘솔 → 인스턴스 선택 → **인스턴스 중지(Stop instance)**
2. 그걸로 끝 — DB는 디스크에 남아 있고, 다음 시작 때 그대로 이어진다
   (앱·DB는 systemd가 정상 종료 신호를 받아 안전하게 내려간다)

## 14-4. DNS 자동 갱신 (최초 1회 설정)

시작할 때마다 공인 IP가 바뀌므로, 부팅 시 서버가 스스로 Cloudflare에
"내 새 IP는 이것"이라고 등록하게 한다. **도메인의 DNS를 Cloudflare(무료)에서 관리하는 것이 전제**
([07](07-dns-tls.md)에서 Cloudflare를 권장한 이유가 이것이다).

### (a) Cloudflare에서 준비물 2개

1. **API 토큰**: Cloudflare 대시보드 → 우상단 프로필 → My Profile → API Tokens →
   Create Token → "Edit zone DNS" 템플릿 → Zone Resources를 해당 도메인으로 한정 → 생성.
   나온 토큰 문자열을 복사(한 번만 보여준다)
2. **Zone ID**: 대시보드에서 도메인 선택 → Overview 우측 하단 "Zone ID" 복사

### (b) 서버에 스크립트 설치

`/etc/attendance/ddns.env` (chmod 600, root 소유):

```bash
CF_API_TOKEN=<위에서 만든 토큰>
CF_ZONE_ID=<Zone ID>
DDNS_HOST=app.<도메인>
```

`/usr/local/bin/attendance-ddns.sh` (chmod 755):

```bash
#!/bin/sh
# 부팅 시 현재 공인 IP를 Cloudflare A 레코드에 반영한다(온디맨드 운용 — 14번 문서)
. /etc/attendance/ddns.env
IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
RECORD_ID=$(curl -s -H "Authorization: Bearer $CF_API_TOKEN" \
  "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/dns_records?type=A&name=$DDNS_HOST" \
  | grep -o '"id":"[a-f0-9]*"' | head -1 | cut -d'"' -f4)
curl -s -X PUT -H "Authorization: Bearer $CF_API_TOKEN" -H "Content-Type: application/json" \
  "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/dns_records/$RECORD_ID" \
  --data "{\"type\":\"A\",\"name\":\"$DDNS_HOST\",\"content\":\"$IP\",\"ttl\":60,\"proxied\":false}"
```

`/etc/systemd/system/attendance-ddns.service`:

```ini
[Unit]
Description=Update DNS A record to current public IP
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/attendance-ddns.sh

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable attendance-ddns
sudo systemctl start attendance-ddns && systemctl status attendance-ddns --no-pager
dig +short app.<도메인>   # 현재 공인 IP가 나오면 성공
```

- Cloudflare의 해당 A 레코드는 TTL 60초·프록시 꺼짐(DNS only)으로 두어야
  IP 변경이 1분 내 반영된다(스크립트가 그 상태로 갱신한다).

## 14-5. 알아둘 것

- **SSH 접속도 그때그때의 IP로**: 시작 후 콘솔의 퍼블릭 IPv4를 보고 접속하거나,
  DNS 반영 후 `ssh -i mt-dev-key.pem ubuntu@app.<도메인>`으로 접속하면 된다
- **내 IP 변경으로 SSH가 막히면**: 보안 그룹의 SSH 소스가 "내 IP"라서다 —
  콘솔에서 인바운드 규칙의 소스를 현재 IP로 갱신([03](03-aws-ec2.md) 3-6)
- **상시가동 전환 시**: Elastic IP 할당·연결 + 이 DDNS 서비스 비활성
  (`systemctl disable attendance-ddns`)만 하면 된다

## 완료 확인 (최초 설정 후 1회)

- [ ] 중지 → 시작을 한 번 실제로 해 보고, 3분 내 `https://app.<도메인>` 접속 성공
- [ ] 시작 후 `dig +short app.<도메인>`이 새 공인 IP와 일치
- [ ] 중지 상태에서 결제 콘솔 예상 요금이 디스크 외 증가하지 않는 것 확인(다음날)
