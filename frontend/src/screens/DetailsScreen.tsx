import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { attendanceApi, languageApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { localeOf } from '../i18n/lang'
import type {
  AttendanceType,
  DailyStampEntry,
  ManualReason,
  MonthlyResponse,
} from '../api/types'

/** 분 → "h:mm" 조립(서버는 로케일 무관 수치만 — 표기는 화면 책임). null은 '-' */
function formatMinutes(minutes: number | null): string {
  if (minutes === null) return '-'
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

/** 정정 등록 가능한 타입(BREAK는 서버도 400 — manual-attendance §3) */
const MANUAL_TYPES: { type: AttendanceType; labelKey: string }[] = [
  { type: 'GO_TO_WORK', labelKey: 'TYPE_GO' },
  { type: 'OFF_WORK', labelKey: 'TYPE_OFF' },
  { type: 'EARLY_DEPARTURE', labelKey: 'TYPE_EARLY' },
]

/** 사유 선택지 — 자주 있는 "찍는 것을 잊음"이 선두, OTHER는 상세 입력 필수 */
const REASONS: { code: ManualReason; labelKey: string }[] = [
  { code: 'FORGOT', labelKey: 'REASON_FORGOT' },
  { code: 'DEVICE', labelKey: 'REASON_DEVICE' },
  { code: 'OFFSITE', labelKey: 'REASON_OFFSITE' },
  { code: 'OTHER', labelKey: 'REASON_OTHER' },
]

/** 이력 행의 타입 라벨 키(BREAK는 시작/종료 구분) */
function stampTypeKey(stamp: DailyStampEntry): string {
  switch (stamp.type) {
    case 'GO_TO_WORK':
      return 'TYPE_GO'
    case 'OFF_WORK':
      return 'TYPE_OFF'
    case 'EARLY_DEPARTURE':
      return 'TYPE_EARLY'
    case 'BREAK':
      return stamp.breakEnd ? 'TYPE_BREAK_END' : 'TYPE_BREAK_START'
  }
}

/**
 * W006 출결 상세(월별). 출결 화면(W005)에서 확장 표시로도 사용되므로
 * 자신의 화면 텍스트(W006)를 언어 마스터에서 직접 취득한다(공통 텍스트는 컨텍스트 사용).
 * Phase 5: 행 클릭 → 일자 상세 모달(전 스탬프 이력 — 수동/자동·사유), [정정 등록] 모달,
 * 휴일·휴무(dayOff) 날도 스탬프가 있으면 시각·근무시간을 표시한다.
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

  //일자 상세 모달(스탬프 이력)
  const [detailDate, setDetailDate] = useState<string | null>(null)
  const [detailStamps, setDetailStamps] = useState<DailyStampEntry[] | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)

  //정정 등록 모달
  const [manualOpen, setManualOpen] = useState(false)
  const [manualDate, setManualDate] = useState('')
  const [manualTime, setManualTime] = useState('09:00')
  const [manualType, setManualType] = useState<AttendanceType>('GO_TO_WORK')
  const [reasonCode, setReasonCode] = useState<ManualReason>('FORGOT')
  const [reasonText, setReasonText] = useState('')
  const [manualError, setManualError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [manualNotice, setManualNotice] = useState<string | null>(null)

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

  const t = useCallback(
    (key: string) => texts[key] ?? commonT(key),
    [texts, commonT],
  )

  //요일 명칭은 사전 없이 Intl 표준 API로 생성
  const weekdayOf = useMemo(() => {
    const format = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return (date: Date) => format.format(date)
  }, [lang])

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setMonthly(await attendanceApi.monthly(year, month))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [year, month])

  useEffect(() => {
    void reload()
  }, [reload])

  /** 행 클릭 → 그 날짜의 전 스탬프 이력 취득(append-only — 중복 스탬프도 전부 나온다) */
  async function openDetail(date: string) {
    setDetailDate(date)
    setDetailStamps(null)
    setDetailError(null)
    try {
      const response = await attendanceApi.daily(date)
      setDetailStamps(response.stamps)
    } catch (e) {
      setDetailError(e instanceof ApiError ? e.message : String(e))
    }
  }

  function openManual(date: string) {
    setManualDate(date)
    setManualTime('09:00')
    setManualType('GO_TO_WORK')
    setReasonCode('FORGOT')
    setReasonText('')
    setManualError(null)
    setManualNotice(null)
    setDetailDate(null)
    setManualOpen(true)
  }

  async function submitManual(event: FormEvent) {
    event.preventDefault()
    setManualError(null)
    setSubmitting(true)
    try {
      const response = await attendanceApi.manual({
        date: manualDate,
        time: manualTime,
        type: manualType,
        reasonCode,
        //사유는 한 컬럼 — 직접 입력(OTHER)을 골랐을 때만 텍스트가 사유가 된다
        reasonText: reasonCode === 'OTHER' ? reasonText.trim() : null,
      })
      setManualOpen(false)
      setManualNotice(response.message)
      await reload()
    } catch (e) {
      setManualError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  const years = Array.from({ length: 5 }, (_, i) => today.getFullYear() - 3 + i)

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('ATTDETAILS')}</h2>
        <div className="toolbar-actions">
          <select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label={t('YEAR')}>
            {years.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
          <select value={month} onChange={(e) => setMonth(Number(e.target.value))} aria-label={t('MONTH')}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
      </div>
      {/* 정정 진입은 날짜 클릭 → 일자 상세 → [정정 등록] 단일 동선(날짜가 먼저 정해지는 UX) */}
      {manualNotice && (
        <div className="banner" role="status">
          <p className="success">{manualNotice}</p>
          <button onClick={() => setManualNotice(null)}>{commonT('CLOSE')}</button>
        </div>
      )}
      {error && <p className="error center">{error}</p>}
      {loading && <p className="muted center">{commonT('LOADING')}</p>}
      {monthly && !loading && (
        <div className="table-wrap">
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
              const offDuty = day.holiday || day.dayOff
              //휴일·휴무여도 스탬프가 있으면 통상 열로 표시(휴일 근무 — manual-attendance §4)
              const hasStamps = day.stampIn !== null || day.stampOut !== null
              //실휴식이 법정 휴게를 초과한 날은 강조 — "왜 총계가 줄었는지" 시인성(work-schedule §7-2)
              const breakOver =
                day.breakMinutes !== null &&
                day.statutoryBreakMinutes !== null &&
                day.breakMinutes > day.statutoryBreakMinutes
              const offDutyLabel = day.holidayName ?? (day.holiday ? t('HOLIDAY') : t('DAY_OFF'))
              return (
                <tr
                  key={day.date}
                  className={`day-row${offDuty ? ' holiday' : ''}`}
                  onClick={() => void openDetail(day.date)}
                >
                  <td className={weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}>
                    {date.getDate()}({weekdayOf(date)})
                    {day.manual && <span className="mini-badge">{t('SOURCE_MANUAL')}</span>}
                  </td>
                  {offDuty && !hasStamps ? (
                    <td colSpan={7} className="center muted">
                      {offDutyLabel}
                    </td>
                  ) : (
                    <>
                      <td>{day.scheduleStart ?? (offDuty ? offDutyLabel : '')}</td>
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
        </div>
      )}

      {detailDate && (
        <Modal title={`${detailDate} — ${t('DAY_DETAIL')}`} onClose={() => setDetailDate(null)}>
          {detailError && <p className="error" role="alert">{detailError}</p>}
          {detailStamps && detailStamps.length === 0 && (
            <p className="muted center">{t('EMPTY')}</p>
          )}
          {detailStamps && detailStamps.length > 0 && (
            <ul className="stamp-list">
              {detailStamps.map((stamp, index) => (
                <li key={index}>
                  <span className="stamp-time">{stamp.stampedAt.slice(11, 16)}</span>
                  <span className="stamp-type">{t(stampTypeKey(stamp))}</span>
                  {stamp.source === 'MANUAL' ? (
                    <span className="mini-badge">{t('SOURCE_MANUAL')}</span>
                  ) : (
                    <span className="muted stamp-source">{t('SOURCE_AUTO')}</span>
                  )}
                  {stamp.reasonCode && (
                    <span className="stamp-reason muted">
                      {t(`REASON_${stamp.reasonCode}`)}
                      {stamp.reasonText ? ` — ${stamp.reasonText}` : ''}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
          <div className="btn-row">
            <button className="primary" onClick={() => openManual(detailDate)}>
              {t('MANUAL_ADD')}
            </button>
            <button onClick={() => setDetailDate(null)}>{commonT('CLOSE')}</button>
          </div>
        </Modal>
      )}

      {manualOpen && (
        <Modal title={t('MANUAL_ADD')} onClose={() => setManualOpen(false)}>
          <form onSubmit={(e) => void submitManual(e)}>
            <div className="field-row">
              <label>
                {t('DATE')}
                <input
                  type="date"
                  value={manualDate}
                  max={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setManualDate(e.target.value)}
                  required
                />
              </label>
              <label>
                {t('TIME')}
                <input
                  type="time"
                  value={manualTime}
                  onChange={(e) => setManualTime(e.target.value)}
                  required
                />
              </label>
            </div>
            <label>
              {t('TYPE')}
              <select
                value={manualType}
                onChange={(e) => setManualType(e.target.value as AttendanceType)}
              >
                {MANUAL_TYPES.map((option) => (
                  <option key={option.type} value={option.type}>
                    {t(option.labelKey)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('REASON')}
              <select
                value={reasonCode}
                onChange={(e) => {
                  setReasonCode(e.target.value as ManualReason)
                  setReasonText('') //직접 입력에서 벗어나면 텍스트 폐기(한 컬럼 원칙)
                }}
              >
                {REASONS.map((reason) => (
                  <option key={reason.code} value={reason.code}>
                    {t(reason.labelKey)}
                  </option>
                ))}
              </select>
              {/* 직접 입력을 골랐을 때만 입력란이 나타난다 — 사유는 하나의 값 */}
              {reasonCode === 'OTHER' && (
                <input
                  value={reasonText}
                  maxLength={200}
                  placeholder={t('REASON_TEXT')}
                  onChange={(e) => setReasonText(e.target.value)}
                  required
                  autoFocus
                />
              )}
            </label>
            {manualError && <p className="error" role="alert">{manualError}</p>}
            <button type="submit" className="primary" disabled={submitting}>
              {t('MANUAL_ADD')}
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}
