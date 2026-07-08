import { useState } from 'react'
import type { FormEvent } from 'react'
import { authApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'

/** W001 로그인 */
export function LoginScreen() {
  const { t, navigate } = useApp()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await authApi.login({ email, password })
      //로그인 성공 → 서버가 홈 화면(관리자/출결)을 결정한다
      await navigate('W000')
    } catch (e) {
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
        <button type="submit" className="primary" disabled={submitting || !email || !password}>
          {t('LOGIN')}
        </button>
      </form>
    </div>
  )
}
