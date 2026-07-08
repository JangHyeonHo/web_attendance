import { useState } from 'react'
import type { FormEvent } from 'react'
import { userApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'

/** W003 회원가입 */
export function SignupScreen() {
  const { t, navigate } = useApp()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [departCd, setDepartCd] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await userApi.signup({ email, password, name, departCd: departCd || null })
      setDone(true)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        if (e.fieldErrors) {
          const byField: Record<string, string> = {}
          for (const fe of e.fieldErrors) {
            byField[fe.field] = fe.message
          }
          setFieldErrors(byField)
        }
      } else {
        setError(String(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div className="panel narrow center">
        <p>{t('SIGNUP_DONE')}</p>
        <button className="primary" onClick={() => void navigate('W001')}>
          {t('LOGIN')}
        </button>
      </div>
    )
  }

  return (
    <div className="panel narrow">
      <h2>{t('SIGNUP')}</h2>
      <form onSubmit={onSubmit}>
        <label>
          {t('EMAIL')}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          {fieldErrors.email && <span className="error">{fieldErrors.email}</span>}
        </label>
        <label>
          {t('PWD')}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
          {fieldErrors.password && <span className="error">{fieldErrors.password}</span>}
        </label>
        <label>
          {t('NAME')}
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} required />
          {fieldErrors.name && <span className="error">{fieldErrors.name}</span>}
        </label>
        <label>
          {t('DEPART')}
          <input type="text" value={departCd} onChange={(e) => setDepartCd(e.target.value)} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={submitting}>
          {t('SUBMIT')}
        </button>
      </form>
    </div>
  )
}
