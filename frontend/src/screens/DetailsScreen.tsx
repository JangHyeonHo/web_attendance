import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { attendanceApi, languageApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField, TimeField } from '../components/fields'
import { useIsMobile } from '../hooks/useIsMobile'
import { localeOf } from '../i18n/lang'
import type { AttendanceType, DailyAttendance, DailyStampEntry, ManualReason, MonthlyResponse } from '../api/types'

/** 분 → 소수 시간(엑셀 보고서와 동일한 0.00 표기 — 서버는 수치만, 표기는 화면 책임). null은 '-' */
function formatHours(minutes: number | null): string {
  if (minutes === null) return '-'
  return (minutes / 60).toFixed(2)
}

/** 사유 선택지 — 자주 있는 "찍는 것을 잊음"이 선두, OTHER(직접 입력)만 텍스트 필수 */
const REASONS: { code: ManualReason; labelKey: string }[] = [
  { code: 'FORGOT', labelKey: 'REASON_FORGOT' },
  { code: 'DEVICE', labelKey: 'REASON_DEVICE' },
  { code: 'OFFSITE', labelKey: 'REASON_OFFSITE' },
  { code: 'OTHER', labelKey: 'REASON_OTHER' },
]

/** 이력 행의 타입 라벨 키(BREAK는 시작/종료 구분. 조퇴는 과거 데이터 표기용) */
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

/** 수정 대상(일자 상세의 [수정] → 정정 모달을 수정 모드로) */
interface EditingStamp {
  attendanceId: number
  type: AttendanceType
  /** 휴식 스탬프의 종료 여부(수정 모달의 시작/종료 라벨용) */
  breakEnd: boolean
}

/**
 * W006 출결 상세(월별). 출결 화면(W005)에서 확장 표시로도 사용되므로
 * 자신의 화면 텍스트(W006)를 언어 마스터에서 직접 취득한다(공통 텍스트는 컨텍스트 사용).
 * Phase 5.2:
 * - 정정 모달: 날짜 고정, 출근·퇴근 행을 한 그룹으로(시각은 "HH:mm" 단일 필드 — 통합 타임피커)
 * - 조퇴 UI 폐지(출근/퇴근만) — 과거 조퇴 데이터는 이력 표기만 유지
 * - 잘못 입력 복구는 [수정](시각·구분·사유 변경) — 이력 삭제는 제공하지 않는다
 */
