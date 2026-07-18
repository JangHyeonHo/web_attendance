# 결제(과금) 계산 방식 — 현재 구현 기준

- 대상: `com.attendance.pro.billing` (BillingService·SeatUsageMapper·InvoiceMapper) + `tenant_billing`/`tenant_seat_usage`/`invoice` (Flyway V22, V24)
- 목적: **지금 코드가 실제로 어떻게 과금 금액을 계산하는지**를 사실 그대로 기록한다. (요금 정책의 당위가 아니라 현행 동작 문서)
- 한 줄 요약: **달력 월(月) 단위 · 인당(seat) 과금**. 그 달의 **최대 활성 인원**으로 계산하며, **일할계산·결제일·자동결제·알림은 없다.**

---

## 1. 과금 모델 한눈에

| 항목 | 값 / 방식 |
|------|-----------|
| 과금 기준 | **활성 멤버 수(seat)** — `ACTIVE` · 미삭제 · `SYSTEM_ADMIN` 제외 |
| 집계 주기 | **달력 월**(`ym` = `YYYY-MM`), 월중 **최대치(high-water mark)** |
| 인당 단가 | 기본 **2,000원/월** (VAT 별도) — 운영사가 회사별 조정 |
| 무료 좌석 | 기본 **5좌석** — 이 수까지는 과금 제외 (운영사가 회사별 조정) |
| 통화 | KRW(원) |
| 실제 결제(PG) | **미구현** — 청구서 계산·표시까지만 |

### 과금식 (`BillingService.compute`)

```
과금인원(billedSeats) = max(0, 월중최대활성(maxSeats) − 무료좌석(freeSeats))
공급가(subtotal)      = 과금인원 × 인당단가(unitPrice)
부가세(vat)           = round(공급가 × 10%)
합계(total)           = 공급가 + 부가세
```

---

## 2. "무료"의 정확한 의미 — 기간이 아니라 **좌석 수**

- 무료는 **가입 후 N일 무료체험**이 아니다. **처음 `free_seats`(기본 5)좌석까지 항상 과금 제외**라는 뜻이다.
- 따라서 **활성 5명 이하면 매월 0원**, 6명째부터 과금 시작.
- 코드에는 "가입 첫 달 무료", "무료 체험 기간" 같은 **시간 기반 무료 개념이 존재하지 않는다.** (필요하면 별도 기능으로 신설해야 함 — §7)

---

## 3. 좌석 수 집계 — 언제, 어떻게 기록되나

월별 최대 좌석은 `tenant_seat_usage(tenant_id, ym, max_seats)`에 **high-water mark**로 쌓인다. 갱신은 `SeatUsageMapper.touch`가 담당하며, **현재 활성 좌석 수를 서버에서 세어 `GREATEST`로만 올린다**(내려가지 않음 → 그 달의 피크 보존).

```sql
INSERT INTO tenant_seat_usage (tenant_id, ym, max_seats)
SELECT :tenantId, :ym,
       (SELECT COUNT(*) FROM users
         WHERE tenant_id = :tenantId AND status = 'ACTIVE'
           AND deleted = FALSE AND role <> 'SYSTEM_ADMIN')
ON DUPLICATE KEY UPDATE max_seats = GREATEST(max_seats, VALUES(max_seats))
```

**갱신 시점(`touchSeatUsage` 호출 지점):**

| 시점 | 위치 |
|------|------|
| 멤버 활성화 / 상태 변경 | `MemberService` |
| 초대 링크로 계정 활성화(비밀번호 설정) | `PasswordService` |
| 청구서 조회(회사·운영사) | `BillingService.listForTenant` / `getInvoice` |
| 월 마감(확정) | `BillingService.close` |

- **핵심:** 기록되는 월(`ym`)은 **touch가 일어난 시점의 서버 달력 월**이다. 6월에 활성화되면 `2025-06` 버킷이 올라간다.
- 부가 기능이라 **실패해도 본 요청을 깨지 않는다**(경고 로그만). 아무도 조회하지 않은 채 잠깐 생겼다 사라진 순간 피크는 이론상 누락될 수 있고, 조회·마감 시점에 확정된다.

---

## 4. 잠정(PROVISIONAL) vs 확정(ISSUED)

| 구분 | 언제 | 계산 | 저장 |
|------|------|------|------|
| **잠정** | 진행 중인 달(마감 전) | 조회할 때마다 **현재 활성 수로 실시간 계산** | 저장 안 함(`tenant_seat_usage`만 갱신) |
| **확정** | 운영사가 **마감**한 달 | 마감 시점의 최대·단가·무료를 **스냅샷** | `invoice` 행으로 보존 |

- 확정 규칙(`BillingService.close`):
  - 마감 시점의 `max_seats`·`unit_price`·`free_seats`를 `invoice`에 **스냅샷** → **이후 단가를 바꿔도 과거 청구서에 소급되지 않는다.**
  - 이미 확정된 달을 다시 마감하면 **409 `INVOICE_ALREADY_ISSUED`** (확정 청구서는 불변).
  - 잘못된 월 형식은 **400 `INVALID_YM`**.
