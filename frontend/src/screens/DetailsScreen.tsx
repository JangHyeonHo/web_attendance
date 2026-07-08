import { useEffect, useState } from 'react'
import { attendanceApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { Lang, MonthlyResponse } from '../api/types'

const WEEKDAYS: Record<Lang, string[]> = {
  KOR: ['일', '월', '화', '수', '목', '금', '토'],
  ENG: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  JPN: ['日', '月', '火', '水', '木', '金', '土'],
}

/** W006 출결 상세(월별). 출결 화면에서 확장 표시로도 사용된다. */
export function DetailsScreen() {
  const { t, lang } = useApp()
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1) //백엔드 계약: 1~12
  const [monthly, setMonthly] = useState<MonthlyResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    attendanceApi
      .monthly(year, month)
      .then((response) => {
        if (!cancelled) setMonthly(response)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : String(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [year, month])

  const years = Array.from({ length: 5 }, (_, i) => today.getFullYear() - 3 + i)
  const weekdays = WEEKDAYS[lang]

  return (
    <div className="panel">
      <h2 className="center">{t('ATTDETAILS')}</h2>
      <div className="selectors center">
        <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
          {years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
        <span>{t('YEAR')}</span>
        <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <span>{t('MONTH')}</span>
      </div>
      {error && <p className="error center">{error}</p>}
      {loading && <p className="muted center">{t('LOADING')}</p>}
      {monthly && !loading && (
        <table className="detail-table">
          <thead>
            <tr>
              <th rowSpan={2}>{t('DATES')}</th>
              <th colSpan={2}>{t('USERSCHE')}</th>
              <th colSpan={2}>{t('INPUTTIME')}</th>
            </tr>
            <tr>
              <th>{t('SCHE_IN')}</th>
              <th>{t('SCHE_OUT')}</th>
              <th>{t('SCHE_IN')}</th>
              <th>{t('SCHE_OUT')}</th>
            </tr>
          </thead>
          <tbody>
            {monthly.days.map((day) => {
              const date = new Date(day.date)
              const weekday = date.getDay()
              return (
                <tr key={day.date} className={day.holiday ? 'holiday' : ''}>
                  <td className={weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}>
                    {date.getDate()}({weekdays[weekday]})
                  </td>
                  {day.holiday ? (
                    <td colSpan={4} className="center muted">
                      {t('HOLIDAY')}
                    </td>
                  ) : (
                    <>
                      <td>{day.scheduleStart ?? ''}</td>
                      <td>{day.scheduleEnd ?? ''}</td>
                      <td>{day.stampIn ?? ''}</td>
                      <td>{day.stampOut ?? ''}</td>
                    </>
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
