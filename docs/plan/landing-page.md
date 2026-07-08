# 랜딩페이지 콘텐츠 기획서 — Web Attendance

- 목적: 회사 소개용 랜딩페이지(인덱스 화면 W000 확장)의 **구조·카피·언어 키·CTA·시각 가이드**를 확정한다.
- 상태: 기획(구현 전). 카피는 이 문서 그대로 V5 시드 SQL(`language_master`)로 옮길 수 있는 형태로 작성.
- 전제:
  - **B2B 계약형 SaaS** — 셀프가입 없음. 도입 문의 → 운영자(SYSTEM_ADMIN)가 고객사 등록·계정 발급 (`docs/plan-saas-multitenancy.md` §6).
  - 카피는 **실제 구현된 기능만** 서술한다. 모바일 앱·AI·알림·리포트 등 미구현 기능 언급 금지.
  - 외부 리소스(이미지 CDN, 웹폰트, 외부 스크립트) 사용 불가 — CSS만으로 구성.

---

## 1. 페이지 구조 (섹션 순서와 목적)

랜딩은 기존 W000 인덱스 화면을 대체·확장한다(서버 주도 화면 전개 유지, URL 라우팅 없음).

| # | 섹션 | 목적 |
|---|------|------|
| 1 | **히어로** | 3초 안에 "무엇을 하는 제품인지 + 누구를 위한 것인지" 전달. 헤드라인 + 서브카피 + 주 CTA(도입 문의) + 보조 CTA(로그인) |
| 2 | **핵심 기능 4** | 차별점을 카드 4장으로: ① 위치 기반 출결 ② 체크→확정 변조 탐지 ③ 월별 근태 상세 ④ 한/영/일 3개 언어 |
| 3 | **신뢰 요소 (보안/격리)** | B2B 구매 결정권자의 불안 해소: 기업별 데이터 격리, 인증·비밀번호 보안, 기록 무결성 |
| 4 | **도입 절차 3단계** | 셀프가입이 없다는 점을 "간단한 절차"로 반전: 문의 → 계정 발급 → 사용 시작 |
| 5 | **문의 CTA** | 마지막 행동 유도. mailto 링크 반복 + 로그인 버튼 |
| 6 | **푸터** | 제품명·저작권·문의 이메일. 최소 구성 |

섹션 배치 논리: 관심(히어로) → 근거(기능) → 안심(신뢰) → 행동 장벽 제거(3단계) → 행동(CTA).

> **주의(격리 카피 게시 시점)**: 테넌트 격리는 현재 "계획 확정·구현 전"(plan-saas-multitenancy.md)이다.
> 신뢰 섹션의 격리 카피(`LANDING_TRUST1_*`)는 **Phase 1(코어 멀티테넌시) 완료 후 게시**를 원칙으로 한다.
> Phase 1 이전에 랜딩을 먼저 열어야 하면 해당 카드 1장만 빼고 2장(인증/무결성)으로 게시한다.

---

## 2. 카피 전문 + 언어 마스터 키 (한/영/일)

- 키는 기존 규약대로 **화면 그룹 `W000`** 하위, `LANDING_` 프리픽스.
- 기존 `INDEX_TITLE`/`INDEX_SUB`는 유지(다른 화면 폴백 용도)하되 랜딩 렌더링에는 사용하지 않는다.
- 아래 표 그대로 `V5__seed_landing_texts.sql`의 `(win_id, text_key, lang, value)` INSERT로 이관 가능.

### 2-1. 히어로

| 키 | 한국어(KOR) | English(ENG) | 日本語(JPN) |
|----|-------------|--------------|-------------|
| `LANDING_HERO_BADGE` | 중소기업을 위한 근태 관리 SaaS | Attendance SaaS for small & mid-sized teams | 中小企業向け勤怠管理SaaS |
| `LANDING_HERO_TITLE` | 출근의 순간을, 신뢰할 수 있는 기록으로 | Turn every clock-in into a record you can trust | 出退勤の瞬間を、信頼できる記録に |
| `LANDING_HERO_SUB` | 위치 기반 출결과 2단계 변조 탐지로 근태 기록의 신뢰를 지킵니다. 별도 설치 없이 웹 브라우저에서 바로 사용하는 기업용 출결 시스템입니다. | Location-based check-in and two-step tamper detection keep your attendance records trustworthy. A web-based attendance system for companies — no installation required. | 位置情報にもとづく打刻と2段階の改ざん検知で、勤怠記録の信頼性を守ります。インストール不要、Webブラウザだけで使える企業向け勤怠システムです。 |

