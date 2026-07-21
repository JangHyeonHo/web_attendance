import { del, get, post, put } from './client'
import type {
  AuditLogEntry,
  CheckRequest,
  CheckResponse,
  ConfirmRequest,
  DailyResponse,
  ManualBreakRequest,
  ManualStampRequest,
  HolidayCreateRequest,
  HolidayEntry,
  HolidaySyncResult,
  HolidayUpdateRequest,
  InviteResponse,
  LanguageEntry,
  LanguageUpsertRequest,
  Lang,
  LeaveApplyRequest,
  LeaveBalance,
  LeaveBalanceRow,
  LeaveDecisionRequest,
  LeaveBulkGrantRequest,
  LeaveGrantRequest,
  LeaveRequestItem,
  LeaveType,
  LeaveTypeCreateRequest,
  LeaveTypeUpdateRequest,
  MemberLeaveDetail,
  MemberLeaveSummary,
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
  RotaCell,
  RotaSaveRequest,
  PatternResponse,
  PatternSaveRequest,
  EffectiveDay,
  NavigateRequest,
  NavigateResponse,
  InvoiceEntry,
  BillingProfileResponse,
  BillingProfileRequest,
  ContractSummaryResponse,
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
  ReportSetting,
  ReportSettingUpdateRequest,
  PayrollResponse,
  CloseStatusResponse,
  PendingCloseResponse,
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
  //회사별 청구서 조회·마감(운영사)
  invoices: (tenantId: number) =>
    get<InvoiceEntry[]>(`/api/v1/system/tenants/${tenantId}/invoices`),
  closeInvoice: (tenantId: number, ym: string) =>
    post<InvoiceEntry>(`/api/v1/system/tenants/${tenantId}/invoices/${ym}/close`),
}

/** 회사(TENANT_ADMIN) 전용 — 자사 월별 청구서 + 결제 정보 + 계약 요약(읽기전용, #14) */
export const tenantBillingApi = {
  invoices: () => get<InvoiceEntry[]>('/api/v1/tenant/billing/invoices'),
  profile: () => get<BillingProfileResponse>('/api/v1/tenant/billing/profile'),
  updateProfile: (request: BillingProfileRequest) =>
    put<BillingProfileResponse>('/api/v1/tenant/billing/profile', request),
  contract: () => get<ContractSummaryResponse>('/api/v1/tenant/billing/contract'),
}

/** 회사(TENANT_ADMIN) 전용 — 자사 사업자 정보 자율 관리(W019, #14) */
export const tenantProfileApi = {
  //미등록이면 서버가 200 빈 응답(null) — 404 콘솔 노이즈 없이 '미등록'을 표현
  get: () => get<TenantProfileResponse | null>('/api/v1/tenant/profile'),
  update: (request: TenantProfileUpsertRequest) =>
    put<TenantProfileResponse>('/api/v1/tenant/profile', request),
}

/** 회사(TENANT_ADMIN/HR_ADMIN) 회사 설정 — 결재란·가산 적용·도장 이미지/크기 */
export const tenantReportApi = {
  get: () => get<ReportSetting>('/api/v1/tenant/report-setting'),
  update: (request: ReportSettingUpdateRequest) =>
    put<ReportSetting>('/api/v1/tenant/report-setting', request),
  /** 도장 이미지 업로드(base64 data URL 허용 + MIME) — 서버가 크기·형식 검증 */
  uploadStamp: (imageBase64: string, mime: string) =>
    put<ReportSetting>('/api/v1/tenant/report-setting/stamp-image', { imageBase64, mime }),
  removeStamp: () => del<void>('/api/v1/tenant/report-setting/stamp-image'),
}

/** SYSTEM_ADMIN 전용 — 감사 로그 조회(W017). 전역 최신순 + category 필터 */
export const adminAuditApi = {
  list: (category?: string, limit = 100) => {
    const p = new URLSearchParams()
    if (category) p.set('category', category)
    p.set('limit', String(limit))
    return get<AuditLogEntry[]>(`/api/v1/admin/audit?${p.toString()}`)
  },
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
  /** 멤버 목록/검색 — 이름·이메일·부서 텍스트(q) */
  list: (params?: { q?: string }) => {
    const qs = new URLSearchParams()
    if (params?.q?.trim()) qs.set('q', params.q.trim())
    const s = qs.toString()
    return get<MemberSummary[]>(`/api/v1/tenant/members${s ? `?${s}` : ''}`)
  },
  /** 특정 날짜·시각 근무 중인 멤버(#6) — 실효 스케줄로 서버가 판정. date=YYYY-MM-DD, time=HH:mm */
  working: (date: string, time: string, q?: string) => {
    const qs = new URLSearchParams({ date, time })
    if (q?.trim()) qs.set('q', q.trim())
    return get<MemberSummary[]>(`/api/v1/tenant/members/working?${qs.toString()}`)
  },
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
  /** 월 기본급 수정(급여 정산 기준) — null이면 미입력으로 저장 */
  updateSalary: (userId: number, baseMonthlySalary: number | null) =>
    put<MemberSummary>(`/api/v1/tenant/members/${userId}/salary`, { baseMonthlySalary }),
}

