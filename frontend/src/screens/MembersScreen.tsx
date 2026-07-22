import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, tenantMemberApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { ScheduleEditor } from '../components/ScheduleEditor'
import { SelectField, TimeField, TextField, ModalSubject } from '../components/fields'
import { DateField } from '../components/DateField'
import { Pagination } from '../components/Pagination'
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
  //페이지 번호 방식(#9) — 회사 규모가 커져도 목록이 무한정 길어지지 않게. 근무 시점 검색 결과는 비페이지.
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  const [totalCount, setTotalCount] = useState(0)
  const [query, setQuery] = useState('') //이름·이메일·부서 검색(대규모 인원 대비, #9와 동일 패턴)
  //근무 현황 검색(#6) — 특정 날짜·시각에 실제로 근무 중인 멤버(실효 스케줄 기준, 백엔드 판정).
  //시각은 항상 유효한 HH:mm(선택 화면 폴백), 검색은 날짜를 지정해야 활성화된다.
  const [searchDate, setSearchDate] = useState('')
  const [searchTime, setSearchTime] = useState('09:00')
  const [listError, setListError] = useState<string | null>(null)
  /** 자기 자신 행의 강등/비활성/삭제 버튼은 렌더하지 않는다(세션 파괴 사고 방지) */
  const [myUserId, setMyUserId] = useState<number | null>(null)

  //등록 모달(이메일/이름/부서 + 근무 시작/종료 — CR3-6 합성 레이아웃)
  const [formOpen, setFormOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [departCd, setDepartCd] = useState('')
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
  //멤버 관리 패널 — 목록은 표시만 하고, 역할·상태·급여를 한 번에(일괄 저장) 편집. 스케줄/삭제는 별도 조작
  const [manageMember, setManageMember] = useState<MemberSummary | null>(null)
  const [draftRole, setDraftRole] = useState<Role>('MEMBER')
  const [draftStatus, setDraftStatus] = useState<UserStatus>('ACTIVE')
  const [draftSalary, setDraftSalary] = useState('')
  const [manageBusy, setManageBusy] = useState(false)
  const [manageError, setManageError] = useState<string | null>(null)
  //통합 근무 스케줄 화면 대상 — 정기(반복 패턴) + 월 달력(예외)을 한 화면에서
  const [scheduleMember, setScheduleMember] = useState<
    { userId: number; name: string; email: string } | null
  >(null)
  const [rowError, setRowError] = useState<{ userId: number; message: string } | null>(null)

  const reload = useCallback(async () => {
    try {
      //날짜를 지정하면 그 날짜·시각의 근무자만(실효 스케줄 — 근무 인원으로 자연 축소되므로 비페이지),
      //아니면 텍스트 필터 + 페이지 조회(#9)
      if (searchDate && searchTime) {
        const data = await tenantMemberApi.working(searchDate, searchTime, query)
        setMembers(data)
        setTotalPages(1)
        setTotalCount(data.length)
      } else {
        const data = await tenantMemberApi.list({ q: query, page })
        setMembers(data.items)
        setTotalPages(data.totalPages)
        setTotalCount(data.totalCount)
        //검색 결과가 줄어 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정
        if (page > data.totalPages) setPage(data.totalPages)
      }
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [query, searchDate, searchTime, page])

  //검색 조건이 바뀌면 1페이지부터 다시
  useEffect(() => {
    setPage(1)
  }, [query, searchDate, searchTime])

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
        hireDate: hireDate || null,
        baseMonthlySalary: salary.trim() ? Number(salary) : null,
      })
      //mailSent=false여도 멤버는 PENDING으로 생성됨(201) — 재발송이 수습 경로
      setCreated({ email: response.email, mailSent: response.mailSent })
      setEmail('')
      setName('')
      setDepartCd('')
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

  //관리 패널을 열 때 편집 초안(역할·상태·급여)을 그 멤버 기준으로 초기화
  useEffect(() => {
    if (manageMember) {
      setDraftRole(manageMember.role)
      setDraftStatus(manageMember.status)
      setDraftSalary(manageMember.baseMonthlySalary == null ? '' : String(manageMember.baseMonthlySalary))
      setManageError(null)
    }
  }, [manageMember])

  /**
   * 관리 패널 일괄 저장 — 바뀐 항목(역할·상태·월 기본급)만 순서대로 반영한다.
   * 하나라도 실패하면 패널 안에 오류를 남기고 유지, 전부 성공하면 목록 새로고침 후 닫는다.
   */
  async function saveManage() {
    const m = manageMember
    if (!m) return
    setManageBusy(true)
    setManageError(null)
    try {
      if (draftRole !== m.role) {
        await tenantMemberApi.updateRole(m.userId, { role: draftRole })
      }
      if (draftStatus !== m.status) {
        await tenantMemberApi.updateStatus(m.userId, { status: draftStatus })
      }
      const salaryVal = draftSalary.trim() === '' ? null : Number(draftSalary)
      if (salaryVal !== m.baseMonthlySalary) {
        await tenantMemberApi.updateSalary(m.userId, salaryVal)
      }
      await reload()
      setManageMember(null)
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
      const data = await tenantMemberApi.list({ q: query, page })
      setMembers(data.items)
      setTotalPages(data.totalPages)
      setTotalCount(data.totalCount)
      setManageMember(data.items.find((m) => m.userId === userId) ?? null)
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
            {/* 등록 행위의 결과 안내(폼 단위) — 특정 필드 설명이 아니므로 등록 버튼 직전에 배치.
                근무시간은 등록 시 묻지 않는다: 회사 기본 스케줄이 정기 스케줄로 자동 생성(이중 설정 제거) */}
            <p className="hint">{t('SCHEDULE_AUTO_HINT')}</p>
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
        const canStatus = !self && !isPending //ACTIVE↔DISABLED (초대 대기는 재발송으로만)
        return (
          <Modal title={t('MEMBER_MANAGE')} onClose={() => setManageMember(null)}>
            <ModalSubject primary={m.name} secondary={m.email} />
            {manageError && <p className="error" role="alert">{manageError}</p>}

            {/* 역할·상태·월 기본급을 초안으로 편집하고 하단 '저장'으로 일괄 반영 */}
            <div className="manage-field">
              <span className="form-field-label">{t('ROLE')}</span>
              {canRole ? (
                <SelectField
                  value={draftRole}
                  options={ROLE_OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) }))}
                  ariaLabel={t('ROLE')}
                  onChange={(v) => setDraftRole(v as Role)}
                />
              ) : (
                <span className="manage-value">{t(ROLE_LABEL_KEYS[m.role] ?? m.role)}</span>
              )}
            </div>

            <div className="manage-field">
              <span className="form-field-label">{t('STATUS')}</span>
              {canStatus ? (
                <SelectField
                  value={draftStatus}
                  options={[
                    { value: 'ACTIVE', label: t(STATUS_LABEL_KEYS.ACTIVE) },
                    { value: 'DISABLED', label: t(STATUS_LABEL_KEYS.DISABLED) },
                  ]}
                  ariaLabel={t('STATUS')}
                  onChange={(v) => setDraftStatus(v as UserStatus)}
                />
              ) : (
                <div className="manage-value-row">
                  <span className={`member-status member-status-${m.status.toLowerCase()}`}>
                    {t(STATUS_LABEL_KEYS[m.status])}
                  </span>
                  {isPending && (
                    <button disabled={manageBusy} onClick={() => void manageResend(m.userId)}>
                      {t('RESEND')}
                    </button>
                  )}
                </div>
              )}
              {isPending && <span className="hint">{inviteExpiryLabel(m)}</span>}
            </div>

            <TextField
              label={t('SALARY')}
              type="number"
              min={0}
              numeric
              value={draftSalary}
              placeholder={t('SALARY_HINT')}
              onChange={setDraftSalary}
            />

            {/* 일괄 저장 — 바뀐 항목만 반영 */}
            <div className="btn-row">
              <button className="primary" disabled={manageBusy} onClick={() => void saveManage()}>
                {t('SAVE')}
              </button>
              <button disabled={manageBusy} onClick={() => setManageMember(null)}>{t('CANCEL')}</button>
            </div>

            {/* 삭제(파괴적)는 저장과 분리 — 스케줄 진입은 목록 행의 스케줄 아이콘으로 단일화 */}
            <div className="manage-links">
              {!self && (
                <button
                  type="button"
                  className="link danger-link"
                  disabled={manageBusy}
                  onClick={() => { setManageMember(null); setPending({ userId: m.userId, name: m.name, action: 'DELETE' }) }}
                >
                  {t('DELETE')}
                </button>
              )}
            </div>
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

      {/* 검색 — 이름·이메일·부서(텍스트) + 특정 날짜·시각 근무자(실효 스케줄, 백엔드 판정, #6) */}
      <div className="member-filter">
        <input
          className="member-search"
          placeholder={t('MEMBER_SEARCH')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label={t('MEMBER_SEARCH')}
        />
        <div className="member-filter-working">
          <span className="field-label">{t('SEARCH_WORKING_AT')}</span>
          <DateField value={searchDate} onChange={setSearchDate} ariaLabel={t('SEARCH_DATE')} placeholder={t('SEARCH_DATE')} />
          <TimeField value={searchTime} onChange={setSearchTime} ariaLabel={t('SEARCH_TIME')} />
          {searchDate && (
            <button
              type="button"
              className="link"
              onClick={() => { setSearchDate(''); setSearchTime('09:00') }}
            >
              {t('FILTER_RESET')}
            </button>
          )}
        </div>
      </div>
      {searchDate && searchTime && (
        <p className="muted working-hint">
          {t('WORKING_RESULT').replace('{date}', searchDate).replace('{time}', searchTime)} · {members.length}
        </p>
      )}

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
                      {/* 행 액션은 아이콘+툴팁(공휴일 화면 패턴) — 관리·스케줄을 행에서 한 번에 진입 */}
                      <div className="row-actions">
                        <button
                          type="button"
                          className="icon-btn"
                          title={t('MEMBER_MANAGE')}
                          aria-label={t('MEMBER_MANAGE')}
                          onClick={() => setManageMember(member)}
                        >
                          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                            <path
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-2-1.2L14.5 3h-5l-.4 2.6a7.4 7.4 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7.4 7.4 0 0 0 2 1.2l.4 2.6h5l.4-2.6a7.4 7.4 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.07-.4.1-.8.1-1.2Z"
                            />
                          </svg>
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          title={t('EDIT_SCHEDULE')}
                          aria-label={t('EDIT_SCHEDULE')}
                          onClick={() => setScheduleMember({ userId: member.userId, name: member.name, email: member.email })}
                        >
                          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                            <path
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              d="M7 3v3m10-3v3M4 8h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm3 7h2m3 0h2m-7 4h2m3 0h2"
                            />
                          </svg>
                        </button>
                      </div>
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
      {/* 근무 시점 검색은 결과가 근무 인원으로 자연 축소 — 페이지네이션은 일반 목록에서만(#9) */}
      {!(searchDate && searchTime) && (
        <Pagination page={page} totalPages={totalPages} totalCount={totalCount} onChange={setPage} />
      )}
    </div>
  )
}
