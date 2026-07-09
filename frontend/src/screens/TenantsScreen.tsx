import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { systemTenantApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { TenantDetailScreen } from './TenantDetailScreen'
import type { TenantCreateResponse, TenantStatus, TenantSummary } from '../api/types'

const STATUS_LABEL_KEYS: Record<TenantStatus, string> = {
  ACTIVE: 'TENANT_STATUS_ACTIVE',
  SUSPENDED: 'TENANT_STATUS_SUSPENDED',
}

/**
 * W007 테넌트 관리 — SYSTEM_ADMIN 전용.
 * 목록/생성/정지·재개. 행의 상세 버튼으로 W008(기업/결제)을 임베드 전개한다(W005→W006 패턴).
 */
export function TenantsScreen() {
  const { t } = useApp()
  const [tenants, setTenants] = useState<TenantSummary[]>([])
  const [listError, setListError] = useState<string | null>(null)

  //생성 폼
  const [tenantCode, setTenantCode] = useState('')
  const [name, setName] = useState('')
  const [adminEmail, setAdminEmail] = useState('')
  const [adminName, setAdminName] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  /** 초기 비밀번호 1회 표시 패널 — 닫으면 상태에서 즉시 폐기(어디에도 저장하지 않는다) */
  const [created, setCreated] = useState<TenantCreateResponse | null>(null)
  const passwordRef = useRef<HTMLElement | null>(null)

  //행 조작
  const [openDetailId, setOpenDetailId] = useState<number | null>(null)
  /** 정지는 파괴적 조작 — 인라인 확인 패널 경유(window.confirm은 언어 마스터를 못 쓰므로 미사용) */
  const [confirmSuspendId, setConfirmSuspendId] = useState<number | null>(null)
  const [rowError, setRowError] = useState<{ tenantId: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      setTenants(await systemTenantApi.list())
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function onCreate(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const response = await systemTenantApi.create({
        tenantCode: tenantCode.trim(),
        name: name.trim(),
        adminEmail: adminEmail.trim(),
        adminName: adminName.trim(),
      })
      setCreated(response)
      setTenantCode('')
      setName('')
      setAdminEmail('')
      setAdminName('')
      await reload()
    } catch (e) {
      if (e instanceof ApiError) {
        setFormError(e.message)
        if (e.fieldErrors) {
          const byField: Record<string, string> = {}
          for (const fe of e.fieldErrors) {
            byField[fe.field] = fe.message
          }
          setFieldErrors(byField)
        }
      } else {
        setFormError(String(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function copyPassword(text: string) {
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      //클립보드 실패시 텍스트 선택 폴백(사용자가 직접 복사)
      const node = passwordRef.current
      if (node) {
        const range = document.createRange()
        range.selectNodeContents(node)
        const selection = window.getSelection()
        selection?.removeAllRanges()
        selection?.addRange(range)
      }
    }
  }

  async function updateStatus(tenantId: number, status: TenantStatus) {
    setRowError(null)
    setConfirmSuspendId(null)
    try {
      await systemTenantApi.updateStatus(tenantId, { status })
      await reload()
    } catch (e) {
      setRowError({ tenantId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  return (
    <div className="panel">
      <h2>{t('TENANTS_TITLE')}</h2>

      <form className="inline-form" onSubmit={onCreate}>
        <label>
          {t('TENANT_CODE')}
          <input
            value={tenantCode}
            onChange={(e) => setTenantCode(e.target.value)}
            autoCapitalize="none"
            spellCheck={false}
            required
          />
          {fieldErrors.tenantCode && <span className="error">{fieldErrors.tenantCode}</span>}
        </label>
        <label>
          {t('TENANT_NAME')}
          <input value={name} onChange={(e) => setName(e.target.value)} required />
          {fieldErrors.name && <span className="error">{fieldErrors.name}</span>}
        </label>
        <label>
          {t('ADMIN_EMAIL')}
          <input
            type="email"
            value={adminEmail}
            onChange={(e) => setAdminEmail(e.target.value)}
            required
          />
          {fieldErrors.adminEmail && <span className="error">{fieldErrors.adminEmail}</span>}
        </label>
        <label>
          {t('ADMIN_NAME')}
          <input value={adminName} onChange={(e) => setAdminName(e.target.value)} required />
          {fieldErrors.adminName && <span className="error">{fieldErrors.adminName}</span>}
        </label>
        <button type="submit" className="primary" disabled={submitting}>
          {t('TENANT_CREATE')}
        </button>
      </form>
      {formError && <p className="error" role="alert">{formError}</p>}

      {created && (
        <div className="stamp-box initial-pwd" role="status">
          <dl className="kv">
            <dt>{t('TENANT_CODE')}</dt>
            <dd>{created.tenantCode}</dd>
            <dt>{t('ADMIN_EMAIL')}</dt>
            <dd>{created.adminEmail}</dd>
            <dt>{t('INITIAL_PWD')}</dt>
            <dd>
              <code ref={passwordRef}>{created.initialPassword}</code>
            </dd>
          </dl>
          <p className="muted">{t('INITIAL_PWD_NOTE')}</p>
          <div className="btn-row">
            <button onClick={() => void copyPassword(created.initialPassword)}>{t('COPY')}</button>
            <button onClick={() => setCreated(null)}>{t('CLOSE')}</button>
          </div>
        </div>
      )}

      {listError && <p className="error" role="alert">{listError}</p>}

      <table className="detail-table">
        <thead>
          <tr>
            <th>{t('TENANT_CODE')}</th>
            <th>{t('TENANT_NAME')}</th>
            <th>{t('STATUS')}</th>
            <th>{t('MEMBER_COUNT')}</th>
            <th>{t('CREATED_AT')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {tenants.map((tenant) => (
            <Fragment key={tenant.tenantId}>
              <tr>
                <td>{tenant.tenantCode}</td>
                <td>{tenant.name}</td>
                <td>{t(STATUS_LABEL_KEYS[tenant.status])}</td>
                <td>{tenant.memberCount}</td>
                <td>{tenant.createdAt.slice(0, 10)}</td>
                <td>
                  <div className="row-actions">
                    <button
                      onClick={() =>
                        setOpenDetailId((current) =>
                          current === tenant.tenantId ? null : tenant.tenantId,
                        )
                      }
                    >
                      {t('DETAIL')}
                    </button>
                    {tenant.status === 'ACTIVE' ? (
                      <button onClick={() => setConfirmSuspendId(tenant.tenantId)}>
                        {t('SUSPEND')}
                      </button>
                    ) : (
                      <button onClick={() => void updateStatus(tenant.tenantId, 'ACTIVE')}>
                        {t('RESUME')}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
              {confirmSuspendId === tenant.tenantId && (
                <tr>
                  <td colSpan={6}>
                    <div className="stamp-box confirm" role="alertdialog">
                      <p>
                        {tenant.name} — {t('SUSPEND')}
                      </p>
                      <div className="btn-row">
                        <button
                          className="primary"
                          onClick={() => void updateStatus(tenant.tenantId, 'SUSPENDED')}
                        >
                          {t('SUBMIT')}
                        </button>
                        <button onClick={() => setConfirmSuspendId(null)}>{t('CANCEL')}</button>
                      </div>
                    </div>
                  </td>
                </tr>
              )}
              {rowError?.tenantId === tenant.tenantId && (
                <tr>
                  <td colSpan={6}>
                    <p className="error" role="alert">
                      {rowError.message}
                    </p>
                  </td>
                </tr>
              )}
              {openDetailId === tenant.tenantId && (
                <tr>
                  <td colSpan={6} className="embed-cell">
                    <TenantDetailScreen tenantId={tenant.tenantId} />
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  )
}
