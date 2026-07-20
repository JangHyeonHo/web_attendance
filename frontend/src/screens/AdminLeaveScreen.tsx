import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantLeaveApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField } from '../components/fields'
import { DateField } from '../components/DateField'
import { formatLeaveAmount } from '../util/leaveFormat'
import type {
  LeaveRequestItem,
  LeaveType,
  LeaveUnit,
  MemberLeaveDetail,
  MemberLeaveSummary,
} from '../api/types'

type Tab = 'decide' | 'members' | 'types'

/** t()로 단위 라벨을 채운 수량 포매터를 만든다(각 탭 컴포넌트에서 사용). */
function useAmountFormatter(t: (key: string) => string) {
  const labels = { day: t('UNIT_DAY'), hour: t('UNIT_HOUR'), min: t('UNIT_MIN') }
  return (minutes: number, unit: LeaveUnit, dayMinutes: number) =>
    formatLeaveAmount(minutes, unit, dayMinutes, labels)
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
  const [tab, setTab] = useState<Tab>('decide')

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
      </div>
      <div className="seg-toggle" role="tablist">
        <button className={tab === 'decide' ? 'active' : ''} onClick={() => setTab('decide')}>
          {t('TAB_DECIDE')}
        </button>
        <button className={tab === 'members' ? 'active' : ''} onClick={() => setTab('members')}>
          {t('TAB_MEMBERS')}
        </button>
        <button className={tab === 'types' ? 'active' : ''} onClick={() => setTab('types')}>
          {t('TAB_TYPES')}
        </button>
      </div>

      {/* 결재: 신규 신청 승인 + 취소 신청 승인을 한 곳에서(#10 탭 통합) */}
      {tab === 'decide' && (
        <>
          <h3 className="section-head">{t('TAB_APPROVALS')}</h3>
          <ApprovalsTab />
          <h3 className="section-head">{t('TAB_CANCELS')}</h3>
          <CancellationsTab />
        </>
      )}
      {tab === 'members' && <MembersTab />}
      {tab === 'types' && <TypesTab />}
    </div>
  )
}

// ===== 승인 대기 =====

