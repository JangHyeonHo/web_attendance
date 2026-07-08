/**
 * 백엔드 API 계약 타입 (백엔드 record DTO와 1:1 대응).
 * 스펙 원본: /v3/api-docs (Swagger)
 */

/** 화면 코드 (실제 화면 명은 은닉) */
export type ScreenCode =
  | 'W000' // 인덱스
  | 'W001' // 로그인
  | 'W002' // 로그아웃(액션)
  | 'W003' // 회원가입
  | 'W004' // 관리자
  | 'W005' // 출결
  | 'W006' // 출결 상세
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
  | 'ADMIN_ONLY'
  | 'ALREADY_LOGGED_IN'
  | 'LOGGED_OUT'
  | 'UNKNOWN_SCREEN'

// ---- navigation ----

export interface NavigateRequest {
  screen?: ScreenCode | null
  lang?: Lang | null
}

export interface NavigateResponse {
  screen: ScreenCode
  reason: NavigationReason | null
  userName: string | null
  texts: Record<string, string>
  headers: Record<string, string>
  data: unknown
}

// ---- auth / user ----

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  userId: number
  email: string
  name: string
  admin: boolean
}

export interface SignupRequest {
  email: string
  password: string
  name: string
  departCd?: string | null
}

export interface UserResponse {
  userId: number
  email: string
  name: string
  departCd: string | null
  admin: boolean
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

export interface ErrorResponse {
  code: string
  message: string
  fieldErrors: FieldErrorDetail[] | null
}
