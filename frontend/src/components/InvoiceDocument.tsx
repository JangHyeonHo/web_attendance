import type { InvoiceEntry, TenantProfileResponse } from '../api/types'

/** 금액(원) — 천단위 구분 + '원'. */
function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/**
 * 청구서 문서 — 화면·인쇄(→PDF) 공용. 실제 청구서 양식(발행정보 · 공급자/공급받는자 · 품목 표 · 합계).
 * 라인 금액은 합계(subtotal)와 정확히 일치하도록 추가분을 먼저 반올림하고 반값 블록을 잔액으로 둔다.
 */
export function InvoiceDocument({
  invoice,
  tenantName,
  profile,
  t,
}: {
  invoice: InvoiceEntry
  tenantName: string | null
  profile?: TenantProfileResponse | null
  t: (key: string) => string
}) {
  const issued = invoice.status === 'ISSUED'
  const invNo = `INV-${invoice.ym}`
  const issuedAt = invoice.issuedAt ? invoice.issuedAt.slice(0, 10) : null

  //공급받는자 주소 = (우편번호) 기본주소 상세주소 — 있는 값만 조합
  const addressText = [profile?.address, profile?.addressDetail].filter(Boolean).join(' ')
  const fullAddress = profile?.postalCode ? `(${profile.postalCode}) ${addressText}`.trim() : addressText

  //품목 금액(합계와 정확히 일치): 추가분 = round(좌석일×단가/일수), 반값 블록 = subtotal − 추가분
  const extraAmount = Math.round((invoice.seatDays * invoice.unitPrice) / invoice.daysInMonth)
  const freeBlockAmount = invoice.subtotal - extraAmount
  const halfPrice = Math.round(invoice.unitPrice / 2)

  return (
    <article className="inv-doc">
      <header className="inv-head">
        <div className="inv-head-title">
          <h1>{t('TITLE')}</h1>
          <span className="inv-sub">INVOICE</span>
        </div>
        <dl className="inv-meta">
          <div>
            <dt>{t('INV_NO')}</dt>
            <dd className="tnum">{invNo}</dd>
          </div>
          <div>
            <dt>{t('INV_PERIOD')}</dt>
            <dd className="tnum">{invoice.ym}</dd>
          </div>
          {issuedAt && (
            <div>
              <dt>{t('INV_ISSUED_AT')}</dt>
              <dd className="tnum">{issuedAt}</dd>
            </div>
          )}
          <div>
            <dt>{t('BILL_STATUS')}</dt>
            <dd>
              <span className={`badge ${issued ? 'ok' : ''}`}>
                {issued ? t('BILL_ISSUED') : t('BILL_PROVISIONAL')}
              </span>
            </dd>
          </div>
        </dl>
      </header>

      <section className="inv-parties">
        <div className="inv-party">
          <span className="inv-party-label">{t('INV_SUPPLIER')}</span>
          <strong>WebAttendance</strong>
        </div>
        <div className="inv-party">
          <span className="inv-party-label">{t('INV_BILL_TO')}</span>
          <strong>{tenantName ?? '-'}</strong>
          {profile?.ceoName && (
            <span className="inv-party-line">{t('INV_CEO')}: {profile.ceoName}</span>
          )}
          {fullAddress && <span className="inv-party-line">{fullAddress}</span>}
        </div>
      </section>

      <table className="inv-items">
        <thead>
          <tr>
            <th>{t('INV_ITEM')}</th>
            <th className="num">{t('INV_QTY')}</th>
            <th className="num">{t('INV_PRICE')}</th>
            <th className="num">{t('INV_AMOUNT')}</th>
          </tr>
        </thead>
        <tbody>
          {invoice.freeBlockDays > 0 && (
            <tr>
              <td>{t('INV_LINE_FREE')}</td>
              <td className="num tnum">{invoice.freeSeats}</td>
              <td className="num tnum">{won(halfPrice)}</td>
              <td className="num tnum">{won(freeBlockAmount)}</td>
            </tr>
          )}
          {invoice.billedSeats > 0 && (
            <tr>
              <td>{t('INV_LINE_EXTRA')}</td>
              <td className="num tnum">{invoice.billedSeats}</td>
              <td className="num tnum">{won(invoice.unitPrice)}</td>
              <td className="num tnum">{won(extraAmount)}</td>
            </tr>
          )}
          {/* 완전 무료(0원) 요약 줄 — 과금 인원(billedSeats)이 0일 때만.
              마지막 날 증원 등으로 billedSeats>0이면 위 '추가 인원' 줄(0원)이 이미 나오므로 중복 0원 줄 방지 */}
          {invoice.total === 0 && invoice.billedSeats === 0 && (
            <tr>
              <td>{t('INV_LINE_FREEPLAN')}</td>
              <td className="num tnum">{invoice.maxSeats}</td>
              <td className="num tnum">—</td>
              <td className="num tnum">{won(0)}</td>
            </tr>
          )}
        </tbody>
      </table>

      <div className="inv-summary">
        <div className="inv-summary-row">
          <span>{t('BILL_SUBTOTAL')}</span>
          <span className="tnum">{won(invoice.subtotal)}</span>
        </div>
        <div className="inv-summary-row">
          <span>{t('BILL_VAT')}</span>
          <span className="tnum">{won(invoice.vat)}</span>
        </div>
        <div className="inv-summary-row inv-total">
          <span>{t('BILL_TOTAL')}</span>
          <strong className="tnum">{won(invoice.total)}</strong>
        </div>
      </div>

      <p className="inv-note">{t('INV_NOTE')}</p>
    </article>
  )
}