- 마감은 **운영사(SYSTEM_ADMIN)가 수동으로** 호출한다: `POST /api/v1/system/tenants/{tenantId}/invoices/{ym}/close`.
  **자동 월마감 스케줄러(@Scheduled/cron)는 없다.**

---

## 5. 결제일·자동결제·알림 — 전부 **없음**

사용자가 흔히 기대하는 "유료 전환 시점에 결제일이 잡히고, 매달 그 날 자동 결제 + 알림" 흐름은 **구현돼 있지 않다.**

- **결제일(가입 기념일) 개념 없음** — 과금은 **가입일과 무관하게 달력 월 기준**이다.
- **반복 결제 없음** — 매달 청구서를 계산·표시할 뿐, 카드에 청구하는 로직이 없다.
- **알림 없음** — 결제/청구 알림(메일·푸시) 미구현.
- **실제 PG 청구 없음** — `tenant_billing`에 결제수단(`INVOICE`/`CARD`)과 `pgCustomerKey`(CARD 시 암호화 저장) 필드는 **있으나**, 그 키로 실제 결제를 실행하는 코드는 없다. 지금은 **"설정 + 청구서 표시"**까지가 범위다.

---

## 6. 시나리오로 확인 — "6월 말일 가입, 20명 등록"

가정: 인당 2,000원 / 무료 5좌석(기본값), 20명이 **6월 중**에 활성화됨.

| 질문 | 현행 동작 |
|------|-----------|
| 6월 말일 하루만 활성이면? | **6월 한 달치 전액.** 일할계산(proration) 없음 — 그 달에 하루라도 활성이면 피크 좌석으로 한 달 과금 |
| 6월 청구 금액 | 과금인원 = 20 − 5 = **15** → 공급가 15 × 2,000 = **30,000원** → VAT **3,000원** → 합계 **33,000원** |
| 그 금액이 7월에 6월 기준으로 청구되나? | 아니다. 금액은 **6월 청구서(`2025-06`)로 귀속**된다. (운영사가 7월 초에 6월을 마감하는 건 가능하지만, 그건 '확정 시점'일 뿐 귀속 월은 6월) |
| 7월은? | **7월대로 다시 계산.** 7월에도 20명이면 7월분(`2025-07`) 33,000원이 또 잡힌다 |
| "결제일이 등록되고 매번 그때 결제"? | 그런 개념 자체가 없다(§5). 순수 달력 월 기준 청구서 계산만 |

---

## 7. 현재 한계 / 후속 과제 (참고)

- **일할계산 없음**: 월중 신규 가입·이탈에 대한 proration 미지원. 말일 가입도 그 달 전액.
- **시간 기반 무료체험 없음**: "첫 달 무료" 같은 요구가 있으면 별도 설계 필요.
- **자동 마감·결제·알림 없음**: 월마감은 운영사 수동, 실제 청구(PG)·안내 메일은 미구현.
- **월중 피크 정확도**: 좌석 기록은 활성 증가 이벤트·조회·마감 시점에만 갱신(best-effort). 조회되지 않은 순간 피크는 마감/조회 때 확정.
- **인메모리 아님/멀티 인스턴스**: 좌석 집계는 DB 원자 연산(`GREATEST`)이라 다중 인스턴스에서도 피크가 낮아지지 않는다.

---

## 부록 A. 관련 API

| 주체 | 메서드 · 경로 | 용도 |
|------|--------------|------|
| 회사(TENANT_ADMIN) | `GET /api/v1/tenant/billing/invoices` | 자사 최근 12개월 청구서(잠정+확정) |
| 회사 | `GET /api/v1/tenant/billing/contract` | 계약 요약(요금제·단가·무료 좌석, 읽기전용) |
| 회사 | `GET·PUT /api/v1/tenant/billing/profile` | 자사 결제수단·청구 이메일·비고 (가격 필드 불변) |
| 운영사(SYSTEM_ADMIN) | `GET·PUT /api/v1/system/tenants/{id}/billing` | 회사별 인당 단가·무료 좌석 설정 |
| 운영사 | `GET /api/v1/system/tenants/{id}/invoices` | 회사별 청구서 목록 |
| 운영사 | `POST /api/v1/system/tenants/{id}/invoices/{ym}/close` | 해당 월 **마감(확정)** |

## 부록 B. 관련 테이블 (Flyway)

| 테이블 | 역할 | 정의 |
|--------|------|------|
| `tenant_billing` | 회사별 과금 설정(단가·무료 좌석·결제수단·PG 키) | V22 확장 |
| `tenant_seat_usage` | 월별 최대 활성 좌석(high-water mark) | V22 |
| `invoice` | 마감 확정 청구서 스냅샷(행 존재 = 확정) | V22, 금액 컬럼 V24에서 BIGINT |
