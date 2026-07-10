import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { systemTenantApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { TenantDetailScreen } from './TenantDetailScreen'
import type { ProfileCountry, TenantCreateResponse, TenantStatus, TenantSummary } from '../api/types'

const STATUS_LABEL_KEYS: Record<TenantStatus, string> = {
  ACTIVE: 'TENANT_STATUS_ACTIVE',
  SUSPENDED: 'TENANT_STATUS_SUSPENDED',
}

const COUNTRY_LABEL_KEYS: Record<ProfileCountry, string> = {
  KR: 'COUNTRY_KR',
  JP: 'COUNTRY_JP',
}

/**
 * W007 테넌트 관리 — SYSTEM_ADMIN 전용.
 * 목록/생성(소재국 필수)/정지·재개/관리자 초대 재발송.
 * 생성은 초대 플로우(CR3-5): initialPassword 없음 — 관리자 초대 메일 발송(mailSent)과
 * 당해·익년 공휴일 동기화(holidaysSynced) 결과를 결과 모달에 표시한다.
 * Phase 4: 생성 폼·정지 확인·생성 결과를 모달로 이전(테이블 내 확장 행 폐지).
 * 행의 상세 버튼으로 W008(기업/결제)을 임베드 전개한다(W005→W006 패턴).
 */
export function TenantsScreen() {
  const { t } = useApp()
  const [tenants, setTenants] = useState<TenantSummary[]>([])
  const [listError, setListError] = useState<string | null>(null)

  //생성 모달
  const [formOpen, setFormOpen] = useState(false)
  const [tenantCode, setTenantCode] = useState('')
  const [name, setName] = useState('')
  const [country, setCountry] = useState<ProfileCountry>('KR')
  const [adminEmail, setAdminEmail] = useState('')
  const [adminName, setAdminName] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  /** 생성 결과 모달 — 초대 발송/공휴일 동기화 결과 표시(초기 비밀번호 패널은 폐지) */
  const [created, setCreated] = useState<TenantCreateResponse | null>(null)

  //행 조작
  const [openDetailId, setOpenDetailId] = useState<number | null>(null)
  /** 정지는 파괴적 조작 — 확인 모달 경유(window.confirm은 언어 마스터를 못 쓰므로 미사용) */
  const [confirmSuspend, setConfirmSuspend] = useState<TenantSummary | null>(null)
  const [rowError, setRowError] = useState<{ tenantId: number; message: string } | null>(null)
  /** 관리자 초대 재발송 성공 안내(행 아래 인라인) */
  const [rowNotice, setRowNotice] = useState<{ tenantId: number; message: string } | null>(null)

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
        country,
        adminEmail: adminEmail.trim(),
        adminName: adminName.trim(),
      })
      setFormOpen(false)
      setCreated(response)
      setTenantCode('')
      setName('')
      setCountry('KR')
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

  async function updateStatus(tenantId: number, status: TenantStatus) {
    setRowError(null)
    setRowNotice(null)
    setConfirmSuspend(null)
    try {
      await systemTenantApi.updateStatus(tenantId, { status })
      await reload()
    } catch (e) {
      setRowError({ tenantId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  /** 관리자 초대 재발송 — mailSent=false·미수신 수습(즉시 실행, 파괴적 조작 아님) */
  async function resendAdminInvite(tenantId: number) {
    setRowError(null)
    setRowNotice(null)
    try {
      const response = await systemTenantApi.adminInvite(tenantId)
      if (response.mailSent) {
        setRowNotice({ tenantId, message: `${response.email} — ${t('ADMIN_INVITE_SENT')}` })
      } else {
        setRowError({ tenantId, message: t('MAIL_FAILED') })
      }
    } catch (e) {
      //409 TENANT_ADMIN_INVITE_INVALID(재발송할 PENDING 관리자 없음) 등 — 서버 메시지 그대로
      setRowError({ tenantId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TENANTS_TITLE')}</h2>
        <div className="toolbar-actions">
          <button className="primary" onClick={() => setFormOpen(true)} disabled={submitting}>
            {t('TENANT_CREATE')}
          </button>
        </div>
      </div>

      {listError && <p className="error" role="alert">{listError}</p>}

      {formOpen && (
        <Modal title={t('TENANT_CREATE')} onClose={() => setFormOpen(false)}>
          <form onSubmit={onCreate}>
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
              {/* 소재국 — 필수(공휴일 동기화·초대 메일 언어 결정, CR3-1) */}
              {t('COUNTRY')}
              <select value={country} onChange={(e) => setCountry(e.target.value as ProfileCountry)}>
                <option value="KR">{t('COUNTRY_KR')}</option>
                <option value="JP">{t('COUNTRY_JP')}</option>
              </select>
              {fieldErrors.country && <span className="error">{fieldErrors.country}</span>}
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
            {formError && <p className="error" role="alert">{formError}</p>}
            <button type="submit" className="primary" disabled={submitting}>
              {t('TENANT_CREATE')}
            </button>
          </form>
        </Modal>
      )}

      {created && (
        <Modal title={t('TENANT_CREATE')} onClose={() => setCreated(null)}>
          <dl className="kv">
            <dt>{t('TENANT_CODE')}</dt>
            <dd>{created.tenantCode}</dd>
            <dt>{t('COUNTRY')}</dt>
            <dd>{t(COUNTRY_LABEL_KEYS[created.country])}</dd>
            <dt>{t('ADMIN_EMAIL')}</dt>
            <dd>{created.adminEmail}</dd>
          </dl>
          {/* 초대 메일(소재국 언어) 발송 결과 — 실패 시 [관리자 초대 재발송]이 수습 경로 */}
          {created.mailSent ? (
            <p className="success">{t('ADMIN_INVITE_SENT')}</p>
          ) : (
            <p className="error">{t('MAIL_FAILED')}</p>
          )}
          {/* 공휴일 자동 동기화 실패 — 고객사 관리자의 W013 수동 동기화 안내 */}
          {!created.holidaysSynced && <p className="error">{t('HOLIDAY_SYNC_FAILED_NOTICE')}</p>}
          <div className="btn-row">
            <button onClick={() => setCreated(null)}>{t('CLOSE')}</button>
          </div>
        </Modal>
      )}

      {confirmSuspend && (
        <Modal title={t('SUSPEND')} onClose={() => setConfirmSuspend(null)} danger>
          <p className="center">
            {confirmSuspend.name} — {t('SUSPEND')}
          </p>
          <div className="btn-row">
            <button
              className="primary"
              onClick={() => void updateStatus(confirmSuspend.tenantId, 'SUSPENDED')}
            >
              {t('SUBMIT')}
            </button>
            <button onClick={() => setConfirmSuspend(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      <div className="table-wrap">
        <table className="detail-table">
          <thead>
            <tr>
              <th>{t('TENANT_CODE')}</th>
              <th>{t('TENANT_NAME')}</th>
              <th>{t('COUNTRY')}</th>
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
                  <td>{t(COUNTRY_LABEL_KEYS[tenant.country])}</td>
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
                      <button onClick={() => void resendAdminInvite(tenant.tenantId)}>
                        {t('ADMIN_INVITE_RESEND')}
                      </button>
                      {tenant.status === 'ACTIVE' ? (
                        <button onClick={() => setConfirmSuspend(tenant)}>{t('SUSPEND')}</button>
                      ) : (
                        <button onClick={() => void updateStatus(tenant.tenantId, 'ACTIVE')}>
                          {t('RESUME')}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
                {rowNotice?.tenantId === tenant.tenantId && (
                  <tr>
                    <td colSpan={7} className="row-note">
                      <p className="success" role="status">
                        {rowNotice.message}
                      </p>
                    </td>
                  </tr>
                )}
                {rowError?.tenantId === tenant.tenantId && (
                  <tr>
                    <td colSpan={7} className="row-note">
                      <p className="error" role="alert">
                        {rowError.message}
                      </p>
                    </td>
                  </tr>
                )}
                {openDetailId === tenant.tenantId && (
                  <tr>
                    <td colSpan={7} className="embed-cell">
                      <TenantDetailScreen tenantId={tenant.tenantId} country={tenant.country} />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
