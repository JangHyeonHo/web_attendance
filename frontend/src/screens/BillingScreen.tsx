import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantBillingApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import type { BillingMethod, InvoiceEntry } from '../api/types'

/** 금액(원) 표시 — 천단위 구분 + '원'. */
function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/**
 * W018 청구서 — 회사 총관리자(TENANT_ADMIN) 전용.
 * 결제 정보 등록(#14) + 달 선택(드롭다운) + 선택한 달의 상세(인원·단가·공급가·부가세·합계).
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

      {/* 결제 정보 등록(#14) — 청구서만 있고 결제수단 등록이 없던 문제 해소 */}
      <PaymentProfile />

      <h3 className="section-head">{t('BILL_INVOICES')}</h3>
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

/** 결제 정보 카드 — 결제수단·청구 이메일·비고 등록/수정(#14). */
function PaymentProfile() {
  const { t } = useApp()
  const [method, setMethod] = useState<BillingMethod>('INVOICE')
  const [email, setEmail] = useState('')
  const [memo, setMemo] = useState('')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    tenantBillingApi
      .profile()
      .then((p) => {
        setMethod(p.billingMethod)
        setEmail(p.billingEmail ?? '')
        setMemo(p.memo ?? '')
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await tenantBillingApi.updateProfile({
        billingMethod: method,
        billingEmail: email.trim() || null,
        memo: memo.trim() || null,
      })
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="bill-profile">
      <h3 className="section-head">{t('BILL_PAYMENT_INFO')}</h3>
      <form onSubmit={save}>
        <div className="field-group">
          <label>
            {t('BILL_METHOD')}
            <SelectField
              value={method}
              ariaLabel={t('BILL_METHOD')}
              options={[
                { value: 'INVOICE', label: t('BILL_METHOD_INVOICE') },
                { value: 'CARD', label: t('BILL_METHOD_CARD') },
              ]}
              onChange={(v) => { setMethod(v as BillingMethod); setSaved(false) }}
            />
          </label>
          <label>
            {t('BILL_EMAIL')}
            <input
              type="email"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setSaved(false) }}
              placeholder="billing@company.com"
              maxLength={100}
            />
          </label>
        </div>
        <label>
          {t('BILL_MEMO')}
          <input
            value={memo}
            onChange={(e) => { setMemo(e.target.value); setSaved(false) }}
            maxLength={500}
          />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        {saved && <p className="success" role="status">{t('SAVED')}</p>}
        <button type="submit" className="primary" disabled={busy}>
          {t('SUBMIT')}
        </button>
      </form>
    </section>
  )
}
