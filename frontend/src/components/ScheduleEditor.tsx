import { useCallback, useEffect, useMemo, useState } from 'react'
import { tenantScheduleApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { TimeField } from './fields'
import { localeOf } from '../i18n/lang'
import type { EffectiveDay, PatternSlot } from '../api/types'

type PatType = 'none' | 'off' | 'work'
type DayMode = 'follow' | 'off' | 'work'
interface Shift {
  start: string
  end: string
  overnight: boolean
}
interface PatCell extends Shift {
  type: PatType
}
interface DayCell extends Shift {
  mode: DayMode
}

const DEF: Shift = { start: '09:00', end: '18:00', overnight: false }
function pad(n: number): string {
  return String(n).padStart(2, '0')
}
function iso(y: number, m: number, d: number): string {
  return `${y}-${pad(m)}-${pad(d)}`
}
function hhmm(t: string | null): string {
  return t ? t.slice(0, 5) : '09:00'
}

/**
 * 통합 근무 스케줄 화면(#13) — 한 화면에서:
 *  ① 반복 패턴(요일별 시간·N주 주기) — 저장하면 매달 자동 적용.
 *  ② 월 달력 — 패턴이 적용된 실효 스케줄을 그대로 보여주고, 특정 날짜만 예외(휴무/근무)로 덮어씀.
 * 전체 화면(서브스크린)으로 표시, 모달 아님.
 */
export function ScheduleEditor({
  userId,
  userName,
  onClose,
}: {
  userId: number
  userName: string
  onClose: () => void
}) {
  const { t, lang } = useApp()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  //반복 패턴
  const [cycleWeeks, setCycleWeeks] = useState(1)
  const [pat, setPat] = useState<Record<string, PatCell>>({})
  const [patSaved, setPatSaved] = useState(false)

  //월 달력(실효 + 예외)
  const [days, setDays] = useState<Record<string, DayCell>>({})
  const [baseline, setBaseline] = useState<Record<string, EffectiveDay>>({})
  const [rotaSaved, setRotaSaved] = useState(false)

  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const daysInMonth = useMemo(() => new Date(year, month, 0).getDate(), [year, month])
  const weekdayFmt = useMemo(() => new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' }), [lang])
  const dowLabel = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return (dow: number) => fmt.format(new Date(2024, 0, dow)) //2024-01-01=월
  }, [lang])

  const loadPattern = useCallback(async () => {
    const p = await tenantScheduleApi.pattern(userId)
    const next: Record<string, PatCell> = {}
    if (p) {
      setCycleWeeks(p.cycleWeeks)
      for (const s of p.slots) {
        next[`${s.weekIndex}-${s.dayOfWeek}`] = s.off
          ? { type: 'off', ...DEF }
          : { type: 'work', start: hhmm(s.start), end: hhmm(s.end), overnight: s.crossesMidnight }
      }
    } else {
      setCycleWeeks(1)
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
            : { mode: 'work', start: hhmm(e.start), end: hhmm(e.end), overnight: e.crossesMidnight }
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

  function setPatCell(key: string, patch: Partial<PatCell>) {
    setPat((prev) => ({ ...prev, [key]: { ...(prev[key] ?? { type: 'none', ...DEF }), ...patch } }))
    setPatSaved(false)
  }
  function setDay(date: string, patch: Partial<DayCell>) {
    setDays((prev) => ({ ...prev, [date]: { ...(prev[date] ?? { mode: 'follow', ...DEF }), ...patch } }))
    setRotaSaved(false)
  }

  function baseLabel(date: string): string {
    const b = baseline[date]
    if (!b) return ''
    if (b.off) return t('SHIFT_OFF')
    const end = b.crossesMidnight ? `${pad((Number(hhmm(b.end).slice(0, 2)) + 24))}:${hhmm(b.end).slice(3)}` : hhmm(b.end)
    return `${hhmm(b.start)}~${end}`
  }

  function invalidWork(): boolean {
    for (const d of Object.values(days)) if (d.mode === 'work' && !d.overnight && d.end <= d.start) return true
    for (let w = 0; w < cycleWeeks; w++)
      for (let dd = 1; dd <= 7; dd++) {
        const c = pat[`${w}-${dd}`]
        if (c?.type === 'work' && !c.overnight && c.end <= c.start) return true
      }
    return false
  }

  async function savePattern(clear = false) {
    setBusy(true)
    setError(null)
    try {
      const slots: PatternSlot[] = []
      if (!clear) {
        for (let w = 0; w < cycleWeeks; w++)
          for (let d = 1; d <= 7; d++) {
            const c = pat[`${w}-${d}`]
            if (!c || c.type === 'none') continue
            slots.push(
              c.type === 'off'
                ? { weekIndex: w, dayOfWeek: d, off: true, start: null, end: null, crossesMidnight: false }
                : { weekIndex: w, dayOfWeek: d, off: false, start: c.start, end: c.end, crossesMidnight: c.overnight },
            )
          }
      }
      await tenantScheduleApi.savePattern(userId, { cycleWeeks, slots })
      if (clear) {
        setPat({})
        setCycleWeeks(1)
      }
      setPatSaved(true)
      await loadMonth() //달력이 새 패턴을 반영
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function saveRota() {
    setBusy(true)
    setError(null)
    try {
      const cells = []
      for (let d = 1; d <= daysInMonth; d++) {
        const date = iso(year, month, d)
        const c = days[date]
        if (!c || c.mode === 'follow') continue //패턴/기본 따름 = 오버라이드 없음
        cells.push(
          c.mode === 'off'
            ? { date, off: true, start: null, end: null, crossesMidnight: false }
            : { date, off: false, start: c.start, end: c.end, crossesMidnight: c.overnight },
        )
      }
      await tenantScheduleApi.saveRota(userId, { year, month, cells })
      setRotaSaved(true)
      await loadMonth()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  function shiftMonth(delta: number) {
    let m = month + delta
    let y = year
    if (m < 1) { m = 12; y -= 1 }
    else if (m > 12) { m = 1; y += 1 }
    setYear(y)
    setMonth(m)
  }

  const weeks = Array.from({ length: cycleWeeks }, (_, i) => i)
  const rows = Array.from({ length: daysInMonth }, (_, i) => i + 1)

  return (
    <div className="panel subscreen">
      <div className="subscreen-head">
        <button type="button" className="link subscreen-back" onClick={onClose}>{t('BACK')}</button>
        <h2>{userName} — {t('SCHEDULE_TITLE')}</h2>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {/* ① 반복 패턴 */}
      <section className="sched-section">
        <h3 className="section-head">{t('PATTERN_SECTION')}</h3>
        <p className="hint">{t('PATTERN_HINT')}</p>
        <label className="pattern-cycle">
          {t('CYCLE_WEEKS')}
          <select value={cycleWeeks} onChange={(e) => setCycleWeeks(Number(e.target.value))}>
            {[1, 2, 3, 4].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <div className="rota-grid">
          {weeks.map((w) => (
            <div key={w} className="pattern-week">
              {cycleWeeks > 1 && <div className="pattern-week-head">{t('WEEK_N').replace('{n}', String(w + 1))}</div>}
              {[1, 2, 3, 4, 5, 6, 7].map((d) => {
                const key = `${w}-${d}`
                const c = pat[key] ?? { type: 'none' as PatType, ...DEF }
                const dowClass = d === 7 ? 'sun' : d === 6 ? 'sat' : ''
                return (
                  <div key={key} className="rota-day">
                    <span className={`rota-day-date ${dowClass}`}>{dowLabel(d)}</span>
                    <select value={c.type} onChange={(e) => setPatCell(key, { type: e.target.value as PatType })}>
                      <option value="none">{t('SHIFT_DEFAULT')}</option>
                      <option value="off">{t('SHIFT_OFF')}</option>
                      <option value="work">{t('SHIFT_WORK')}</option>
                    </select>
                    {c.type === 'work' && (
                      <span className="rota-day-times">
                        <TimeField value={c.start} onChange={(v) => setPatCell(key, { start: v })} ariaLabel={t('WORK_START')} />
                        <span aria-hidden="true">~</span>
                        <TimeField value={c.end} onChange={(v) => setPatCell(key, { end: v })} ariaLabel={t('WORK_END')} />
                        <label className="check-inline">
                          <input type="checkbox" checked={c.overnight} onChange={(e) => setPatCell(key, { overnight: e.target.checked })} />
                          {t('OVERNIGHT')}
                        </label>
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void savePattern(false)} disabled={busy || loading || invalidWork()}>
            {t('SAVE_PATTERN')}
          </button>
          {patSaved && <span className="success" role="status">{t('SAVED')}</span>}
          <button onClick={() => void savePattern(true)} disabled={busy || loading}>{t('PATTERN_CLEAR')}</button>
        </div>
      </section>

      {/* ② 월 달력(예외) */}
      <section className="sched-section">
        <h3 className="section-head">{t('ROTA_SECTION')}</h3>
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
              const overridden = c.mode !== 'follow'
              return (
                <div key={date} className={`rota-day${overridden ? ' overridden' : ''}`}>
                  <span className={`rota-day-date ${dowClass}`}>{d}({weekdayFmt.format(new Date(year, month - 1, d))})</span>
                  <select value={c.mode} onChange={(e) => setDay(date, { mode: e.target.value as DayMode })}>
                    <option value="follow">{t('FOLLOW_PATTERN')}</option>
                    <option value="off">{t('SHIFT_OFF')}</option>
                    <option value="work">{t('SHIFT_WORK')}</option>
                  </select>
                  {c.mode === 'follow' ? (
                    <span className="rota-baseline muted">→ {baseLabel(date)}</span>
                  ) : c.mode === 'work' ? (
                    <span className="rota-day-times">
                      <TimeField value={c.start} onChange={(v) => setDay(date, { start: v })} ariaLabel={t('WORK_START')} />
                      <span aria-hidden="true">~</span>
                      <TimeField value={c.end} onChange={(v) => setDay(date, { end: v })} ariaLabel={t('WORK_END')} />
                      <label className="check-inline">
                        <input type="checkbox" checked={c.overnight} onChange={(e) => setDay(date, { overnight: e.target.checked })} />
                        {t('OVERNIGHT')}
                      </label>
                    </span>
                  ) : null}
                </div>
              )
            })}
          </div>
        )}
        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void saveRota()} disabled={busy || loading || invalidWork()}>
            {t('SAVE_ROTA')}
          </button>
          {rotaSaved && <span className="success" role="status">{t('SAVED')}</span>}
          <button onClick={onClose}>{t('BACK')}</button>
        </div>
      </section>
    </div>
  )
}
