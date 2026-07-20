import type { ErrorResponse } from './types'

/**
 * 백엔드 에러 응답을 담는 예외.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: ErrorResponse['fieldErrors']

  constructor(status: number, body: ErrorResponse) {
    super(body.message)
    this.status = status
    this.code = body.code
    this.fieldErrors = body.fieldErrors
  }
}

/** 401(세션 만료/미로그인) 발생시 호출되는 훅. AppContext가 로그인 화면 전개를 등록한다. */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    //CSRF 심층방어: 서버가 상태변경 요청에 요구하는 비단순 헤더. 모든 요청에 부착(교차출처 위조 차단).
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.headers as Record<string, string> | undefined),
    },
  })
  if (response.status === 204) {
    return undefined as T
  }
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new ApiError(response.status, (body as ErrorResponse) ?? {
      code: 'UNKNOWN',
      message: `HTTP ${response.status}`,
      fieldErrors: null,
    })
    //429(RATE_LIMITED) 포함 — 서버가 세션 언어로 조립한 message를 그대로 담아 화면이 표시한다
    //로그인 API 자체의 401(자격 증명 오류)은 화면 전환 대상이 아니다
    if (response.status === 401 && !path.includes('/auth/login') && onUnauthorized) {
      onUnauthorized()
    }
    throw error
  }
  return body as T
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path)
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}
