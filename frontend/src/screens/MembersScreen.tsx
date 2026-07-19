import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, tenantMemberApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { SelectField, TimeField } from '../components/fields'
import { DateField } from '../components/DateField'
import { localeOf } from '../i18n/lang'
import type { MemberSummary, Role, UserStatus } from '../api/types'

const ROLE_LABEL_KEYS: Partial<Record<Role, string>> = {
  TENANT_ADMIN: 'ROLE_TENANT_ADMIN',
  HR_ADMIN: 'ROLE_HR_ADMIN',
  MEMBER: 'ROLE_MEMBER',
}

/** 역할 지정 선택지(총관리자 전용 UI) — 회사 내 3역할 */
const ROLE_OPTIONS: { value: Role; labelKey: string }[] = [
  { value: 'MEMBER', labelKey: 'ROLE_MEMBER' },
  { value: 'HR_ADMIN', labelKey: 'ROLE_HR_ADMIN' },
  { value: 'TENANT_ADMIN', labelKey: 'ROLE_TENANT_ADMIN' },
]

const STATUS_LABEL_KEYS: Record<UserStatus, string> = {
  ACTIVE: 'STATUS_ACTIVE',
  DISABLED: 'STATUS_DISABLED',
  PENDING: 'STATUS_PENDING', //초대 대기 — 비밀번호 설정 전
}

const DEFAULT_WORK_START = '09:00'
const DEFAULT_WORK_END = '18:00'

/** 파괴적 조작(비활성/삭제)은 확인 모달 경유. 역할 지정은 인라인 SelectField(총관리자 전용) */
interface PendingAction {
  userId: number
  name: string
  action: 'DISABLE' | 'DELETE'
}

/** 행별 스케줄 수정 모달 상태(PENDING 행 포함 — 입사 전 준비, CR3-6). workDays는 월~일 [01]{7} */
interface ScheduleEdit {
  userId: number
  name: string
  workStart: string
  workEnd: string
  workDays: string
}

/** 등록 결과 안내(초대 메일 발송됨 / 발송 실패 — 멤버는 PENDING으로 존재) */
interface CreatedNotice {
  email: string
  mailSent: boolean
}

/**
 * W009 멤버 관리 — TENANT_ADMIN 전용.
 * 등록은 초대 플로우(email-onboarding §8.3): 등록 모달 → 발송 전 이메일 재확인 모달 →
 * [발송]에서 비로소 POST(근무 스케줄 필드 동봉). 초기 비밀번호 방식은 폐지.
 * Phase 4: 등록 폼·스케줄 수정·파괴적 확인을 전부 모달로 이전(테이블 내 확장 행 폐지),
 * 행 아래 인라인 표시는 에러만 남긴다.
 */
