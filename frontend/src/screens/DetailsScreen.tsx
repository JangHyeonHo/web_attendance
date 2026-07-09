import { useEffect, useMemo, useState } from 'react'
import { attendanceApi, languageApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { localeOf } from '../i18n/lang'
import type { MonthlyResponse } from '../api/types'

/** 분 → "h:mm" 조립(서버는 로케일 무관 수치만 — 표기는 화면 책임). null은 '-' */
function formatMinutes(minutes: number | null): string {
  if (minutes === null) return '-'
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

/**
 * W006 출결 상세(월별). 출결 화면(W005)에서 확장 표시로도 사용되므로
 * 자신의 화면 텍스트(W006)를 언어 마스터에서 직접 취득한다(공통 텍스트는 컨텍스트 사용).
 * 레이아웃(CR3-7): 데이터 열 = 스케줄 2 + 실적 2 + 실휴식/법정휴게/총근무 3 = 7열,
 * 휴일 행은 colSpan 7 통합 셀에 holidayName(공휴일 명칭) ?? HOLIDAY 라벨(개인 휴일) 폴백.
 */
export function DetailsScreen() {
  const { t: commonT, lang } = useApp()
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1) //백엔드 계약: 1~12
  const [monthly, setMonthly] = useState<MonthlyResponse | null>(null)
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  //W006 화면 텍스트 취득(언어 변경시 재취득)
  useEffect(() => {
    let cancelled = false
    languageApi
      .texts('W006', lang)
      .then((response) => {
        if (!cancelled) setTexts(response)
      })
      .catch(() => {
        //텍스트 취득 실패시 키 이름이 그대로 표시된다(치명적이지 않음)
      })
    return () => {
      cancelled = true
    }
  }, [lang])

  const t = (key: string) => texts[key] ?? commonT(key)

  //요일 명칭은 사전 없이 Intl 표준 API로 생성
  const weekdayOf = useMemo(() => {
    const format = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return (date: Date) => format.format(date)
  }, [lang])

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
      {loading && <p className="muted center">{commonT('LOADING')}</p>}
      {monthly && !loading && (
        <table className="detail-table">
          <thead>
            <tr>
              <th rowSpan={2}>{t('DATES')}</th>
              <th colSpan={2}>{t('USERSCHE')}</th>
              <th colSpan={2}>{t('INPUTTIME')}</th>
              <th rowSpan={2}>{t('BREAK_ACTUAL')}</th>
              <th rowSpan={2}>{t('BREAK_STATUTORY')}</th>
              <th rowSpan={2}>{t('TOTAL_WORK')}</th>
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
              //실휴식이 법정 휴게를 초과한 날은 강조 — "왜 총계가 줄었는지" 시인성(work-schedule §7-2)
              const breakOver =
                day.breakMinutes !== null &&
                day.statutoryBreakMinutes !== null &&
                day.breakMinutes > day.statutoryBreakMinutes
              return (
                <tr key={day.date} className={day.holiday ? 'holiday' : ''}>
                  <td className={weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}>
                    {date.getDate()}({weekdayOf(date)})
                  </td>
                  {day.holiday ? (
                    //공휴일이면 명칭(holidayName), 개인 휴일(work_schedule.holiday)은 기존 라벨 폴백
                    <td colSpan={7} className="center muted">
                      {day.holidayName ?? t('HOLIDAY')}
                    </td>
                  ) : (
                    <>
                      <td>{day.scheduleStart ?? ''}</td>
                      <td>{day.scheduleEnd ?? ''}</td>
                      <td>{day.stampIn ?? ''}</td>
                      <td>{day.stampOut ?? ''}</td>
                      <td className={breakOver ? 'break-over' : ''}>
                        {formatMinutes(day.breakMinutes)}
                      </td>
                      <td>{formatMinutes(day.statutoryBreakMinutes)}</td>
                      <td>{formatMinutes(day.workMinutes)}</td>
                    </>
                  )}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr className="month-total">
              <td colSpan={7}>{t('MONTH_TOTAL')}</td>
              <td>{formatMinutes(monthly.totalWorkMinutes)}</td>
            </tr>
          </tfoot>
        </table>
      )}
    </div>
  )
}
