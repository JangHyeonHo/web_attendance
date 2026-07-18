# 결제/계약 정보 필드 설계 (Operator-Contract vs Tenant-Self-Service)

- 대상: B2B 멀티테넌트 HR/출결 SaaS. **좌석 단위 과금**(무료 N석 초과 시 과금, 예: 5석 초과분 청구).
- 시장: **한국(KRW) + 일본(JPY)**. 세금계산서(KR) / 適格請求書·インボイス制度(JP) 대응.
- 목적: 지금까지 `tenant_billing` 한 곳(SYSTEM_ADMIN 전용)에 뭉쳐 있던 "회사/결제" 데이터를
  **두 개의 서로 다른 소유 주체·화면**으로 분리한다.
  - **(A) OPERATOR 계약 레코드** — 운영사(SaaS 제공자, `SYSTEM_ADMIN`)가 **고객사와의 계약**을 관리.
  - **(B) TENANT 셀프서비스 프로필** — 고객사 관리자(`TENANT_ADMIN`)가 **자기 회사의 청구/사업자 정보**를 직접 관리.
- 이 문서는 `docs/plan-saas-multitenancy.md` §6-1(`tenant_profile`/`tenant_billing`)의 후속 상세화이며,
  기존 스키마의 "SYSTEM_ADMIN 전용" 덩어리를 A/B로 쪼개는 것이 핵심 변경이다. **코드 변경 없음(문서만).**

---

## 0. 업계 사례에서 얻은 분리 원칙

실무 SaaS 결제 모델은 거의 예외 없이 **"구독/계약(누가 얼마에 파는가)"** 과
**"고객 청구 프로필(누구에게 무슨 정보로 청구서를 보내는가)"** 을 **다른 오브젝트**로 분리한다.

| 제품 | 계약/구독 쪽 (provider가 정함) | 고객 셀프서비스 쪽 (customer가 정함) |
|------|------------------------------|-----------------------------------|
| **Stripe** | `Subscription`(price, currency, items=seat quantity), 할인=`Coupon`/`Discount` | `Customer`(billing address, **tax IDs**, billing email, `tax_exempt`) |
| **Chargebee** | `Subscription`/`Plan`(단가, 주기) | **Customer/Billing Profile**에 VAT·세금 등록번호, 청구주소 |
| **GitHub** | invoice-backed 좌석 수·플랜은 **영업 계약**으로 고정(셀프 좌석 증설 UI 비활성) | **Billing manager**가 결제수단·영수증·청구연락처 셀프관리 |
| **Notion / Slack** | 플랜·연간계약 | 워크스페이스 Owner/Admin이 **결제수단·청구연락처·VAT/사업자번호·인보이스 수신** 관리 |

핵심 시사점 3가지:
1. **단가·좌석·플랜·주기·할인은 "계약(A)"** 에 둔다 — 고객이 임의로 못 바꾼다(특히 invoice-backed B2B는 GitHub처럼 셀프 좌석증설을 막는 것이 정석).
2. **세금 식별번호·청구주소·청구이메일·결제수단은 "고객 프로필(B)"** 에 둔다 — 세금계산서/適格請求書 수신자 정보는 고객만 정확히 안다.
3. 세금계산서에 찍히는 **"공급자" 등록번호는 우리(운영사)의 것 1개**로 전역 설정이고, 고객이 입력하는 사업자번호는 **"공급받는 자(수신자)"** 정보다. (A/B 어디에도 없는 **전역 운영사 설정**으로 별도 관리 — §3 참고.)

---

## A. OPERATOR 계약 레코드 (`tenant_contract`)

- **소유/편집: `SYSTEM_ADMIN`(운영사)만.** `TENANT_ADMIN`은 **읽기 전용**으로 자기 계약의 비민감 항목(플랜/단가/무료석/주기/상태)만 볼 수 있음.
- 좌석 과금의 "가격 규칙"이 여기 산다. 고객이 못 바꾼다.
- 기존 `tenant_billing`의 `per_seat_amount / free_seats / plan / billed_from`을 흡수·확장한다.

