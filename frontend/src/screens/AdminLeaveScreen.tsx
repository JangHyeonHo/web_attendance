import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantLeaveApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField } from '../components/fields'
import type {
  LeaveRequestItem,
  LeaveType,
  LeaveUnit,
  MemberLeaveDetail,
  MemberLeaveSummary,
} from '../api/types'

type Tab = 'approvals' | 'members' | 'types'

function amountText(
  minutes: number,
  unit: LeaveUnit,
  dayMinutes: number,
  dayLabel: string,
  hourLabel: string,
): string {
  if (unit === 'HOUR' || dayMinutes <= 0) {
    const h = minutes / 60
    return `${Number.isInteger(h) ? h : h.toFixed(1)}${hourLabel}`
  }
  return `${Math.round((minutes / dayMinutes) * 2) / 2}${dayLabel}`
}

function dateOf(iso: string) {
  return iso.slice(0, 10)
}

/**
 * W016 휴가 관리 — 인사관리자+총관리자.
 * 탭: 승인 대기(결재) / 멤버 잔여(재계산·부여·입사일) / 휴가 종류(CRUD).
 */
export function AdminLeaveScreen() {
  const { t } = useApp()
  const [tab, setTab] = useState<Tab>('approvals')

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
      </div>
      <div className="seg-toggle" role="tablist">
        <button className={tab === 'approvals' ? 'active' : ''} onClick={() => setTab('approvals')}>
          {t('TAB_APPROVALS')}
        </button>
        <button className={tab === 'members' ? 'active' : ''} onClick={() => setTab('members')}>
          {t('TAB_MEMBERS')}
        </button>
        <button className={tab === 'types' ? 'active' : ''} onClick={() => setTab('types')}>
          {t('TAB_TYPES')}
        </button>
      </div>

      {tab === 'approvals' && <ApprovalsTab />}
      {tab === 'members' && <MembersTab />}
      {tab === 'types' && <TypesTab />}
    </div>
  )
}

// ===== 승인 대기 =====

