/**
 * 백엔드 API 계약 타입 (백엔드 record DTO와 1:1 대응).
 * 스펙 원본: /v3/api-docs (Swagger) + docs/plan/backend-api.md (멀티테넌시 계약)
 */

/** 화면 코드 (실제 화면 명은 은닉). W003(회원가입)은 폐기·영구 결번 */
export type ScreenCode =
  | 'W000' // 랜딩(비로그인 홈)
  | 'W001' // 로그인
  | 'W002' // 로그아웃(액션)
  | 'W004' // 언어 마스터 관리 (SYSTEM_ADMIN)
  | 'W005' // 출결
  | 'W006' // 출결 상세
  | 'W007' // 테넌트 관리 (SYSTEM_ADMIN)
  | 'W008' // 테넌트 상세(기업/결제, SYSTEM_ADMIN) — W007에 임베드 전개
  | 'W009' // 멤버 관리 (TENANT_ADMIN)
  | 'W010' // 비밀번호 설정 (공개 — 토큰 필요)
  | 'W011' // 비밀번호 재설정 요청 (공개)
  | 'W012' // 메일 템플릿 관리 (SYSTEM_ADMIN)
  | 'W013' // 공휴일 관리 (TENANT_ADMIN)
  | 'W014' // 회사 메일 템플릿 (TENANT_ADMIN — 오버라이드, 기본은 전역)
  | 'W015' // 휴가 (멤버 — 잔여·신청)
  | 'W016' // 휴가 관리 (인사관리자+총관리자 — 결재·부여·종류)
  | 'W017' // 감사 로그 조회 (SYSTEM_ADMIN)
  | 'W018' // 청구서 (TENANT_ADMIN — 자사 월별 청구서)
  | 'W019' // 회사 정보/결제 (TENANT_ADMIN — 사업자정보·결제 + 계약 요약)
  | 'W020' // 회사 설정 (TENANT_ADMIN+HR_ADMIN — 근태 보고서 등 운영 설정)
  | 'W999' // 공통(헤더)

export type Lang = 'KOR' | 'ENG' | 'JPN'

// ---- UI 테마 (Phase 4) ----

/** 화면 적용 확정 테마 — 서버가 AUTO를 계절로 해석한 뒤의 값(AUTO는 오지 않는다) */
export type UiTheme = 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER'

/** 저장 설정값 — AUTO는 서버 날짜의 계절로 자동 해석 */
export type UiThemeSetting = UiTheme | 'AUTO'

export interface UiThemeResponse {
  theme: UiThemeSetting
  resolved: UiTheme
}

export interface UiThemeUpdateRequest {
  theme: UiThemeSetting
}

export type AttendanceType = 'GO_TO_WORK' | 'OFF_WORK' | 'EARLY_DEPARTURE' | 'BREAK'

export type ConfirmCode =
  | 'ALREADY_WORKING'
  | 'ALREADY_OFF_WORK'
  | 'ALREADY_EARLY_DEPARTURE'
  | 'RE_ATTEND'
  | 'NOT_WORKING_YET'
  | 'NOT_ON_DUTY'
  | 'CANNOT_BREAK'
  | 'ON_BREAK_CANNOT_ATTEND'

export type WorkStatus =
  | 'WAITING'
  | 'WORKING'
  | 'OFF_WORK_DONE'
  | 'EARLY_DEPARTURE_DONE'
  | 'ON_BREAK'
  | 'BREAK_ENDED'

export type StatusAlert = 'OVERDUE_OFF_WORK' | 'OVERDUE_BREAK_END'

export type NavigationReason =
  | 'LOGIN_REQUIRED'
  | 'ROLE_DENIED'
  | 'ALREADY_LOGGED_IN'
  | 'LOGGED_OUT'
  | 'UNKNOWN_SCREEN'

// ---- tenancy 공통 ----

export type Role = 'SYSTEM_ADMIN' | 'TENANT_ADMIN' | 'HR_ADMIN' | 'MEMBER'
export type UserStatus = 'PENDING' | 'ACTIVE' | 'DISABLED'
export type TenantStatus = 'ACTIVE' | 'SUSPENDED'
export type BillingMethod = 'INVOICE' | 'CARD'