| 필드(영문) | 한국어 라벨 | 타입 | 소유/편집 | 필수 | 비고 |
|-----------|-----------|------|----------|------|------|
| `tenant_id` | 테넌트 | BIGINT (PK, FK) | 시스템 | ✅ | tenant 1:1 |
| `plan` | 요금제/플랜 | ENUM(`FREE`,`BASIC`,`PRO`,`ENTERPRISE`) | SYSTEM_ADMIN | ✅ | 기존 `plan` 승계 |
| `currency` | 청구 통화 | ENUM(`KRW`,`JPY`) | SYSTEM_ADMIN | ✅ | 테넌트별 고정. 시장 결정 |
| `per_seat_amount` | 좌석당 단가 | DECIMAL(12,2) | SYSTEM_ADMIN | ✅ | 통화 최소단위(KRW·JPY는 소수 없음) |
| `free_seats` | 무료 좌석 수 | INT | SYSTEM_ADMIN | ✅ | 예: 5. 이 수 **초과분만** 과금 |
| `seat_cap` | 좌석 상한 | INT (nullable) | SYSTEM_ADMIN | ⬜ | 계약상 최대 인원(무제한=NULL) |
| `billing_cycle` | 청구 주기 | ENUM(`MONTHLY`,`ANNUAL`) | SYSTEM_ADMIN | ✅ | 연간계약이면 셀프 좌석증설 제한(GitHub 패턴) |
| `contract_status` | 계약 상태 | ENUM(`TRIAL`,`ACTIVE`,`PAST_DUE`,`SUSPENDED`,`TERMINATED`) | SYSTEM_ADMIN | ✅ | 테넌트 `status`(ACTIVE/SUSPENDED)와 구분되는 **과금 상태** |
| `contract_start` | 계약 시작일 | DATE | SYSTEM_ADMIN | ✅ | 기존 `billed_from` 승계 |
| `contract_end` | 계약 종료일 | DATE (nullable) | SYSTEM_ADMIN | ⬜ | 자동갱신이면 NULL |
| `trial_end` | 체험 종료일 | DATE (nullable) | SYSTEM_ADMIN | ⬜ | `TRIAL`일 때만 |
| `discount_type` | 할인 유형 | ENUM(`NONE`,`PERCENT`,`FIXED`) | SYSTEM_ADMIN | ⬜ | Stripe Coupon 개념의 최소판 |
| `discount_value` | 할인 값 | DECIMAL(12,2) | SYSTEM_ADMIN | ⬜ | %면 0~100, FIXED면 통화금액 |
| `billing_method` | 결제 방식(계약) | ENUM(`INVOICE`,`CARD`,`BANK_TRANSFER`) | SYSTEM_ADMIN | ✅ | 계약상 합의된 방식. B의 결제수단 상세와 짝 |
| `memo` | 운영 메모 | VARCHAR(500) | SYSTEM_ADMIN | ⬜ | 내부용, 고객 비노출 |
| `created_at`/`updated_at` | 생성/수정 | DATETIME | 시스템 | ✅ | |

> **과금 계산(참고, 저장 아님):** `청구금액 = max(0, 활성좌석수 − free_seats) × per_seat_amount × (1 − 할인)`.
> "활성좌석수"는 `users.status=ACTIVE`(멤버) 집계에서 파생. 통화·세율은 §3.

---

## B. TENANT 셀프서비스 프로필 (`tenant_billing_profile`)

- **소유/편집: `TENANT_ADMIN`(고객사)가 직접.** `SYSTEM_ADMIN`은 지원 목적 조회 가능(감사 로그 대상).
- 세금계산서/適格請求書 **수신자 정보** + 청구 연락 + 결제수단 표시정보가 여기 산다.
- 기존 `tenant_profile`(사업자정보) + `tenant_billing`의 청구이메일/결제표시 필드를 흡수. **카드 원본은 저장 안 함**(빌링키만, `docs/plan-saas-multitenancy.md` §6-1 3원칙 준수).

