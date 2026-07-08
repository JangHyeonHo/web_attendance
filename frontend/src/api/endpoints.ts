import { get, post } from './client'
import type {
  CheckRequest,
  CheckResponse,
  ConfirmRequest,
  LanguageEntry,
  LanguageUpsertRequest,
  Lang,
  LoginRequest,
  LoginResponse,
  MonthlyResponse,
  NavigateRequest,
  NavigateResponse,
  SignupRequest,
  StampResponse,
  StatusResponse,
  UserResponse,
} from './types'

/** 서버 주도 화면 전개 */
export const navigationApi = {
  navigate: (request: NavigateRequest) => post<NavigateResponse>('/api/v1/navigation', request),
}

export const authApi = {
  login: (request: LoginRequest) => post<LoginResponse>('/api/v1/auth/login', request),
  logout: () => post<void>('/api/v1/auth/logout'),
  me: () => get<LoginResponse>('/api/v1/auth/me'),
}

export const userApi = {
  signup: (request: SignupRequest) => post<UserResponse>('/api/v1/users', request),
}

export const attendanceApi = {
  status: () => get<StatusResponse>('/api/v1/attendance/status'),
  check: (request: CheckRequest) => post<CheckResponse>('/api/v1/attendance/check', request),
  confirm: (request: ConfirmRequest) => post<StampResponse>('/api/v1/attendance', request),
  monthly: (year: number, month: number) =>
    get<MonthlyResponse>(`/api/v1/attendance/monthly?year=${year}&month=${month}`),
}

export const languageApi = {
  texts: (windowId: string, lang: Lang) =>
    get<Record<string, string>>(`/api/v1/i18n/${encodeURIComponent(windowId)}?lang=${lang}`),
  adminList: (windowId?: string, lang?: Lang) => {
    const params = new URLSearchParams()
    if (windowId) params.set('windowId', windowId)
    if (lang) params.set('lang', lang)
    const query = params.toString()
    return get<LanguageEntry[]>(`/api/v1/admin/i18n${query ? `?${query}` : ''}`)
  },
  adminUpsert: (request: LanguageUpsertRequest) => post<void>('/api/v1/admin/i18n', request),
}