// ---- navigation ----

export interface NavigateRequest {
  screen?: ScreenCode | null
  lang?: Lang | null
}

export interface NavigateResponse {
  screen: ScreenCode
  /** 서버가 확정한 언어(요청값 > 세션 저장값 > KOR) — 프론트 lang 상태는 이 값으로 동기화 */
  lang: Lang
  reason: NavigationReason | null
  userName: string | null
  /** 로그인 세션의 role (비로그인은 null) — 헤더 메뉴 분기용 */
  role: Role | null
  /** 테넌트 서브도메인으로 접속한 경우 그 테넌트명(로그인 화면 브랜딩·코드 입력란 숨김), 아니면 null */
  hostTenantName: string | null
  /** 화면 적용 확정 테마(시스템 전역 설정 — AUTO는 서버가 계절로 해석 완료) */
  theme: UiTheme
  texts: Record<string, string>
  headers: Record<string, string>
  data: unknown
}

// ---- auth / user ----

export interface LoginRequest {
  /** 테넌트 서브도메인으로 접속한 경우 null(호스트가 우선 확정) — 루트 도메인 접속은 필수 */
  tenantCode: string | null
  email: string
  password: string
}

export interface LoginResponse {
  userId: number
  email: string
  name: string
  role: Role
  /** 백엔드 계약과 1:1 (SYSTEM_ADMIN은 'DEFAULT') */
  tenantCode: string
  /** 헤더 뱃지용 — 비null (뱃지 표시는 role로 분기해 SYSTEM_ADMIN 미표시) */
  tenantName: string
  /** 본인 부서(코드) — me 조회 시 채워짐(로그인 응답은 null). 근무표 인쇄 머리말용 */
  departCd: string | null
}

export interface UserResponse {
  userId: number
  email: string
  name: string
  departCd: string | null
  role: Role
  status: UserStatus
}

// ---- auth: 비밀번호 설정/재설정 (W010/W011 — 공개 API, 토큰은 바디로만 수수) ----

/** 토큰 용도 — 초대(INVITE) / 비밀번호 재설정(RESET). W010의 안내 문구 분기용 */
export type TokenPurpose = 'INVITE' | 'RESET'

export interface TokenVerifyRequest {
  token: string
}

export interface TokenVerifyResponse {
  purpose: TokenPurpose
  name: string
  /** 마스킹된 이메일(예: h***@acme.co.kr) — 서버 책임, 프론트는 표시만 */
  emailMasked: string
  tenantName: string
  expiresAt: string
}

export interface PasswordSetRequest {
  token: string
  password: string
}

export interface PasswordResetRequest {
  /** 테넌트 서브도메인 접속이면 null(호스트가 우선 확정) — 루트 도메인 접속은 필수 */
  tenantCode: string | null
  email: string
}

// ---- system: 테넌트 (W007) ----

export interface TenantSummary {
  tenantId: number
  tenantCode: string
  name: string
  /** 소재국(tenant.country 승격 — holiday-plan §1-1) */
  country: ProfileCountry
  status: TenantStatus
  memberCount: number
  createdAt: string
}

export interface TenantCreateRequest {
  tenantCode: string
  name: string
  /** 소재국 — 필수(공휴일 동기화·초대 메일 언어 결정. CR3-1) */
  country: ProfileCountry
  adminEmail: string
  adminName: string
}

/**
 * 평면(flat) record — 백엔드 컨벤션 1:1.
 * 통합 최종 계약(이메일 온보딩 × 공휴일 병합 — CR3-5): initialPassword 폐지,
 * 관리자는 PENDING으로 생성되고 초대 메일(mailSent)·공휴일 동기화(holidaysSynced) 결과가 동봉된다.
 */