| 필드(영문) | 한국어 라벨 | 타입 | 소유/편집 | 필수 | 비고 |
|-----------|-----------|------|----------|------|------|
| `tenant_id` | 테넌트 | BIGINT (PK, FK) | 시스템 | ✅ | tenant 1:1 |
| `country` | 사업자 국가 | ENUM(`KR`,`JP`) | TENANT_ADMIN | ✅ | KR/JP 필드 검증 분기 기준 |
| `legal_name` | 상호(법인명) | VARCHAR(100) | TENANT_ADMIN | ✅ | KR 세금계산서·JP 適格請求書 공통 필수 |
| `ceo_name` | 대표자명 | VARCHAR(50) | TENANT_ADMIN | KR✅ / JP⬜ | KR 세금계산서 필요적 기재. JP는 임의 |
| `tax_id` | 사업자 식별번호 | VARBINARY(256) [암호화] | TENANT_ADMIN | ✅ | KR=사업자등록번호 10자리 / JP=登録番号 또는 法人番号 13자리. §3 |
| `tax_id_type` | 식별번호 종류 | ENUM(`KR_BRN`,`JP_TORPKU`,`JP_HOJIN`) | TENANT_ADMIN | ✅ | `country`와 정합. 검증 규칙 선택 |
| `biz_address` | 사업장 주소 | VARCHAR(200) | TENANT_ADMIN | ✅ | 계산서/인보이스 주소 |
| `biz_type` | 업태 | VARCHAR(50) | TENANT_ADMIN | KR⬜ / JP✗ | KR 세금계산서 권장 기재(유효성엔 무관) |
| `biz_item` | 종목 | VARCHAR(50) | TENANT_ADMIN | KR⬜ / JP✗ | KR 권장 기재 |
| `billing_contact_name` | 청구 담당자 | VARCHAR(50) | TENANT_ADMIN | ✅ | |
| `billing_email` | 청구/계산서 수신 이메일 | VARCHAR(100) | TENANT_ADMIN | ✅ | 세금계산서·適格請求書·영수증 발송처(Stripe/Notion/Slack 공통 패턴) |
| `billing_phone` | 청구 담당 연락처 | VARBINARY(256) [암호화] | TENANT_ADMIN | ⬜ | 개인정보 → 암호화 |
| `payment_method` | 결제수단(표시) | ENUM(`INVOICE`,`CARD`,`BANK_TRANSFER`) | TENANT_ADMIN | ✅ | A의 `billing_method`와 일치해야. 실단가·주기는 못 바꿈 |
| `card_brand` | 카드 브랜드 | VARCHAR(20) (nullable) | 시스템(PG) | ⬜ | 표시용. CARD일 때만 |
| `card_last4` | 카드 끝 4자리 | CHAR(4) (nullable) | 시스템(PG) | ⬜ | 표시용 평문 허용 |
| `pg_billing_key` | PG 빌링키 | VARBINARY(512) [암호화] | 시스템(PG) | ⬜ | 어떤 API로도 반환 금지. 카드 원본 저장 안 함 |
| `tax_exempt` | 면세 여부 | BOOLEAN | SYSTEM_ADMIN | ⬜ | Stripe `tax_exempt` 개념. 세율 적용에 영향 → 운영사가 확정 |
| `created_at`/`updated_at` | 생성/수정 | DATETIME | 시스템 | ✅ | |

> **마스킹/암호화**: `tax_id`·`billing_phone`·`pg_billing_key`는 AES-256-GCM 저장. 조회 응답은
> 사업자번호 `123-**-*****`, 카드 `**** **** **** 1234` 형태만. (기존 §6-1 3원칙 그대로)

---

## 3. KR vs JP 세금·사업자 식별 차이 (필수 구분)

| 항목 | 🇰🇷 한국 | 🇯🇵 일본 |
|------|---------|---------|
| 세금 문서명 | **세금계산서**(전자세금계산서) | **適格請求書(インボイス)** — インボイス制度(2023.10 시행) |
| 공급자(우리) 식별번호 | 사업자등록번호 10자리 | **登録番号 = "T" + 13자리** (법인은 法人番号와 동일한 13자리) |
| 고객(수신자) 식별번호 | **사업자등록번호 10자리** `XXX-XX-XXXXX` | **法人番号 13자리**(법인) / 개인사업자는 개별 登録番号 |
| 계산서 필요적 기재(수신자 관련) | 상호, **대표자명**, 사업자등록번호(+주소·업태·종목은 권장) | 상호/명칭(대표자명 임의), 발행사업자 登録番号 |
| 소비/부가세 | 부가가치세 10% | 消費税 표준 10%(경감세율 8% 품목 별도) |
| 우리가 계산서에 찍는 것 | **운영사 사업자등록번호 1개**(전역 설정) | **운영사 登録番号 T+13자리 1개**(전역 설정) |

핵심:
- **`tax_id` 검증은 국가별로 분기**한다. KR=10자리 숫자(`\d{3}-\d{2}-\d{5}`), JP=`T`+13자리(法人番号는 순수 13자리). → B의 `tax_id_type` ENUM으로 규칙 선택.
- **KR은 대표자명이 필요적 기재사항**이므로 `ceo_name`이 KR에서 필수, JP에서는 선택.
- 適格請求書의 6대 기재사항(① 발행사업자 명칭+登録番号 ② 거래일 ③ 거래내용 ④ 세율별 대가·적용세율 ⑤ 세율별 소비세액 ⑥ 수신 사업자 명칭)에서 **①·⑤는 우리(발행자)가 채우는 전역/계산 항목**, ⑥이 B의 `legal_name`이다.
- **"공급자 식별번호"는 테넌트별이 아니라 운영사 1개**다. → A/B 어디에도 두지 말고 **애플리케이션 전역 설정(운영사 프로필)** 으로 관리(예: `app.invoice.kr_brn`, `app.invoice.jp_reg_no`). 이 문서 범위의 테넌트 필드와 헷갈리지 말 것.

---

## 4. 화면/IA 분리 권고

