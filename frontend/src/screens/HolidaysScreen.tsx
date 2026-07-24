import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantHolidayApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { DateField } from '../components/DateField'
import { IconButton } from '../components/IconButton'
import { ConfirmModal } from '../components/ConfirmModal'
import { EmptyState } from '../components/EmptyState'
import { ScreenGuide } from '../components/ScreenGuide'
import { localeOf } from '../i18n/lang'
import type { HolidayEntry, HolidaySyncResult } from '../api/types'

const TYPE_LABEL_KEYS: Record<HolidayEntry['holidayType'], string> = {
  NATIONAL: 'TYPE_NATIONAL',
  COMPANY: 'TYPE_COMPANY',
}

/** SYNC_DONE 문구의 카운트 치환은 프론트 문자열 replace(서버 메시지 조립 대상 아님 — holiday-plan §7-2) */
function syncDoneText(template: string, result: HolidaySyncResult): string {
  return template
    .replace('{inserted}', String(result.inserted))
    .replace('{deleted}', String(result.deleted))
    .replace('{skipped}', String(result.skippedCompany))
}

/**
 * T002 공휴일 관리 — TENANT_ADMIN 전용(holiday-plan §5-1).
 * 국가 공휴일(NATIONAL)은 읽기전용(동기화만 관리) + 회사 공휴일(COMPANY)은 등록·수정·삭제 가능(#7·#8).
 * 같은 날짜 중복 등록 허용(예: 창립기념일 + 광복절). 회사 공휴일은 매년 반복 지정 가능하며(#8)
 * 각 연도 인스턴스는 독립 행이라 연도별로 날짜/명칭 이동·삭제가 가능하다. 수정/삭제는 아이콘+툴팁.
 */
