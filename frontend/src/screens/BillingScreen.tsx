import { useCallback, useEffect, useState } from 'react'
import { tenantBillingApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import type { InvoiceEntry } from '../api/types'

/** 금액(원) 표시 — 천단위 구분 + '원'. */
function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/**
 * W018 청구서 — 회사 총관리자(TENANT_ADMIN) 전용.
 * 결제 정보 등록(#14) + 달 선택(드롭다운) + 선택한 달의 상세(인원·단가·공급가·부가세·합계).
 */
export function BillingScreen() {
  const { t, tenantName } = useApp()
  const [rows, setRows] = useState<InvoiceEntry[]>([])
  const [selectedYm, setSelectedYm] = useState<string | null>(null)
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
            <div className="bill-detail printable">
              {/* 인쇄 시에만 나오는 청구서 머리말(회사·문서명·청구월) */}
              <div className="print-only bill-print-head">
                <strong>{tenantName}</strong>
                <span>{t('TITLE')} — {selected.ym}</span>
              </div>
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
                {selected.freeBlockDays > 0 && (
                  <>
                    <dt>{t('BILL_FREE_HALF')}</dt>
                    <dd>{selected.freeSeats} × ½</dd>
                  </>
                )}
                <dt>{t('BILL_SEATDAYS')}</dt>
                <dd className="tnum">{selected.seatDays} / {selected.daysInMonth}</dd>
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
