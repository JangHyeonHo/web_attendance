import { useCallback, useEffect, useState } from 'react'
import { tenantCloseApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { languageApi } from '../api/endpoints'
import { localeOf } from '../i18n/lang'
import { Modal } from '../components/Modal'
import type { PayrollSettlement, PendingCloseResponse } from '../api/types'

/**
 * W021 근태 마감 관리 — 멤버가 신청한 월 마감을 인사관리자가 승인/반려한다.
 * 승인되면 그 (멤버, 월)의 근태 정정이 잠기고, 보고서에 도장이 날인된다.
 */
export function AttendanceCloseAdminScreen() {
  const { t: commonT, lang } = useApp()
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [rows, setRows] = useState<PendingCloseResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  //반려 사유 입력 모달
  const [rejecting, setRejecting] = useState<PendingCloseResponse | null>(null)
  const [rejectNote, setRejectNote] = useState('')
  //급여 정산(참고) 펼침 — closeId별. settlement=null이면 미입력
  const [payrollOpen, setPayrollOpen] = useState<number | null>(null)
  const [payroll, setPayroll] = useState<{ available: boolean; s: PayrollSettlement | null }>({
    available: false,
    s: null,
  })

  const t = useCallback((k: string) => texts[k] ?? commonT(k), [texts, commonT])

  async function togglePayroll(r: PendingCloseResponse) {
    if (payrollOpen === r.closeId) {
      setPayrollOpen(null)
      return
    }
    setPayrollOpen(r.closeId)
    setPayroll({ available: false, s: null })
    try {
      const p = await tenantCloseApi.payroll(r.userId, r.year, r.month)
      setPayroll({ available: p.available, s: p.settlement })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  useEffect(() => {
    languageApi.texts('W021', lang).then(setTexts).catch(() => {})
  }, [lang])

  const reload = useCallback(async () => {
    try {
      setRows(await tenantCloseApi.pending())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function decide(closeId: number, approve: boolean, note?: string) {
    setBusy(true)
    setError(null)
    try {
      await tenantCloseApi.decide(closeId, approve, note)
      setRejecting(null)
      setRejectNote('')
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  //마감 취소 — 승인된 마감을 열린 상태로 되돌린다(#11). 확인 후 실행.
  async function reopen(r: PendingCloseResponse) {
    if (!window.confirm(t('CLOSE_REOPEN_CONFIRM'))) return
    setBusy(true)
    setError(null)
    try {
      await tenantCloseApi.reopen(r.closeId)
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel">
      <h2>{t('CLOSE_ADMIN_TITLE')}</h2>
      <p className="muted">{t('CLOSE_ADMIN_SUB')}</p>
      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 ? (
        <p className="muted center">{t('CLOSE_PENDING_NONE')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('CLOSE_MEMBER')}</th>
                <th>{t('CLOSE_TARGET')}</th>
                <th>{t('CLOSE_STATUS')}</th>
                <th>{t('CLOSE_REQUESTED_AT')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => ([
                <tr key={r.closeId}>
                  <td>{r.userName}</td>
                  <td>{r.year}. {String(r.month).padStart(2, '0')}</td>
                  <td>
                    <span className={`close-badge ${r.status === 'APPROVED' ? 'done' : 'pending'}`}>
                      {r.status === 'APPROVED' ? t('CLOSE_ST_APPROVED') : t('CLOSE_ST_REQUESTED')}
                    </span>
                  </td>
                  <td>{r.requestedAt.slice(0, 10)}</td>
                  <td>
                    <div className="row-actions">
                      {r.status === 'REQUESTED' ? (
                        <>
                          <button
                            className="primary"
                            disabled={busy}
                            onClick={() => void decide(r.closeId, true)}
                          >
                            {t('CLOSE_APPROVE')}
                          </button>
                          <button disabled={busy} onClick={() => setRejecting(r)}>
                            {t('CLOSE_REJECT')}
                          </button>
                        </>
                      ) : (
                        <button className="danger" disabled={busy} onClick={() => void reopen(r)}>
                          {t('CLOSE_REOPEN')}
                        </button>
                      )}
                      <button onClick={() => void togglePayroll(r)}>
                        {t('PAYROLL_TITLE')}
                      </button>
                    </div>
                  </td>
                </tr>,
                payrollOpen === r.closeId && (
                  <tr key={`${r.closeId}-pay`} className="payroll-expand">
                    <td colSpan={5}>
                      <PayrollView data={payroll} lang={lang} t={t} />
                    </td>
                  </tr>
                ),
              ]))}
            </tbody>
          </table>
        </div>
      )}

      {rejecting && (
        <Modal
          title={`${rejecting.userName} — ${t('CLOSE_REJECT')}`}
          onClose={() => setRejecting(null)}
          danger
        >
          <p className="center">
            {rejecting.year}. {String(rejecting.month).padStart(2, '0')}
          </p>
          <input
            className="reason-input"
            value={rejectNote}
            maxLength={200}
            placeholder={commonT('REASON')}
            onChange={(e) => setRejectNote(e.target.value)}
            autoFocus
          />
          <div className="btn-row">
            <button
              className="primary"
              disabled={busy}
              onClick={() => void decide(rejecting.closeId, false, rejectNote.trim() || undefined)}
            >
              {t('CLOSE_REJECT')}
            </button>
            <button onClick={() => setRejecting(null)}>{commonT('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

/**
 * 급여 정산(참고) 표 — 관리자 전용. 근태 기반 가감 명세. 실지급이 아닌 참고값.
 * 월 기본급 미입력이면 안내만 표시. 국가별(원/円) 통화.
 */
function PayrollView({
  data,
  lang,
  t,
}: {
  data: { available: boolean; s: PayrollSettlement | null }
  lang: string
  t: (key: string) => string
}) {
  if (!data.available || !data.s) {
    return <p className="muted">{t('PAYROLL_UNSET')}</p>
  }
  const s = data.s
  const unit = s.country === 'JP' ? '円' : '원'
  const won = (n: number) => `${n.toLocaleString(localeOf(lang as never))}${unit}`
  const hrs = (m: number) => `${(m / 60).toFixed(1)}h`
  const L = (ko: string, en: string, ja: string) => (lang === 'ENG' ? en : lang === 'JPN' ? ja : ko)
  //통상시급 환산 제수(월 기본급 ÷ 통상시급 ≈ 소정근로시간) — 산식 표기용
  const divisor = s.hourlyWage > 0 ? Math.round(s.baseMonthlySalary / s.hourlyWage) : 0
  const estimatedPay = s.baseMonthlySalary + s.netAdjustment
  return (
    <section className="payroll-panel">
      <table className="payroll-table">
        <tbody>
          <tr>
            <th>{t('PAYROLL_BASE')}</th>
            <td className="num">{won(s.baseMonthlySalary)}</td>
            <td className="muted">{t('PAYROLL_HOURLY')} {won(s.hourlyWage)} <span className="payroll-formula">= {won(s.baseMonthlySalary)} ÷ {divisor}h</span></td>
          </tr>
          <tr>
            <th>{t('PAYROLL_OT')}</th>
            <td className="num plus">+{won(s.overtimePay)}</td>
            <td className="muted">{hrs(s.overtimeMinutes)}</td>
          </tr>
          <tr>
            <th>{t('PAYROLL_NIGHT')}</th>
            <td className="num plus">+{won(s.nightPay)}</td>
            <td className="muted">{hrs(s.nightMinutes)}</td>
          </tr>
          <tr>
            <th>{t('PAYROLL_HOLIDAY')}</th>
            <td className="num plus">+{won(s.holidayPay)}</td>
            <td className="muted">{hrs(s.holidayWorkMinutes)}</td>
          </tr>
          <tr>
            <th>{t('PAYROLL_DEDUCT')}</th>
            <td className="num minus">-{won(s.deduction)}</td>
            <td className="muted">{hrs(s.shortfallMinutes)}</td>
          </tr>
          <tr>
            <th>{t('PAYROLL_NET')}</th>
            <td className="num">{s.netAdjustment >= 0 ? '+' : ''}{won(s.netAdjustment)}</td>
            <td className="muted">{L('기본급 대비 가감', 'vs base', '基本給比')}</td>
          </tr>
          <tr className="payroll-net">
            <th>{L('예상 실지급', 'Est. net pay', '支給見込')}</th>
            <td className="num">{won(estimatedPay)}</td>
            <td className="muted">{L('기본급 + 가감', 'base + adj.', '基本給+増減')}</td>
          </tr>
        </tbody>
      </table>
      <p className="hint">{t('PAYROLL_NOTE')}</p>
    </section>
  )
}