export interface TenantCreateResponse {
  tenantId: number
  tenantCode: string
  name: string
  country: ProfileCountry
  status: TenantStatus
  adminUserId: number
  adminEmail: string
  /** 항상 PENDING — 비밀번호는 초대 링크에서 본인이 설정 */
  adminStatus: UserStatus
  /** 초대 메일 발송 결과 — false면 [관리자 초대 재발송]으로 수습 */
  mailSent: boolean
  /** 당해·익년 공휴일 자동 동기화 결과 — false면 W013 수동 동기화 안내 */
  holidaysSynced: boolean
}

export interface TenantStatusUpdateRequest {
  status: TenantStatus
}

// ---- system: 기업/결제 정보 (W008) ----

/** 소재국 — 사업자 식별번호 체계·공휴일 동기화·메일 언어 결정(KR=사업자등록번호, JP=法人番号) */
export type ProfileCountry = 'KR' | 'JP'

/**
 * 전체 재입력(평문) — 부분 수정 없음.
 * country는 tenant.country 승격(holiday-plan §4-2)으로 요청에서 제거 — 서버가 tenant에서 취득한다.
 */
export interface TenantProfileUpsertRequest {
  businessRegNo: string
  ceoName: string | null
  postalCode: string | null
  address: string | null
  addressDetail: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
}

/** 조회는 마스킹 필드만 (마스킹은 서버 책임 — 프론트는 표시만) */
export interface TenantProfileResponse {
  tenantId: number
  country: ProfileCountry
  businessRegNoMasked: string // 예: KR 123-**-***** / JP *********0123
  ceoName: string | null
  postalCode: string | null
  address: string | null
  addressDetail: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhoneMasked: string | null // 예: 010-****-5678
  updatedAt: string
}

/** 전체 재입력 */
export interface TenantBillingUpsertRequest {
  billingMethod: BillingMethod
  billingEmail: string | null
  /** CARD일 때만. 응답으로는 절대 돌아오지 않는다 */
  pgCustomerKey: string | null
  cardLast4: string | null
  cardBrand: string | null
  plan: string
  /** 인당 월 단가(원, VAT 별도). null이면 서버 기본값 2000 */
  perSeatAmount: number | null
  /** 무료 인원(이 수까지 과금 제외). null이면 서버 기본값 5 */
  freeSeats: number | null
  billedFrom: string | null // ISO date
  memo: string | null
}

export interface TenantBillingResponse {
  tenantId: number
  billingMethod: BillingMethod
  billingEmail: string | null
  /** 빌링키는 존재 여부만 */
  hasBillingKey: boolean
  cardMasked: string | null // 예: **** **** **** 1234
  cardBrand: string | null
  plan: string
  perSeatAmount: number
  freeSeats: number
  billedFrom: string | null
  memo: string | null
  updatedAt: string
}

/** 월별 청구서 한 건(금액은 원, 부가세 별도). status=PROVISIONAL(진행 중)/ISSUED(마감 확정). */
/** 회사 결제 정보(#14) — 결제수단·청구 이메일·비고. 가격/카드정보는 다루지 않는다. */
export interface BillingProfileResponse {
  billingMethod: BillingMethod
  billingEmail: string | null
  memo: string | null
}
export interface BillingProfileRequest {
  billingMethod: BillingMethod
  billingEmail: string | null
  memo: string | null
}
/** 회사에 보여주는 계약 요약(#14, 읽기전용) — 요금제·인당 단가·무료 좌석은 운영사가 정함 */
export interface ContractSummaryResponse {
  plan: string
  perSeatAmount: number
  freeSeats: number
}
export interface InvoiceEntry {
  ym: string // YYYY-MM
  maxSeats: number
  freeSeats: number
  billedSeats: number
  seatDays: number // 초과분(추가인원) 좌석-일 누적
  daysInMonth: number // 청구월 일수
  freeBlockDays: number // 무료좌석 반값 블록 좌석-일(무료좌석수 × 유료 상태 일수)
  unitPrice: number
  subtotal: number
  vat: number
  total: number
  status: 'PROVISIONAL' | 'ISSUED'
  issuedAt: string | null
}

// ---- tenant: 멤버 (W009) ----

