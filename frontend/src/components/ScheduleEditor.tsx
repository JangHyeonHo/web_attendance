import { useCallback, useEffect, useMemo, useState } from 'react'
import { tenantScheduleApi, tenantMemberApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from './Modal'
import { TimeField } from './fields'
import { localeOf } from '../i18n/lang'
import type { EffectiveDay, MemberSummary, PatternSlot } from '../api/types'

type PatType = 'none' | 'off' | 'work'
type DayMode = 'follow' | 'off' | 'work'
interface Shift { start: string; end: string; night: boolean }
interface PatCell extends Shift { type: PatType }
interface DayCell extends Shift { mode: DayMode }

const DEF: Shift = { start: '09:00', end: '18:00', night: false }
const pad = (n: number) => String(n).padStart(2, '0')
const iso = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`
const hhmm = (t: string | null) => (t ? t.slice(0, 5) : '09:00')

/**
 * 근무 스케줄 화면(#13) — 한 화면에서:
 *  ① 정기 스케줄(요일별, 매주 반복)  ② 상세 스케줄(일별 예외)  ③ 같은 정기 스케줄을 다른 멤버에 일괄 적용.
 * 전체 화면(서브스크린).
 */
export function ScheduleEditor({
  userId,
  userName,
  userEmail,
  onClose,
}: {
  userId: number
  userName: string
  userEmail: string
  onClose: () => void
}) {
  const { t, lang } = useApp()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const [pat, setPat] = useState<Record<number, PatCell>>({})
  const [patSaved, setPatSaved] = useState(false)
  const [days, setDays] = useState<Record<string, DayCell>>({})
  const [baseline, setBaseline] = useState<Record<string, EffectiveDay>>({})
  const [rotaSaved, setRotaSaved] = useState(false)

  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  //멤버 공통 적용
  const [pickOpen, setPickOpen] = useState(false)
  const [memberList, setMemberList] = useState<MemberSummary[]>([])
  const [picked, setPicked] = useState<Set<number>>(new Set())
  const [query, setQuery] = useState('')
  const [applyMsg, setApplyMsg] = useState<string | null>(null)

  const daysInMonth = useMemo(() => new Date(year, month, 0).getDate(), [year, month])
  const weekdayFmt = useMemo(() => new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' }), [lang])
  const dowLabel = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return (dow: number) => fmt.format(new Date(2024, 0, dow)) //2024-01-01=월
  }, [lang])

  const loadPattern = useCallback(async () => {
    const p = await tenantScheduleApi.pattern(userId)
    const next: Record<number, PatCell> = {}
    if (p) {
      for (const s of p.slots) {
        next[s.dayOfWeek] = s.off
          ? { type: 'off', ...DEF }
          : { type: 'work', start: hhmm(s.start), end: hhmm(s.end), night: s.crossesMidnight }
      }
    }
    setPat(next)
  }, [userId])

  const loadMonth = useCallback(async () => {
    const eff = await tenantScheduleApi.effective(userId, year, month)
    const base: Record<string, EffectiveDay> = {}
    const dc: Record<string, DayCell> = {}
    for (const e of eff) {
      base[e.date] = e
      dc[e.date] =
        e.source === 'OVERRIDE'
          ? e.off
            ? { mode: 'off', ...DEF }
            : { mode: 'work', start: hhmm(e.start), end: hhmm(e.end), night: e.crossesMidnight }
          : { mode: 'follow', ...DEF }
    }
    setBaseline(base)
    setDays(dc)
  }, [userId, year, month])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setPatSaved(false)
    setRotaSaved(false)
    try {
      await Promise.all([loadPattern(), loadMonth()])
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [loadPattern, loadMonth])

  useEffect(() => {
    void load()
  }, [load])

  const setPatCell = (dow: number, patch: Partial<PatCell>) => {
    setPat((p) => ({ ...p, [dow]: { ...(p[dow] ?? { type: 'none', ...DEF }), ...patch } }))
    setPatSaved(false)
  }
  const setDay = (date: string, patch: Partial<DayCell>) => {
    setDays((p) => ({ ...p, [date]: { ...(p[date] ?? { mode: 'follow', ...DEF }), ...patch } }))
    setRotaSaved(false)
  }

  function baseLabel(date: string): string {
    const b = baseline[date]
    if (!b) return ''
    if (b.off) return t('SHIFT_OFF')
    const e = hhmm(b.end)
    const end = b.crossesMidnight ? `${pad(Number(e.slice(0, 2)) + 24)}:${e.slice(3)}` : e
    return `${hhmm(b.start)}~${end}`
  }

  function slots(): PatternSlot[] {
    const out: PatternSlot[] = []
    for (let d = 1; d <= 7; d++) {
      const c = pat[d]
      if (!c || c.type === 'none') continue
      out.push(
        c.type === 'off'
          ? { weekIndex: 0, dayOfWeek: d, off: true, start: null, end: null, crossesMidnight: false }
          : { weekIndex: 0, dayOfWeek: d, off: false, start: c.start, end: c.end, crossesMidnight: c.night },
      )
    }
    return out
  }

  function invalid(): boolean {
    for (let d = 1; d <= 7; d++) {
      const c = pat[d]
      if (c?.type === 'work' && !c.night && c.end <= c.start) return true
    }
    for (const c of Object.values(days)) if (c.mode === 'work' && !c.night && c.end <= c.start) return true
    return false
  }

  async function saveRegular(clear = false) {
    setBusy(true)
    setError(null)
    try {
      await tenantScheduleApi.savePattern(userId, { cycleWeeks: 1, slots: clear ? [] : slots() })
      if (clear) setPat({})
      setPatSaved(true)
      await loadMonth()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  //현재 월의 상세(예외) 셀 — 저장·타 멤버 적용 공용
  function detailCells() {
    const cells = []
    for (let d = 1; d <= daysInMonth; d++) {
      const date = iso(year, month, d)
      const c = days[date]
      if (!c || c.mode === 'follow') continue
      cells.push(
        c.mode === 'off'
          ? { date, off: true, start: null, end: null, crossesMidnight: false }
          : { date, off: false, start: c.start, end: c.end, crossesMidnight: c.night },
      )
    }
    return cells
  }

  async function saveDetail() {
    setBusy(true)
    setError(null)
    try {
      await tenantScheduleApi.saveRota(userId, { year, month, cells: detailCells() })
      setRotaSaved(true)
      await loadMonth()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function openPicker() {
    setApplyMsg(null)
    setPicked(new Set())
    setQuery('')
    setPickOpen(true)
    try {
      setMemberList(await tenantMemberApi.list())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  async function applyToSelected() {
    const targets = [...picked]
    setBusy(true)
    setError(null)
    try {
      //이 스케줄 전체(정기 + 현재 월 상세)를 대상 멤버에 그대로 복사
      const pattern = { cycleWeeks: 1, slots: slots() }
      const cells = detailCells()
      for (const id of targets) {
        await tenantScheduleApi.savePattern(id, pattern)
        await tenantScheduleApi.saveRota(id, { year, month, cells })
      }
      setPickOpen(false)
      setApplyMsg(t('APPLIED_N').replace('{n}', String(targets.length)))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  function shiftMonth(delta: number) {
    let m = month + delta
    let y = year
    if (m < 1) { m = 12; y -= 1 } else if (m > 12) { m = 1; y += 1 }
    setYear(y)
    setMonth(m)
  }

  const filtered = memberList
    .filter((m) => m.userId !== userId)
    .filter((m) => !query.trim() || `${m.name} ${m.email}`.toLowerCase().includes(query.trim().toLowerCase()))
  const rows = Array.from({ length: daysInMonth }, (_, i) => i + 1)

  const nightBox = (checked: boolean, onChange: (v: boolean) => void) => (
    <label className="check-inline">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {t('NIGHT_WORK')}
    </label>
  )

  return (
    <div className="panel subscreen">
      <div className="subscreen-head">
        <button type="button" className="link subscreen-back" onClick={onClose}>{t('BACK')}</button>
        <div>
          <h2>{t('SCHEDULE_TITLE')} <span className="subscreen-who">({userName} : {userEmail})</span></h2>
          <p className="subscreen-sub muted">{t('SCHEDULE_SUBTITLE')}</p>
        </div>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {/* ① 정기 스케줄 */}
      <section className="sched-section">
        <h3 className="section-head">{t('REGULAR_SCHEDULE')}</h3>
        <p className="hint">{t('REGULAR_HINT')}</p>
        <div className="rota-grid">
          {[1, 2, 3, 4, 5, 6, 7].map((d) => {
            const c = pat[d] ?? { type: 'none' as PatType, ...DEF }
            const dowClass = d === 7 ? 'sun' : d === 6 ? 'sat' : ''
            return (
              <div key={d} className="rota-day">
                <span className={`rota-day-date ${dowClass}`}>{dowLabel(d)}</span>
                <select value={c.type} onChange={(e) => setPatCell(d, { type: e.target.value as PatType })}>
                  <option value="none">{t('SHIFT_DEFAULT')}</option>
                  <option value="off">{t('SHIFT_OFF')}</option>
                  <option value="work">{t('SHIFT_WORK')}</option>
                </select>
                {c.type === 'work' && (
                  <span className="rota-day-times">
                    <TimeField value={c.start} onChange={(v) => setPatCell(d, { start: v })} ariaLabel={t('WORK_START')} />
                    <span aria-hidden="true">~</span>
                    <TimeField value={c.end} onChange={(v) => setPatCell(d, { end: v })} ariaLabel={t('WORK_END')} />
                    {nightBox(c.night, (v) => setPatCell(d, { night: v }))}
                  </span>
                )}
              </div>
            )
          })}
        </div>
        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void saveRegular(false)} disabled={busy || loading || invalid()}>
            {t('SAVE_REGULAR')}
          </button>
          {patSaved && <span className="success" role="status">{t('SAVED')}</span>}
          <button onClick={() => void saveRegular(true)} disabled={busy || loading}>{t('REGULAR_CLEAR')}</button>
        </div>
      </section>

      {/* ② 상세 스케줄(일별) */}
      <section className="sched-section">
        <h3 className="section-head">{t('DETAIL_SCHEDULE')}</h3>
        <p className="hint">{t('DETAIL_HINT')}</p>
        <div className="rota-monthnav">
          <button type="button" onClick={() => shiftMonth(-1)} aria-label="prev">‹</button>
          <strong>{year}. {pad(month)}</strong>
          <button type="button" onClick={() => shiftMonth(1)} aria-label="next">›</button>
        </div>
        {loading ? (
          <p className="muted center">…</p>
        ) : (
          <div className="rota-grid">
            {rows.map((d) => {
              const date = iso(year, month, d)
              const dow = new Date(year, month - 1, d).getDay()
              const c = days[date] ?? { mode: 'follow' as DayMode, ...DEF }
              const dowClass = dow === 0 ? 'sun' : dow === 6 ? 'sat' : ''
              return (
                <div key={date} className={`rota-day${c.mode !== 'follow' ? ' overridden' : ''}`}>
                  <span className={`rota-day-date ${dowClass}`}>{d}({weekdayFmt.format(new Date(year, month - 1, d))})</span>
                  <select value={c.mode} onChange={(e) => setDay(date, { mode: e.target.value as DayMode })}>
                    <option value="follow">{t('FOLLOW_REGULAR')}</option>
                    <option value="work">{t('SHIFT_WORK')}</option>
                    <option value="off">{t('SHIFT_OFF')}</option>
                  </select>
                  {c.mode === 'follow' && <span className="rota-baseline muted">→ {baseLabel(date)}</span>}
                  {c.mode === 'work' && (
                    <span className="rota-day-times">
                      <TimeField value={c.start} onChange={(v) => setDay(date, { start: v })} ariaLabel={t('WORK_START')} />
                      <span aria-hidden="true">~</span>
                      <TimeField value={c.end} onChange={(v) => setDay(date, { end: v })} ariaLabel={t('WORK_END')} />
                      {nightBox(c.night, (v) => setDay(date, { night: v }))}
                    </span>
                  )}
                </div>
              )
            })}
          </div>
        )}
        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void saveDetail()} disabled={busy || loading || invalid()}>
            {t('SAVE_DETAIL')}
          </button>
          {rotaSaved && <span className="success" role="status">{t('SAVED')}</span>}
        </div>
      </section>

      {/* ③ 다른 멤버에 정기 스케줄 일괄 적용 */}
      <section className="sched-section">
        <h3 className="section-head">{t('APPLY_TITLE')}</h3>
        <p className="hint">{t('APPLY_HINT')}</p>
        <div className="btn-row rota-actions">
          <button onClick={() => void openPicker()} disabled={busy || loading}>{t('SELECT_MEMBERS')}</button>
          {applyMsg && <span className="success" role="status">{applyMsg}</span>}
        </div>
      </section>

      {pickOpen && (
        <Modal title={t('SELECT_MEMBERS')} onClose={() => setPickOpen(false)}>
          <input
            className="reason-input"
            placeholder={t('SEARCH_MEMBER')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
          <div className="member-pick-list">
            {filtered.map((m) => {
              const on = picked.has(m.userId)
              return (
                <label key={m.userId} className="check-inline member-pick">
                  <input
                    type="checkbox"
                    checked={on}
                    onChange={() => {
                      const next = new Set(picked)
                      if (on) next.delete(m.userId)
                      else next.add(m.userId)
                      setPicked(next)
                    }}
                  />
                  <span>{m.name} <span className="muted">{m.email}</span></span>
                </label>
              )
            })}
            {filtered.length === 0 && <p className="muted center">{t('EMPTY')}</p>}
          </div>
          <div className="btn-row">
            <button className="primary" disabled={busy || picked.size === 0} onClick={() => void applyToSelected()}>
              {t('APPLY_TO_SELECTED')} {picked.size > 0 ? `(${t('SELECTED_N').replace('{n}', String(picked.size))})` : ''}
            </button>
            <button onClick={() => setPickOpen(false)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