export function MembersScreen() {
  const { t, lang, role: viewerRole } = useApp()

  //요일 라벨(월~일)은 사전 없이 Intl로 생성 — 2024-01-01이 월요일
  const weekdayLabels = (() => {
    const format = new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' })
    return Array.from({ length: 7 }, (_, i) => format.format(new Date(2024, 0, 1 + i)))
  })()
  const [members, setMembers] = useState<MemberSummary[]>([])
  const [query, setQuery] = useState('') //이름·이메일·부서 검색(대규모 인원 대비, #9와 동일 패턴)
  const [listError, setListError] = useState<string | null>(null)
  /** 자기 자신 행의 강등/비활성/삭제 버튼은 렌더하지 않는다(세션 파괴 사고 방지) */
  const [myUserId, setMyUserId] = useState<number | null>(null)

  //등록 모달(이메일/이름/부서 + 근무 시작/종료 — CR3-6 합성 레이아웃)
  const [formOpen, setFormOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [departCd, setDepartCd] = useState('')
  const [workStart, setWorkStart] = useState(DEFAULT_WORK_START)
  const [workEnd, setWorkEnd] = useState(DEFAULT_WORK_END)
  const [hireDate, setHireDate] = useState('') //입사일(선택) — 연차 계산 기준(#11)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  /** 발송 전 이메일 재확인 모달(오송신 방지 UX) — 취소하면 등록 모달로 복귀(폼 값 유지) */
  const [confirmSend, setConfirmSend] = useState(false)
  const [created, setCreated] = useState<CreatedNotice | null>(null)

  //행 조작
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [scheduleEdit, setScheduleEdit] = useState<ScheduleEdit | null>(null)
  const [rowError, setRowError] = useState<{ userId: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      setMembers(await tenantMemberApi.list())
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (!cancelled) setMyUserId(me.userId)
      })
      .catch(() => {
        //취득 실패시 자기 행 보호 버튼 억제만 못할 뿐 — 서버가 최종 게이트(401은 훅이 처리)
      })
    return () => {
      cancelled = true
    }
  }, [])

  /** 등록 모달 제출 = 즉시 발송하지 않는다 — 입력 이메일 재확인 모달로 전환 */
  function onFormSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setCreated(null)
    setFormOpen(false)
    setConfirmSend(true)
  }

  /** 확인 모달의 [발송]에서 비로소 POST(스케줄 필드 동봉) */
  async function sendInvite() {
    setConfirmSend(false)
    setSubmitting(true)
    try {
      const response = await tenantMemberApi.create({
        email: email.trim(),
        name: name.trim(),
        departCd: departCd.trim() || null,
        workStart,
        workEnd,
        hireDate: hireDate || null,
      })
      //mailSent=false여도 멤버는 PENDING으로 생성됨(201) — 재발송이 수습 경로
      setCreated({ email: response.email, mailSent: response.mailSent })
      setEmail('')
      setName('')
      setDepartCd('')
      setWorkStart(DEFAULT_WORK_START)
      setWorkEnd(DEFAULT_WORK_END)
      setHireDate('')
      await reload()
    } catch (e) {
      //오류 시 폼 값을 유지한 채 등록 모달을 다시 연다(오타 수정 후 재시도)
      setFormOpen(true)
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

  /** 재발송 — 파괴적 조작이 아니므로 즉시 실행(구 링크는 서버가 무효화) */
  async function resendInvite(userId: number) {
    setRowError(null)
    try {
      const response = await tenantMemberApi.invite(userId)
      if (!response.mailSent) {
        setRowError({ userId, message: t('MAIL_FAILED') })
      }
      await reload()
    } catch (e) {
      setRowError({ userId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function removeMember(userId: number) {
    setPending(null)
    setRowError(null)
    try {
      await tenantMemberApi.remove(userId)
      await reload()
    } catch (e) {
      //400 자기 자신 / 409 LAST_TENANT_ADMIN 등 — 행 아래 인라인 표시
      setRowError({ userId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function updateStatus(userId: number, status: UserStatus) {
    setPending(null)
    setRowError(null)
    try {
      await tenantMemberApi.updateStatus(userId, { status })
      await reload()
    } catch (e) {
      setRowError({ userId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function updateRole(userId: number, role: Role) {
    setPending(null)
    setRowError(null)
    try {
      await tenantMemberApi.updateRole(userId, { role })
      await reload()
    } catch (e) {
      setRowError({ userId, message: e instanceof ApiError ? e.message : String(e) })
    }
  }

  async function saveSchedule() {
    if (!scheduleEdit) return
    setRowError(null)
    try {
      await tenantMemberApi.updateSchedule(scheduleEdit.userId, {
        workStart: scheduleEdit.workStart,
        workEnd: scheduleEdit.workEnd,
        workDays: scheduleEdit.workDays,
      })
      setScheduleEdit(null)
      await reload()
    } catch (e) {
      //400 WORK_TIME_INVALID_RANGE 등 — 서버 메시지 그대로(모달은 닫고 행 아래 표시)
      setRowError({
        userId: scheduleEdit.userId,
        message: e instanceof ApiError ? e.message : String(e),
      })
      setScheduleEdit(null)
    }
  }

  function confirmLabel(action: PendingAction['action']): string {
    if (action === 'DISABLE') return t('DISABLE')
    return t('DELETE')
  }

  /**
   * PENDING 행의 초대 만료 표시 — 서버가 유효 토큰만 내려준다(expires_at > NOW 필터).
   * 값이 있으면 그대로 표시, null이면 만료(클라이언트 시계로 재판정하지 않는다 — 시계 오차 오표시 방지).
   */
  function inviteExpiryLabel(member: MemberSummary): string {
    if (member.inviteExpiresAt) {
      return member.inviteExpiresAt.replace('T', ' ').slice(0, 16)
    }
    return t('INVITE_EXPIRED')
  }

  const q = query.trim().toLowerCase()
  const filtered = q
    ? members.filter(
        (m) =>
          m.name.toLowerCase().includes(q) ||
          m.email.toLowerCase().includes(q) ||
          (m.departCd?.toLowerCase().includes(q) ?? false),
      )
    : members

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('MEMBERS_TITLE')}</h2>
        <div className="toolbar-actions">
          <button className="primary" onClick={() => setFormOpen(true)} disabled={submitting}>
            {t('MEMBER_CREATE')}
          </button>
        </div>
      </div>

      {created && (
        <div className={`banner${created.mailSent ? '' : ' banner-error'}`} role="status">
          <p className={created.mailSent ? 'success' : 'error'}>
            {created.email} — {created.mailSent ? t('INVITE_SENT') : t('MAIL_FAILED')}
          </p>
          <button onClick={() => setCreated(null)}>{t('CLOSE')}</button>
        </div>
      )}

      {formError && !formOpen && <p className="error" role="alert">{formError}</p>}
      {listError && <p className="error" role="alert">{listError}</p>}

      {formOpen && (
        <Modal title={t('MEMBER_CREATE')} onClose={() => setFormOpen(false)}>
          <form onSubmit={onFormSubmit}>
            <label>
              {t('EMAIL')}
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              {fieldErrors.email && <span className="error">{fieldErrors.email}</span>}
            </label>
            <label>
              {t('NAME')}
              <input value={name} onChange={(e) => setName(e.target.value)} required />
              {fieldErrors.name && <span className="error">{fieldErrors.name}</span>}
            </label>
            <label>
              {t('DEPART')}
              <input value={departCd} onChange={(e) => setDepartCd(e.target.value)} />
              {fieldErrors.departCd && <span className="error">{fieldErrors.departCd}</span>}
            </label>
            <div className="field-row">
              <label>
                {t('WORK_START')}
                <TimeField value={workStart} onChange={setWorkStart} ariaLabel={t('WORK_START')} />
                {fieldErrors.workStart && <span className="error">{fieldErrors.workStart}</span>}
              </label>
              <label>
                {t('WORK_END')}
                <TimeField value={workEnd} onChange={setWorkEnd} ariaLabel={t('WORK_END')} />
                {fieldErrors.workEnd && <span className="error">{fieldErrors.workEnd}</span>}
              </label>
            </div>
            {/* 입사일(선택) — 연차 계산 기준. 미입력 시 등록일(#11) */}
            <label>
              {t('HIRE_DATE')}
              <DateField
                value={hireDate}
                onChange={setHireDate}
                ariaLabel={t('HIRE_DATE')}
                placeholder={t('HIRE_DATE_PLACEHOLDER')}
              />
              {fieldErrors.hireDate && <span className="error">{fieldErrors.hireDate}</span>}
            </label>
            {formError && <p className="error" role="alert">{formError}</p>}
            <button type="submit" className="primary" disabled={submitting}>
              {t('MEMBER_CREATE')}
            </button>
          </form>
        </Modal>
      )}

      {confirmSend && (
        <Modal
          title={t('MEMBER_CREATE')}
          onClose={() => {
            //취소 = 등록 모달로 복귀(폼 값 유지 — 오타 수정)
            setConfirmSend(false)
            setFormOpen(true)
          }}
          danger
        >
          {/* 오송신 방지 — 입력 이메일을 강조 재표시하고 발송 여부를 재확인한다 */}
          <strong className="confirm-email">{email.trim()}</strong>
          <p className="center">{t('CONFIRM_SEND_DESC')}</p>
          <div className="btn-row">
            <button className="primary" onClick={() => void sendInvite()} disabled={submitting}>
              {t('SEND_INVITE')}
            </button>
            <button
              onClick={() => {
                setConfirmSend(false)
                setFormOpen(true)
              }}
            >
              {t('CANCEL')}
            </button>
          </div>
        </Modal>
      )}

      {scheduleEdit && (
        <Modal title={`${scheduleEdit.name} — ${t('EDIT_SCHEDULE')}`} onClose={() => setScheduleEdit(null)}>
          <div className="field-row">
            <label>
              {t('WORK_START')}
              <TimeField
                value={scheduleEdit.workStart}
                onChange={(v) => setScheduleEdit({ ...scheduleEdit, workStart: v })}
                ariaLabel={t('WORK_START')}
              />
            </label>
            <label>
              {t('WORK_END')}
              <TimeField
                value={scheduleEdit.workEnd}
                onChange={(v) => setScheduleEdit({ ...scheduleEdit, workEnd: v })}
                ariaLabel={t('WORK_END')}
              />
            </label>
          </div>
          {/* 근무 요일(월~일) — 토·일 근무 유무를 멤버별로 설정(manual-attendance §2) */}
          <span className="field-label">{t('WORK_DAYS')}</span>
          <div className="weekday-row" role="group" aria-label={t('WORK_DAYS')}>
            {weekdayLabels.map((label, index) => {
              const on = scheduleEdit.workDays.charAt(index) === '1'
              return (
                <label key={index} className={`weekday-chip${on ? ' on' : ''}`}>
                  <input
                    type="checkbox"
                    checked={on}
                    onChange={() => {
                      const chars = scheduleEdit.workDays.split('')
                      chars[index] = on ? '0' : '1'
                      setScheduleEdit({ ...scheduleEdit, workDays: chars.join('') })
                    }}
                  />
                  {label}
                </label>
              )
            })}
          </div>
          <div className="btn-row">
            <button
              className="primary"
              onClick={() => void saveSchedule()}
              disabled={
                !scheduleEdit.workStart ||
                !scheduleEdit.workEnd ||
                !scheduleEdit.workDays.includes('1') //전 요일 휴무는 서버도 400
              }
            >
              {t('SUBMIT')}
            </button>
            <button onClick={() => setScheduleEdit(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      {pending && (
        <Modal title={confirmLabel(pending.action)} onClose={() => setPending(null)} danger>
          <p className="center">
            {pending.name} — {confirmLabel(pending.action)}
          </p>
          {pending.action === 'DELETE' && <p className="hint center">{t('DELETE_CONFIRM')}</p>}
          <div className="btn-row">
            <button
              className="primary"
              onClick={() => {
                if (pending.action === 'DISABLE') {
                  void updateStatus(pending.userId, 'DISABLED')
                } else {
                  void removeMember(pending.userId)
                }
              }}
            >
              {t('SUBMIT')}
            </button>
            <button onClick={() => setPending(null)}>{t('CANCEL')}</button>
          </div>
        </Modal>
      )}

      {/* 대규모 인원 대비 검색 — 이름·이메일·부서(클라이언트 필터, 즉시) */}
      <input
        className="member-search"
        placeholder={t('MEMBER_SEARCH')}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        aria-label={t('MEMBER_SEARCH')}
      />

      <div className="table-wrap">
        <table className="detail-table">
          <thead>
            <tr>
              <th>{t('NAME')}</th>
              <th>{t('EMAIL')}</th>
              <th>{t('DEPART')}</th>
              <th>{t('ROLE')}</th>
              <th>{t('STATUS')}</th>
              <th>{t('WORK_START')}</th>
              <th>{t('WORK_END')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan={8} className="muted center">{t('EMPTY')}</td>
              </tr>
            )}
            {filtered.map((member) => {
              const self = myUserId !== null && member.userId === myUserId
              const isPending = member.status === 'PENDING'
              return (
                <Fragment key={member.userId}>
                  <tr>
                    <td>{member.name}</td>
                    <td>{member.email}</td>
                    <td>{member.departCd ?? '-'}</td>
                    <td>
                      {/* 역할 지정은 총관리자 전용(직권 분산). 그 외 뷰어·자기 자신·초대 대기는 정적 라벨 */}
                      {viewerRole === 'TENANT_ADMIN' && !self && !isPending ? (
                        <SelectField
                          compact
                          value={member.role}
                          options={ROLE_OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) }))}
                          ariaLabel={t('ROLE')}
                          onChange={(v) => void updateRole(member.userId, v as Role)}
                        />
                      ) : (
                        t(ROLE_LABEL_KEYS[member.role] ?? member.role)
                      )}
                    </td>
                    <td>
                      {t(STATUS_LABEL_KEYS[member.status])}
                      {isPending && <span className="hint">{inviteExpiryLabel(member)}</span>}
                    </td>
                    <td>{member.workStart}</td>
                    <td>{member.workEnd}</td>
                    <td>
                      <div className="row-actions">
                        {isPending ? (
                          //초대 대기 행: 재발송(즉시)·삭제 — 상태 변경은 서버도 400으로 거부(§4.2)
                          <button onClick={() => void resendInvite(member.userId)}>
                            {t('RESEND')}
                          </button>
                        ) : (
                          <>
                            {/* 역할 변경은 역할 열의 SelectField(총관리자 전용)로 이동 */}
                            {member.status === 'ACTIVE' && !self && (
                              <button
                                onClick={() =>
                                  setPending({ userId: member.userId, name: member.name, action: 'DISABLE' })
                                }
                              >
                                {t('DISABLE')}
                              </button>
                            )}
                            {member.status === 'DISABLED' && (
                              <button onClick={() => void updateStatus(member.userId, 'ACTIVE')}>
                                {t('ENABLE')}
                              </button>
                            )}
                          </>
                        )}
                        <button
                          onClick={() =>
                            setScheduleEdit({
                              userId: member.userId,
                              name: member.name,
                              workStart: member.workStart,
                              workEnd: member.workEnd,
                              workDays: member.workDays,
                            })
                          }
                        >
                          {t('EDIT_SCHEDULE')}
                        </button>
                        {!self && (
                          <button
                            onClick={() =>
                              setPending({ userId: member.userId, name: member.name, action: 'DELETE' })
                            }
                          >
                            {t('DELETE')}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {rowError?.userId === member.userId && (
                    <tr>
                      <td colSpan={8} className="row-note">
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
    </div>
  )
}
