import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { leaveApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField, TimeField, TextAreaField, ModalSubject } from '../components/fields'
import { DateField } from '../components/DateField'
import { Pagination } from '../components/Pagination'
import { ConfirmModal } from '../components/ConfirmModal'
import { SectionHead } from '../components/SectionHead'
import { EmptyState } from '../components/EmptyState'
import { LoadingOverlay } from '../components/LoadingOverlay'
import { useIsMobile } from '../hooks/useIsMobile'
import { formatLeaveAmount } from '../util/leaveFormat'
import type { LeaveBalance, LeaveBalanceRow, LeaveRequestItem, LeaveStatus, LeaveType, LeaveUnit } from '../api/types'

/** 만기일별 잔여 한 건(합산 행의 상세 표시용). */
interface ExpiryDetail {
  expiresOn: string | null
  remainingMinutes: number
}

/** 같은 휴가 종류를 한 행으로 합산 — 총 잔여 + 만기일별 상세. */
interface GroupedBalance {
  leaveTypeId: number
  name: string
  unit: LeaveUnit
  standardDayMinutes: number
  totalMinutes: number
  details: ExpiryDetail[]
}

/**
 * '남은' 값 — 만기일별 상세가 있으면 PC는 호버 툴팁으로 펼친다(만기일 컬럼 대체).
 * 툴팁은 값 '옆'(오른쪽 우선, 공간 없으면 왼쪽)에 띄워 아래 행의 잔여를 가리지 않는다.
 * 상세가 없으면(부여 없음·잔여 0) 값만 조용히 표시한다.
 */
function BalanceAmount({
  amountText,
  details,
  unit,
  standardDayMinutes,
}: {
  amountText: string
  details: ExpiryDetail[]
  unit: LeaveUnit
  standardDayMinutes: number
}) {
  const { t } = useApp()
  const anchorRef = useRef<HTMLSpanElement>(null)
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null)

  function show() {
    const r = anchorRef.current?.getBoundingClientRect()
    if (!r) return
    const margin = 8
    const panelW = 210
    //값 왼쪽에 우선 배치(더 자연스러움), 왼쪽 공간이 없으면 오른쪽으로 뒤집는다.
    //세로는 값과 같은 높이 — 아래 행의 잔여 열을 가리지 않는다.
    let left = r.left - panelW - 10
    if (left < margin) left = Math.min(window.innerWidth - panelW - margin, r.right + 10)
    const top = Math.max(margin, r.top - 6)
    setPos({ left, top })
  }

  if (details.length === 0) return <span>{amountText}</span>
  const labels = { day: t('UNIT_DAY'), hour: t('UNIT_HOUR'), min: t('UNIT_MIN') }
  return (
    <span
      className="bal-amt-anchor"
      ref={anchorRef}
      onMouseEnter={show}
      onMouseLeave={() => setPos(null)}
    >
      <span className="bal-amt-trigger">{amountText}</span>
      {pos &&
        createPortal(
          <div
            className="bal-tip popover-portal"
            role="tooltip"
            aria-label={t('EXP_BREAKDOWN')}
            style={{ position: 'fixed', left: pos.left, top: pos.top }}
          >
            <p className="bal-tip-title">{t('EXP_BREAKDOWN')}</p>
            {details.map((d, i) => (
              <div className={`bal-tip-row${i === 0 && d.expiresOn ? ' soon' : ''}`} key={`${d.expiresOn ?? 'none'}-${i}`}>
                <span className="d">{d.expiresOn ?? t('NO_EXPIRY')}</span>
                <b className="a">{formatLeaveAmount(d.remainingMinutes, unit, standardDayMinutes, labels)}</b>
              </div>
            ))}
          </div>,
          document.body,
        )}
    </span>
  )
}

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
 * M003 휴가 — 멤버 본인 잔여·신청·취소.
 * 잔여 카드(종류별) + 신청 모달(일/시간) + 신청 내역(취소).
 */
