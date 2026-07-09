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
  | 'W999' // 공통(헤더)

export type Lang = 'KOR' | 'ENG' | 'JPN'

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

export type Role = 'SYSTEM_ADMIN' | 'TENANT_ADMIN' | 'MEMBER'
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
}

export interface UserResponse {
  userId: number
  email: string
  name: string
  departCd: string | null
  role: Role
  status: UserStatus
}

// ---- system: 테넌트 (W007) ----

export interface TenantSummary {
  tenantId: number
  tenantCode: string
  name: string
  status: TenantStatus
  memberCount: number
  createdAt: string
}

export interface TenantCreateRequest {
  tenantCode: string
  name: string
  adminEmail: string
  adminName: string
}

/** 평면(flat) record — 백엔드 컨벤션 1:1 */
export interface TenantCreateResponse {
  tenantId: number
  tenantCode: string
  name: string
  status: TenantStatus
  adminUserId: number
  adminEmail: string
  /** 1회성 — 응답 이후 어디에도 저장하지 않는다 */
  initialPassword: string
}

export interface TenantStatusUpdateRequest {
  status: TenantStatus
}

// ---- system: 기업/결제 정보 (W008) ----

/** 전체 재입력(평문) — 부분 수정 없음 */
export interface TenantProfileUpsertRequest {
  businessRegNo: string
  ceoName: string | null
  address: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
}

/** 조회는 마스킹 필드만 (마스킹은 서버 책임 — 프론트는 표시만) */
export interface TenantProfileResponse {
  tenantId: number
  businessRegNoMasked: string // 예: 123-**-*****
  ceoName: string | null
  address: string | null
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
  billedFrom: string | null
  memo: string | null
  updatedAt: string
}

// ---- tenant: 멤버 (W009) ----

export interface MemberSummary {
  userId: number
  email: string
  name: string
  departCd: string | null
  /** TENANT_ADMIN | MEMBER (SYSTEM_ADMIN은 등장 안 함) */
  role: Role
  status: UserStatus
  createdAt: string
}

export interface MemberCreateRequest {
  email: string
  name: string
  departCd?: string | null
}

/** 평면(flat) record — 백엔드 컨벤션 1:1 */
export interface MemberCreateResponse {
  userId: number
  email: string
  name: string
  departCd: string | null
  /** 등록 시 항상 MEMBER (role은 별도 PUT /role로 변경) */
  role: Role
  status: UserStatus
  /** 1회성 */
  initialPassword: string
}

export interface MemberStatusUpdateRequest {
  status: UserStatus // ACTIVE | DISABLED
}

export interface MemberRoleUpdateRequest {
  role: Role // TENANT_ADMIN | MEMBER
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
}

export interface DailyAttendance {
  date: string
  holiday: boolean
  scheduleStart: string | null
  scheduleEnd: string | null
  stampIn: string | null
  stampOut: string | null
}

export interface MonthlyResponse {
  year: number
  month: number
  days: DailyAttendance[]
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