function ApprovalsTab() {
  const { t } = useApp()
  const [pending, setPending] = useState<LeaveRequestItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [rejectTarget, setRejectTarget] = useState<LeaveRequestItem | null>(null)
  const [note, setNote] = useState('')
  const [rowError, setRowError] = useState<{ id: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      setPending(await tenantLeaveApi.pending())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function decide(id: number, approve: boolean, decisionNote?: string) {
    setRowError(null)
    try {
      await tenantLeaveApi.decide(id, { approve, note: decisionNote ?? null })
      setRejectTarget(null)
      setNote('')
      await reload()
    } catch (e) {
      setRowError({ id, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  return (
    <>
      {error && <p className="error" role="alert">{error}</p>}
      {pending.length === 0 ? (
        <p className="muted center">{t('NO_PENDING')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('MEMBER')}</th>
                <th>{t('LEAVE_TYPE')}</th>
                <th>{t('PERIOD')}</th>
                <th>{t('AMOUNT')}</th>
                <th>{t('REASON')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pending.map((r) => (
                <tr key={r.leaveRequestId}>
                  <td>{r.userName}</td>
                  <td>{r.typeName}</td>
                  <td className="wrap">
                    {r.dayUnit
                      ? `${dateOf(r.startAt)}${r.halfDay ? ` (${t('HALF_DAY')})` : ''}`
                      : `${dateOf(r.startAt)} ${r.startAt.slice(11, 16)}~${r.endAt.slice(11, 16)}`}
                  </td>
                  <td className="num">{amountText(r.minutes, r.unit, 480, t('UNIT_DAY'), t('UNIT_HOUR'))}</td>
                  <td className="wrap">{r.reason ?? ''}</td>
                  <td>
                    <div className="row-actions">
                      <button className="primary" onClick={() => void decide(r.leaveRequestId, true)}>
                        {t('APPROVE')}
                      </button>
                      <button onClick={() => { setRejectTarget(r); setNote('') }}>{t('REJECT')}</button>
                    </div>
                    {rowError?.id === r.leaveRequestId && (
                      <span className="error"> {rowError.message}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rejectTarget && (
        <Modal title={t('REJECT')} onClose={() => setRejectTarget(null)} danger>
          <p className="center">{rejectTarget.userName} — {rejectTarget.typeName}</p>
          <label>
            {t('DECISION_NOTE')}
            <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} />
          </label>
          <div className="btn-row">
            <button
              className="primary"
              onClick={() => void decide(rejectTarget.leaveRequestId, false, note.trim() || undefined)}
            >
              {t('REJECT')}
            </button>
            <button onClick={() => setRejectTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </>
  )
}

// ===== 멤버 잔여 =====

function MembersTab() {
  const { t } = useApp()
  const [members, setMembers] = useState<MemberLeaveSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<MemberLeaveDetail | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setMembers(await tenantLeaveApi.members())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function openDetail(userId: number) {
    setError(null)
    try {
      setDetail(await tenantLeaveApi.memberDetail(userId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  async function recomputeAll() {
    setBusy(true)
    setError(null)
    try {
      await tenantLeaveApi.recomputeAll()
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="toolbar-actions" style={{ justifyContent: 'flex-end', marginBottom: '0.75rem' }}>
        <button onClick={() => void recomputeAll()} disabled={busy}>
          {t('RECOMPUTE_ALL')}
        </button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {members.length === 0 ? (
        <p className="muted center">{t('EMPTY')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('MEMBER')}</th>
                <th>{t('START_DATE')}</th>
                <th>{t('BALANCE')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.userId}>
                  <td>{m.name}</td>
                  <td>{m.hireDate ?? '—'}</td>
                  <td className="num">
                    {m.annualRemainingMinutes != null
                      ? amountText(m.annualRemainingMinutes, 'DAY', m.standardDayMinutes, t('UNIT_DAY'), t('UNIT_HOUR'))
                      : '—'}
                  </td>
                  <td>
                    <button onClick={() => void openDetail(m.userId)}>{t('BALANCE')}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {detail && (
        <MemberDetailModal
          detail={detail}
          onClose={() => setDetail(null)}
          onChanged={async () => {
            await openDetail(detail.userId)
            await reload()
          }}
        />
      )}
    </>
  )
}

function MemberDetailModal({
  detail,
  onClose,
  onChanged,
}: {
  detail: MemberLeaveDetail
  onClose: () => void
  onChanged: () => Promise<void>
}) {
  const { t } = useApp()
  const [hireDate, setHireDate] = useState(detail.hireDate ?? '')
  const [grantTypeId, setGrantTypeId] = useState(detail.balances[0]?.leaveTypeId ?? 0)
  const [grantDays, setGrantDays] = useState('1')
  const [memo, setMemo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(fn: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await fn()
      await onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={detail.name} onClose={onClose}>
      {error && <p className="error" role="alert">{error}</p>}

      <div className="field-group">
        <label>
          {t('START_DATE')}
          <input type="date" value={hireDate} onChange={(e) => setHireDate(e.target.value)} />
        </label>
        <button
          type="button"
          disabled={busy || !hireDate}
          onClick={() => void run(() => tenantLeaveApi.updateHireDate(detail.userId, hireDate))}
        >
          {t('SUBMIT')}
        </button>
        <button
          type="button"
          className="primary"
          disabled={busy}
          onClick={() => void run(() => tenantLeaveApi.recompute(detail.userId))}
        >
          {t('RECOMPUTE')}
        </button>
      </div>

      <table className="detail-table compact">
        <thead>
          <tr>
            <th>{t('LEAVE_TYPE')}</th>
            <th>{t('GRANTED')}</th>
            <th>{t('USED')}</th>
            <th>{t('BALANCE')}</th>
          </tr>
        </thead>
        <tbody>
          {detail.balances.map((b) => (
            <tr key={b.leaveTypeId}>
              <td>{b.name}</td>
              <td className="num">{amountText(b.grantedMinutes, b.unit, b.standardDayMinutes, t('UNIT_DAY'), t('UNIT_HOUR'))}</td>
              <td className="num">{amountText(b.usedMinutes, b.unit, b.standardDayMinutes, t('UNIT_DAY'), t('UNIT_HOUR'))}</td>
              <td className="num">{amountText(b.remainingMinutes, b.unit, b.standardDayMinutes, t('UNIT_DAY'), t('UNIT_HOUR'))}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h4 className="section-head">{t('GRANT')}</h4>
      <div className="field-group">
        <SelectField
          value={String(grantTypeId)}
          options={detail.balances.map((b) => ({ value: String(b.leaveTypeId), label: b.name }))}
          onChange={(v) => setGrantTypeId(Number(v))}
          ariaLabel={t('LEAVE_TYPE')}
        />
        <label>
          {t('GRANT_MINUTES')}
          <input
            type="number"
            step="0.5"
            value={grantDays}
            onChange={(e) => setGrantDays(e.target.value)}
          />
        </label>
        <input
          placeholder={t('MEMO')}
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          maxLength={200}
        />
        <button
          type="button"
          disabled={busy || grantTypeId === 0}
          onClick={() =>
            void run(() =>
              tenantLeaveApi.grant({
                userId: detail.userId,
                leaveTypeId: grantTypeId,
                days: Number(grantDays),
                memo: memo.trim() || null,
              }),
            )
          }
        >
          {t('GRANT')}
        </button>
      </div>
    </Modal>
  )
}

// ===== 휴가 종류 =====

const UNIT_OPTIONS: LeaveUnit[] = ['DAY', 'HOUR']

function TypesTab() {
  const { t } = useApp()
  const [types, setTypes] = useState<LeaveType[]>([])
  const [error, setError] = useState<string | null>(null)
  const [edit, setEdit] = useState<LeaveType | 'new' | null>(null)

  const reload = useCallback(async () => {
    try {
      setTypes(await tenantLeaveApi.types())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <>
      <div className="toolbar-actions" style={{ justifyContent: 'flex-end', marginBottom: '0.75rem' }}>
        <button className="primary" onClick={() => setEdit('new')}>
          {t('ADD_TYPE')}
        </button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
        <table className="detail-table">
          <thead>
            <tr>
              <th>{t('CODE')}</th>
              <th>{t('NAME')}</th>
              <th>{t('UNIT')}</th>
              <th>{t('PAID')}</th>
              <th>{t('REQUIRES_APPROVAL')}</th>
              <th>{t('ACTIVE')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {types.map((ty) => (
              <tr key={ty.leaveTypeId}>
                <td>{ty.code}</td>
                <td>{ty.name}</td>
                <td>{ty.unit === 'DAY' ? t('MODE_DAY') : t('MODE_HOUR')}</td>
                <td>{ty.paid ? '✓' : ''}</td>
                <td>{ty.requiresApproval ? '✓' : ''}</td>
                <td>{ty.active ? '✓' : ''}</td>
                <td>
                  <button onClick={() => setEdit(ty)}>{t('EDIT')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {edit && (
        <TypeModal
          initial={edit === 'new' ? null : edit}
          onClose={() => setEdit(null)}
          onDone={async () => {
            setEdit(null)
            await reload()
          }}
        />
      )}
    </>
  )
}

function TypeModal({
  initial,
  onClose,
  onDone,
}: {
  initial: LeaveType | null
  onClose: () => void
  onDone: () => Promise<void>
}) {
  const { t } = useApp()
  const [code, setCode] = useState(initial?.code ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  const [unit, setUnit] = useState<LeaveUnit>(initial?.unit ?? 'DAY')
  const [paid, setPaid] = useState(initial?.paid ?? true)
  const [requiresApproval, setRequiresApproval] = useState(initial?.requiresApproval ?? true)
  const [active, setActive] = useState(initial?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (initial) {
        await tenantLeaveApi.updateType(initial.leaveTypeId, {
          name: name.trim(),
          paid,
          unit,
          requiresApproval,
          active,
          sortOrder: initial.sortOrder,
        })
      } else {
        await tenantLeaveApi.createType({
          code: code.trim().toUpperCase(),
          name: name.trim(),
          paid,
          unit,
          requiresApproval,
          sortOrder: 0,
        })
      }
      await onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={initial ? t('EDIT') : t('ADD_TYPE')} onClose={onClose}>
      <form onSubmit={submit}>
        {!initial && (
          <label>
            {t('CODE')}
            <input value={code} onChange={(e) => setCode(e.target.value)} maxLength={30} required />
          </label>
        )}
        <label>
          {t('NAME')}
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} required />
        </label>
        <label>
          {t('UNIT')}
          <SelectField
            value={unit}
            options={UNIT_OPTIONS.map((u) => ({
              value: u,
              label: u === 'DAY' ? t('MODE_DAY') : t('MODE_HOUR'),
            }))}
            onChange={(v) => setUnit(v as LeaveUnit)}
            ariaLabel={t('UNIT')}
          />
        </label>
        <label className="check-inline">
          <input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} />
          {t('PAID')}
        </label>
        <label className="check-inline">
          <input
            type="checkbox"
            checked={requiresApproval}
            onChange={(e) => setRequiresApproval(e.target.checked)}
          />
          {t('REQUIRES_APPROVAL')}
        </label>
        {initial && (
          <label className="check-inline">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            {t('ACTIVE')}
          </label>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={busy}>
          {t('SUBMIT')}
        </button>
      </form>
    </Modal>
  )
}
