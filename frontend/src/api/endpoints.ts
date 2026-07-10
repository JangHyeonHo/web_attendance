import { del, get, post, put } from './client'
import type {
  CheckRequest,
  CheckResponse,
  ConfirmRequest,
  HolidayCreateRequest,
  HolidayEntry,
  HolidaySyncResult,
  HolidayUpdateRequest,
  InviteResponse,
  LanguageEntry,
  LanguageUpsertRequest,
  Lang,
  LoginRequest,
  LoginResponse,
  MailTemplatePreviewRequest,
  MailTemplatePreviewResponse,
  MailTemplateResponse,
  TenantMailTemplateResponse,
  MailTemplateUpdateRequest,
  MemberCreateRequest,
  MemberCreateResponse,
  MemberRoleUpdateRequest,
  MemberScheduleUpdateRequest,
  MemberStatusUpdateRequest,
  MemberSummary,
  MonthlyResponse,
  NavigateRequest,
  NavigateResponse,
  PasswordResetRequest,
  PasswordSetRequest,
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
  TokenPurpose,
  TokenVerifyRequest,
  TokenVerifyResponse,
  UiThemeResponse,
  UiThemeUpdateRequest,
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

/** 공개 — 비밀번호 설정/재설정(W010/W011). 토큰은 항상 바디로만 보낸다(URL 유출 방지) */
export const passwordApi = {
  verify: (request: TokenVerifyRequest) =>
    post<TokenVerifyResponse>('/api/v1/auth/password/verify', request),
  set: (request: PasswordSetRequest) => post<void>('/api/v1/auth/password', request),
  /** 202 통일(계정 존재와 무관 — 존재 비노출) */
  resetRequest: (request: PasswordResetRequest) =>
    post<void>('/api/v1/auth/password/reset-request', request),
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
  /** 최초 관리자(PENDING) 초대 재발송 — mailSent=false·미수신 수습 */
  adminInvite: (tenantId: number) =>
    post<InviteResponse>(`/api/v1/system/tenants/${tenantId}/admin-invite`),
  profile: (tenantId: number) =>
    get<TenantProfileResponse | null>(`/api/v1/system/tenants/${tenantId}/profile`),
  upsertProfile: (tenantId: number, request: TenantProfileUpsertRequest) =>
    put<TenantProfileResponse>(`/api/v1/system/tenants/${tenantId}/profile`, request),
  billing: (tenantId: number) =>
    get<TenantBillingResponse | null>(`/api/v1/system/tenants/${tenantId}/billing`),
  upsertBilling: (tenantId: number, request: TenantBillingUpsertRequest) =>
    put<TenantBillingResponse>(`/api/v1/system/tenants/${tenantId}/billing`, request),
}

/** SYSTEM_ADMIN 전용 — 시스템 전역 UI 테마(W004). 확정 테마의 배포는 navigation 응답이 담당 */
export const adminUiThemeApi = {
  get: () => get<UiThemeResponse>('/api/v1/admin/ui-theme'),
  update: (request: UiThemeUpdateRequest) =>
    put<UiThemeResponse>('/api/v1/admin/ui-theme', request),
}

/** SYSTEM_ADMIN 전용 — 메일 템플릿(W012, 글로벌 제품 자산) */
export const mailTemplateApi = {
  list: () => get<MailTemplateResponse[]>('/api/v1/admin/mail-templates'),
  update: (purpose: TokenPurpose, lang: Lang, request: MailTemplateUpdateRequest) =>
    put<MailTemplateResponse>(`/api/v1/admin/mail-templates/${purpose}/${lang}`, request),
  preview: (request: MailTemplatePreviewRequest) =>
    post<MailTemplatePreviewResponse>('/api/v1/admin/mail-templates/preview', request),
}

/** TENANT_ADMIN 전용 — 회사별 메일 템플릿 오버라이드(W014, 없으면 기본 템플릿 폴백) */
export const tenantMailTemplateApi = {
  list: () => get<TenantMailTemplateResponse[]>('/api/v1/tenant/mail-templates'),
  update: (purpose: TokenPurpose, lang: Lang, request: MailTemplateUpdateRequest) =>
    put<TenantMailTemplateResponse>(`/api/v1/tenant/mail-templates/${purpose}/${lang}`, request),
  revert: (purpose: TokenPurpose, lang: Lang) =>
    del(`/api/v1/tenant/mail-templates/${purpose}/${lang}`),
  preview: (request: MailTemplatePreviewRequest) =>
    post<MailTemplatePreviewResponse>('/api/v1/tenant/mail-templates/preview', request),
}

/** TENANT_ADMIN 전용 — 멤버 (tenantId는 항상 서버 세션에서 — 파라미터로 보내지 않는다) */
export const tenantMemberApi = {
  list: () => get<MemberSummary[]>('/api/v1/tenant/members'),
  create: (request: MemberCreateRequest) =>
    post<MemberCreateResponse>('/api/v1/tenant/members', request),
  /** 초대 재발송 — 구 토큰 무효 + 신규 발급(PENDING 대상 한정) */
  invite: (userId: number) =>
    post<InviteResponse>(`/api/v1/tenant/members/${userId}/invite`),
  /** 소프트 삭제 — 출결 기록 보존, 오송신 수습 경로 */
  remove: (userId: number) => del<void>(`/api/v1/tenant/members/${userId}`),
  updateStatus: (userId: number, request: MemberStatusUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/status`, request),
  updateRole: (userId: number, request: MemberRoleUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/role`, request),
  updateSchedule: (userId: number, request: MemberScheduleUpdateRequest) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/schedule`, request),
}

/** TENANT_ADMIN 전용 — 공휴일(W013). 식별자는 날짜 자체(yyyy-MM-dd) */
export const tenantHolidayApi = {
  list: (year: number) => get<HolidayEntry[]>(`/api/v1/tenant/holidays?year=${year}`),
  sync: (year: number) =>
    post<HolidaySyncResult>(`/api/v1/tenant/holidays/sync?year=${year}`),
  create: (request: HolidayCreateRequest) =>
    post<HolidayEntry>('/api/v1/tenant/holidays', request),
  update: (holidayDate: string, request: HolidayUpdateRequest) =>
    put<HolidayEntry>(`/api/v1/tenant/holidays/${holidayDate}`, request),
  remove: (holidayDate: string) => del<void>(`/api/v1/tenant/holidays/${holidayDate}`),
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
