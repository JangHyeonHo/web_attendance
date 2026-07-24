# 02. OCI 계정·인스턴스·네트워크 준비

> 이전: [01. 전체 구성](01-architecture.md) · 다음: [03. 소프트웨어 설치](03-software-install.md) / [인덱스](README.md)
> 모르는 용어가 나오면 [용어집](glossary.md).

이 문서가 끝나면: 인터넷에서 접속 가능한 우분투 서버 1대가 생기고, 내 PC에서 SSH로
들어갈 수 있는 상태가 된다.

## 2-1. 계정 만들기

만드는 것은 **오라클 클라우드 무료 계정**(Oracle Cloud Free Tier)이다.
oracle.com의 일반 "오라클 계정"(다운로드·커뮤니티용 웹 프로필)과는 다른 것 —
반드시 아래 클라우드 가입 경로로 만들어야 서버를 만들 수 있다
(가입 중 오라클 프로필은 자동으로 함께 생성되므로 따로 만들 필요 없음).

준비물: 이메일 / 휴대폰(SMS 인증) / **본인 명의 신용·체크카드**(해외 결제 가능한 것).

1. https://signup.cloud.oracle.com 접속 → 이메일·국가 입력으로 시작
2. **Cloud Account Name(클라우드 계정 이름 = 테넌시 이름)** 입력 — 이후 로그인 때마다 쓰는
   식별자다. 짧고 기억하기 쉬운 영소문자로(예: `mtdev`). 나중에 바꾸기 어렵다고 생각할 것
3. **★Home Region(홈 리전) 선택 — 가입 후 변경 불가★**
   - 무료(Always Free)의 ARM 서버는 **홈 리전에서만** 만들 수 있다
   - **가입 화면의 목록은 "지금 신규 무료 가입을 받는 리전"만 보여준다** — 서울이 재고
     사정으로 목록에서 빠져 한국이 아예 안 보이는 시기도 있다(실제 관측됨)
   - 춘천(South Korea North)은 **무료 ARM(A1) 생성 불가 리전**이라 목록에 있어도 제외
   - **권장: Japan Central (Osaka)** — 도쿄보다 재고 여유가 있는 편. 한국에서의 접속도
     지연 30~40ms 수준이라 테스트 용도에 문제없다. (서울이 목록에 보이면 서울도 무방)
4. 주소·휴대폰 인증 → 카드 등록
   - 카드는 본인 확인용: 소액이 승인됐다 취소된다. **본인이 콘솔에서 "Pay As You Go
     업그레이드"를 하지 않는 한 과금은 발생하지 않는다**
5. 가입 완료 메일이 오면 https://cloud.oracle.com → 클라우드 계정 이름 입력 → 이메일/비밀번호로 로그인

가입하면 30일 $300 체험 크레딧 + Always Free(기간 무제한 무료)가 함께 주어진다.
우리는 Always Free 범위만 쓴다 — 30일 후 크레딧이 사라져도 서버는 그대로 무료로 돈다.

## 2-2. SSH 키 만들기 (인스턴스 생성 전에)

서버 접속은 비밀번호가 아니라 **SSH 키**(파일로 된 열쇠 한 쌍)로 한다.
인스턴스를 만들 때 공개키를 등록해야 하므로 먼저 만든다. **내 PC에서** 실행:

```
ssh-keygen -t ed25519 -f oci_key
```

- Windows는 PowerShell, Mac은 터미널 — 둘 다 위 명령 그대로(Windows 10 이후 ssh 내장).
  passphrase는 물어보면 빈 채로 Enter 2번(테스트 단계 단순화)
- 파일 2개가 생긴다: `oci_key`(**개인키 — 유출 금지, 이 파일이 곧 서버 열쇠**),
  `oci_key.pub`(공개키 — 서버에 등록하는 쪽)
- `oci_key.pub`를 메모장 등으로 열면 `ssh-ed25519 AAAA...`로 시작하는 한 줄이 나온다 —
  인스턴스 생성 화면에서 이 내용을 쓴다

## 2-3. 인스턴스(서버) 만들기

콘솔 상단의 **검색창에 "instances"** 를 입력해 Instances 화면으로 이동(메뉴 계층을
외울 필요 없이 검색창이 가장 빠르다) → **Create instance** 버튼.

생성 화면에서 기본값에서 **바꿔야 할 것만** 나열한다:

