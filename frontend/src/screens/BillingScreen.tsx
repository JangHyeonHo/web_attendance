import { useCallback, useEffect, useState } from 'react'
import { tenantBillingApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { InvoiceEntry } from '../api/types'

/** 금액(원) 표시 — 천단위 구분 + '원'. */
function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/**
 * W018 청구서 — 회사 총관리자(TENANT_ADMIN) 전용.
 * 자사 월별 청구서(진행 중=잠정, 마감=확정). 인당 과금·무료 인원·부가세 별도.
 */
export function BillingScreen() {
  const { t } = useApp()
  const [rows, setRows] = useState<InvoiceEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setRows(await tenantBillingApi.invoices())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
        <div className="toolbar-actions">
          <button onClick={() => void reload()}>{t('REFRESH')}</button>
        </div>
      </div>

      <p className="muted">{t('NOTE')}</p>
      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 && !error ? (
        <p className="muted center">{t('EMPTY')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('BILL_MONTH')}</th>
                <th>{t('BILL_SEATS_MAX')}</th>
                <th>{t('BILL_SEATS_FREE')}</th>
                <th>{t('BILL_SEATS_BILLED')}</th>
                <th>{t('BILL_UNIT')}</th>
                <th>{t('BILL_SUBTOTAL')}</th>
                <th>{t('BILL_VAT')}</th>
                <th>{t('BILL_TOTAL')}</th>
                <th>{t('BILL_STATUS')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.ym}>
                  <td className="nowrap">{r.ym}</td>
                  <td>{r.maxSeats}</td>
                  <td>{r.freeSeats}</td>
                  <td>{r.billedSeats}</td>
                  <td className="nowrap">{won(r.unitPrice)}</td>
                  <td className="nowrap">{won(r.subtotal)}</td>
                  <td className="nowrap">{won(r.vat)}</td>
                  <td className="nowrap"><strong>{won(r.total)}</strong></td>
                  <td>
                    <span className={`badge ${r.status === 'ISSUED' ? 'ok' : 'muted'}`}>
                      {r.status === 'ISSUED' ? t('BILL_ISSUED') : t('BILL_PROVISIONAL')}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