### 2-2. 핵심 기능 카드 (제목 + 2문장 설명)

| 키 | 한국어(KOR) | English(ENG) | 日本語(JPN) |
|----|-------------|--------------|-------------|
| `LANDING_FEATURES_TITLE` | 핵심 기능 | Key features | 主な機能 |
| `LANDING_FEAT1_TITLE` | 위치 기반 출결 | Location-based clock-in | 位置情報にもとづく打刻 |
| `LANDING_FEAT1_DESC` | 출근·퇴근·조퇴·휴식을 위치 정보와 함께 기록합니다. 어디에서 남긴 기록인지가 근태 데이터에 그대로 남습니다. | Record clock-in, clock-out, early leave, and breaks together with location data. Where each stamp was made stays part of the attendance record. | 出勤・退勤・早退・休憩を位置情報とともに記録します。どこで打刻されたかが、勤怠データにそのまま残ります。 |
| `LANDING_FEAT2_TITLE` | 체크 → 확정, 2단계 변조 탐지 | Two-step tamper detection | チェック→確定、2段階の改ざん検知 |
| `LANDING_FEAT2_DESC` | 출결은 체크와 확정 두 단계로 처리되고, 두 시점의 데이터를 해시로 대조해 중간 변조를 탐지합니다. 조작된 요청은 기록으로 남지 않습니다. | Every stamp goes through a check step and a confirm step, compared with a cryptographic hash. Requests altered in between are rejected before they ever become records. | 打刻はチェックと確定の2段階で処理され、両時点のデータをハッシュで照合して改ざんを検知します。改変されたリクエストは記録として残りません。 |
| `LANDING_FEAT3_TITLE` | 월별 근태 상세 | Monthly attendance detail | 月別勤怠詳細 |
| `LANDING_FEAT3_DESC` | 일자별 근무 스케줄과 실제 출퇴근 시각을 한 화면에서 확인합니다. 야간 근무 표기, 개인별 근무시간, 공휴일까지 반영된 월별 뷰를 제공합니다. | See each day's work schedule and actual clock-in/out times in a single view. Overnight work, per-person schedules, and company holidays are all reflected in the monthly view. | 日ごとの勤務スケジュールと実際の出退勤時刻を1つの画面で確認できます。深夜勤務の表記、個人別の勤務時間、休日まで反映した月別ビューを提供します。 |
| `LANDING_FEAT4_TITLE` | 한국어·영어·일본어 지원 | Korean, English, and Japanese | 日本語・英語・韓国語に対応 |
| `LANDING_FEAT4_DESC` | 화면과 안내 메시지가 사용자가 선택한 언어로 표시됩니다. 다국적 구성원이 있는 팀도 같은 시스템을 함께 사용할 수 있습니다. | Screens and messages are shown in the language each user selects. Teams with members from different countries can share one system. | 画面や案内メッセージが、ユーザーの選んだ言語で表示されます。多国籍のメンバーがいるチームでも、同じシステムを一緒に使えます。 |

### 2-3. 신뢰 요소 (보안/격리)