/** MemberResponse — 통합 최종 필드 집합(이메일 온보딩 × 스케줄 병합, CR3-3) */
export interface MemberSummary {
  userId: number
  email: string
  name: string
  departCd: string | null
  /** TENANT_ADMIN | MEMBER (SYSTEM_ADMIN은 등장 안 함) */
  role: Role
  status: UserStatus
  createdAt: string
  /** 개인 기본 시업 시각("HH:mm") */
  workStart: string
  /** 개인 기본 종업 시각("HH:mm") */
  workEnd: string
  /** 요일별 근무 플래그(월화수목금토일, '1'=근무 — V12) */
  workDays: string
  /** PENDING + 유효 INVITE 토큰이면 그 만료 시각, 아니면 null(만료/실패 → "재발송 필요" 표시) */
  inviteExpiresAt: string | null
}

export interface MemberCreateRequest {
  email: string
  name: string
  departCd?: string | null
  /** "HH:mm" — 미지정(null)은 09:00 */
  workStart?: string | null
  /** "HH:mm" — 미지정(null)은 18:00 */
  workEnd?: string | null
  /** "YYYY-MM-DD" 입사일(선택) — 미지정(null)은 등록일. 연차 계산 기준(#11) */
  hireDate?: string | null
}

/**
 * 평면(flat) record — 백엔드 컨벤션 1:1.
 * 통합 최종 계약(CR3-3): initialPassword 폐지 — 등록은 초대(PENDING + 메일 발송)로 전환.
 */
export interface MemberCreateResponse {
  userId: number
  email: string
  name: string
  departCd: string | null
  /** 등록 시 항상 MEMBER (role은 별도 PUT /role로 변경) */
  role: Role
  /** 항상 PENDING — 비밀번호는 초대 링크에서 본인이 설정 */
  status: UserStatus
  workStart: string
  workEnd: string
  /** 발송 실패해도 201(멤버는 생성됨) — false면 재발송 유도 */
  mailSent: boolean
  inviteExpiresAt: string
}

/** 초대 재발송(POST /members/{id}/invite, POST /system/tenants/{id}/admin-invite) 결과 */
export interface InviteResponse {
  userId: number
  email: string
  mailSent: boolean
  inviteExpiresAt: string
}

export interface MemberStatusUpdateRequest {
  status: UserStatus // ACTIVE | DISABLED
}

export interface MemberRoleUpdateRequest {
  role: Role // TENANT_ADMIN | MEMBER
}

/** PUT /members/{id}/schedule — 속성별 PUT(기존 /status·/role 패턴) */
export interface MemberScheduleUpdateRequest {
  workStart: string // "HH:mm"
  workEnd: string // "HH:mm"
  /** 요일별 근무 플래그(월~일, [01]{7} — 최소 1일 '1') */
  workDays: string
}

// ---- attendance ----

export interface CheckRequest {
  type: AttendanceType
  latitude?: number | null
  longitude?: number | null
  placeInfo?: string | null
  terminal?: string | null
}

export interface CheckResponse {
  allowed: boolean
  requiresConfirmation: boolean
  code: ConfirmCode | null
  message: string | null
  token: string | null
}

export interface ConfirmRequest extends CheckRequest {
  token: string
}

export interface StampResponse {
  type: AttendanceType
  stampedAt: string
  message: string
}

export interface StatusResponse {
  status: WorkStatus
  statusLabel: string
  stampedAt: string | null
  alert: StatusAlert | null
  alertLabel: string | null
  /** 오늘의 해석된 스케줄("HH:mm" — work_schedule > 개인 기본값). 휴일이면 null */
  todayScheduleStart: string | null
  todayScheduleEnd: string | null
}

