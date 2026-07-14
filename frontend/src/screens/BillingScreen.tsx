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
 * 달 선택(월 목록) + 선택한 달의 상세(인원·단가·공급가·부가세·합계). 진행 중=잠정, 마감=확정.
 */
export function BillingScreen() {
  const { t } = useApp()
  const [rows, setRows] = useState<InvoiceEntry[]>([])
  const [selectedYm, setSelectedYm] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const data = await tenantBillingApi.invoices()
      setRows(data)
      //선택 유지(있으면), 없으면 최신 달
      setSelectedYm((cur) => (cur && data.some((r) => r.ym === cur) ? cur : data[0]?.ym ?? null))
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const selected = rows.find((r) => r.ym === selectedYm) ?? null

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
        <>
          {/* 달 선택 — 마감(확정)월은 점으로 표시 */}
          <div className="bill-months" role="tablist" aria-label={t('BILL_MONTH')}>
            {rows.map((r) => (
              <button
                key={r.ym}
                role="tab"
                aria-selected={r.ym === selectedYm}
                className={r.ym === selectedYm ? 'active' : ''}
                onClick={() => setSelectedYm(r.ym)}
              >
                {r.ym}
                {r.status === 'ISSUED' && <span className="bill-dot" aria-hidden="true" />}
              </button>
            ))}
          </div>

          {selected && (
            <div className="bill-detail">
              <div className="bill-detail-head">
                <span className="bill-detail-ym">{selected.ym}</span>
                <span className={`badge ${selected.status === 'ISSUED' ? 'ok' : ''}`}>
                  {selected.status === 'ISSUED' ? t('BILL_ISSUED') : t('BILL_PROVISIONAL')}
                </span>
              </div>
              <dl className="kv">
                <dt>{t('BILL_SEATS_MAX')}</dt>
                <dd>{selected.maxSeats}</dd>
                <dt>{t('BILL_SEATS_FREE')}</dt>
                <dd>{selected.freeSeats}</dd>
                <dt>{t('BILL_SEATS_BILLED')}</dt>
                <dd>{selected.billedSeats}</dd>
                <dt>{t('BILL_UNIT')}</dt>
                <dd className="tnum">{won(selected.unitPrice)}</dd>
                <dt>{t('BILL_SUBTOTAL')}</dt>
                <dd className="tnum">{won(selected.subtotal)}</dd>
                <dt>{t('BILL_VAT')}</dt>
                <dd className="tnum">{won(selected.vat)}</dd>
              </dl>
              <div className="bill-total">
                <span>{t('BILL_TOTAL')}</span>
                <strong className="tnum">{won(selected.total)}</strong>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