| 키 | 한국어(KOR) | English(ENG) | 日本語(JPN) |
|----|-------------|--------------|-------------|
| `LANDING_TRUST_TITLE` | 기업 데이터를 지키는 기본기 | Built to protect your company's data | 企業データを守る基本設計 |
| `LANDING_TRUST1_TITLE` | 기업별 데이터 격리 | Per-company data isolation | 企業ごとのデータ分離 |
| `LANDING_TRUST1_DESC` | 고객사마다 분리된 계정 체계로 운영되어 다른 회사의 데이터에는 접근할 수 없습니다. 운영자도 고객사 내부의 출결 데이터는 열람하지 않는 것을 원칙으로 합니다. | Each company operates on its own isolated account scope — no one can reach another company's data. As a policy, even the service operator does not view attendance data inside a customer's workspace. | 顧客企業ごとに分離されたアカウント体系で運用され、他社のデータにはアクセスできません。運営者であっても、顧客企業内の勤怠データは閲覧しないことを原則としています。 |
| `LANDING_TRUST2_TITLE` | 안전한 인증 | Secure authentication | 安全な認証 |
| `LANDING_TRUST2_DESC` | 비밀번호는 복호화할 수 없는 BCrypt 해시로만 저장합니다. 로그인 실패 응답은 계정 존재 여부를 노출하지 않도록 설계되어 있으며, 관리자 권한은 일반 사용자와 분리되어 있습니다. | Passwords are stored only as irreversible BCrypt hashes. Failed logins never reveal whether an account exists, and administrator privileges are kept separate from regular users. | パスワードは復号できないBCryptハッシュとしてのみ保存します。ログイン失敗時の応答はアカウントの有無を明かさない設計で、管理者権限は一般ユーザーと分離されています。 |
| `LANDING_TRUST3_TITLE` | 기록의 무결성 | Integrity of every record | 記録の完全性 |
| `LANDING_TRUST3_DESC` | 모든 출결은 체크 시점의 데이터와 해시로 대조된 뒤에만 확정됩니다. 확정 이후의 기록은 시각·위치·단말 정보와 함께 보관됩니다. | An attendance record is finalized only after it matches the hash of its check step. Once confirmed, each record is kept with its time, location, and device information. | すべての打刻は、チェック時点のデータとハッシュで照合された後にのみ確定されます。確定後の記録は、時刻・位置・端末情報とともに保管されます。 |

### 2-4. 도입 절차 3단계

| 키 | 한국어(KOR) | English(ENG) | 日本語(JPN) |
|----|-------------|--------------|-------------|
| `LANDING_STEPS_TITLE` | 도입 절차 | How to get started | ご導入の流れ |
| `LANDING_STEP1_TITLE` | 1. 도입 문의 | 1. Contact us | 1. お問い合わせ |
| `LANDING_STEP1_DESC` | 이메일로 회사명과 인원 규모를 보내 주세요. 도입 범위와 일정을 안내해 드립니다. | Send us your company name and team size by email. We will get back to you with scope and schedule. | メールで会社名とご利用人数をお知らせください。導入範囲とスケジュールをご案内します。 |
| `LANDING_STEP2_TITLE` | 2. 회사 계정 발급 | 2. Workspace setup | 2. アカウント発行 |
| `LANDING_STEP2_DESC` | 계약이 확정되면 운영팀이 귀사 전용 공간과 관리자 계정을 만들어 드립니다. 별도의 설치나 서버 준비는 필요 없습니다. | Once the contract is set, our team creates your company workspace and an administrator account. No installation or servers on your side. | ご契約後、運営チームが貴社専用のワークスペースと管理者アカウントを作成します。インストールやサーバーの準備は不要です。 |
| `LANDING_STEP3_TITLE` | 3. 구성원 등록 후 바로 사용 | 3. Add members and go | 3. メンバー登録してすぐ利用 |
| `LANDING_STEP3_DESC` | 관리자가 구성원을 등록하면 그날부터 웹 브라우저에서 출결 기록을 시작할 수 있습니다. | As soon as your administrator adds members, everyone can start recording attendance in the browser. | 管理者がメンバーを登録すれば、その日からWebブラウザで勤怠記録を始められます。 |

### 2-5. CTA / 푸터