export function LeaveScreen() {
  const { t } = useApp()
  const isMobile = useIsMobile()
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [balanceRows, setBalanceRows] = useState<LeaveBalanceRow[]>([])
  const [types, setTypes] = useState<LeaveType[]>([])
  const [requests, setRequests] = useState<LeaveRequestItem[]>([])
  //내 신청 내역 페이지(#9) — 해가 갈수록 늘어나는 목록이라 페이지 번호 방식
  const [reqPage, setReqPage] = useState(1)
  const [reqTotalPages, setReqTotalPages] = useState(1)
  const [reqTotalCount, setReqTotalCount] = useState(0)
  const [listError, setListError] = useState<string | null>(null)

  const [formOpen, setFormOpen] = useState(false)
  //모바일: 만기일별 상세를 펼친 종류(호버 대신 탭)
  const [openTypeId, setOpenTypeId] = useState<number | null>(null)
  const [cancelTarget, setCancelTarget] = useState<LeaveRequestItem | null>(null)
  const [cancelReqTarget, setCancelReqTarget] = useState<LeaveRequestItem | null>(null)
  const [cancelReqReason, setCancelReqReason] = useState('')
  const [rowError, setRowError] = useState<{ id: number; message: string } | null>(null)

  //데이터 도착 전 로딩 베일(이중 클릭 방지) — 잔여·신청 내역을 한 번에 조회
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const [b, rows, ty, rq] = await Promise.all([
        leaveApi.balances(),
        leaveApi.balanceRows(),
        leaveApi.types(),
        leaveApi.myRequests(reqPage),
      ])
      setBalances(b)
      setBalanceRows(rows)
      setTypes(ty)
      setRequests(rq.items)
      setReqTotalPages(rq.totalPages)
      setReqTotalCount(rq.totalCount)
      //취소 등으로 건수가 줄어 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
      if (reqPage > rq.totalPages) setReqPage(rq.totalPages)
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [reqPage])

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

  //잔여가 없어도 회사의 모든 휴가 종류를 보여준다 — 어떤 휴가가 있는지 확인 가능하게(#3).
  //부여 이력이 있는 종류는 만기일별 행 그대로, 없는 종류는 잔여 0 한 행으로 채운다.
  const displayRows = useMemo<LeaveBalanceRow[]>(() => {
    const rows = [...balanceRows]
    const withRows = new Set(balanceRows.map((r) => r.leaveTypeId))
    for (const ty of types) {
      if (!withRows.has(ty.leaveTypeId)) {
        rows.push({
          leaveTypeId: ty.leaveTypeId,
          name: ty.name,
          unit: ty.unit,
          remainingMinutes: 0,
          expiresOn: null,
          standardDayMinutes: stdDay,
        })
      }
    }
    //종류 등록 순서(sortOrder)를 따르되, 같은 종류의 여러 만기 행은 원래 순서 유지
    const order = new Map(types.map((ty, i) => [ty.leaveTypeId, i]))
    return rows
      .map((r, i) => ({ r, i }))
      .sort((a, b) => (order.get(a.r.leaveTypeId) ?? 0) - (order.get(b.r.leaveTypeId) ?? 0) || a.i - b.i)
      .map(({ r }) => r)
  }, [balanceRows, types, stdDay])

  //같은 종류를 한 행으로 합산 — 총 잔여 + 만기일별 상세(만기일 컬럼 대신 호버/탭으로 노출)
  const grouped = useMemo<GroupedBalance[]>(() => {
    const map = new Map<number, GroupedBalance>()
    for (const r of displayRows) {
      let g = map.get(r.leaveTypeId)
      if (!g) {
        g = {
          leaveTypeId: r.leaveTypeId,
          name: r.name,
          unit: r.unit,
          standardDayMinutes: r.standardDayMinutes,
          totalMinutes: 0,
          details: [],
        }
        map.set(r.leaveTypeId, g)
      }
      g.totalMinutes += r.remainingMinutes
      //실제 부여 행만 상세로(부여 없는 종류의 합성 0행은 잔여0·만기 null이라 제외)
      if (r.remainingMinutes > 0 || r.expiresOn) {
        g.details.push({ expiresOn: r.expiresOn, remainingMinutes: r.remainingMinutes })
      }
    }
    return [...map.values()]
  }, [displayRows])

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
    return start === end ? start : `${start} ~ ${end}`
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

      {/* 잔여는 종류별 한 행으로 합산 — 만기일 컬럼은 없애고, '남은' 값 호버(PC)/행 탭(모바일)으로
          만기일별 내역을 펼친다. 잔여 없는 종류도 0으로 노출(#3). */}
      {grouped.length === 0 && !listError ? (
        <p className="muted">{t('EMPTY')}</p>
      ) : isMobile ? (
        /* 모바일: 종류마다 한 줄(합산). 상세 있으면 탭해서 만기일별로 펼침(호버 대체) */
        <div className="lv-bal-box">
          {grouped.map((g) => {
            const hasDetail = g.details.length > 0
            const isOpen = openTypeId === g.leaveTypeId
            return (
              <div key={g.leaveTypeId} className="lv-bal-item">
                <button
                  type="button"
                  className={`lv-bal-row${hasDetail ? ' tappable' : ''}${isOpen ? ' open' : ''}`}
                  onClick={() => hasDetail && setOpenTypeId(isOpen ? null : g.leaveTypeId)}
                  aria-expanded={hasDetail ? isOpen : undefined}
                  disabled={!hasDetail}
                >
                  <span className="lv-bal-name">{g.name}</span>
                  <strong className={`lv-bal-amt${g.totalMinutes === 0 ? ' is-zero' : ''}`}>
                    {amt(g.totalMinutes, g.unit, g.standardDayMinutes)}
                  </strong>
                  {hasDetail && <span className="lv-bal-caret" aria-hidden="true" />}
                </button>
                {isOpen && hasDetail && (
                  <div className="lv-bal-detail">
                    <p className="lv-bal-detail-title">{t('EXP_BREAKDOWN')}</p>
                    {g.details.map((d, i) => (
                      <div
                        className={`lv-bal-detail-row${i === 0 && d.expiresOn ? ' soon' : ''}`}
                        key={`${d.expiresOn ?? 'none'}-${i}`}
                      >
                        <span>{d.expiresOn ?? t('NO_EXPIRY')}</span>
                        <b>{amt(d.remainingMinutes, g.unit, g.standardDayMinutes)}</b>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      ) : (
        <div className="table-wrap">
          <LoadingOverlay show={loading} label={t('LOADING')} />
          <table className="detail-table lv-bal-table">
            <thead>
              <tr>
                <th>{t('LEAVE_TYPE')}</th>
                <th className="num">{t('REMAINING')}</th>
              </tr>
            </thead>
            <tbody>
              {grouped.map((g) => (
                <tr key={g.leaveTypeId}>
                  <td>{g.name}</td>
                  <td className="num">
                    {g.totalMinutes === 0 ? (
                      <span className="bal-zero">{amt(g.totalMinutes, g.unit, g.standardDayMinutes)}</span>
                    ) : (
                      <BalanceAmount
                        amountText={amt(g.totalMinutes, g.unit, g.standardDayMinutes)}
                        details={g.details}
                        unit={g.unit}
                        standardDayMinutes={g.standardDayMinutes}
                      />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

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
        <ConfirmModal
          title={t('CANCEL')}
          subject={periodText(cancelTarget)}
          hint={t('CANCEL_CONFIRM')}
          danger
          confirmLabel={t('CANCEL')}
          cancelLabel={t('CLOSE')}
          onConfirm={() => void runCancel(cancelTarget.leaveRequestId)}
          onClose={() => setCancelTarget(null)}
        />
      )}

      {cancelReqTarget && (
        <Modal title={t('REQUEST_CANCEL')} onClose={() => setCancelReqTarget(null)} danger>
          <ModalSubject primary={periodText(cancelReqTarget)} />
          <p className="hint center">{t('REQUEST_CANCEL_HINT')}</p>
          <TextAreaField
            label={t('CANCEL_REASON')}
            value={cancelReqReason}
            onChange={setCancelReqReason}
            maxLength={200}
            autoFocus
          />
          <div className="btn-row">
            <button
              className="primary"
              disabled={!cancelReqReason.trim()}
              onClick={() =>
                void runCancelRequest(cancelReqTarget.leaveRequestId, cancelReqReason.trim())
              }
            >
              {t('REQUEST_CANCEL')}
            </button>
            <button onClick={() => setCancelReqTarget(null)}>{t('CLOSE')}</button>
          </div>
        </Modal>
      )}

      {/* 취소 규칙 안내는 행 데이터가 아니라 섹션 공통 규칙(SectionHead hint) — 행 반복 표기 금지 */}
      <SectionHead
        title={t('MY_REQUESTS')}
        hint={requests.length > 0 ? t('CANCEL_SAME_DAY') : undefined}
      />
      {!loading && requests.length === 0 ? (
        <EmptyState>{t('EMPTY')}</EmptyState>
      ) : isMobile ? (
        /* 모바일: 신청 1건=한 줄(요약), 펼치면 기간·사유·조치(#4 아코디언) */
        <div className="lv-acc-list">
          {requests.map((r) => {
            const canCancelPending = r.status === 'PENDING'
            const canRequestCancel = r.status === 'APPROVED' && startsInFuture(r.startAt)
            return (
              <details className="lv-acc" key={r.leaveRequestId}>
                <summary className="lv-acc-sum">
                  <span className="lv-req-type">{r.typeName}</span>
                  <span className={`badge leave-${r.status.toLowerCase()}`}>{t(STATUS_KEYS[r.status])}</span>
                  <span className="num lv-acc-amt">{amt(r.minutes, r.unit, dayMinutesByType.get(r.leaveTypeId) ?? stdDay)}</span>
                </summary>
                <div className="lv-acc-body">
                  <div className="lv-req-line">
                    <span>{periodText(r)}</span>
                  </div>
                  {r.reason && <p className="lv-req-reason muted">{r.reason}</p>}
                  {r.status === 'REJECTED' && r.decisionNote && (
                    <p className="hint">{r.decisionNote}</p>
                  )}
                  {r.status === 'CANCEL_REQUESTED' && r.cancelReason && (
                    <p className="hint">{r.cancelReason}</p>
                  )}
                  {(canCancelPending || canRequestCancel) && (
                    <div className="lv-req-actions">
                      {canCancelPending && (
                        <button onClick={() => setCancelTarget(r)}>{t('CANCEL')}</button>
                      )}
                      {canRequestCancel && (
                        <button onClick={() => { setCancelReqTarget(r); setCancelReqReason('') }}>
                          {t('REQUEST_CANCEL')}
                        </button>
                      )}
                    </div>
                  )}
                  {rowError?.id === r.leaveRequestId && (
                    <p className="error">{rowError.message}</p>
                  )}
                </div>
              </details>
            )
          })}
        </div>
      ) : (
        <div className="table-wrap">
          <LoadingOverlay show={loading} label={t('LOADING')} />
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
                //대기(PENDING)는 본인 직접 취소, 승인건은 시작 전이면 취소 신청(당일·시작 후는 관리자에게 — 섹션 상단 안내)
                const canCancelPending = r.status === 'PENDING'
                const canRequestCancel = r.status === 'APPROVED' && startsInFuture(r.startAt)
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
      <Pagination
        page={reqPage}
        totalPages={reqTotalPages}
        totalCount={reqTotalCount}
        onChange={setReqPage}
      />
    </div>
  )
}

/** 신청 모달 — 일 단위(시작~종료일). */
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
  //신청 모드(#12) — 시간 휴가 허용 종류일 때만 '시간' 선택 가능
  const [mode, setMode] = useState<'day' | 'hour'>('day')
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  //시간 모드 — 같은 날 시작~종료 시각
  const [hourDate, setHourDate] = useState(today)
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('12:00')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const selectedType = types.find((ty) => ty.leaveTypeId === leaveTypeId)
  const canHour = selectedType?.hourlyEnabled ?? false
  //시간 미허용 종류로 바꾸면 날짜 모드로 강제(토글 자체가 사라지므로 상태만 정리)
  const effectiveMode = canHour ? mode : 'day'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (effectiveMode === 'hour') {
        await leaveApi.apply({
          leaveTypeId,
          dayUnit: false,
          startTime: `${hourDate}T${startTime}:00`,
          endTime: `${hourDate}T${endTime}:00`,
          reason: reason.trim() || null,
        })
      } else {
        await leaveApi.apply({
          leaveTypeId,
          dayUnit: true,
          startDate,
          endDate,
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

        {/* 시간 휴가 허용 종류일 때만 날짜/시간 토글 노출(#12) */}
        {canHour && (
          <div className="seg-toggle" role="tablist" aria-label={t('LEAVE_TYPE')}>
            <button
              type="button"
              role="tab"
              aria-selected={effectiveMode === 'day'}
              className={effectiveMode === 'day' ? 'active' : ''}
              onClick={() => setMode('day')}
            >
              {t('MODE_DAY')}
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={effectiveMode === 'hour'}
              className={effectiveMode === 'hour' ? 'active' : ''}
              onClick={() => setMode('hour')}
            >
              {t('MODE_HOUR')}
            </button>
          </div>
        )}

        {effectiveMode === 'day' ? (
          <>
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
            </div>
          </>
        ) : (
          <>
            <label>
              {t('DATE')}
              <DateField value={hourDate} onChange={setHourDate} ariaLabel={t('DATE')} />
            </label>
            <div className="field-group">
              <label>
                {t('START_TIME')}
                <TimeField value={startTime} onChange={setStartTime} ariaLabel={t('START_TIME')} />
              </label>
              <label>
                {t('END_TIME')}
                <TimeField value={endTime} onChange={setEndTime} ariaLabel={t('END_TIME')} />
              </label>
            </div>
          </>
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
