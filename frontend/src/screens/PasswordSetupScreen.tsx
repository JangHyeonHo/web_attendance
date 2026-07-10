import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { passwordApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { TokenVerifyResponse } from '../api/types'

/**
 * W010 비밀번호 설정 — 공개 화면(초대 INVITE / 재설정 RESET 공용, email-onboarding §8.2).
 *
 * - 토큰은 AppContext가 기동 시 URL에서 캡처해 메모리(ref)로만 전달한다.
 *   이 화면은 진입 즉시 verify하고, 완료/이탈 시 토큰과 비밀번호 입력을 즉시 폐기한다.
 * - verify/set의 404(부존재=만료=사용 완료 단일 코드)는 무효 안내 + 재설정 요청(W011) 유도.
 */
export function PasswordSetupScreen() {
  const { t, navigate, getPasswordToken, clearPasswordToken } = useApp()
  //토큰은 마운트 시점에 1회 취득(ref가 이후 클리어돼도 설정 플로우는 이어진다)
  const [token] = useState<string | null>(getPasswordToken)
  const [info, setInfo] = useState<TokenVerifyResponse | null>(null)
  const [invalid, setInvalid] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [mismatch, setMismatch] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  //화면 이탈 시 토큰 즉시 폐기(민감 입력 클리어 정책)
  useEffect(() => clearPasswordToken, [clearPasswordToken])

  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    setVerifying(true)
    passwordApi
      .verify({ token })
      .then((response) => {
        if (!cancelled) setInfo(response)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) {
          setInvalid(true)
        } else {
          //429(RATE_LIMITED)·네트워크 등 — 서버 메시지 그대로 표시
          setError(e instanceof ApiError ? e.message : String(e))
        }
      })
      .finally(() => {
        if (!cancelled) setVerifying(false)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    setError(null)
    setFieldErrors({})
    if (password !== passwordConfirm) {
      setMismatch(true)
      return
    }
    setMismatch(false)
    setSubmitting(true)
    try {
      await passwordApi.set({ token, password })
      //완료 — 토큰·비밀번호 입력을 즉시 폐기(어디에도 남기지 않는다)
      clearPasswordToken()
      setPassword('')
      setPasswordConfirm('')
      setDone(true)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setInvalid(true)
      } else if (e instanceof ApiError) {
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

  //완료 패널 — 로그인 유도
  if (done) {
    return (
      <div className="panel narrow center">
        <h2>{t('SETUP_TITLE')}</h2>
        <p className="success" role="status">
          {t('SETUP_DONE')}
        </p>
        <button className="primary" onClick={() => void navigate('W001')}>
          {t('LOGIN')}
        </button>
      </div>
    )
  }

  //토큰 없음(직접 전개) 또는 무효/만료 — 재설정 요청으로 유도
  if (!token || invalid) {
    return (
      <div className="panel narrow center">
        <h2>{t('SETUP_TITLE')}</h2>
        <p className="error" role="alert">
          {t('TOKEN_INVALID_DESC')}
        </p>
        <button className="primary" onClick={() => void navigate('W011')}>
          {t('GO_RESET')}
        </button>
      </div>
    )
  }

  return (
    <div className="panel narrow">
      <h2>{t('SETUP_TITLE')}</h2>
      {verifying && <p className="muted center">{t('LOADING')}</p>}
      {info && (
        <>
          <p className="muted">
            {info.purpose === 'INVITE' ? t('SETUP_INVITE_DESC') : t('SETUP_RESET_DESC')}
          </p>
          <dl className="kv">
            <dt>{t('NAME')}</dt>
            <dd>{info.name}</dd>
            <dt>{t('TENANT_NAME')}</dt>
            <dd>{info.tenantName}</dd>
            <dt>{t('EMAIL')}</dt>
            <dd>{info.emailMasked}</dd>
            <dt>{t('EXPIRES_AT')}</dt>
            <dd>{info.expiresAt.replace('T', ' ').slice(0, 16)}</dd>
          </dl>
          <form onSubmit={onSubmit}>
            <label>
              {t('NEW_PWD')}
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
              {t('NEW_PWD_CONFIRM')}
              <input
                type="password"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                autoComplete="new-password"
                required
              />
              {mismatch && <span className="error">{t('PWD_MISMATCH')}</span>}
            </label>
            {error && <p className="error" role="alert">{error}</p>}
            <button
              type="submit"
              className="primary"
              disabled={submitting || !password || !passwordConfirm}
            >
              {t('SUBMIT')}
            </button>
          </form>
        </>
      )}
      {!info && !verifying && error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
