import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantHolidayApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { DateField } from '../components/DateField'
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
 * W013 공휴일 관리 — TENANT_ADMIN 전용(holiday-plan §5-1).
 * 연도별 목록(NATIONAL/COMPANY 뱃지) + 회사 공휴일 등록 + 명칭 수정/삭제 + 국가 공휴일 동기화.
 * Phase 4: 등록 폼·동기화 확인·수정/삭제 확인을 전부 모달로 이전(테이블 내 확장 행 폐지).
 * NATIONAL 행 조작은 "재동기화 시 복원" 경고(SYNC_REVERT_WARN)를 확인 모달에 함께 표시한다.
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
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  //행 조작(수정/삭제 — 확인 모달)
  const [editTarget, setEditTarget] = useState<{ holiday: HolidayEntry; name: string } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<HolidayEntry | null>(null)
  const [rowError, setRowError] = useState<{ date: string; message: string } | null>(null)

  //요일 명칭은 사전 없이 Intl 표준 API로 생성(W006 방식).
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
    //연도 전환 시 일회성 상태(결과·확인 모달·행 조작) 정리 후 재조회
    setSyncResult(null)
    setSyncError(null)
    setSyncConfirm(false)
    setEditTarget(null)
    setDeleteTarget(null)
    setRowError(null)
    void reload()
  }, [reload])

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
      await tenantHolidayApi.create({ holidayDate: newDate, holidayName: newName.trim() })
      setNewDate('')
      setNewName('')
      setFormOpen(false)
      await reload()
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'HOLIDAY_DATE_DUPLICATED') {
          //409 중복은 날짜 필드 에러로 표시(holiday-plan §5-1)
          setFieldErrors({ holidayDate: e.message })
        } else {
          setFormError(e.message)
          if (e.fieldErrors) {
            const byField: Record<string, string> = {}
            for (const fe of e.fieldErrors) {
              byField[fe.field] = fe.message
            }
            setFieldErrors(byField)
          }
        }
      } else {
        setFormError(String(e))
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function saveEdit() {
    if (!editTarget) return
    setRowError(null)
    try {
      await tenantHolidayApi.update(editTarget.holiday.holidayDate, {
        holidayName: editTarget.name.trim(),
      })
      setEditTarget(null)
      await reload()
    } catch (e) {
      setRowError({
        date: editTarget.holiday.holidayDate,
        message: e instanceof ApiError ? e.message : String(e),
      })
      setEditTarget(null)
    }
  }

  async function runDelete(holidayDate: string) {
    setDeleteTarget(null)
    setRowError(null)
    try {
      await tenantHolidayApi.remove(holidayDate)
      await reload()
    } catch (e) {
      setRowError({ date: holidayDate, message: e instanceof ApiError ? e.message : String(e) })
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

      {syncConfirm && (
        <Modal title={t('SYNC')} onClose={() => setSyncConfirm(false)} danger>
          <p className="center">{t('SYNC_CONFIRM')}</p>
          <div className="btn-row">
            <button className="primary" onClick={() => void runSync()}>
              {t('SUBMIT')}
            </button>
            <button onClick={() => setSyncConfirm(false)}>{t('CANCEL')}</button>
          </div>
        </Modal>
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
            {formError && <p className="error" role="alert">{formError}</p>}
            <button type="submit" className="primary" disabled={submitting}>
              {t('ADD_HOLIDAY')}
            </button>
          </form>
        </Modal>
      )}

      {editTarget && (
        <Modal title={t('EDIT')} onClose={() => setEditTarget(null)}>
          {/* 명칭만 수정 가능 — 유형 변경 불가(holiday-plan §3-3) */}
          {editTarget.holiday.holidayType === 'NATIONAL' && (
            <p className="hint">{t('SYNC_REVERT_WARN')}</p>
          )}
          <label>
            {t('NAME')}
            <input
              value={editTarget.name}
              onChange={(e) => setEditTarget({ ...editTarget, name: e.target.value })}
              required
            />
          </label>
          <div className="btn-row">
            <button className="primary" onClick={() => void saveEdit()} disabled={!editTarget.name.trim()}>
              {t('SUBMIT')}
            </button>
            <button onClick={() => setEditTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      {deleteTarget && (
        <Modal title={t('DELETE')} onClose={() => setDeleteTarget(null)} danger>
          <p className="center">
            {deleteTarget.holidayName} — {t('DELETE_CONFIRM')}
          </p>
          {deleteTarget.holidayType === 'NATIONAL' && (
            <p className="hint center">{t('SYNC_REVERT_WARN')}</p>
          )}
          <div className="btn-row">
            <button className="primary" onClick={() => void runDelete(deleteTarget.holidayDate)}>
              {t('SUBMIT')}
            </button>
            <button onClick={() => setDeleteTarget(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      {listError && <p className="error" role="alert">{listError}</p>}

      {holidays.length === 0 && !listError ? (
        <p className="muted center">{t('EMPTY')}</p>
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
                  <Fragment key={holiday.holidayDate}>
                    <tr>
                      <td>
                        {holiday.holidayDate}({weekdayOf(holiday.holidayDate)})
                      </td>
                      <td>{holiday.holidayName}</td>
                      <td>
                        <span className={`badge ${national ? 'badge-national' : 'badge-company'}`}>
                          {t(TYPE_LABEL_KEYS[holiday.holidayType])}
                        </span>
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            onClick={() => {
                              setDeleteTarget(null)
                              setEditTarget({ holiday, name: holiday.holidayName })
                            }}
                          >
                            {t('EDIT')}
                          </button>
                          <button
                            onClick={() => {
                              setEditTarget(null)
                              setDeleteTarget(holiday)
                            }}
                          >
                            {t('DELETE')}
                          </button>
                        </div>
                      </td>
                    </tr>
                    {rowError?.date === holiday.holidayDate && (
                      <tr>
                        <td colSpan={4} className="row-note">
                          <p className="error" role="alert">
                            {rowError.message}
                          </p>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
