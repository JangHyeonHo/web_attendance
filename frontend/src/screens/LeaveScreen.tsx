import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { leaveApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField, TimeField } from '../components/fields'
import { DateField } from '../components/DateField'
import { formatLeaveAmount } from '../util/leaveFormat'
import type { LeaveBalance, LeaveRequestItem, LeaveStatus, LeaveType, LeaveUnit } from '../api/types'

const STATUS_KEYS: Record<LeaveStatus, string> = {
  PENDING: 'STATUS_PENDING',
  APPROVED: 'STATUS_APPROVED',
  REJECTED: 'STATUS_REJECTED',
  CANCELED: 'STATUS_CANCELED',
  CANCEL_REQUESTED: 'STATUS_CANCEL_REQUESTED',
}

/** 오늘보다 이후 날짜에 시작하는가(당일·시작된 휴가는 멤버 취소 신청 불가) */
function startsInFuture(startAtIso: string): boolean {
  const today = new Date().toISOString().slice(0, 10)
  return startAtIso.slice(0, 10) > today
}

function dateOf(iso: string): string {
  return iso.slice(0, 10)
}

function timeOf(iso: string): string {
  return iso.slice(11, 16)
}

/** endAt(종료 익일 00:00) → 표시용 종료일 */
function endDateOf(iso: string): string {
  const [y, m, d] = dateOf(iso).split('-').map(Number)
  const dt = new Date(y, m - 1, d - 1)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`
}

/**
 * W015 휴가 — 멤버 본인 잔여·신청·취소.
 * 잔여 카드(종류별) + 신청 모달(일/반차/시간) + 신청 내역(취소).
 */
export function LeaveScreen() {
  const { t } = useApp()
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [requests, setRequests] = useState<LeaveRequestItem[]>([])
  const [listError, setListError] = useState<string | null>(null)

  const [formOpen, setFormOpen] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<LeaveRequestItem | null>(null)
  const [cancelReqTarget, setCancelReqTarget] = useState<LeaveRequestItem | null>(null)
  const [cancelReqReason, setCancelReqReason] = useState('')
  const [rowError, setRowError] = useState<{ id: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      const [b, ty, rq] = await Promise.all([
        leaveApi.balances(),
        leaveApi.types(),
        leaveApi.myRequests(),
      ])
      setBalances(b)
      setTypes(ty)
      setRequests(rq)
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const dayMinutesByType = useMemo(() => {
    const map = new Map<number, number>()
    for (const b of balances) map.set(b.leaveTypeId, b.standardDayMinutes)
    return map
  }, [balances])

  const stdDay = balances[0]?.standardDayMinutes ?? 480
  const labels = { day: t('UNIT_DAY'), hour: t('UNIT_HOUR'), min: t('UNIT_MIN') }
  const amt = (m: number, unit: LeaveUnit, dm: number) => formatLeaveAmount(m, unit, dm, labels)

  async function runCancel(id: number) {
    setCancelTarget(null)
    setRowError(null)
    try {
      await leaveApi.cancel(id)
      await reload()
    } catch (e) {
      setRowError({ id, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function runCancelRequest(id: number, reason: string) {
    setCancelReqTarget(null)
    setCancelReqReason('')
    setRowError(null)
    try {
      await leaveApi.requestCancel(id, reason)
      await reload()
    } catch (e) {
      setRowError({ id, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  function periodText(r: LeaveRequestItem): string {
    if (!r.dayUnit) {
      return `${dateOf(r.startAt)} ${timeOf(r.startAt)}~${timeOf(r.endAt)}`
    }
    const start = dateOf(r.startAt)
    const end = endDateOf(r.endAt)
    const base = start === end ? start : `${start} ~ ${end}`
    return r.halfDay ? `${base} (${t('HALF_DAY')})` : base
  }

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
        <div className="toolbar-actions">
          <button className="primary" onClick={() => setFormOpen(true)} disabled={types.length === 0}>
            {t('APPLY')}
          </button>
        </div>
      </div>

      {listError && <p className="error" role="alert">{listError}</p>}

      <section className="balance-cards">
        {balances.map((b) => (
          <div className="balance-card" key={b.leaveTypeId}>
            <span className="balance-name">{b.name}</span>
            <span className="balance-remaining">{amt(b.remainingMinutes, b.unit, b.standardDayMinutes)}</span>
            <span className="balance-sub muted">
              {t('GRANTED')} {amt(b.grantedMinutes, b.unit, b.standardDayMinutes)} ·{' '}
              {t('USED')} {amt(b.usedMinutes, b.unit, b.standardDayMinutes)}
              {b.pendingMinutes > 0 &&
                ` · ${t('STATUS_PENDING')} ${amt(b.pendingMinutes, b.unit, b.standardDayMinutes)}`}
            </span>
          </div>
        ))}
        {balances.length === 0 && !listError && <p className="muted">{t('EMPTY')}</p>}
      </section>

      {formOpen && (
        <ApplyModal
          types={types}
          onClose={() => setFormOpen(false)}
          onDone={async () => {
            setFormOpen(false)
            await reload()
          }}
        />
      )}

      {cancelTarget && (
        <Modal title={t('CANCEL')} onClose={() => setCancelTarget(null)} danger>
          <p className="center">{periodText(cancelTarget)} — {t('CANCEL')}?</p>
          <div className="btn-row">
            <button className="primary" onClick={() => void runCancel(cancelTarget.leaveRequestId)}>
              {t('SUBMIT')}
            </button>
            <button onClick={() => setCancelTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      {cancelReqTarget && (
        <Modal title={t('REQUEST_CANCEL')} onClose={() => setCancelReqTarget(null)} danger>
          <p className="center">{periodText(cancelReqTarget)}</p>
          <label>
            {t('CANCEL_REASON')}
            <input
              value={cancelReqReason}
              onChange={(e) => setCancelReqReason(e.target.value)}
              maxLength={200}
              autoFocus
            />
          </label>
          <div className="btn-row">
            <button
              className="primary"
              disabled={!cancelReqReason.trim()}
              onClick={() =>
                void runCancelRequest(cancelReqTarget.leaveRequestId, cancelReqReason.trim())
              }
            >
              {t('SUBMIT')}
            </button>
            <button onClick={() => setCancelReqTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      <h3 className="section-head">{t('MY_REQUESTS')}</h3>
      {requests.length === 0 ? (
        <p className="muted center">{t('EMPTY')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('LEAVE_TYPE')}</th>
                <th>{t('PERIOD')}</th>
                <th>{t('AMOUNT')}</th>
                <th>{t('REASON')}</th>
                <th />
                <th />
              </tr>
            </thead>
            <tbody>
              {requests.map((r) => {
                //대기(PENDING)는 본인 직접 취소, 승인건은 시작 전이면 취소 신청(당일·시작 후는 관리자에게)
                const canCancelPending = r.status === 'PENDING'
                const canRequestCancel = r.status === 'APPROVED' && startsInFuture(r.startAt)
                const approvedSameDay = r.status === 'APPROVED' && !startsInFuture(r.startAt)
                return (
                  <tr key={r.leaveRequestId}>
                    <td>{r.typeName}</td>
                    <td className="wrap">{periodText(r)}</td>
                    <td className="num">
                      {amt(r.minutes, r.unit, dayMinutesByType.get(r.leaveTypeId) ?? stdDay)}
                    </td>
                    <td className="wrap">{r.reason ?? ''}</td>
                    <td>
                      <span className={`badge leave-${r.status.toLowerCase()}`}>
                        {t(STATUS_KEYS[r.status])}
                      </span>
                      {r.status === 'REJECTED' && r.decisionNote && (
                        <span className="hint"> {r.decisionNote}</span>
                      )}
                      {r.status === 'CANCEL_REQUESTED' && r.cancelReason && (
                        <span className="hint"> {r.cancelReason}</span>
                      )}
                    </td>
                    <td>
                      {canCancelPending && (
                        <button onClick={() => setCancelTarget(r)}>{t('CANCEL')}</button>
                      )}
                      {canRequestCancel && (
                        <button onClick={() => { setCancelReqTarget(r); setCancelReqReason('') }}>
                          {t('REQUEST_CANCEL')}
                        </button>
                      )}
                      {approvedSameDay && <span className="hint">{t('CANCEL_SAME_DAY')}</span>}
                      {rowError?.id === r.leaveRequestId && (
                        <span className="error"> {rowError.message}</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/** 신청 모달 — 일/반차/시간 분기. */
function ApplyModal({
  types,
  onClose,
  onDone,
}: {
  types: LeaveType[]
  onClose: () => void
  onDone: () => Promise<void>
}) {
  const { t } = useApp()
  const today = new Date().toISOString().slice(0, 10)
  const [leaveTypeId, setLeaveTypeId] = useState(types[0]?.leaveTypeId ?? 0)
  const [dayUnit, setDayUnit] = useState(true)
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  const [halfDay, setHalfDay] = useState(false)
  const [hourDate, setHourDate] = useState(today)
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('12:00')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const singleDay = startDate === endDate

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (dayUnit) {
        await leaveApi.apply({
          leaveTypeId,
          dayUnit: true,
          startDate,
          endDate,
          halfDay: singleDay && halfDay,
          reason: reason.trim() || null,
        })
      } else {
        await leaveApi.apply({
          leaveTypeId,
          dayUnit: false,
          startTime: `${hourDate}T${startTime}:00`,
          endTime: `${hourDate}T${endTime}:00`,
          reason: reason.trim() || null,
        })
      }
      await onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={t('APPLY')} onClose={onClose}>
      <form onSubmit={submit}>
        <label>
          {t('LEAVE_TYPE')}
          <SelectField
            value={String(leaveTypeId)}
            options={types.map((ty) => ({ value: String(ty.leaveTypeId), label: ty.name }))}
            onChange={(v) => setLeaveTypeId(Number(v))}
            ariaLabel={t('LEAVE_TYPE')}
          />
        </label>

        <div className="seg-toggle" role="tablist" aria-label={t('LEAVE_TYPE')}>
          <button
            type="button"
            className={dayUnit ? 'active' : ''}
            onClick={() => setDayUnit(true)}
          >
            {t('MODE_DAY')}
          </button>
          <button
            type="button"
            className={!dayUnit ? 'active' : ''}
            onClick={() => setDayUnit(false)}
          >
            {t('MODE_HOUR')}
          </button>
        </div>

        {dayUnit ? (
          <div className="field-group">
            <label>
              {t('START_DATE')}
              <DateField
                value={startDate}
                ariaLabel={t('START_DATE')}
                onChange={(v) => {
                  setStartDate(v)
                  if (v > endDate) setEndDate(v)
                }}
              />
            </label>
            <label>
              {t('END_DATE')}
              <DateField value={endDate} min={startDate} onChange={setEndDate} ariaLabel={t('END_DATE')} />
            </label>
            {singleDay && (
              <label className="check-inline">
                <input
                  type="checkbox"
                  checked={halfDay}
                  onChange={(e) => setHalfDay(e.target.checked)}
                />
                {t('HALF_DAY')}
              </label>
            )}
          </div>
        ) : (
          <div className="field-group">
            <label>
              {t('START_DATE')}
              <DateField value={hourDate} onChange={setHourDate} ariaLabel={t('START_DATE')} />
            </label>
            <label>
              {t('START_TIME')}
              <TimeField value={startTime} onChange={setStartTime} ariaLabel={t('START_TIME')} />
            </label>
            <label>
              {t('END_TIME')}
              <TimeField value={endTime} onChange={setEndTime} ariaLabel={t('END_TIME')} />
            </label>
          </div>
        )}

        <label>
          {t('REASON')}
          <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />
        </label>

        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={submitting || leaveTypeId === 0}>
          {t('SUBMIT')}
        </button>
      </form>
    </Modal>
  )
}
