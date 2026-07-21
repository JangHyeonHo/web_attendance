import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, tenantMemberApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { ScheduleEditor } from '../components/ScheduleEditor'
import { SelectField, TimeField, TextField, ModalSubject } from '../components/fields'
import { DateField } from '../components/DateField'
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
  const { t, role: viewerRole } = useApp()

  const [members, setMembers] = useState<MemberSummary[]>([])
  const [query, setQuery] = useState('') //이름·이메일·부서 검색(대규모 인원 대비, #9와 동일 패턴)
  //근무 시간대 검색(#6) — 개인 기본 스케줄이 이 구간과 겹치는 멤버만. 백엔드 필터
  const [workFrom, setWorkFrom] = useState('')
  const [workTo, setWorkTo] = useState('')
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
  const [salary, setSalary] = useState('') //월 기본급(선택) — 급여 정산 기준
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  /** 발송 전 이메일 재확인 모달(오송신 방지 UX) — 취소하면 등록 모달로 복귀(폼 값 유지) */
  const [confirmSend, setConfirmSend] = useState(false)
  const [created, setCreated] = useState<CreatedNotice | null>(null)

  //행 조작
  const [pending, setPending] = useState<PendingAction | null>(null)
  //멤버 관리 패널 — 목록은 표시만 하고, 역할·상태·급여·스케줄·삭제는 이 패널에서 처리
  const [manageMember, setManageMember] = useState<MemberSummary | null>(null)
  const [manageSalary, setManageSalary] = useState('')
  const [manageBusy, setManageBusy] = useState(false)
  const [manageError, setManageError] = useState<string | null>(null)
  //통합 근무 스케줄 화면 대상(#1,#13) — 개인 기본 + 반복 패턴 + 월 달력(예외)을 한 화면에서
  const [scheduleMember, setScheduleMember] = useState<
    { userId: number; name: string; email: string; workStart: string; workEnd: string; workDays: string } | null
  >(null)
  const [rowError, setRowError] = useState<{ userId: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      setMembers(await tenantMemberApi.list({ q: query, workFrom, workTo }))
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [query, workFrom, workTo])

  //검색은 백엔드 필터(#6) — 입력 변화마다 디바운스로 재조회(대규모 인원 대비)
  useEffect(() => {
    const id = setTimeout(() => { void reload() }, 250)
    return () => clearTimeout(id)
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
        baseMonthlySalary: salary.trim() ? Number(salary) : null,
      })
      //mailSent=false여도 멤버는 PENDING으로 생성됨(201) — 재발송이 수습 경로
      setCreated({ email: response.email, mailSent: response.mailSent })
      setEmail('')
      setName('')
      setDepartCd('')
      setWorkStart(DEFAULT_WORK_START)
      setWorkEnd(DEFAULT_WORK_END)
      setHireDate('')
      setSalary('')
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

  //관리 패널에서 편집 대상이 바뀌면 급여 입력값·오류를 그 멤버 기준으로 초기화
  useEffect(() => {
    if (manageMember) {
      setManageSalary(manageMember.baseMonthlySalary == null ? '' : String(manageMember.baseMonthlySalary))
      setManageError(null)
    }
  }, [manageMember])

  /**
   * 관리 패널 내 즉시 반영 조작(역할·급여·활성화·재발송). 성공 후 목록을 새로고침하고,
   * 패널이 열린 채로 최신 멤버 값으로 갱신한다(모달은 유지). 실패는 패널 안에 표시.
   */
  async function manageAction(userId: number, fn: () => Promise<unknown>) {
    setManageBusy(true)
    setManageError(null)
    try {
      await fn()
      const list = await tenantMemberApi.list({ q: query, workFrom, workTo })
      setMembers(list)
      const fresh = list.find((m) => m.userId === userId)
      setManageMember(fresh ?? null)
    } catch (e) {
      setManageError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setManageBusy(false)
    }
  }

  //관리 패널에서 초대 재발송 — 발송 실패(mailSent=false)도 멤버는 존재하므로 경고만 표시
  async function manageResend(userId: number) {
    setManageBusy(true)
    setManageError(null)
    try {
      const response = await tenantMemberApi.invite(userId)
      const list = await tenantMemberApi.list({ q: query, workFrom, workTo })
      setMembers(list)
      setManageMember(list.find((m) => m.userId === userId) ?? null)
      if (!response.mailSent) setManageError(t('MAIL_FAILED'))
    } catch (e) {
      setManageError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setManageBusy(false)
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

  //스케줄 화면은 모달이 아니라 전체 화면(패널)으로 — 목록을 밀어내고 그 자리를 채운다(#1,#13)
  if (scheduleMember) {
    return (
      <ScheduleEditor
        userId={scheduleMember.userId}
        userName={scheduleMember.name}
        userEmail={scheduleMember.email}
        workStart={scheduleMember.workStart}
        workEnd={scheduleMember.workEnd}
        workDays={scheduleMember.workDays}
        onClose={() => setScheduleMember(null)}
      />
    )
  }

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
            {/* 월 기본급(선택) — 급여 정산 기준값 */}
            <label>
              {t('SALARY')}
              <input
                type="number"
                min={0}
                inputMode="numeric"
                value={salary}
                placeholder={t('SALARY_HINT')}
                onChange={(e) => setSalary(e.target.value)}
              />
              {fieldErrors.baseMonthlySalary && (
                <span className="error">{fieldErrors.baseMonthlySalary}</span>
              )}
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

      {manageMember && (() => {
        const m = manageMember
        const self = myUserId !== null && m.userId === myUserId
        const isPending = m.status === 'PENDING'
        const canRole = viewerRole === 'TENANT_ADMIN' && !self && !isPending
        return (
          <Modal title={t('MEMBER_MANAGE')} onClose={() => setManageMember(null)}>
            <ModalSubject primary={m.name} secondary={m.email} />
            {manageError && <p className="error" role="alert">{manageError}</p>}

            {/* 역할 — 총관리자만 변경(직권 분산). 그 외/자기 자신/초대 대기는 표시만 */}
            <div className="manage-field">
              <span className="form-field-label">{t('ROLE')}</span>
              {canRole ? (
                <SelectField
                  value={m.role}
                  options={ROLE_OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) }))}
                  ariaLabel={t('ROLE')}
                  onChange={(v) =>
                    void manageAction(m.userId, () => tenantMemberApi.updateRole(m.userId, { role: v as Role }))
                  }
                />
              ) : (
                <span className="manage-value">{t(ROLE_LABEL_KEYS[m.role] ?? m.role)}</span>
              )}
            </div>

            {/* 상태 — 활성/비활성 전환·초대 재발송 */}
            <div className="manage-field">
              <span className="form-field-label">{t('STATUS')}</span>
              <div className="manage-value-row">
                <span className={`member-status member-status-${m.status.toLowerCase()}`}>
                  {t(STATUS_LABEL_KEYS[m.status])}
                </span>
                {!self && !isPending && m.status === 'ACTIVE' && (
                  <button
                    disabled={manageBusy}
                    onClick={() => { setManageMember(null); setPending({ userId: m.userId, name: m.name, action: 'DISABLE' }) }}
                  >
                    {t('DISABLE')}
                  </button>
                )}
                {m.status === 'DISABLED' && (
                  <button
                    disabled={manageBusy}
                    onClick={() =>
                      void manageAction(m.userId, () => tenantMemberApi.updateStatus(m.userId, { status: 'ACTIVE' }))
                    }
                  >
                    {t('ENABLE')}
                  </button>
                )}
                {isPending && (
                  <button disabled={manageBusy} onClick={() => void manageResend(m.userId)}>
                    {t('RESEND')}
                  </button>
                )}
              </div>
              {isPending && <span className="hint">{inviteExpiryLabel(m)}</span>}
            </div>

            {/* 월 기본급 — 급여 정산 기준값 */}
            <TextField
              label={t('SALARY')}
              type="number"
              min={0}
              numeric
              value={manageSalary}
              placeholder={t('SALARY_HINT')}
              onChange={setManageSalary}
            />
            <div className="manage-inline-save">
              <button
                className="primary"
                disabled={manageBusy}
                onClick={() =>
                  void manageAction(m.userId, () =>
                    tenantMemberApi.updateSalary(m.userId, manageSalary.trim() === '' ? null : Number(manageSalary)),
                  )
                }
              >
                {t('SALARY_SAVE')}
              </button>
            </div>

            {/* 근무 스케줄 — 개인 기본+정기+상세 통합 화면으로 이동(#1) */}
            <div className="manage-field">
              <span className="form-field-label">{t('SCHEDULE_TITLE')}</span>
              <button
                onClick={() => {
                  setScheduleMember({
                    userId: m.userId, name: m.name, email: m.email,
                    workStart: m.workStart, workEnd: m.workEnd, workDays: m.workDays,
                  })
                  setManageMember(null)
                }}
              >
                {t('EDIT_SCHEDULE')}
              </button>
            </div>

            {/* 삭제 — 확인 모달 경유(자기 자신은 불가) */}
            {!self && (
              <div className="manage-danger">
                <button
                  className="danger-outline"
                  disabled={manageBusy}
                  onClick={() => { setManageMember(null); setPending({ userId: m.userId, name: m.name, action: 'DELETE' }) }}
                >
                  {t('DELETE')}
                </button>
              </div>
            )}
          </Modal>
        )
      })()}

      {pending && (
        <Modal title={confirmLabel(pending.action)} onClose={() => setPending(null)} danger>
          <ModalSubject primary={pending.name} secondary={confirmLabel(pending.action)} />
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

      {/* 대규모 인원 대비 검색(#6) — 이름·이메일·부서 + 근무 시간대(개인 기본 스케줄). 백엔드 필터 */}
      <div className="member-filter">
        <input
          className="member-search"
          placeholder={t('MEMBER_SEARCH')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label={t('MEMBER_SEARCH')}
        />
        <div className="member-filter-time">
          <span className="field-label">{t('SEARCH_WORK_TIME')}</span>
          <TimeField value={workFrom} onChange={setWorkFrom} ariaLabel={t('WORK_START')} />
          <span aria-hidden="true">~</span>
          <TimeField value={workTo} onChange={setWorkTo} ariaLabel={t('WORK_END')} />
          {(workFrom || workTo || query) && (
            <button
              type="button"
              className="link"
              onClick={() => { setQuery(''); setWorkFrom(''); setWorkTo('') }}
            >
              {t('FILTER_RESET')}
            </button>
          )}
        </div>
      </div>

      <div className="table-wrap">
        <table className="detail-table">
          <thead>
            <tr>
              <th>{t('NAME')}</th>
              <th>{t('EMAIL')}</th>
              <th>{t('DEPART')}</th>
              <th>{t('ROLE')}</th>
              <th>{t('STATUS')}</th>
              <th className="num">{t('SALARY')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {members.length === 0 && (
              <tr>
                <td colSpan={7} className="muted center">{t('EMPTY')}</td>
              </tr>
            )}
            {members.map((member) => {
              const isPending = member.status === 'PENDING'
              return (
                <Fragment key={member.userId}>
                  <tr>
                    <td>{member.name}</td>
                    <td>{member.email}</td>
                    <td>{member.departCd ?? '-'}</td>
                    {/* 목록은 표시만 — 편집(역할·상태·급여·스케줄·삭제)은 '관리' 패널에서 일괄 처리 */}
                    <td>{t(ROLE_LABEL_KEYS[member.role] ?? member.role)}</td>
                    <td>
                      <span className={`member-status member-status-${member.status.toLowerCase()}`}>
                        {t(STATUS_LABEL_KEYS[member.status])}
                      </span>
                      {isPending && <span className="hint">{inviteExpiryLabel(member)}</span>}
                    </td>
                    <td className="num">
                      {member.baseMonthlySalary == null ? '—' : member.baseMonthlySalary.toLocaleString()}
                    </td>
                    <td className="row-actions-cell">
                      <button className="ghost" onClick={() => setManageMember(member)}>
                        {t('MEMBER_MANAGE')}
                      </button>
                    </td>
                  </tr>
                  {rowError?.userId === member.userId && (
                    <tr>
                      <td colSpan={7} className="row-note">
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