/** 근태 마감 — 멤버 셀프(본인 월 마감 신청/취소/상태) */
export const attendanceCloseApi = {
  status: (year: number, month: number) =>
    get<CloseStatusResponse>(`/api/v1/attendance/close/status?year=${year}&month=${month}`),
  request: (year: number, month: number) =>
    post<CloseStatusResponse>('/api/v1/attendance/close', { year, month }),
  cancel: (year: number, month: number) =>
    del<CloseStatusResponse>(`/api/v1/attendance/close?year=${year}&month=${month}`),
}

/** 근태 마감 결재(HR_ADMIN/TENANT_ADMIN) — 대기 목록 + 승인/반려 + 급여 정산(참고, 관리자만)(W021) */
export const tenantCloseApi = {
  pending: () => get<PendingCloseResponse[]>('/api/v1/tenant/attendance-close/pending'),
  /** 마감 완료(선택 월) — '마감 취소' 대상. 승인 이력 전체가 아니라 대상 월만 */
  approved: (year: number, month: number) =>
    get<PendingCloseResponse[]>(`/api/v1/tenant/attendance-close/approved?year=${year}&month=${month}`),
  decide: (closeId: number, approve: boolean, note?: string) =>
    post<void>(`/api/v1/tenant/attendance-close/${closeId}/decision`, { approve, note }),
  /** 마감 취소 — 승인된 마감을 열린(REQUESTED) 상태로 되돌려 잠금 해제 */
  reopen: (closeId: number) =>
    post<void>(`/api/v1/tenant/attendance-close/${closeId}/reopen`, {}),
  /** 멤버 급여 정산(참고) — 관리자 전용. 마감 검토 시 확인 */
  payroll: (userId: number, year: number, month: number) =>
    get<PayrollResponse>(`/api/v1/tenant/attendance-close/${userId}/payroll?year=${year}&month=${month}`),
}

/** TENANT_ADMIN+HR_ADMIN — 월 로타(일자 오버라이드: 야간교대·휴무). #13 */
export const tenantScheduleApi = {
  /** 실효 스케줄(오버라이드>패턴>기본 적용) — 통합 화면 달력 표시용 */
  effective: (userId: number, year: number, month: number) =>
    get<EffectiveDay[]>(`/api/v1/tenant/schedule/${userId}/effective?year=${year}&month=${month}`),
  rota: (userId: number, year: number, month: number) =>
    get<RotaCell[]>(`/api/v1/tenant/schedule/${userId}/rota?year=${year}&month=${month}`),
  saveRota: (userId: number, request: RotaSaveRequest) =>
    put<void>(`/api/v1/tenant/schedule/${userId}/rota`, request),
  /** 반복 패턴(요일별 시간·N주 주기) — 없으면 null */
  pattern: (userId: number) =>
    get<PatternResponse | null>(`/api/v1/tenant/schedule/${userId}/pattern`),
  savePattern: (userId: number, request: PatternSaveRequest) =>
    put<void>(`/api/v1/tenant/schedule/${userId}/pattern`, request),
}

/** TENANT_ADMIN 전용 — 공휴일(W013). 읽기전용 목록 + 회사 공휴일 등록 + 국가 공휴일 동기화(#7) */
export const tenantHolidayApi = {
  list: (year: number) => get<HolidayEntry[]>(`/api/v1/tenant/holidays?year=${year}`),
  sync: (year: number) =>
    post<HolidaySyncResult>(`/api/v1/tenant/holidays/sync?year=${year}`),
  create: (request: HolidayCreateRequest) =>
    post<HolidayEntry>('/api/v1/tenant/holidays', request),
  /** 회사 공휴일만 수정(국가 공휴일은 서버가 차단, #8) — 날짜/명칭/반복 */
  update: (holidayId: number, request: HolidayUpdateRequest) =>
    put<HolidayEntry>(`/api/v1/tenant/holidays/${holidayId}`, request),
  /** 회사 공휴일만 삭제(국가 공휴일은 서버가 차단, #7) — 대리키 기준 */
  remove: (holidayId: number) => del<void>(`/api/v1/tenant/holidays/${holidayId}`),
}