| 키 | 한국어(KOR) | English(ENG) | 日本語(JPN) |
|----|-------------|--------------|-------------|
| `LANDING_CTA_TITLE` | 팀의 근태 관리, 지금 시작하세요 | Start managing attendance the reliable way | チームの勤怠管理を、今すぐ |
| `LANDING_CTA_SUB` | 도입 문의를 보내 주시면 하루 안에 회신드립니다. | Send an inquiry and we will reply within one business day. | お問い合わせいただければ、1営業日以内にご返信します。 |
| `LANDING_CTA_CONTACT` | 도입 문의하기 | Contact us | 導入のお問い合わせ |
| `LANDING_CTA_LOGIN` | 로그인 | Log in | ログイン |
| `LANDING_FOOTER_PRODUCT` | Web Attendance — 웹 출결 관리 시스템 | Web Attendance — web-based attendance management | Web Attendance — Web勤怠管理システム |
| `LANDING_FOOTER_CONTACT` | 도입 문의: {CONTACT_EMAIL} | Sales inquiries: {CONTACT_EMAIL} | 導入に関するお問い合わせ: {CONTACT_EMAIL} |
| `LANDING_FOOTER_COPYRIGHT` | © 2026 Web Attendance. All rights reserved. | © 2026 Web Attendance. All rights reserved. | © 2026 Web Attendance. All rights reserved. |

- `{CONTACT_EMAIL}`은 시드 시점에 실제 운영자 주소로 치환(플레이스홀더 그대로 시드하지 말 것). 주소 미확정 시 임시로 `contact@attendance.example` 사용.
- `LANDING_CTA_LOGIN`은 공통 화면(W999)에 동일 문구가 이미 있으면 그 키를 재사용하고 본 키는 시드하지 않는다(중복 방지).

### 카피 검수 체크리스트 (시드 전 확인)

- [ ] 미구현 기능 언급 없음: 모바일 앱 / AI / 푸시 알림 / 리포트 내보내기 / 급여 연동 — 모두 없음 확인됨
- [ ] "GPS"라는 단어 대신 "위치 정보/위치 기반"만 사용(구현은 브라우저가 제공하는 좌표 수신)
- [ ] 격리 카피(TRUST1)는 Phase 1 완료 전 게시 금지 (§1 주의 참조)
- [ ] 일본어 존칭·영어 어조 최종 네이티브 검수 1회

---

## 3. CTA 설계

셀프가입이 없으므로 랜딩의 전환 목표는 단 두 가지: **① 도입 문의 발신, ② 기존 고객 로그인.**

### 3-1. 도입 문의 = mailto 링크 (Phase 현행)

```html
<a class="btn primary" href="mailto:{CONTACT_EMAIL}?subject=[Web%20Attendance]%20도입%20문의">
  {t('LANDING_CTA_CONTACT')}
</a>
```

- 히어로와 §5 CTA 섹션, 두 곳에 동일 버튼 배치(스크롤 어느 지점에서든 1클릭 거리).
- `subject`는 언어별로 프리필: KOR `도입 문의` / ENG `Sales inquiry` / JPN `導入のお問い合わせ` — 언어 키(`LANDING_MAIL_SUBJECT`)로 관리해도 좋고, 초기엔 한국어 고정도 무방.
- 이메일 주소는 프론트 하드코딩 대신 **환경/설정값 또는 언어 마스터(`LANDING_FOOTER_CONTACT`)에서 주입** — 주소 변경 시 재배포 불필요.

### 3-2. 로그인 버튼

- 보조 CTA. `navigation` API로 `W001` 요청(기존 헤더 네비게이션과 동일 경로) — 새 구현 불필요.
- 히어로에서 주 CTA(주황 채움) 옆에 아웃라인 스타일로 병치. 기존 고객이 랜딩에 들어와도 헤매지 않게.

### 3-3. 문의 폼(DB 저장)은 Phase 3 이후 권고 — 판단 근거

| 항목 | mailto (현행 권고) | 문의 폼 + DB 저장 |
|------|--------------------|-------------------|
| 스팸 리스크 | 낮음(발신자 메일 클라이언트 필요) | **높음** — 비인증 공개 POST 엔드포인트 = 봇 표적. CAPTCHA/레이트리밋 필요 |
| 인프라 | 0 (링크 1개) | 테이블 + API + 관리 화면 + 알림(메일 인프라) 필요 |
| 개인정보 | 저장 안 함 | 연락처 저장 → 보관/파기 정책 필요(멀티테넌시 계획 §6-1의 파기 정책과 세트) |
| 리드 유실 | 메일함에서 관리(초기 규모에 충분) | 체계적이나 초기엔 과잉 |

