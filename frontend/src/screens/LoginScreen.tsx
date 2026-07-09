import { useState } from 'react'
import type { FormEvent } from 'react'
import { authApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'

/** 마지막 성공 테넌트 코드 기억용 localStorage 키 (이메일/비밀번호는 저장하지 않는다) */
const TENANT_CODE_STORAGE_KEY = 'wa.tenantCode'

function storedTenantCode(): string {
  try {
    return localStorage.getItem(TENANT_CODE_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

/** W001 로그인 */
export function LoginScreen() {
  const { t, navigate } = useApp()
  const [tenantCode, setTenantCode] = useState(storedTenantCode)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    const code = tenantCode.trim() //대문자 정규화는 서버 정책 — 프론트는 trim만
    try {
      await authApi.login({ tenantCode: code, email, password })
      try {
        localStorage.setItem(TENANT_CODE_STORAGE_KEY, code)
      } catch {
        //저장 실패(사생활 보호 모드 등)는 무시 — 로그인 자체에는 영향 없음
      }
      //로그인 성공 → 서버가 role별 홈 화면을 결정한다
      await navigate('W000')
    } catch (e) {
      //401(자격 증명)·429(RATE_LIMITED) 모두 서버가 조립한 단일 메시지를 그대로 표시
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="panel narrow">
      <h2>{t('LOGIN')}</h2>
      <form onSubmit={onSubmit}>
        <label>
          {t('TENANT_CODE')}
          <input
            type="text"
            value={tenantCode}
            onChange={(e) => setTenantCode(e.target.value)}
            autoComplete="organization"
            autoCapitalize="none"
            spellCheck={false}
            required
          />
          <span className="hint">{t('TENANT_CODE_HINT')}</span>
        </label>
        <label>
          {t('EMAIL')}
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          {t('PWD')}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button
          type="submit"
          className="primary"
          disabled={submitting || !tenantCode.trim() || !email || !password}
        >
          {t('LOGIN')}
        </button>
      </form>
    </div>
  )
}