| 항목 | 할 일 |
|---|---|
| Name | 알아보기 쉬운 이름(예: `mt-dev-01`) |
| **Image** | [Change image] → **Ubuntu** → Canonical Ubuntu 24.04 선택 (Oracle Linux가 기본값이므로 반드시 변경) |
| **Shape** | [Change shape] → **Ampere** → **VM.Standard.A1.Flex** → OCPU 2~4 / 메모리 12~24GB (Always Free 한도: 계정 합계 4 OCPU/24GB) |
| Networking | "Create new virtual cloud network" 기본 제안 그대로(신규 VCN·서브넷 자동 생성). **Assign a public IPv4 address가 켜져 있는지 확인** |
| **Add SSH keys** | "Paste public keys" 선택 → 2-2의 `oci_key.pub` 내용 한 줄 붙여넣기 |
| Boot volume | 기본(~47GB)도 가능, 여유를 원하면 50GB~ |

**Create** → 아이콘이 주황(PROVISIONING) → 초록(RUNNING)이 되면 완료.
상세 화면의 **Public IP address**를 메모한다.

- **"Out of capacity" 오류로 생성 실패 시**: 그 시간대에 무료 ARM 재고가 없는 것.
  Placement의 AD(가용 도메인)를 바꿔 재시도하거나, 몇 시간~며칠 간격으로 재시도한다.
  (오사카 홈 리전이면 비교적 빨리 뚫리는 편)

## 2-4. 첫 SSH 접속

내 PC에서(키 파일이 있는 폴더에서):

```
ssh -i oci_key ubuntu@<Public IP>
```

- 처음 접속 시 "fingerprint ... yes/no" → `yes`
- 프롬프트가 `ubuntu@...`로 바뀌면 서버 안이다. 첫 갱신:

```bash
sudo apt update && sudo apt -y upgrade
```

- 접속이 안 될 때: ① IP 오타 ② `-i` 경로가 개인키(`oci_key`, `.pub` 아님)인지
  ③ Windows에서 "bad permissions" 오류면 키 파일 우클릭 → 속성 → 보안에서 본인만 남기기

## 2-5. 방화벽 — 80·443 열기

방화벽이 **2중**이다: OCI 콘솔의 보안 목록(클라우드 쪽) + 서버 안 iptables(OS 쪽).
**둘 다** 열어야 브라우저 접속이 된다.

### (a) 콘솔 — 보안 목록(Security List)

인스턴스 상세 화면에서 서브넷 링크 클릭 → Security Lists → 기본 목록(Default Security List) 클릭
→ **Add Ingress Rules**로 아래 2건 추가:

| Source CIDR | IP Protocol | Destination Port | 용도 |
|---|---|---|---|
| 0.0.0.0/0 | TCP | 80 | 인증서 발급·https 리다이렉트 |
| 0.0.0.0/0 | TCP | 443 | 서비스 본체 |

- 22(SSH)는 기본 규칙으로 이미 열려 있다 — 보안을 높이려면 Source를 내 IP로 좁힌다
  (단, 내 IP가 자주 바뀌는 회선이면 잠길 수 있으니 테스트 단계에선 기본 유지도 무방)
- **3306(DB)은 절대 추가하지 않는다** — DB는 서버 안에서만 접속한다

### (b) 서버 안 — iptables (★OCI 함정★)

Oracle 제공 Ubuntu 이미지는 OS 안 iptables에 거부 규칙이 미리 들어 있다.
보안 목록을 열어도 접속이 안 되는 원인 1순위. SSH 접속 상태에서:

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save   # 재부팅 후에도 유지
```

## 2-6. 예약 공인 IP로 바꿔두기 (권장)

기본(Ephemeral) 공인 IP는 인스턴스를 지우면 사라진다. **예약(Reserved) IP**로 바꿔 두면
인스턴스를 재생성해도 IP가 유지돼 DNS를 다시 만질 일이 없다:

1. 인스턴스 상세 → Attached VNICs → 첫 VNIC 클릭 → IPv4 Addresses
2. 해당 사설 IP 행의 ⋮ 메뉴 → **Edit** → Public IP 유형에서 **Reserved public IP** 선택
   → "Create a new reserved public IP" → 저장
3. 새 Public IP를 다시 메모(이후 문서의 `<공인 IP>`는 전부 이 값)

## 완료 확인

- [ ] `ssh -i oci_key ubuntu@<공인 IP>` 접속 성공
- [ ] 보안 목록에 80·443 인그레스 2건 존재
- [ ] `sudo iptables -L INPUT -n | head`에 80·443 ACCEPT가 보임
- [ ] 공인 IP가 Reserved 유형
