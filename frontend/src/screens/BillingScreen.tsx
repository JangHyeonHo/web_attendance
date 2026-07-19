import { useCallback, useEffect, useState } from 'react'
import { tenantBillingApi, tenantProfileApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import { InvoiceDocument } from '../components/InvoiceDocument'
import type { InvoiceEntry, TenantProfileResponse } from '../api/types'

/**
 * W018 청구서 — 회사 총관리자(TENANT_ADMIN) 전용.
 * 결제 정보 등록(#14) + 달 선택(드롭다운) + 선택한 달의 상세(인원·단가·공급가·부가세·합계).
 */
export function BillingScreen() {
  const { t, tenantName } = useApp()
  const [rows, setRows] = useState<InvoiceEntry[]>([])
  const [selectedYm, setSelectedYm] = useState<string | null>(null)
  const [profile, setProfile] = useState<TenantProfileResponse | null>(null)
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

  //공급받는자(청구서)에 표기할 회사 정보 — 미등록(404)이면 회사명만 표기
  useEffect(() => {
    tenantProfileApi
      .get()
      .then(setProfile)
      .catch(() => setProfile(null))
  }, [])

  const selected = rows.find((r) => r.ym === selectedYm) ?? null

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
        <div className="toolbar-actions">
          <button onClick={() => void reload()}>{t('REFRESH')}</button>
          {selected && <button onClick={() => window.print()}>{t('PRINT')}</button>}
        </div>
      </div>

      {/* 결제/회사 정보 등록은 별도 '회사 정보' 화면으로 분리 예정(#14) — 청구서는 내역만 */}
      <p className="muted">{t('NOTE')}</p>
      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 && !error ? (
        <p className="muted center">{t('EMPTY')}</p>
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