export function HolidaysScreen() {
  const { t, lang } = useApp()
  const currentYear = new Date().getFullYear()
  //허용 연도 = 현재 −1 ~ +2 (동기화 범위 §2-6과 동일)
  const years = useMemo(
    () => Array.from({ length: 4 }, (_, i) => currentYear - 1 + i),
    [currentYear],
  )
  const [year, setYear] = useState(currentYear)
  const [holidays, setHolidays] = useState<HolidayEntry[]>([])
  const [listError, setListError] = useState<string | null>(null)

  //동기화
  const [syncConfirm, setSyncConfirm] = useState(false)
  const [syncResult, setSyncResult] = useState<HolidaySyncResult | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [syncing, setSyncing] = useState(false)

  //등록 모달(항상 COMPANY)
  const [formOpen, setFormOpen] = useState(false)
  const [newDate, setNewDate] = useState('')
  const [newName, setNewName] = useState('')
  const [newRecurring, setNewRecurring] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  //회사 공휴일 수정 모달(#8) — 개별 인스턴스(날짜/명칭/반복)
  const [editTarget, setEditTarget] = useState<HolidayEntry | null>(null)
  const [editDate, setEditDate] = useState('')
  const [editName, setEditName] = useState('')
  const [editRecurring, setEditRecurring] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({})

  //회사 공휴일 삭제(확인 모달) — 국가 공휴일은 삭제 불가
  const [deleteTarget, setDeleteTarget] = useState<HolidayEntry | null>(null)
  const [rowError, setRowError] = useState<string | null>(null)

  function openEdit(holiday: HolidayEntry) {
    setEditTarget(holiday)
    setEditDate(holiday.holidayDate)
    setEditName(holiday.holidayName)
    setEditRecurring(holiday.recurring)
    setEditError(null)
    setEditFieldErrors({})
  }

  //요일 명칭은 사전 없이 Intl 표준 API로 생성(M002 방식).
  //'YYYY-MM-DD'를 new Date(문자열)로 넘기면 UTC 자정 해석 — 음수 오프셋 시간대에서 전날 요일이 되므로 로컬 성분 생성
  const weekdayOf = useMemo(() => {
    const format = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return (date: string) => {
      const [y, m, d] = date.split('-').map(Number)
      return format.format(new Date(y, m - 1, d))
    }
  }, [lang])

  const reload = useCallback(async () => {
    try {
      setHolidays(await tenantHolidayApi.list(year))
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [year])

  useEffect(() => {
    //연도 전환 시 일회성 상태(결과·확인 모달) 정리 후 재조회
    setSyncResult(null)
    setSyncError(null)
    setSyncConfirm(false)
    setDeleteTarget(null)
    setEditTarget(null)
    setRowError(null)
    void reload()
  }, [reload])

  async function runDelete(holidayId: number) {
    setDeleteTarget(null)
    setRowError(null)
    try {
      await tenantHolidayApi.remove(holidayId)
      await reload()
    } catch (e) {
      setRowError(e instanceof ApiError ? e.message : String(e))
    }
  }

  async function runSync() {
    setSyncConfirm(false)
    setSyncResult(null)
    setSyncError(null)
    setSyncing(true)
    try {
      setSyncResult(await tenantHolidayApi.sync(year))
      await reload()
    } catch (e) {
      //502 HOLIDAY_SYNC_UPSTREAM / 400 HOLIDAY_YEAR_RANGE — 배너 표시(DB 무변경 계약)
      setSyncError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setSyncing(false)
    }
  }

  async function onCreate(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await tenantHolidayApi.create({
        holidayDate: newDate,
        holidayName: newName.trim(),
        recurring: newRecurring,
      })
      setNewDate('')
      setNewName('')
      setNewRecurring(false)
      setFormOpen(false)
      await reload()
    } catch (e) {
      if (e instanceof ApiError) {
        setFormError(e.message)
        if (e.fieldErrors) {
          const byField: Record<string, string> = {}
          for (const fe of e.fieldErrors) {
            byField[fe.field] = fe.message
          }
          setFieldErrors(byField)
        }
      } else {
        setFormError(String(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function onUpdate(event: FormEvent) {
    event.preventDefault()
    if (!editTarget) return
    setEditError(null)
    setEditFieldErrors({})
    setSubmitting(true)
    try {
      await tenantHolidayApi.update(editTarget.holidayId, {
        holidayDate: editDate,
        holidayName: editName.trim(),
        recurring: editRecurring,
      })
      setEditTarget(null)
      await reload()
    } catch (e) {
      if (e instanceof ApiError) {
        setEditError(e.message)
        if (e.fieldErrors) {
          const byField: Record<string, string> = {}
          for (const fe of e.fieldErrors) {
            byField[fe.field] = fe.message
          }
          setEditFieldErrors(byField)
        }
      } else {
        setEditError(String(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('HOLIDAYS_TITLE')}</h2>
        <div className="toolbar-actions">
          <select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label={t('YEAR')}>
            {years.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
          <button onClick={() => setSyncConfirm(true)} disabled={syncing}>
            {t('SYNC')}
          </button>
          <button className="primary" onClick={() => setFormOpen(true)} disabled={submitting}>
            {t('ADD_HOLIDAY')}
          </button>
        </div>
      </div>
      <ScreenGuide>{t('SCREEN_GUIDE')}</ScreenGuide>

      {syncConfirm && (
        <ConfirmModal
          title={t('SYNC')}
          danger
          confirmLabel={t('SYNC')}
          cancelLabel={t('CANCEL')}
          onConfirm={() => void runSync()}
          onClose={() => setSyncConfirm(false)}
        >
          <p className="center">{t('SYNC_CONFIRM')}</p>
        </ConfirmModal>
      )}
      {syncResult && (
        <p className="success center" role="status">
          {syncDoneText(t('SYNC_DONE'), syncResult)}
        </p>
      )}
      {syncError && <p className="error center" role="alert">{syncError}</p>}

      {formOpen && (
        <Modal title={t('ADD_HOLIDAY')} onClose={() => setFormOpen(false)}>
          <form onSubmit={onCreate}>
            <label>
              {t('DATE')}
              <DateField value={newDate} onChange={setNewDate} ariaLabel={t('DATE')} />
              {fieldErrors.holidayDate && <span className="error">{fieldErrors.holidayDate}</span>}
            </label>
            <label>
              {t('NAME')}
              <input value={newName} onChange={(e) => setNewName(e.target.value)} required />
              {fieldErrors.holidayName && <span className="error">{fieldErrors.holidayName}</span>}
            </label>
            <label className="check-inline">
              <input
                type="checkbox"
                checked={newRecurring}
                onChange={(e) => setNewRecurring(e.target.checked)}
              />
              {t('RECURRING')}
            </label>
            <p className="hint">{t('RECURRING_HINT')}</p>
            {formError && <p className="error" role="alert">{formError}</p>}
            <button type="submit" className="primary" disabled={submitting}>
              {t('ADD_HOLIDAY')}
            </button>
          </form>
        </Modal>
      )}

      {editTarget && (
        <Modal title={t('EDIT_HOLIDAY')} onClose={() => setEditTarget(null)}>
          <form onSubmit={onUpdate}>
            <label>
              {t('DATE')}
              <DateField value={editDate} onChange={setEditDate} ariaLabel={t('DATE')} />
              {editFieldErrors.holidayDate && <span className="error">{editFieldErrors.holidayDate}</span>}
            </label>
            <label>
              {t('NAME')}
              <input value={editName} onChange={(e) => setEditName(e.target.value)} required />
              {editFieldErrors.holidayName && <span className="error">{editFieldErrors.holidayName}</span>}
            </label>
            <label className="check-inline">
              <input
                type="checkbox"
                checked={editRecurring}
                onChange={(e) => setEditRecurring(e.target.checked)}
              />
              {t('RECURRING')}
            </label>
            <p className="hint">{t('RECURRING_HINT')}</p>
            {editError && <p className="error" role="alert">{editError}</p>}
            {/* 수정 모달의 제출은 '수정' — 등록 모달과 라벨을 구분(행위 라벨) */}
            <button type="submit" className="primary" disabled={submitting}>
              {t('EDIT')}
            </button>
          </form>
        </Modal>
      )}

      {deleteTarget && (
        <ConfirmModal
          title={t('DELETE')}
          subject={`${deleteTarget.holidayDate} ${deleteTarget.holidayName}`}
          hint={t('DELETE_CONFIRM')}
          danger
          confirmLabel={t('DELETE')}
          cancelLabel={t('CANCEL')}
          onConfirm={() => void runDelete(deleteTarget.holidayId)}
          onClose={() => setDeleteTarget(null)}
        />
      )}

      {listError && <p className="error" role="alert">{listError}</p>}
      {rowError && <p className="error" role="alert">{rowError}</p>}

      {holidays.length === 0 && !listError ? (
        <EmptyState>{t('EMPTY')}</EmptyState>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('DATE')}</th>
                <th>{t('NAME')}</th>
                <th>{t('TYPE')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {holidays.map((holiday) => {
                const national = holiday.holidayType === 'NATIONAL'
                return (
                  <tr key={holiday.holidayId}>
                    <td>
                      {holiday.holidayDate}({weekdayOf(holiday.holidayDate)})
                    </td>
                    <td>
                      {holiday.holidayName}
                      {holiday.recurring && (
                        <span className="badge badge-recurring" title={t('RECURRING_HINT')}>
                          {t('RECURRING')}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${national ? 'badge-national' : 'badge-company'}`}>
                        {t(TYPE_LABEL_KEYS[holiday.holidayType])}
                      </span>
                    </td>
                    <td>
                      {/* 회사 공휴일만 수정/삭제 — 국가 공휴일은 동기화만 관리(읽기전용, #7·#8).
                          아이콘+툴팁으로 국가 공휴일 행과 높이를 맞춘다(텍스트 버튼 제거).
                          flex는 div에만 — td에 직접 걸면 table-cell이 깨져 테두리가 어긋난다(#10). */}
                      {!national && (
                        <div className="row-actions">
                          <IconButton icon="edit" label={t('EDIT')} onClick={() => openEdit(holiday)} />
                          <IconButton
                            icon="delete"
                            label={t('DELETE')}
                            danger
                            onClick={() => setDeleteTarget(holiday)}
                          />
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