/** 현행 10필드(CR3-7) + Phase 5: dayOff·manual = 12필드 */
export interface DailyAttendance {
  date: string
  holiday: boolean
  /** 요일 휴무(work_days 플래그 0 && 일자 오버라이드 없음) — holiday와 상호배타 */
  dayOff: boolean
  scheduleStart: string | null
  scheduleEnd: string | null
  stampIn: string | null
  stampOut: string | null
  /** 공휴일 명칭(NATIONAL/COMPANY). 개인 휴일(work_schedule.holiday)은 null → HOLIDAY 라벨 폴백 */
  holidayName: string | null
  /** 예정 근무(분) = 스케줄 구간 − 법정휴게. 휴일·휴무는 null */
  scheduledMinutes: number | null
  /** 실휴식 합(분). 출근·퇴근 미확정이면 null */
  breakMinutes: number | null
  /** 법정휴게(분). 근무일=스케줄 기반, 휴일·휴무 근무=실체류 기반, 그 외 null */
  statutoryBreakMinutes: number | null
  /** 인정 휴게(분) = max(법정, 실휴식) — 총근무에서 실제 차감. 휴식 미기록이어도 근무일이면 법정. 미확정 null */
  recognizedBreakMinutes: number | null
  /** 총 근무시간(분) = max(0, 체류 − 인정 휴게). 미확정이면 null */
  workMinutes: number | null
  /** 그 날에 수동 정정 스탬프 존재(상세는 daily API) */
  manual: boolean
  /** 정정 사유(비고) — 없으면 null */
  note: string | null
  /** 승인된 휴가 명칭(#9) — 그 날 유효 휴가(반차/시간은 접미 표기). 없으면 null */
  leaveName: string | null
}

// ---- 수동 정정 + 일자 이력 (Phase 5) ----

/** 스탬프 등록 경로 — 버튼(자동)/정정 등록(수동) */
export type StampSource = 'AUTO' | 'MANUAL'

/** 수동 정정 사유 코드 — OTHER는 reasonText 필수 */
export type ManualReason = 'FORGOT' | 'DEVICE' | 'OFFSITE' | 'OTHER'

export interface ManualStampRequest {
  date: string // yyyy-MM-dd
  time: string // HH:mm
  /** 등록은 BREAK 제외(서버 400). 수정(manualUpdate)은 휴식 스탬프에 한해 BREAK 허용 */
  type: AttendanceType
  reasonCode: ManualReason
  reasonText?: string | null
}

/** 휴식 시간 정정 등록 — 시작·종료 쌍(단일 스탬프 정합성 회피) */
export interface ManualBreakRequest {
  date: string // yyyy-MM-dd
  startTime: string // HH:mm
  endTime: string // HH:mm
  reasonCode: ManualReason
  reasonText?: string | null
}

/** 일자 스탬프 이력 1건 — attendance는 append-only(중복 스탬프도 전부 나온다) */
export interface DailyStampEntry {
  /** 수동 정정 수정(잘못 입력 복구)의 대상 식별자 — MANUAL 행만 수정 가능 */
  attendanceId: number
  stampedAt: string
  type: AttendanceType
  breakEnd: boolean
  source: StampSource
  reasonCode: string | null
  reasonText: string | null
}

export interface DailyResponse {
  date: string
  stamps: DailyStampEntry[]
}

export interface MonthlyResponse {
  year: number
  month: number
  days: DailyAttendance[]
  /** 월 예정근무 합(분) = scheduledMinutes non-null 합 */
  totalScheduledMinutes: number
  /** 월 인정휴게 합(분) = recognizedBreakMinutes non-null 합 */
  totalBreakMinutes: number
  /** 월 실근무 합(분) = workMinutes non-null 합 */
  totalWorkMinutes: number
}

// ---- admin: 메일 템플릿 (W012 — SYSTEM_ADMIN 글로벌 자산) ----

/** 행 집합은 시드 6행(purpose×lang) 고정 — 생성/삭제 없음, 수정·미리보기만 */
export interface MailTemplateResponse {
  purpose: TokenPurpose
  lang: Lang
  subject: string
  body: string
  updatedAt: string
}

export interface MailTemplateUpdateRequest {
  subject: string
  body: string
}

/** 저장하지 않고 샘플 값 치환 결과만 반환 — 저장과 같은 변수 검증을 먼저 통과 가능 */
export interface MailTemplatePreviewRequest {
  purpose: TokenPurpose
  lang: Lang
  subject: string
  body: string
}

