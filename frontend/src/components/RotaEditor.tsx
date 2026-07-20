import { useCallback, useEffect, useMemo, useState } from 'react'
import { tenantScheduleApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { TimeField } from './fields'
import { localeOf } from '../i18n/lang'
import type { RotaSaveCell } from '../api/types'

type CellType = 'default' | 'off' | 'work'
interface CellState {
  type: CellType
  start: string
  end: string
  overnight: boolean
}

const DEFAULT_CELL: CellState = { type: 'default', start: '09:00', end: '18:00', overnight: false }

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
 * 월 로타 편집기(#13) — 사람별 한 달 스케줄을 날짜별로 지정.
 * 각 날짜: 기본(개인 스케줄 따름) / 휴무 / 근무(시업·종업, 야간 교대=자정 넘김).
 * 저장은 그 달을 통째 대체 — '기본'인 날은 오버라이드를 남기지 않는다.
 */
export function RotaEditor({
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
  const [cells, setCells] = useState<Record<string, CellState>>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  //채우기 도구
  const [fillDays, setFillDays] = useState<Set<number>>(new Set([1, 2, 3, 4, 5])) //월~금
  const [fillType, setFillType] = useState<CellType>('work')
  const [fillStart, setFillStart] = useState('09:00')
  const [fillEnd, setFillEnd] = useState('18:00')
  const [fillOvernight, setFillOvernight] = useState(false)

  const daysInMonth = useMemo(() => new Date(year, month, 0).getDate(), [year, month])
  const weekdayFmt = useMemo(
    () => new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' }),
    [lang],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setSaved(false)
    try {
      const rows = await tenantScheduleApi.rota(userId, year, month)
      const next: Record<string, CellState> = {}
      const dim = new Date(year, month, 0).getDate()
      for (let d = 1; d <= dim; d++) next[iso(year, month, d)] = { ...DEFAULT_CELL }
      for (const r of rows) {
        next[r.date] = r.off
          ? { type: 'off', start: '09:00', end: '18:00', overnight: false }
          : { type: 'work', start: hhmm(r.start), end: hhmm(r.end), overnight: r.crossesMidnight }
      }
      setCells(next)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [userId, year, month])

  useEffect(() => {
    void load()
  }, [load])

  function setCell(date: string, patch: Partial<CellState>) {
    setCells((prev) => ({ ...prev, [date]: { ...(prev[date] ?? DEFAULT_CELL), ...patch } }))
    setSaved(false)
  }

  function applyFill() {
    setCells((prev) => {
      const next = { ...prev }
      for (let d = 1; d <= daysInMonth; d++) {
        const date = iso(year, month, d)
        const dow = new Date(year, month - 1, d).getDay()
        if (fillDays.has(dow)) {
          next[date] =
            fillType === 'work'
              ? { type: 'work', start: fillStart, end: fillEnd, overnight: fillOvernight }
              : { type: fillType, start: '09:00', end: '18:00', overnight: false }
        }
      }
      return next
    })
    setSaved(false)
  }

  function invalidWork(): boolean {
    for (let d = 1; d <= daysInMonth; d++) {
      const c = cells[iso(year, month, d)]
      if (c?.type === 'work' && !c.overnight && c.end <= c.start) return true
    }
    return false
  }

  async function save() {
    setSaving(true)
    setError(null)
    try {
      const out: RotaSaveCell[] = []
      for (let d = 1; d <= daysInMonth; d++) {
        const date = iso(year, month, d)
        const c = cells[date]
        if (!c || c.type === 'default') continue
        out.push(
          c.type === 'off'
            ? { date, off: true, start: null, end: null, crossesMidnight: false }
            : { date, off: false, start: c.start, end: c.end, crossesMidnight: c.overnight },
        )
      }
      await tenantScheduleApi.saveRota(userId, { year, month, cells: out })
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  function shiftMonth(delta: number) {
    let m = month + delta
    let y = year
    if (m < 1) {
      m = 12
      y -= 1
    } else if (m > 12) {
      m = 1
      y += 1
    }
    setYear(y)
    setMonth(m)
  }

  const weekdayChips = [1, 2, 3, 4, 5, 6, 0] //월~일
  const rows = Array.from({ length: daysInMonth }, (_, i) => i + 1)

  return (
    <div className="panel subscreen">
      <div className="subscreen-head">
        <button type="button" className="link subscreen-back" onClick={onClose}>{t('BACK')}</button>
        <h2>{userName} — {t('ROTA_TITLE')}</h2>
      </div>
      <div className="rota">
        <div className="rota-monthnav">
          <button type="button" onClick={() => shiftMonth(-1)} aria-label="prev">‹</button>
          <strong>{year}. {pad(month)}</strong>
          <button type="button" onClick={() => shiftMonth(1)} aria-label="next">›</button>
        </div>

        {/* 요일 일괄 적용 */}
        <div className="rota-fill">
          <span className="field-label">{t('FILL_WEEKDAYS')}</span>
          <div className="weekday-row">
            {weekdayChips.map((dow) => {
              const on = fillDays.has(dow)
              const label = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
                .format(new Date(2024, 0, 7 + dow)) //2024-01-07=일
              return (
                <label key={dow} className={`weekday-chip${on ? ' on' : ''}`}>
                  <input
                    type="checkbox"
                    checked={on}
                    onChange={() => {
                      const next = new Set(fillDays)
                      if (on) next.delete(dow)
                      else next.add(dow)
                      setFillDays(next)
                    }}
                  />
                  {label}
                </label>
              )
            })}
          </div>
          <div className="rota-fill-shift">
            <select value={fillType} onChange={(e) => setFillType(e.target.value as CellType)}>
              <option value="work">{t('SHIFT_WORK')}</option>
              <option value="off">{t('SHIFT_OFF')}</option>
              <option value="default">{t('SHIFT_DEFAULT')}</option>
            </select>
            {fillType === 'work' && (
              <>
                <TimeField value={fillStart} onChange={setFillStart} ariaLabel={t('WORK_START')} />
                <span aria-hidden="true">~</span>
                <TimeField value={fillEnd} onChange={setFillEnd} ariaLabel={t('WORK_END')} />
                <label className="check-inline">
                  <input type="checkbox" checked={fillOvernight} onChange={(e) => setFillOvernight(e.target.checked)} />
                  {t('OVERNIGHT')}
                </label>
              </>
            )}
            <button type="button" onClick={applyFill}>{t('FILL_APPLY')}</button>
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {loading ? (
          <p className="muted center">…</p>
        ) : (
          <div className="rota-grid">
            {rows.map((d) => {
              const date = iso(year, month, d)
              const dow = new Date(year, month - 1, d).getDay()
              const c = cells[date] ?? DEFAULT_CELL
              const dowClass = dow === 0 ? 'sun' : dow === 6 ? 'sat' : ''
              return (
                <div key={date} className="rota-day">
                  <span className={`rota-day-date ${dowClass}`}>
                    {d}({weekdayFmt.format(new Date(year, month - 1, d))})
                  </span>
                  <select
                    value={c.type}
                    onChange={(e) => setCell(date, { type: e.target.value as CellType })}
                  >
                    <option value="default">{t('SHIFT_DEFAULT')}</option>
                    <option value="off">{t('SHIFT_OFF')}</option>
                    <option value="work">{t('SHIFT_WORK')}</option>
                  </select>
                  {c.type === 'work' && (
                    <span className="rota-day-times">
                      <TimeField value={c.start} onChange={(v) => setCell(date, { start: v })} ariaLabel={t('WORK_START')} />
                      <span aria-hidden="true">~</span>
                      <TimeField value={c.end} onChange={(v) => setCell(date, { end: v })} ariaLabel={t('WORK_END')} />
                      <label className="check-inline">
                        <input
                          type="checkbox"
                          checked={c.overnight}
                          onChange={(e) => setCell(date, { overnight: e.target.checked })}
                        />
                        {t('OVERNIGHT')}
                      </label>
                    </span>
                  )}
                </div>
              )
            })}
          </div>
        )}

        <div className="btn-row rota-actions">
          <button className="primary" onClick={() => void save()} disabled={saving || loading || invalidWork()}>
            {t('SAVE')}
          </button>
          {saved && <span className="success" role="status">{t('SAVED')}</span>}
          <button onClick={onClose}>{t('CANCEL')}</button>
        </div>
      </div>
    </div>
  )
}
