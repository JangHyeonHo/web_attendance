import { get, post, put } from './client'
import type {
  CheckRequest,
  CheckResponse,
  ConfirmRequest,
  LanguageEntry,
  LanguageUpsertRequest,
  Lang,
  LoginRequest,
  LoginResponse,
  MemberCreateRequest,
  MemberCreateResponse,
  MemberRoleUpdateRequest,
  MemberStatusUpdateRequest,
  MemberSummary,
  MonthlyResponse,
  NavigateRequest,
  NavigateResponse,
  StampResponse,
  StatusResponse,
  TenantBillingResponse,
  TenantBillingUpsertRequest,
  TenantCreateRequest,
  TenantCreateResponse,
  TenantProfileResponse,
  TenantProfileUpsertRequest,
  TenantStatusUpdateRequest,
  TenantSummary,
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

/** SYSTEM_ADMIN 전용 — 테넌트/기업/결제 */
export const systemTenantApi = {
  list: () => get<TenantSummary[]>('/api/v1/system/tenants'),
  detail: (tenantId: number) =>
    get<TenantSummary>(`/api/v1/system/tenants/${tenantId}`),
  create: (request: TenantCreateRequest) =>
    post<TenantCreateResponse>('/api/v1/system/tenants', request),
  updateStatus: (tenantId: number, request: TenantStatusUpdateRequest) =>
    put<TenantSummary>(`/api/v1/system/tenants/${tenantId}/status`, request),
  profile: (tenantId: number) =>
    get<TenantProfileResponse | null>(`/api/v1/system/tenants/${tenantId}/profile`),
  upsertProfile: (tenantId: number, request: TenantProfileUpsertRequest) =>
    put<TenantProfileResponse>(`/api/v1/system/tenants/${tenantId}/profile`, request),
  billing: (tenantId: number) =>
    get<TenantBillingResponse | null>(`/api/v1/system/tenants/${tenantId}/billing`),
  upsertBilling: (tenantId: number, request: TenantBillingUpsertRequest) =>
    put<TenantBillingResponse>(`/api/v1/system/tenants/${tenantId}/billing`, request),
}

/** TENANT_ADMIN 전용 — 멤버 (tenantId는 항상 서버 세션에서 — 파라미터로 보내지 않는다) */
export const tenantMemberApi = {
  list: () => get<MemberSummary[]>('/api/v1/tenant/members'),
  create: (request: MemberCreateRequest) =>
    post<MemberCreateResponse>('/api/v1/tenant/members', request),
  updateStatus: (userId: number, request: MemberStatusUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/status`, request),
  updateRole: (userId: number, request: MemberRoleUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/role`, request),
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