export interface MailTemplatePreviewResponse {
  subject: string
  body: string
}

// ---- tenant: 회사별 메일 템플릿 오버라이드 (W014 — TENANT_ADMIN) ----

/** 유효 템플릿(전역 6행 기준) — overridden=true면 회사 설정이 발송에 쓰인다 */
export interface TenantMailTemplateResponse {
  purpose: TokenPurpose
  lang: Lang
  subject: string
  body: string
  overridden: boolean
  updatedAt: string
}

// ---- tenant: 공휴일 (W013 — TENANT_ADMIN 전용) ----

/** NATIONAL=국가 공휴일(동기화 대상) / COMPANY=회사 지정(동기화 불가침) */
export type HolidayType = 'NATIONAL' | 'COMPANY'

export interface HolidayEntry {
  holidayId: number // 대리키(날짜 중복 허용 — #7)
  holidayDate: string // yyyy-MM-dd
  holidayName: string
  holidayType: HolidayType
  recurring: boolean // 매년 반복(회사 공휴일 — #8)
  updatedAt: string
}

export interface HolidaySyncResult {
  year: number
  country: string
  fetched: number
  inserted: number
  deleted: number
  skippedCompany: number
}

/** 수동 등록은 항상 COMPANY로 저장(요청에 type 없음). 날짜 중복 허용(#7) */
export interface HolidayCreateRequest {
  holidayDate: string
  holidayName: string
  recurring: boolean // 매년 반복(#8) — 동기화된 모든 연도로 실체화
}

/** 회사 공휴일 개별 수정(#8) — 날짜/명칭 이동 + 반복 토글 */
export interface HolidayUpdateRequest {
  holidayDate: string
  holidayName: string
  recurring: boolean
}

// ---- 근무 스케줄 월 로타 (#13) ----

/** 로타 셀(일자 오버라이드) — off=휴무, 아니면 start/end(+crossesMidnight 야간교대). 시각은 HH:mm(:ss) */
export interface RotaCell {
  date: string
  off: boolean
  start: string | null
  end: string | null
  crossesMidnight: boolean
  holiday: boolean
}

export interface RotaSaveCell {
  date: string
  off: boolean
  start: string | null
  end: string | null
  crossesMidnight: boolean
}

export interface RotaSaveRequest {
  year: number
  month: number
  cells: RotaSaveCell[]
}

/** 반복 패턴 슬롯 — (주차 0.., 요일 1..7) → 휴무(off) 또는 근무(start/end + 야간). */
export interface PatternSlot {
  weekIndex: number
  dayOfWeek: number
  off: boolean
  start: string | null
  end: string | null
  crossesMidnight: boolean
}

export interface PatternResponse {
  cycleWeeks: number
  slots: PatternSlot[]
}

export interface PatternSaveRequest {
  cycleWeeks: number
  slots: PatternSlot[]
}

// ---- i18n (언어 마스터) ----

export interface LanguageEntry {
  windowId: string
  langKey: string
  lang: Lang
  langValue: string
}

export interface LanguageUpsertRequest {
  windowId: string
  langKey: string
  lang: Lang
  langValue: string
}

// ---- 휴가 (Phase 6-2) ----

export type LeaveUnit = 'DAY' | 'HOUR'
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'CANCEL_REQUESTED'

export interface LeaveType {
  leaveTypeId: number
  code: string
  name: string
  paid: boolean
  unit: LeaveUnit
  /** 시간 단위 휴가 신청 허용(#12) — 켜진 종류만 신청 시 날짜/시간 토글 노출 */
  hourlyEnabled: boolean
  requiresApproval: boolean
  isAnnual: boolean
  active: boolean
  sortOrder: number
}

export interface LeaveTypeCreateRequest {
  /** 코드는 서버가 자동 생성 — 클라이언트는 보내지 않는다(#10) */
  name: string
  paid: boolean
  unit: LeaveUnit
  hourlyEnabled: boolean
  requiresApproval: boolean
  sortOrder: number
}

