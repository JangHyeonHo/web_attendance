import { useCallback, useEffect, useMemo, useState } from 'react'
import { tenantScheduleApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { TimeField } from './fields'
import { localeOf } from '../i18n/lang'
import type { PatternSlot } from '../api/types'

type CellType = 'none' | 'off' | 'work'
interface CellState {
  type: CellType
  start: string
  end: string
  overnight: boolean
}
const EMPTY: CellState = { type: 'none', start: '09:00', end: '18:00', overnight: false }

function hhmm(t: string | null): string {
  return t ? t.slice(0, 5) : '09:00'
}

/**
 * 반복 근무 패턴 편집기(#13) — 요일별 시간 + N주 주기. 매월 자동 반복.
 * cycle_weeks 주 × 7요일 그리드. 각 칸: 기본(패턴 없음)/휴무/근무(시업·종업, 야간 교대).
 */
export function PatternEditor({
  userId,
  userName,
  onClose,
}: {
  userId: number
  userName: string
  onClose: () => void
}) {
  const { t, lang } = useApp()
  const [cycleWeeks, setCycleWeeks] = useState(1)
  const [cells, setCells] = useState<Record<string, CellState>>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dowLabel = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    //2024-01-01 = 월요일 → dow 1..7
    return (dow: number) => fmt.format(new Date(2024, 0, dow))
  }, [lang])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setSaved(false)
    try {
      const p = await tenantScheduleApi.pattern(userId)
      const next: Record<string, CellState> = {}
      if (p) {
        setCycleWeeks(p.cycleWeeks)
        for (const s of p.slots) {
          next[`${s.weekIndex}-${s.dayOfWeek}`] = s.off
            ? { type: 'off', start: '09:00', end: '18:00', overnight: false }
            : { type: 'work', start: hhmm(s.start), end: hhmm(s.end), overnight: s.crossesMidnight }
        }
      } else {
        setCycleWeeks(1)
      }
      setCells(next)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    void load()
  }, [load])

  function setCell(key: string, patch: Partial<CellState>) {
    setCells((prev) => ({ ...prev, [key]: { ...(prev[key] ?? EMPTY), ...patch } }))
    setSaved(false)
  }

  function buildSlots(): PatternSlot[] {
    const out: PatternSlot[] = []
    for (let w = 0; w < cycleWeeks; w++) {
      for (let d = 1; d <= 7; d++) {
        const c = cells[`${w}-${d}`]
        if (!c || c.type === 'none') continue
        out.push(
          c.type === 'off'
            ? { weekIndex: w, dayOfWeek: d, off: true, start: null, end: null, crossesMidnight: false }
            : { weekIndex: w, dayOfWeek: d, off: false, start: c.start, end: c.end, crossesMidnight: c.overnight },
        )
      }
    }
    return out
  }

  function invalidWork(): boolean {
    for (let w = 0; w < cycleWeeks; w++) {
      for (let d = 1; d <= 7; d++) {
        const c = cells[`${w}-${d}`]
        if (c?.type === 'work' && !c.overnight && c.end <= c.start) return true
      }
    }
    return false
  }

  async function save(clear = false) {
    setSaving(true)
    setError(null)
    try {
      await tenantScheduleApi.savePattern(userId, {
        cycleWeeks,
        slots: clear ? [] : buildSlots(),
      })
      if (clear) {
        setCells({})
        setCycleWeeks(1)
      }
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  const weeks = Array.from({ length: cycleWeeks }, (_, i) => i)
  const days = [1, 2, 3, 4, 5, 6, 7]

  return (
    <div className="panel subscreen">
      <div className="subscreen-head">
        <button type="button" className="link subscreen-back" onClick={onClose}>{t('BACK')}</button>
        <h2>{userName} — {t('PATTERN_TITLE')}</h2>
      </div>
      <div className="rota">
        <p className="hint">{t('PATTERN_HINT')}</p>
        <label className="pattern-cycle">
          {t('CYCLE_WEEKS')}
          <select value={cycleWeeks} onChange={(e) => setCycleWeeks(Number(e.target.value))}>
            {[1, 2, 3, 4].map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </label>

        {error && <p className="error" role="alert">{error}</p>}
        {loading ? (
          <p className="muted center">…</p>
        ) : (
          <div className="rota-grid">
            {weeks.map((w) => (
              <div key={w} className="pattern-week">
                {cycleWeeks > 1 && (
                  <div className="pattern-week-head">{t('WEEK_N').replace('{n}', String(w + 1))}</div>
                )}
                {days.map((d) => {
                  const key = `${w}-${d}`
                  const c = cells[key] ?? EMPTY
                  const dowClass = d === 7 ? 'sun' : d === 6 ? 'sat' : ''
                  return (
                    <div key={key} className="rota-day">
                      <span className={`rota-day-date ${dowClass}`}>{dowLabel(d)}</span>
                      <select value={c.type} onChange={(e) => setCell(key, { type: e.target.value as CellType })}>
                        <option value="none">{t('SHIFT_DEFAULT')}</option>
                        <option value="off">{t('SHIFT_OFF')}</option>
                        <option value="work">{t('SHIFT_WORK')}</option>
                      </select>
                      {c.type === 'work' && (
                        <span className="rota-day-times">
                          <TimeField value={c.start} onChange={(v) => setCell(key, { start: v })} ariaLabel={t('WORK_START')} />
                          <span aria-hidden="true">~</span>
                          <TimeField value={c.end} onChange={(v) => setCell(key, { end: v })} ariaLabel={t('WORK_END')} />
                          <label className="check-inline">
                            <input
                              type="checkbox"
                              checked={c.overnight}
                              onChange={(e) => setCell(key, { overnight: e.target.checked })}
                            />
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
        )}

        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void save(false)} disabled={saving || loading || invalidWork()}>
            {t('SAVE')}
          </button>
          {saved && <span className="success" role="status">{t('SAVED')}</span>}
          <button onClick={() => void save(true)} disabled={saving || loading}>{t('PATTERN_CLEAR')}</button>
          <button onClick={onClose}>{t('CANCEL')}</button>
        </div>
      </div>
    </div>
  )
}