### (A) OPERATOR — 기존 **테넌트 상세 화면**(SYSTEM_ADMIN, 예: W007 테넌트 관리) 내 "계약/과금" 탭
`tenant_contract` 전 필드. 편집은 운영사만. 여기에 **파생 표시**(현재 활성좌석수, 예상 청구액 미리보기)를 곁들인다.
- 넣을 것: 플랜, 통화, 좌석 단가, 무료 좌석, 좌석 상한, 청구 주기, 계약 상태, 계약 시작/종료, 체험 종료, 할인, 계약상 결제방식, 운영 메모.
- **빼야 할 것**(B로 이동): 사업자등록/상호/대표자/주소/업태/종목, 청구 이메일·담당자, 결제수단 상세, 빌링키.

### (B) TENANT — **신규 "회사 정보 / 결제 설정" 화면**(TENANT_ADMIN 전용, 신규 화면 코드 예: W008)
`tenant_billing_profile` 전 필드. 고객이 스스로 입력·수정. Notion/Slack의 "Billing details" 대응.
- 3개 섹션 권장:
  1. **사업자 정보(세금계산서/インボイス 수신용)**: 국가, 상호, 대표자명(KR 필수), 사업자 식별번호+종류, 사업장 주소, 업태/종목(KR).
  2. **청구 연락처**: 청구 담당자, 청구/계산서 수신 이메일, 담당 연락처.
  3. **결제 수단**: 결제수단 ENUM(계약 방식과 일치, 변경은 승인 필요), 카드 표시정보(끝 4자리/브랜드), (카드면) 빌링키 등록 진입점.
- **읽기 전용으로 계약 요약 노출**: 현재 플랜/단가/무료석/주기/다음 청구일 — 값은 A에서 오고 고객은 못 고침(문의 유도).

### 접근·경계 요약
| | A: `tenant_contract` | B: `tenant_billing_profile` |
|--|--|--|
| 편집 | SYSTEM_ADMIN | TENANT_ADMIN |
| 조회 | SYSTEM_ADMIN(전체), TENANT_ADMIN(비민감 요약만) | TENANT_ADMIN(자기 것), SYSTEM_ADMIN(지원·감사 로그) |
| 화면 | 운영사 테넌트상세 > 계약/과금 탭 | 신규 고객용 "회사 정보/결제 설정" 화면 |
| 성격 | 가격 규칙(계약) | 청구·세무 프로필(고객 데이터) |

---

## 5. 출처 (Citations)

- Stripe — Subscription object (price/currency/items): https://docs.stripe.com/api/subscriptions/object
- Stripe — Customer object (billing·tax 정보 보관): https://docs.stripe.com/billing/customer
- Stripe — Customer Tax IDs: https://docs.stripe.com/billing/customer/tax-ids
- Chargebee — Customers/Billing Profile(VAT/tax reg number는 customer 레벨): https://apidocs.chargebee.com/docs/api/customers
- Chargebee — Configuring Taxes: https://www.chargebee.com/docs/2.0/tax.html
- GitHub — Managing user licenses(invoice-backed는 셀프 좌석증설 비활성): https://docs.github.com/en/billing/how-tos/manage-plan-and-licenses/manage-user-licenses
- GitHub — Adding a billing manager(결제수단·영수증·청구연락 셀프): https://docs.github.com/en/organizations/managing-peoples-access-to-your-organization-with-roles/adding-a-billing-manager-to-your-organization
- Notion — Billing / Payment methods(주소·VAT·결제수단): https://www.notion.com/help/billing , https://www.notion.com/help/payment-methods
- Slack — Manage your billing details / 결제수단: https://slack.com/help/articles/218915087-Manage-your-billing-details , https://slack.com/help/articles/360002038947-Supported-payment-methods
- KR 세금계산서 필요적 기재사항(사업자번호 10자리·상호·대표자명): https://www.chungoose.kr/blog/세금계산서-필요적-기재사항-반드시-입력해야-발행-된다
- KR 세금계산서 발행 가이드: https://www.heumtax.com/contents/posts/tax-invoice-guide
- 国税庁 — 適格請求書発行事業者 登録番号(T+13桁): https://www.invoice-kohyo.nta.go.jp/about-toroku/index.html
- 国税庁 — 適格請求書の記載事項(6項目) PDF: https://www.nta.go.jp/taxes/shiraberu/zeimokubetsu/shohi/keigenzeiritsu/pdf/qa/01-09.pdf
- 適格請求書 6つの記載事項 해설: https://media.invoice.ne.jp/column/invoices/Invoice-requirements.html
- 弥生 — 登録番号・法人番号 해설: https://www.yayoi-kk.co.jp/seikyusho/oyakudachi/tekikaku-torokubango/
- 토스페이먼츠 — 자동결제(빌링) / 빌링키(카드 원본 미보관): https://docs.tosspayments.com/guides/v2/billing