export interface LeaveTypeUpdateRequest {
  name: string
  paid: boolean
  unit: LeaveUnit
  hourlyEnabled: boolean
  requiresApproval: boolean
  active: boolean
  sortOrder: number
}

/** 종류별 잔여 — 분 단위 + 1일 환산(standardDayMinutes) */
export interface LeaveBalance {
  leaveTypeId: number
  code: string
  name: string
  unit: LeaveUnit
  isAnnual: boolean
  grantedMinutes: number
  usedMinutes: number
  pendingMinutes: number
  remainingMinutes: number
  standardDayMinutes: number
}

/** 만기일별 잔여 한 행(부여 하나의 남은 분 + 만기일). expiresOn null = 무기한. */
export interface LeaveBalanceRow {
  leaveTypeId: number
  name: string
  unit: LeaveUnit
  remainingMinutes: number
  expiresOn: string | null
  standardDayMinutes: number
}

export interface LeaveApplyRequest {
  leaveTypeId: number
  dayUnit: boolean
  startDate?: string | null
  endDate?: string | null
  halfDay?: boolean
  startTime?: string | null
  endTime?: string | null
  reason?: string | null
}

export interface LeaveRequestItem {
  leaveRequestId: number
  userId: number
  userName: string
  leaveTypeId: number
  typeName: string
  unit: LeaveUnit
  startAt: string
  endAt: string
  minutes: number
  dayUnit: boolean
  halfDay: boolean
  reason: string | null
  status: LeaveStatus
  decidedAt: string | null
  decisionNote: string | null
  cancelReason: string | null
  createdAt: string
}

export interface LeaveDecisionRequest {
  approve: boolean
  note?: string | null
}

export interface LeaveGrantRequest {
  userId: number
  leaveTypeId: number
  days: number
  expiresOn?: string | null
  memo?: string | null
}

/** 일괄 부여(#9) — 여러 멤버에 같은 종류·일수 */
export interface LeaveBulkGrantRequest {
  userIds: number[]
  leaveTypeId: number
  days: number
  expiresOn?: string | null
  memo?: string | null
}

export interface MemberLeaveSummary {
  userId: number
  name: string
  hireDate: string | null
  annualRemainingMinutes: number | null
  /** 법정 제안(미리보기 — 자동 부여 아님). 관리자가 확인 후 적용 */
  suggestedAnnualMinutes: number | null
  standardDayMinutes: number
}

export interface MemberLeaveDetail {
  userId: number
  name: string
  hireDate: string | null
  standardDayMinutes: number
  suggestedAnnualMinutes: number | null
  balances: LeaveBalance[]
  requests: LeaveRequestItem[]
}

// ---- 감사 로그 (SYSTEM_ADMIN) ----

export interface AuditLogEntry {
  auditId: number
  category: string
  event: string
  tenantId: number | null
  tenantName: string | null
  userId: number | null
  actor: string | null
  ip: string | null
  userAgent: string | null
  requestPath: string | null
  detail: string | null
  createdAt: string
}

// ---- error ----

export interface FieldErrorDetail {
  field: string
  message: string
}

/**
 * 공통 에러 응답. code는 유니온으로 좁히지 않는다(현행 컨벤션).
 * 주요 코드: 'LAST_TENANT_ADMIN'(409 마지막 관리자 보호), 'RATE_LIMITED'(429 로그인 레이트 리밋),
 * 'TENANT_PROFILE_NOT_FOUND'/'TENANT_BILLING_NOT_FOUND'(404 미등록) 등 —
 * message는 서버가 세션 언어로 조립해 내려준다(프론트는 표시만).
 */
export interface ErrorResponse {
  code: string
  message: string
  fieldErrors: FieldErrorDetail[] | null
}

// ---- 근태 보고서 설정 (W019 — 결재/도장란 on/off) ----

export interface ReportSetting {
  /** 근태 보고서(Excel·인쇄)에 결재(도장)란 표시 여부 */
  stampEnabled: boolean
}