**결론**: 메일 발송 인프라·감사 로그가 들어오는 **Phase 3 이후**에 폼 전환을 재검토한다. 그 전까지는 mailto + 안내 문구(`LANDING_CTA_SUB`)로 충분. 폼 도입 시에도 비인증 엔드포인트에는 레이트리밋 + 허니팟 필드를 전제 조건으로 한다.

---

## 4. 시각 요소 가이드 (CSS-only, 외부 리소스 없음)

### 4-1. 기존 토큰 그대로 사용 (`frontend/src/app.css`)

| 토큰 | 값 | 랜딩 사용처 |
|------|-----|------------|
| `--accent` | `#e8833a` | 주 CTA 버튼, 히어로 강조 텍스트, 섹션 구분선, 단계 번호 배지 |
| `--accent-dark` | `#c96a24` | 버튼 hover, 히어로 그라디언트 끝색 |
| `--border` / 배경 `#f7f6f4` | 기존값 | 카드 테두리 / 페이지 배경 유지 |
| `.panel` (흰 배경, radius 10px, 24px 패딩) | 기존 클래스 | 기능 카드·신뢰 카드·단계 카드의 베이스 |
| `button.primary` | 기존 클래스 | CTA 버튼 그대로 재사용 |

새 색상·새 폰트를 추가하지 않는다. 폰트는 기존 시스템 스택(Segoe UI/Noto Sans KR/Hiragino Sans) 유지 — 한/영/일 모두 커버되고 외부 로딩이 없다.

### 4-2. 이미지 없이 구성하는 방법

- **히어로 배경**: `linear-gradient(135deg, #fff 55%, rgba(232,131,58,.12))` 정도의 은은한 주황 그라디언트 + `--accent` 2px 하단 보더(기존 헤더와 톤 일치). 사진·일러스트 불필요.
- **기능 카드 4장**: `display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:16px`. 각 카드 상단에 아이콘 대용으로 ①③은 **유니코드 심볼**(예: 위치 `◎`, 달력 `▦`, 언어 `Aあ`), ② 변조 탐지는 CSS로 그린 체크 배지(원형 `border-radius:50%` + `::after`로 체크 표시) — 이모지 컬러 렌더링 편차가 싫으면 전부 CSS 도형으로 통일.
- **신뢰 섹션**: 배경을 카드 없이 옅은 회색 밴드(`#f0eeeb`)로 깔고 3열 텍스트 블록 — "무거운 보안 문서" 느낌보다 담백하게.
- **도입 절차 3단계**: CSS 카운터 또는 고정 번호. 번호는 `--accent` 배경의 원형 배지(`width:32px; border-radius:50%; color:#fff`), 단계 사이 연결선은 `border-top:2px dashed var(--border)` (모바일에서는 세로 스택 + 좌측 선).
- **다국어 전환**: 기존 헤더의 언어 셀렉터를 그대로 노출(랜딩도 W000이므로 `texts` 응답으로 즉시 반영됨). 이것 자체가 "3개 언어 지원"의 라이브 데모가 된다 — 별도 스크린샷 불필요.
- **반응형**: 컨테이너는 기존 `.app`(max-width 860px) 유지. 히어로 타이틀 `clamp(1.6rem, 4vw, 2.4rem)`.
- **금지**: 외부 이미지/폰트/아이콘 CDN, base64 대용량 이미지, 지도 타일(위치 기능 설명은 텍스트+도형으로만).

### 4-3. 구현 메모

- 랜딩은 `IndexScreen.tsx`(W000)를 확장하는 방향 — navigation 응답의 `texts`에 위 키들이 실리므로 컴포넌트는 `t('LANDING_...')`만 호출하면 된다.
- 시드는 `V5__seed_landing_texts.sql`로 추가(멀티테넌시 계획 Phase 2의 "언어 마스터 신규 화면 텍스트 시드(V5)"와 파일 번호 충돌 시 조정).
- 로그인 상태로 W000 접근 시 홈으로 리다이렉트되는 현행 전환 규칙은 유지(랜딩은 비로그인 방문자 전용이라는 전제와 일치).