function ApprovalsTab() {
  const { t } = useApp()
  const amt = useAmountFormatter(t)
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
                  <td className="num">{amt(r.minutes, r.unit, 480)}</td>
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

// ===== 취소 신청 =====

function CancellationsTab() {
  const { t } = useApp()
  const amt = useAmountFormatter(t)
  const [rows, setRows] = useState<LeaveRequestItem[]>([])
  //현재/예정 휴가자(APPROVED) — 관리자 직접 취소용(#11)
  const [approved, setApproved] = useState<LeaveRequestItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [rowError, setRowError] = useState<{ id: number; message: string } | null>(null)
  //직접 취소 사유 모달
  const [cancelTarget, setCancelTarget] = useState<LeaveRequestItem | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const reload = useCallback(async () => {
    try {
      const [cr, ap] = await Promise.all([tenantLeaveApi.cancelRequests(), tenantLeaveApi.approved()])
      setRows(cr)
      setApproved(ap)
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function act(fn: () => Promise<unknown>, id: number) {
    setRowError(null)
    try {
      await fn()
      await reload()
    } catch (e) {
      setRowError({ id, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  const periodText = (r: LeaveRequestItem) =>
    r.dayUnit
      ? `${dateOf(r.startAt)}${r.halfDay ? ` (${t('HALF_DAY')})` : ''}`
      : `${dateOf(r.startAt)} ${r.startAt.slice(11, 16)}~${r.endAt.slice(11, 16)}`

  return (
    <>
      {error && <p className="error" role="alert">{error}</p>}

      <h3 className="section-head">{t('CANCEL_REQUESTS_TITLE')}</h3>
      {rows.length === 0 ? (
        <p className="muted center">{t('NO_CANCELS')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('MEMBER')}</th>
                <th>{t('LEAVE_TYPE')}</th>
                <th>{t('PERIOD')}</th>
                <th>{t('AMOUNT')}</th>
                <th>{t('CANCEL_REASON')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.leaveRequestId}>
                  <td>{r.userName}</td>
                  <td>{r.typeName}</td>
                  <td className="wrap">{dateOf(r.startAt)}</td>
                  <td className="num">{amt(r.minutes, r.unit, 480)}</td>
                  <td className="wrap">{r.cancelReason ?? ''}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        className="primary"
                        onClick={() =>
                          void act(
                            () => tenantLeaveApi.cancel(r.leaveRequestId, r.cancelReason || '-'),
                            r.leaveRequestId,
                          )
                        }
                      >
                        {t('CANCEL_APPROVE')}
                      </button>
                      <button
                        onClick={() =>
                          void act(() => tenantLeaveApi.rejectCancel(r.leaveRequestId, ''), r.leaveRequestId)
                        }
                      >
                        {t('CANCEL_REJECT')}
                      </button>
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

      {/* 현재/예정 휴가자 — 당일이라 멤버가 취소 신청을 못 만든 경우에도 관리자가 직접 취소(#11) */}
      <h3 className="section-head" style={{ marginTop: '1.5rem' }}>{t('CURRENT_LEAVES')}</h3>
      {approved.length === 0 ? (
        <p className="muted center">{t('NO_CURRENT_LEAVES')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('MEMBER')}</th>
                <th>{t('LEAVE_TYPE')}</th>
                <th>{t('PERIOD')}</th>
                <th>{t('AMOUNT')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {approved.map((r) => (
                <tr key={r.leaveRequestId}>
                  <td>{r.userName}</td>
                  <td>{r.typeName}</td>
                  <td className="wrap">{periodText(r)}</td>
                  <td className="num">{amt(r.minutes, r.unit, 480)}</td>
                  <td>
                    <div className="row-actions">
                      <button onClick={() => { setCancelTarget(r); setCancelReason('') }}>
                        {t('CANCEL_LEAVE')}
                      </button>
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

      {cancelTarget && (
        <Modal title={t('CANCEL_LEAVE')} onClose={() => setCancelTarget(null)} danger>
          <p className="center">{cancelTarget.userName} — {cancelTarget.typeName} ({periodText(cancelTarget)})</p>
          <label>
            {t('CANCEL_REASON')}
            <input value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} maxLength={200} autoFocus />
          </label>
          <div className="btn-row">
            <button
              className="primary"
              disabled={!cancelReason.trim()}
              onClick={() => {
                const id = cancelTarget.leaveRequestId
                const reason = cancelReason.trim()
                setCancelTarget(null)
                void act(() => tenantLeaveApi.cancel(id, reason), id)
              }}
            >
              {t('CANCEL_LEAVE')}
            </button>
            <button onClick={() => setCancelTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </>
  )
}

// ===== 멤버 잔여 =====

function MembersTab() {
  const { t } = useApp()
  const amt = useAmountFormatter(t)
  const [members, setMembers] = useState<MemberLeaveSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<MemberLeaveDetail | null>(null)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

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

  return (
    <>
      {/* 법정 연차 '제안/재계산'은 요구조건 재정의 전까지 보류(#11) — 부여는 아래 '일괄 부여'/개별 부여로 */}
      <div className="toolbar-actions" style={{ justifyContent: 'flex-end', marginBottom: '0.75rem' }}>
        <button
          className="primary"
          onClick={() => { setNotice(null); setBulkOpen(true) }}
          disabled={members.length === 0}
        >
          {t('BULK_GRANT')}
        </button>
      </div>
      {notice && (
        <div className="banner" role="status">
          <p className="success">{notice}</p>
          <button onClick={() => setNotice(null)}>{t('CLOSE')}</button>
        </div>
      )}
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
                      ? amt(m.annualRemainingMinutes, 'DAY', m.standardDayMinutes)
                      : '—'}
                  </td>
                  <td>
                    <button onClick={() => void openDetail(m.userId)}>{t('EDIT')}</button>
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

      {bulkOpen && (
        <BulkGrantModal
          members={members}
          onClose={() => setBulkOpen(false)}
          onDone={async (count) => {
            setBulkOpen(false)
            setNotice(`${t('BULK_GRANT')} — ${count}`)
            await reload()
          }}
        />
      )}
    </>
  )
}

/** 일괄 부여 모달(#9) — 멤버 다중 선택 + 종류·일수·만기·메모 → 한 번에 부여. */
function BulkGrantModal({
  members,
  onClose,
  onDone,
}: {
  members: MemberLeaveSummary[]
  onClose: () => void
  onDone: (count: number) => Promise<void>
}) {
  const { t } = useApp()
  const [types, setTypes] = useState<LeaveType[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [leaveTypeId, setLeaveTypeId] = useState(0)
  const [days, setDays] = useState('1')
  const [expiresOn, setExpiresOn] = useState('')
  const [memo, setMemo] = useState('')
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    tenantLeaveApi
      .types()
      .then((list) => {
        setTypes(list)
        setLeaveTypeId((cur) => (cur === 0 ? list[0]?.leaveTypeId ?? 0 : cur))
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  //대규모 인원(수천 명)에서도 이름 검색으로 일부만 골라 부여할 수 있게(#9).
  //전체 선택은 "검색 결과 전체"에 대해 동작한다(가려진 인원을 실수로 포함/제외하지 않게).
  const q = query.trim().toLowerCase()
  const filtered = q
    ? members.filter((m) => m.name.toLowerCase().includes(q))
    : members
  const allChecked = filtered.length > 0 && filtered.every((m) => selected.has(m.userId))

  function toggle(userId: number) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(userId)) next.delete(userId)
      else next.add(userId)
      return next
    })
  }

  function toggleAll() {
    setSelected((prev) => {
      const next = new Set(prev)
      if (allChecked) filtered.forEach((m) => next.delete(m.userId))
      else filtered.forEach((m) => next.add(m.userId))
      return next
    })
  }

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      const result = await tenantLeaveApi.grantBulk({
        userIds: [...selected],
        leaveTypeId,
        days: Number(days),
        expiresOn: expiresOn || null,
        memo: memo.trim() || null,
      })
      await onDone(result.count)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={t('BULK_GRANT')} onClose={onClose}>
      <div className="field-group">
        <SelectField
          value={String(leaveTypeId)}
          options={types.map((ty) => ({ value: String(ty.leaveTypeId), label: ty.name }))}
          onChange={(v) => setLeaveTypeId(Number(v))}
          ariaLabel={t('LEAVE_TYPE')}
        />
        <label>
          {t('GRANT_MINUTES')}
          <input type="number" step="0.5" value={days} onChange={(e) => setDays(e.target.value)} />
        </label>
        <label>
          {t('EXPIRES')}
          <DateField value={expiresOn} onChange={setExpiresOn} ariaLabel={t('EXPIRES')} />
        </label>
      </div>
      <input
        placeholder={t('MEMO')}
        value={memo}
        onChange={(e) => setMemo(e.target.value)}
        maxLength={200}
      />

      {/* 대규모 인원 대비 이름 검색(#9) — 결과 안에서만 전체 선택/해제 */}
      <input
        className="member-search"
        style={{ marginTop: '0.75rem' }}
        placeholder={t('MEMBER_SEARCH')}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <label className="check-inline">
        <input type="checkbox" checked={allChecked} onChange={toggleAll} />
        {t('SELECT_ALL')} ({selected.size}/{members.length})
      </label>
      <div className="checklist">
        {filtered.length === 0 ? (
          <p className="muted center" style={{ margin: '0.5rem 0' }}>{t('EMPTY')}</p>
        ) : (
          filtered.map((m) => (
            <label key={m.userId} className="check-inline">
              <input
                type="checkbox"
                checked={selected.has(m.userId)}
                onChange={() => toggle(m.userId)}
              />
              {m.name}
            </label>
          ))
        )}
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      <button
        className="primary"
        disabled={busy || selected.size === 0 || leaveTypeId === 0}
        onClick={() => void submit()}
      >
        {t('GRANT')}
      </button>
    </Modal>
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
  const amt = useAmountFormatter(t)
  const [grantTypeId, setGrantTypeId] = useState(detail.balances[0]?.leaveTypeId ?? 0)
  const [grantDays, setGrantDays] = useState('1')
  const [memo, setMemo] = useState('')
  const [cancelId, setCancelId] = useState<number | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const cancelable = detail.requests.filter(
    (r) => r.status === 'APPROVED' || r.status === 'CANCEL_REQUESTED',
  )

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

      {/* 입사일은 멤버 관리 화면에서 등록/수정(#11) — 여기서는 참고 표시만 */}
      <p className="muted" style={{ marginTop: 0 }}>
        {t('START_DATE')}: {detail.hireDate ?? '—'}
      </p>

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
              <td className="num">{amt(b.grantedMinutes, b.unit, b.standardDayMinutes)}</td>
              <td className="num">{amt(b.usedMinutes, b.unit, b.standardDayMinutes)}</td>
              <td className="num">{amt(b.remainingMinutes, b.unit, b.standardDayMinutes)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {cancelable.length > 0 && (
        <>
          <h4 className="section-head">{t('CANCEL_LEAVE')}</h4>
          <table className="detail-table compact">
            <tbody>
              {cancelable.map((r) => (
                <tr key={r.leaveRequestId}>
                  <td className="wrap">{dateOf(r.startAt)}</td>
                  <td className="num">{amt(r.minutes, r.unit, detail.standardDayMinutes)}</td>
                  <td>{t(`STATUS_${r.status}`)}</td>
                  <td>
                    {cancelId === r.leaveRequestId ? (
                      <div className="row-actions">
                        <input
                          placeholder={t('CANCEL_REASON')}
                          value={cancelReason}
                          onChange={(e) => setCancelReason(e.target.value)}
                          maxLength={200}
                          autoFocus
                        />
                        <button
                          type="button"
                          className="primary"
                          disabled={busy || !cancelReason.trim()}
                          onClick={() =>
                            void run(() =>
                              tenantLeaveApi.cancel(r.leaveRequestId, cancelReason.trim()),
                            ).then(() => {
                              setCancelId(null)
                              setCancelReason('')
                            })
                          }
                        >
                          {t('SUBMIT')}
                        </button>
                        <button type="button" onClick={() => setCancelId(null)}>
                          {t('CANCEL')}
                        </button>
                      </div>
                    ) : (
                      <button type="button" onClick={() => { setCancelId(r.leaveRequestId); setCancelReason('') }}>
                        {t('CANCEL_LEAVE')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

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
              <th>{t('NAME')}</th>
              <th>{t('PAID')}</th>
              <th>{t('REQUIRES_APPROVAL')}</th>
              <th>{t('ACTIVE')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {types.map((ty) => (
              <tr key={ty.leaveTypeId}>
                <td>{ty.name}</td>
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
  const [name, setName] = useState(initial?.name ?? '')
  const [paid, setPaid] = useState(initial?.paid ?? true)
  const [hourlyEnabled, setHourlyEnabled] = useState(initial?.hourlyEnabled ?? false)
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
          unit: 'DAY',
          hourlyEnabled,
          requiresApproval,
          active,
          sortOrder: initial.sortOrder,
        })
      } else {
        //코드는 서버 자동생성(#10) — 명칭만 전송
        await tenantLeaveApi.createType({
          name: name.trim(),
          paid,
          unit: 'DAY',
          hourlyEnabled,
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
        {/* 코드는 서버 자동생성(#10) — 사용자는 명칭만 입력 */}
        <label>
          {t('NAME')}
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} required autoFocus />
        </label>
        <label className="check-inline">
          <input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} />
          {t('PAID')}
        </label>
        <label className="check-inline">
          <input
            type="checkbox"
            checked={hourlyEnabled}
            onChange={(e) => setHourlyEnabled(e.target.checked)}
          />
          {t('HOURLY_ENABLED')}
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