export const attendanceApi = {
  status: () => get<StatusResponse>('/api/v1/attendance/status'),
  check: (request: CheckRequest) => post<CheckResponse>('/api/v1/attendance/check', request),
  confirm: (request: ConfirmRequest) => post<StampResponse>('/api/v1/attendance', request),
  monthly: (year: number, month: number) =>
    get<MonthlyResponse>(`/api/v1/attendance/monthly?year=${year}&month=${month}`),
  /** 근태 보고서 설정(결재란 표시) 조회 — 전 멤버(인쇄 시 결재란 판단) */
  reportSetting: () => get<ReportSetting>('/api/v1/attendance/report-setting'),
  /** 수동 정정 등록(사유 필수) — MANUAL로 기록되어 버튼 스탬프와 구분 */
  manual: (request: ManualStampRequest) =>
    post<StampResponse>('/api/v1/attendance/manual', request),
  /** 휴식 시간 정정 등록 — 시작·종료 쌍 */
  manualBreak: (request: ManualBreakRequest) =>
    post<StampResponse>('/api/v1/attendance/manual/break', request),
  /** 일자 스탬프 이력 — 중복 스탬프(출근 2번 등)·수동 정정 전부 포함 */
  daily: (date: string) => get<DailyResponse>(`/api/v1/attendance/daily?date=${date}`),
  /** 수동 정정 수정(잘못 입력 복구 — 시각/구분/사유 변경) — 본인 MANUAL 행만(자동 기록은 불변) */
  manualUpdate: (attendanceId: number, request: ManualStampRequest) =>
    put<StampResponse>(`/api/v1/attendance/manual/${attendanceId}`, request),
}

/** 멤버 휴가 — 본인 잔여·신청·취소 (/attendance/leave) */
export const leaveApi = {
  types: () => get<LeaveType[]>('/api/v1/attendance/leave/types'),
  balances: () => get<LeaveBalance[]>('/api/v1/attendance/leave/balances'),
  balanceRows: () => get<LeaveBalanceRow[]>('/api/v1/attendance/leave/balances/rows'),
  myRequests: () => get<LeaveRequestItem[]>('/api/v1/attendance/leave/requests'),
  apply: (request: LeaveApplyRequest) =>
    post<LeaveRequestItem>('/api/v1/attendance/leave/requests', request),
  cancel: (requestId: number) =>
    post<void>(`/api/v1/attendance/leave/requests/${requestId}/cancel`),
  requestCancel: (requestId: number, reason: string) =>
    post<void>(`/api/v1/attendance/leave/requests/${requestId}/cancel-request`, { reason }),
}

/** 관리자 휴가 — 종류·결재·부여·멤버 잔여 (/tenant/leave, 인사관리자+총관리자) */
export const tenantLeaveApi = {
  types: () => get<LeaveType[]>('/api/v1/tenant/leave/types'),
  createType: (request: LeaveTypeCreateRequest) =>
    post<LeaveType>('/api/v1/tenant/leave/types', request),
  updateType: (leaveTypeId: number, request: LeaveTypeUpdateRequest) =>
    put<LeaveType>(`/api/v1/tenant/leave/types/${leaveTypeId}`, request),
  pending: () => get<LeaveRequestItem[]>('/api/v1/tenant/leave/requests/pending'),
  decide: (requestId: number, request: LeaveDecisionRequest) =>
    post<void>(`/api/v1/tenant/leave/requests/${requestId}/decision`, request),
  cancelRequests: () =>
    get<LeaveRequestItem[]>('/api/v1/tenant/leave/requests/cancel-requests'),
  /** 현재/예정 휴가자(APPROVED) — 관리자 직접 취소용(#11) */
  approved: () =>
    get<LeaveRequestItem[]>('/api/v1/tenant/leave/requests/approved'),
  cancel: (requestId: number, reason: string) =>
    post<void>(`/api/v1/tenant/leave/requests/${requestId}/cancel`, { reason }),
  rejectCancel: (requestId: number, note: string) =>
    post<void>(`/api/v1/tenant/leave/requests/${requestId}/cancel-reject`, { note }),
  grant: (request: LeaveGrantRequest) => post<void>('/api/v1/tenant/leave/grants', request),
  grantBulk: (request: LeaveBulkGrantRequest) =>
    post<{ count: number }>('/api/v1/tenant/leave/grants/bulk', request),
  recompute: (userId: number) =>
    post<void>(`/api/v1/tenant/leave/members/${userId}/recompute`),
  recomputeAll: () => post<{ count: number }>('/api/v1/tenant/leave/recompute'),
  members: () => get<MemberLeaveSummary[]>('/api/v1/tenant/leave/members'),
  memberDetail: (userId: number) =>
    get<MemberLeaveDetail>(`/api/v1/tenant/leave/members/${userId}`),
  updateHireDate: (userId: number, hireDate: string) =>
    put<void>(`/api/v1/tenant/leave/members/${userId}/hire-date`, { hireDate }),
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
