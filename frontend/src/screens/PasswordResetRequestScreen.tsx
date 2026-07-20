import { useState } from 'react'
import type { FormEvent } from 'react'
import { passwordApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { storedTenantCode } from './LoginScreen'

/**
 * W011 비밀번호 재설정 요청 — 공개 화면(email-onboarding §8.2).
 *
 * - 테넌트 스코프는 로그인(W001)과 동일 규칙: 서브도메인 접속이면 호스트가 확정(코드란 숨김),
 *   루트 접속이면 tenantCode 입력 필수.
 * - 응답은 계정 존재와 무관하게 202 통일(존재 비노출) — 성공 문구도 입력값과 무관하게 동일.
 */
export function PasswordResetRequestScreen() {
  const { t, navigate, hostTenantName } = useApp()
  const [tenantCode, setTenantCode] = useState(storedTenantCode)
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  //발송 전 확인 모달(#1) — 엔터/버튼은 즉시 발송하지 않고 이 모달을 연다
  const [confirmOpen, setConfirmOpen] = useState(false)

  //테넌트 서브도메인 접속: 호스트가 테넌트를 확정하므로 코드 입력란을 숨긴다(W001 패턴)
  const onTenantHost = hostTenantName !== null

  //제출(엔터/버튼) → 즉시 발송 대신 확인 모달을 연다(#1)
  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setConfirmOpen(true)
  }

  //확인 모달에서 [확인] → 실제 발송
  async function runSend() {
    setConfirmOpen(false)
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await passwordApi.resetRequest({
        tenantCode: onTenantHost ? null : tenantCode.trim(),
        email: email.trim(),
      })
      //202 통일 — 계정 존재와 무관하게 같은 완료 문구만 표시
      setSent(true)
    } catch (e) {
      //400 TENANT_CODE_REQUIRED/MISMATCH·429 RATE_LIMITED — 서버 메시지 그대로
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

  if (sent) {
    return (
      <div className="panel narrow center">
        <h2>{t('RESET_TITLE')}</h2>
        <p className="success" role="status">
          {t('RESET_SENT')}
        </p>
        <button className="primary" onClick={() => void navigate('W001')}>
          {t('LOGIN')}
        </button>
      </div>
    )
  }

  return (
    <div className="panel narrow">
      <h2>{t('RESET_TITLE')}</h2>
      {onTenantHost && <p className="tenant-badge login-host-tenant">{hostTenantName}</p>}
      <p className="muted">{t('RESET_DESC')}</p>
      <form onSubmit={onSubmit}>
        {!onTenantHost && (
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
            {fieldErrors.tenantCode && <span className="error">{fieldErrors.tenantCode}</span>}
          </label>
        )}
        <label>
          {t('EMAIL')}
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
          {fieldErrors.email && <span className="error">{fieldErrors.email}</span>}
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button
          type="submit"
          className="primary"
          disabled={submitting || (!onTenantHost && !tenantCode.trim()) || !email}
        >
          {t('RESET_SUBMIT')}
        </button>
      </form>
      <p className="center">
        <button type="button" className="link" onClick={() => void navigate('W001')}>
          {t('LOGIN')}
        </button>
      </p>

      {confirmOpen && (
        <Modal title={t('RESET_TITLE')} onClose={() => setConfirmOpen(false)}>
          <p className="center">{t('RESET_CONFIRM').replace('{email}', email.trim())}</p>
          <div className="btn-row">
            <button className="primary" onClick={() => void runSend()}>
              {t('CONFIRM')}
            </button>
            <button onClick={() => setConfirmOpen(false)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
