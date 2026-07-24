import { useCallback, useEffect, useState } from 'react'
import { tenantBillingApi, tenantProfileApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import { EmptyState } from '../components/EmptyState'
import { InvoiceDocument } from '../components/InvoiceDocument'
import { ScreenGuide } from '../components/ScreenGuide'
import type { InvoiceEntry, TenantProfileResponse } from '../api/types'

/**
 * T006 청구서 — 회사 총관리자(TENANT_ADMIN) 전용.
 * 결제 정보 등록(#14) + 달 선택(드롭다운) + 선택한 달의 상세(인원·단가·공급가·부가세·합계).
 */
export function BillingScreen() {
  const { t, tenantName, navigate } = useApp()
  const [rows, setRows] = useState<InvoiceEntry[]>([])
  const [selectedYm, setSelectedYm] = useState<string | null>(null)
  const [profile, setProfile] = useState<TenantProfileResponse | null>(null)
  const [profileLoaded, setProfileLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const data = await tenantBillingApi.invoices()
      setRows(data)
      setSelectedYm((cur) => (cur && data.some((r) => r.ym === cur) ? cur : data[0]?.ym ?? null))
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  //받는 곳(청구서)에 표기할 회사 정보 — 미등록이면 서버가 200 빈 응답(null) → 회사명만 표기 + 입력 안내
  useEffect(() => {
    tenantProfileApi
      .get()
      .then(setProfile)
      .catch(() => setProfile(null))
      .finally(() => setProfileLoaded(true))
  }, [])

  const selected = rows.find((r) => r.ym === selectedYm) ?? null

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
        <div className="toolbar-actions">
          <button onClick={() => void reload()}>{t('REFRESH')}</button>
          {selected && <button onClick={() => window.print()}>{t('PRINT_INVOICE')}</button>}
        </div>
      </div>

      <ScreenGuide>{t('SCREEN_GUIDE')}</ScreenGuide>
      {/* 요금 계산 규칙 안내(무료 인원·일할 계산) — 화면 가이드와 별도 존치 */}
      <p className="muted">{t('NOTE')}</p>
      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 && !error ? (
        <EmptyState>{t('EMPTY')}</EmptyState>
      ) : (
        <>
          {/* 달 선택 — 드롭다운(글자 안 깨지게). 마감월은 (확정) 표기 */}
          <div className="bill-month-pick">
            <span className="muted">{t('BILL_MONTH')}</span>
            <SelectField
              value={selectedYm ?? ''}
              ariaLabel={t('BILL_MONTH')}
              options={rows.map((r) => ({
                value: r.ym,
                label: `${r.ym} ${r.status === 'ISSUED' ? `(${t('BILL_ISSUED')})` : `(${t('BILL_PROVISIONAL')})`}`,
              }))}
              onChange={(v) => setSelectedYm(v)}
            />
          </div>

          {/* 회사 정보 미등록 안내 — 청구월과 청구서 사이. 등록 화면(T007)으로 바로 이동 */}
          {selected && profileLoaded && !profile && (
            <div className="inv-profile-warn" role="alert">
              <span>{t('INVOICE_PROFILE_MISSING')}</span>
              <button type="button" onClick={() => void navigate('T007')}>
                {t('COMPANY_INFO')}
              </button>
            </div>
          )}

          {selected && (
            <div className="printable">
              <InvoiceDocument invoice={selected} tenantName={tenantName} profile={profile} t={t} />
            </div>
          )}
        </>
      )}
    </div>
  )
}
