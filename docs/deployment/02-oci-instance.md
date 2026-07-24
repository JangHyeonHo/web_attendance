# 02. OCI 인스턴스·네트워크 준비

> 이전: [01. 전체 구성](01-architecture.md) · 다음: [03. 소프트웨어 설치](03-software-install.md) / [인덱스](README.md)

## 계정 준비 (아직 없다면)

"OCI 계정"이 따로 있는 게 아니라 **Oracle Cloud 가입 = OCI 사용 계정**이다.
oracle.com의 일반 "오라클 계정"(다운로드·커뮤니티용 웹 프로필)과는 다른 것이니 주의 —
반드시 아래 **클라우드 가입 경로**로 만들어야 서버를 만들 수 있다(가입 중 오라클 프로필은
자동으로 함께 생성되므로 따로 만들 필요 없음).

1. https://signup.cloud.oracle.com 에서 가입 (무료 시작 — 30일 $300 크레딧 + Always Free)
2. 준비물: 이메일, 휴대폰(SMS 인증), **본인 명의 신용/체크카드**(해외 결제 가능한 것).
   카드는 본인 확인용 — 소액 승인 후 취소되며, **Pay As You Go로 직접 업그레이드하기 전까지
   과금 자체가 발생하지 않는다.**
3. 가입 중 정하는 **클라우드 계정 이름(테넌시 이름)** 은 로그인마다 쓰는 식별자 — 짧고 기억하기
   쉬운 것으로.
4. **★홈 리전 선택은 가입 후 변경 불가★** — Always Free의 A1(ARM) 인스턴스는 홈 리전에서만
   만들 수 있다. 서울/춘천/도쿄/오사카 중 주 사용 위치 기준으로 고르되, 서울·도쿄는 무료 ARM
   재고 부족이 잦다(아래 참조) — 춘천·오사카가 상대적으로 여유 있는 편.

가입 완료 후 콘솔(cloud.oracle.com 로그인)에서 아래 인스턴스 생성으로 진행한다.

## 인스턴스 사양

| 항목 | 권장값 | 이유 |
|---|---|---|
| Shape | **VM.Standard.A1.Flex** (Ampere ARM) 2~4 OCPU / 12~24GB | Always Free 범위(합계 4 OCPU/24GB까지). JVM+MariaDB 동거에 x86 Micro(1GB)는 부족 |
| 이미지 | Ubuntu 24.04 (aarch64) | JDK21·MariaDB·nginx 모두 ARM 패키지 제공 — x86과 절차 차이 없음 |
| 부트 볼륨 | 50GB~ | 기본(47GB)도 가능하나 로그·백업 여유분 |
| 공인 IP | **예약(Reserved) 공인 IP** | 인스턴스를 지웠다 다시 만들어도 IP 유지 → DNS 재설정 불요 |

- A1.Flex는 리전·가용영역에 따라 Free 재고가 없어 생성 실패("Out of capacity")가 잦다.
  시간대를 바꿔 재시도하거나 다른 AD(가용 도메인)를 선택한다.

## 네트워크(보안 목록)

VCN의 Security List 인그레스 규칙 — **이 3개만** 연다:

| 포트 | 대상 | 비고 |
|---|---|---|
| 22 | 가급적 내 IP만 | SSH. 전체 공개 시 무차별 로그인 시도가 상시로 온다 |
| 80 | 0.0.0.0/0 | certbot 인증·HTTPS 리다이렉트용 |
| 443 | 0.0.0.0/0 | 서비스 본체 |

3306(MariaDB)은 **열지 않는다** — DB는 같은 서버 안(127.0.0.1)에서만 접속한다.

## ★OCI 함정 — 보안 목록을 열어도 접속이 안 될 때★

Oracle 제공 Ubuntu 이미지는 **OS 내부 iptables에 거부 규칙이 미리 들어 있다.**
보안 목록(클라우드 방화벽)과 OS 방화벽의 이중 구조라, 둘 다 열려야 통신이 된다:

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save   # 재부팅 후에도 유지
```

"보안 목록은 분명 열었는데 브라우저가 타임아웃"이면 십중팔구 이것이다.

## SSH 초기 접속

```bash
ssh -i <개인키> ubuntu@<예약 공인 IP>
sudo apt update && sudo apt -y upgrade
```