export function DetailsScreen() {
  const { t: commonT, lang, userName, tenantName } = useApp()
  const isMobile = useIsMobile()
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1) //백엔드 계약: 1~12
  const [monthly, setMonthly] = useState<MonthlyResponse | null>(null)
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  //회사 설정: 근태 보고서 결재(도장)란 표시 여부 — 인쇄 시에만 반영
  const [stampEnabled, setStampEnabled] = useState(false)

  //일자 상세 모달(스탬프 이력)
  const [detailDate, setDetailDate] = useState<string | null>(null)
  const [detailStamps, setDetailStamps] = useState<DailyStampEntry[] | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)

  //정정 모달 — 날짜는 클릭한 날로 고정. editing이 있으면 수정 모드(단일 행)
  const [manualOpen, setManualOpen] = useState(false)
  const [manualDate, setManualDate] = useState('')
  const [editing, setEditing] = useState<EditingStamp | null>(null)
  //등록 모드: 출근/퇴근/휴식 행 토글(둘 다 잊은 날 한 번에). 기본 전부 해제 — 켠 항목만 등록(#2)
  const [inOn, setInOn] = useState(false)
  const [inTime, setInTime] = useState('09:00')
  const [outOn, setOutOn] = useState(false)
  const [outTime, setOutTime] = useState('18:00')
  const [breakOn, setBreakOn] = useState(false)
  const [breakStartTime, setBreakStartTime] = useState('12:00')
  const [breakEndTime, setBreakEndTime] = useState('13:00')
  //수정 모드: 구분(출근/퇴근/휴식)과 시각
  const [editType, setEditType] = useState<AttendanceType>('GO_TO_WORK')
  const [editTime, setEditTime] = useState('09:00')
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

  const t = useCallback((key: string) => texts[key] ?? commonT(key), [texts, commonT])

  //근태 보고서 결재란 표시 설정(회사 설정) — 인쇄 결재란 노출 판단
  useEffect(() => {
    attendanceApi
      .reportSetting()
      .then((r) => setStampEnabled(r.stampEnabled))
      .catch(() => {
        //설정 취득 실패는 무시(결재란 미표시로 안전)
      })
  }, [])

  //근태 Excel(.xlsx) 내보내기 — 세션 쿠키로 인증된 GET을 blob으로 받아 다운로드 트리거
  const exportExcel = useCallback(async () => {
    try {
      const res = await fetch(
        `/api/v1/attendance/monthly/export?year=${year}&month=${month}&lang=${lang}`,
        { credentials: 'same-origin' },
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `attendance-${year}-${String(month).padStart(2, '0')}.xlsx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [year, month])

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

  /** 날짜 버튼 클릭 → 그 날짜의 전 스탬프 이력 취득(append-only — 중복 스탬프도 전부 나온다) */
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

  /** 등록 모드로 열기(일자 상세 → [정정 등록]) */
  function openManual(date: string) {
    setManualDate(date)
    setEditing(null)
    setInOn(false) //기본 해제 — 사용자가 켠 항목만 등록 대상(#2)
    setInTime('09:00')
    setOutOn(false)
    setOutTime('18:00')
    setBreakOn(false)
    setBreakStartTime('12:00')
    setBreakEndTime('13:00')
    setReasonCode('FORGOT')
    setReasonText('')
    setManualError(null)
    setManualNotice(null)
    setDetailDate(null)
    setManualOpen(true)
  }

  /** 수정 모드로 열기(일자 상세의 MANUAL 행 [수정] — 시각·구분·사유가 채워진 채) */
  function openEdit(date: string, stamp: DailyStampEntry) {
    setManualDate(date)
    setEditing({ attendanceId: stamp.attendanceId, type: stamp.type, breakEnd: stamp.breakEnd })
    //휴식은 BREAK 유지(시각만 정정). 조퇴는 UI 폐지 — 과거 조퇴 스탬프 수정 시 퇴근으로 정리
    setEditType(stamp.type === 'GO_TO_WORK' ? 'GO_TO_WORK' : stamp.type === 'BREAK' ? 'BREAK' : 'OFF_WORK')
    setEditTime(stamp.stampedAt.slice(11, 16))
    setReasonCode((stamp.reasonCode as ManualReason) ?? 'FORGOT')
    setReasonText(stamp.reasonCode === 'OTHER' ? (stamp.reasonText ?? '') : '')
    setManualError(null)
    setManualNotice(null)
    setDetailDate(null)
    setManualOpen(true)
  }

  async function submitManual(event: FormEvent) {
    event.preventDefault()
    setManualError(null)
    setSubmitting(true)
    //사유는 한 컬럼 — 직접 입력(OTHER)을 골랐을 때만 텍스트가 사유가 된다
    const reasonTextValue = reasonCode === 'OTHER' ? reasonText.trim() : null
    try {
      const messages: string[] = []
      if (editing) {
        //수정 모드: 기존 수동 스탬프의 시각/구분/사유 변경(이력 삭제 없음)
        const response = await attendanceApi.manualUpdate(editing.attendanceId, {
          date: manualDate,
          time: editTime,
          type: editType,
          reasonCode,
          reasonText: reasonTextValue,
        })
        messages.push(response.message)
      } else {
        //등록 모드: 출근·퇴근을 한 번에(둘 다 잊은 날) — 출근 먼저 등록해 시각 순서 유지
        if (inOn) {
          const response = await attendanceApi.manual({
            date: manualDate,
            time: inTime,
            type: 'GO_TO_WORK',
            reasonCode,
            reasonText: reasonTextValue,
          })
          messages.push(response.message)
        }
        if (outOn) {
          const response = await attendanceApi.manual({
            date: manualDate,
            time: outTime,
            type: 'OFF_WORK',
            reasonCode,
            reasonText: reasonTextValue,
          })
          messages.push(response.message)
        }
        if (breakOn) {
          //휴식은 시작·종료 쌍으로 등록(단일 스탬프 정합성 회피)
          const response = await attendanceApi.manualBreak({
            date: manualDate,
            startTime: breakStartTime,
            endTime: breakEndTime,
            reasonCode,
            reasonText: reasonTextValue,
          })
          messages.push(response.message)
        }
      }
      setManualOpen(false)
      setManualNotice(messages.join(' / '))
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
          <SelectField
            compact
            value={String(year)}
            options={years.map((y) => ({ value: String(y), label: String(y) }))}
            ariaLabel={t('YEAR')}
            onChange={(v) => setYear(Number(v))}
          />
          <SelectField
            compact
            value={String(month)}
            options={Array.from({ length: 12 }, (_, i) => ({
              value: String(i + 1),
              label: String(i + 1),
            }))}
            ariaLabel={t('MONTH')}
            onChange={(v) => setMonth(Number(v))}
          />
          {/* 근무표 다운로드 — 형식(Excel/PDF)을 셀렉트로 고르면 바로 실행.
              Excel=서버 .xlsx 즉시 다운로드, PDF=브라우저 인쇄→PDF 저장. 값은 항상 빈 값으로 되돌려 라벨 유지 */}
          <SelectField
            compact
            value=""
            ariaLabel={t('DOWNLOAD_TIMESHEET')}
            options={[
              { value: '', label: t('DOWNLOAD_TIMESHEET') },
              { value: 'excel', label: 'Excel' },
              { value: 'pdf', label: 'PDF' },
            ]}
            onChange={(v) => {
              if (!monthly) return
              if (v === 'excel') void exportExcel()
              else if (v === 'pdf') window.print()
            }}
          />
        </div>
      </div>
      {/* 정정 진입은 날짜 버튼 → 일자 상세 → [정정 등록]/[수정] 단일 동선 */}
      {manualNotice && (
        <div className="banner" role="status">
          <p className="success">{manualNotice}</p>
          <button onClick={() => setManualNotice(null)}>{commonT('CLOSE')}</button>
        </div>
      )}
      {error && <p className="error center">{error}</p>}
      {loading && <p className="muted center">{commonT('LOADING')}</p>}
      {monthly && !loading && (
      <div className="printable">
        {/* 인쇄 시에만 나오는 근태 머리말(성명·회사·기간) + 결재란(설정 시) */}
        <div className="print-only att-print-head">
          <div className="att-print-id">
            <strong>{userName}{tenantName ? ` — ${tenantName}` : ''}</strong>
            <span>{t('ATTDETAILS')} — {year}. {String(month).padStart(2, '0')}</span>
          </div>
          {stampEnabled && (
            <table className="att-stamp">
              <tbody>
                <tr>
                  <td className="att-stamp-label" rowSpan={2}>{t('APPROVAL')}</td>
                  <th>{t('HR_MANAGER')}</th>
                  <th>{t('GENERAL_MANAGER')}</th>
                </tr>
                <tr>
                  <td className="att-stamp-cell"></td>
                  <td className="att-stamp-cell"></td>
                </tr>
              </tbody>
            </table>
          )}
        </div>
      {isMobile && (
        <MonthlyCards monthly={monthly} weekdayOf={weekdayOf} t={t} onOpen={openDetail} />
      )}
      {!isMobile && (
        <div className="table-wrap">
        <table className="detail-table">
          <thead>
            <tr>
              <th rowSpan={2}>{t('DATES')}</th>
              <th colSpan={2}>{t('USERSCHE')}</th>
              <th colSpan={2}>{t('INPUTTIME')}</th>
              <th rowSpan={2}>{t('BREAK_RECOGNIZED')}</th>
              <th rowSpan={2}>{t('TOTAL_WORK')}</th>
              <th rowSpan={2}>{t('REMARKS')}</th>
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
              //인정 휴게가 법정보다 큰 날 = 실휴식이 법정 초과 → 총계 감소 사유 강조(work-schedule §7-2)
              const breakOver =
                day.recognizedBreakMinutes !== null &&
                day.statutoryBreakMinutes !== null &&
                day.recognizedBreakMinutes > day.statutoryBreakMinutes
              const offDutyLabel = day.holidayName ?? (day.holiday ? t('HOLIDAY') : t('DAY_OFF'))
              //엑셀 보고서와 동일한 행 배경: 토=옅은 파랑, 일·공휴일=옅은 빨강(그 외 없음)
              const rowBg = day.holiday || weekday === 0 ? 'row-sun' : weekday === 6 ? 'row-sat' : ''
              return (
                <tr key={day.date} className={rowBg}>
                  <td className={weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''}>
                    {/* 행 전체 클릭은 스크롤 중 오조작이 있어 날짜 버튼만 진입점으로 */}
                    <button
                      type="button"
                      className="day-link"
                      onClick={() => void openDetail(day.date)}
                    >
                      {date.getDate()}({weekdayOf(date)})
                    </button>
                    {day.manual && <span className="mini-badge">{t('SOURCE_MANUAL')}</span>}
                  </td>
                  {offDuty && !hasStamps ? (
                    <>
                      <td colSpan={6} className="center muted">
                        {offDutyLabel}
                      </td>
                      <td>{day.note ?? ''}</td>
                    </>
                  ) : (
                    <>
                      <td>{day.scheduleStart ?? (offDuty ? offDutyLabel : '')}</td>
                      <td>{day.scheduleEnd ?? ''}</td>
                      <td>{day.stampIn ?? ''}</td>
                      <td>{day.stampOut ?? ''}</td>
                      <td className={breakOver ? 'break-over' : ''}>
                        {formatHours(day.recognizedBreakMinutes)}
                      </td>
                      <td>{formatHours(day.workMinutes)}</td>
                      <td>{day.note ?? ''}</td>
                    </>
                  )}
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr className="month-total">
              <td>{t('MONTH_TOTAL')}</td>
              {/* 예정근무는 스케줄 열(4칸) 아래, 인정휴게·실근무는 각 열 아래에 정렬 */}
              <td colSpan={4} className="num">
                {t('EXPECTED_WORK')} {formatHours(monthly.totalScheduledMinutes)}
              </td>
              <td>{formatHours(monthly.totalBreakMinutes)}</td>
              <td>{formatHours(monthly.totalWorkMinutes)}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
        </div>
      )}
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
              {detailStamps.map((stamp) => (
                <li key={stamp.attendanceId}>
                  <span className="stamp-time">{stamp.stampedAt.slice(11, 16)}</span>
                  <span className="stamp-type">{t(stampTypeKey(stamp))}</span>
                  {stamp.source === 'MANUAL' ? (
                    <span className="mini-badge">{t('SOURCE_MANUAL')}</span>
                  ) : (
                    <span className="muted stamp-source">{t('SOURCE_AUTO')}</span>
                  )}
                  {/* 잘못 입력 복구 = 수정(시각·구분·사유 변경) — 이력 삭제는 제공하지 않는다 */}
                  {stamp.source === 'MANUAL' && (
                    <span className="stamp-actions">
                      <button type="button" onClick={() => openEdit(detailDate, stamp)}>
                        {t('EDIT')}
                      </button>
                    </span>
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
        <Modal
          title={`${manualDate} — ${editing ? t('EDIT') : t('MANUAL_ADD')}`}
          onClose={() => setManualOpen(false)}
        >
          <form onSubmit={(e) => void submitManual(e)}>
            {editing ? (
              //수정 모드: 구분 + 시각 한 행. 휴식은 구분 전환 불가라 라벨만(시각 정정)
              <div className="manual-rows">
                <div className="manual-row">
                  {editing.type === 'BREAK' ? (
                    <span className="row-label">
                      {t('TYPE_BREAK')} {editing.breakEnd ? t('BREAK_END') : t('BREAK_START')}
                    </span>
                  ) : (
                    <SelectField
                      compact
                      value={editType}
                      options={[
                        { value: 'GO_TO_WORK', label: t('TYPE_GO') },
                        { value: 'OFF_WORK', label: t('TYPE_OFF') },
                      ]}
                      ariaLabel={t('TYPE')}
                      onChange={(v) => setEditType(v as AttendanceType)}
                    />
                  )}
                  <div className="row-controls">
                    <TimeField value={editTime} onChange={setEditTime} ariaLabel={t('TIME')} />
                  </div>
                </div>
              </div>
            ) : (
              //등록 모드: 출근/퇴근을 하나의 그룹에서 켜고 끄며 한 번에 등록
              <>
                <p className="hint">{t('MANUAL_HINT')}</p>
                <div className="manual-rows">
                  <div className={`manual-row${inOn ? '' : ' off'}`}>
                    <label className="row-check">
                      <input type="checkbox" checked={inOn} onChange={() => setInOn((v) => !v)} />
                      <span>{t('TYPE_GO')}</span>
                    </label>
                    <div className="row-controls">
                      <TimeField
                        value={inTime}
                        onChange={setInTime}
                        ariaLabel={t('TYPE_GO')}
                        disabled={!inOn}
                      />
                    </div>
                  </div>
                  <div className={`manual-row${outOn ? '' : ' off'}`}>
                    <label className="row-check">
                      <input type="checkbox" checked={outOn} onChange={() => setOutOn((v) => !v)} />
                      <span>{t('TYPE_OFF')}</span>
                    </label>
                    <div className="row-controls">
                      <TimeField
                        value={outTime}
                        onChange={setOutTime}
                        ariaLabel={t('TYPE_OFF')}
                        disabled={!outOn}
                      />
                    </div>
                  </div>
                  {/* 휴식은 시작~종료 쌍 — 한 행에서 두 시각을 함께 등록 */}
                  <div className={`manual-row${breakOn ? '' : ' off'}`}>
                    <label className="row-check">
                      <input type="checkbox" checked={breakOn} onChange={() => setBreakOn((v) => !v)} />
                      <span>{t('TYPE_BREAK')}</span>
                    </label>
                    <div className="row-controls">
                      <TimeField
                        value={breakStartTime}
                        onChange={setBreakStartTime}
                        ariaLabel={t('BREAK_START')}
                        disabled={!breakOn}
                      />
                      <span className="time-sep" aria-hidden="true">~</span>
                      <TimeField
                        value={breakEndTime}
                        onChange={setBreakEndTime}
                        ariaLabel={t('BREAK_END')}
                        disabled={!breakOn}
                      />
                    </div>
                  </div>
                </div>
              </>
            )}

            <span className="field-label">{t('REASON')}</span>
            <SelectField
              value={reasonCode}
              options={REASONS.map((reason) => ({ value: reason.code, label: t(reason.labelKey) }))}
              ariaLabel={t('REASON')}
              onChange={(v) => {
                setReasonCode(v as ManualReason)
                setReasonText('') //직접 입력에서 벗어나면 텍스트 폐기(한 컬럼 원칙)
              }}
            />
            {/* 직접 입력을 골랐을 때만 입력란이 나타난다 — 사유는 하나의 값 */}
            {reasonCode === 'OTHER' && (
              <input
                className="reason-input"
                value={reasonText}
                maxLength={200}
                placeholder={t('REASON_TEXT')}
                onChange={(e) => setReasonText(e.target.value)}
                required
                autoFocus
              />
            )}
            {manualError && <p className="error" role="alert">{manualError}</p>}
            <button
              type="submit"
              className="primary"
              disabled={
                submitting ||
                (!editing && !inOn && !outOn && !breakOn) || //등록 모드는 최소 하나
                (reasonCode === 'OTHER' && !reasonText.trim())
              }
            >
              {editing ? t('EDIT') : t('MANUAL_ADD')}
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}

/**
 * 월별 출결 — 모바일 카드 뷰(#모바일). 표를 가로 스크롤로 욱여넣지 않고 하루=한 카드로 편다.
 * 데스크톱 표와 같은 monthly 데이터를 공유하고, 날짜 카드 탭 → 같은 일자 상세 모달로 진입한다.
 */
function MonthlyCards({
  monthly,
  weekdayOf,
  t,
  onOpen,
}: {
  monthly: MonthlyResponse
  weekdayOf: (date: Date) => string
  t: (key: string) => string
  onOpen: (date: string) => void | Promise<void>
}) {
  return (
    <div className="att-cards">
      {monthly.days.map((day: DailyAttendance) => {
        const date = new Date(day.date)
        const weekday = date.getDay()
        const offDuty = day.holiday || day.dayOff
        const hasStamps = day.stampIn !== null || day.stampOut !== null
        const offDutyLabel = day.holidayName ?? (day.holiday ? t('HOLIDAY') : t('DAY_OFF'))
        const dowClass = weekday === 0 ? 'sun' : weekday === 6 ? 'sat' : ''
        return (
          <button
            key={day.date}
            type="button"
            className={`att-card${offDuty ? ' off' : ''}`}
            onClick={() => void onOpen(day.date)}
          >
            <div className="att-card-head">
              <span className={`att-card-date ${dowClass}`}>
                {date.getDate()}({weekdayOf(date)})
              </span>
              {day.manual && <span className="mini-badge">{t('SOURCE_MANUAL')}</span>}
              <span className="att-card-work">{formatHours(day.workMinutes)}</span>
            </div>
            {offDuty && !hasStamps ? (
              <div className="att-card-off muted">{offDutyLabel}</div>
            ) : (
              <dl className="att-card-body">
                <div>
                  <dt>{t('USERSCHE')}</dt>
                  <dd>{day.scheduleStart ?? '-'} ~ {day.scheduleEnd ?? '-'}</dd>
                </div>
                <div>
                  <dt>{t('INPUTTIME')}</dt>
                  <dd>{day.stampIn ?? '-'} ~ {day.stampOut ?? '-'}</dd>
                </div>
                <div>
                  <dt>{t('BREAK_RECOGNIZED')}</dt>
                  <dd>{formatHours(day.recognizedBreakMinutes)}</dd>
                </div>
                {day.note && (
                  <div>
                    <dt>{t('REMARKS')}</dt>
                    <dd>{day.note}</dd>
                  </div>
                )}
              </dl>
            )}
          </button>
        )
      })}
      {/* 월 합계 요약 카드 — 예정/휴게/실근무 */}
      <div className="att-total-card">
        <div>
          <span className="muted">{t('EXPECTED_WORK')}</span>
          <strong>{formatHours(monthly.totalScheduledMinutes)}</strong>
        </div>
        <div>
          <span className="muted">{t('BREAK_RECOGNIZED')}</span>
          <strong>{formatHours(monthly.totalBreakMinutes)}</strong>
        </div>
        <div>
          <span className="muted">{t('TOTAL_WORK')}</span>
          <strong>{formatHours(monthly.totalWorkMinutes)}</strong>
        </div>
      </div>
    </div>
  )
}
