import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { authApi, tenantMemberApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { MemberCreateResponse, MemberSummary, Role, UserStatus } from '../api/types'

const ROLE_LABEL_KEYS: Partial<Record<Role, string>> = {
  TENANT_ADMIN: 'ROLE_TENANT_ADMIN',
  MEMBER: 'ROLE_MEMBER',
}

const STATUS_LABEL_KEYS: Record<UserStatus, string> = {
  ACTIVE: 'STATUS_ACTIVE',
  DISABLED: 'STATUS_DISABLED',
  PENDING: 'STATUS_PENDING', //스키마 대비용 — Phase 2에선 표시만
}

/** 파괴적 조작(비활성/관리자 해제)은 인라인 확인 패널 경유 */
interface PendingAction {
  userId: number
  action: 'DISABLE' | 'DEMOTE'
}

/**
 * W009 멤버 관리 — TENANT_ADMIN 전용.
 * 목록/등록(초기 비밀번호 1회 표시)/비활성·재활성/관리자 지정·해제.
 * 마지막 관리자 보호(409 LAST_TENANT_ADMIN)는 서버 단일 책임 — 프론트는 사전 판단 없이
 * 에러 메시지를 해당 행 바로 아래 인라인으로 표시한다.
 */
export function MembersScreen() {
  const { t } = useApp()
  const [members, setMembers] = useState<MemberSummary[]>([])
  const [listError, setListError] = useState<string | null>(null)
  /** 자기 자신 행의 강등/비활성 버튼은 렌더하지 않는다(세션 파괴 사고 방지) */
  const [myUserId, setMyUserId] = useState<number | null>(null)

  //등록 폼
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [departCd, setDepartCd] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  /** 초기 비밀번호 1회 표시 패널 — 닫으면 상태에서 즉시 폐기(어디에도 저장하지 않는다) */
  const [created, setCreated] = useState<MemberCreateResponse | null>(null)
  const passwordRef = useRef<HTMLElement | null>(null)

  //행 조작
  const [pending, setPending] = useState<PendingAction | null>(null)
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

  async function onCreate(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const response = await tenantMemberApi.create({
        email: email.trim(),
        name: name.trim(),
        departCd: departCd.trim() || null,
      })
      setCreated(response)
      setEmail('')
      setName('')
      setDepartCd('')
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

  async function copyPassword(text: string) {
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      //클립보드 실패시 텍스트 선택 폴백(사용자가 직접 복사)
      const node = passwordRef.current
      if (node) {
        const range = document.createRange()
        range.selectNodeContents(node)
        const selection = window.getSelection()
        selection?.removeAllRanges()
        selection?.addRange(range)
      }
    }
  }

  async function updateStatus(userId: number, status: UserStatus) {
    setPending(null)
    setRowError(null)
    try {
      await tenantMemberApi.updateStatus(userId, { status })
      await reload()
    } catch (e) {
      //409 LAST_TENANT_ADMIN 등 — 행 바로 아래 인라인 표시, 목록은 그대로 둔다
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

  function confirmLabel(action: PendingAction['action']): string {
    return action === 'DISABLE' ? t('DISABLE') : t('DEMOTE')
  }

  return (
    <div className="panel">
      <h2>{t('MEMBERS_TITLE')}</h2>

      <form className="inline-form" onSubmit={onCreate}>
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
        <button type="submit" className="primary" disabled={submitting}>
          {t('MEMBER_CREATE')}
        </button>
      </form>
      {formError && <p className="error" role="alert">{formError}</p>}

      {created && (
        <div className="stamp-box initial-pwd" role="status">
          <dl className="kv">
            <dt>{t('EMAIL')}</dt>
            <dd>{created.email}</dd>
            <dt>{t('NAME')}</dt>
            <dd>{created.name}</dd>
            <dt>{t('INITIAL_PWD')}</dt>
            <dd>
              <code ref={passwordRef}>{created.initialPassword}</code>
            </dd>
          </dl>
          <p className="muted">{t('INITIAL_PWD_NOTE')}</p>
          <div className="btn-row">
            <button onClick={() => void copyPassword(created.initialPassword)}>{t('COPY')}</button>
            <button onClick={() => setCreated(null)}>{t('CLOSE')}</button>
          </div>
        </div>
      )}

      {listError && <p className="error" role="alert">{listError}</p>}

      <table className="detail-table">
        <thead>
          <tr>
            <th>{t('NAME')}</th>
            <th>{t('EMAIL')}</th>
            <th>{t('DEPART')}</th>
            <th>{t('ROLE')}</th>
            <th>{t('STATUS')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {members.map((member) => {
            const self = myUserId !== null && member.userId === myUserId
            return (
              <Fragment key={member.userId}>
                <tr>
                  <td>{member.name}</td>
                  <td>{member.email}</td>
                  <td>{member.departCd ?? '-'}</td>
                  <td>{t(ROLE_LABEL_KEYS[member.role] ?? member.role)}</td>
                  <td>{t(STATUS_LABEL_KEYS[member.status])}</td>
                  <td>
                    <div className="row-actions">
                      {member.role === 'MEMBER' && (
                        <button onClick={() => void updateRole(member.userId, 'TENANT_ADMIN')}>
                          {t('PROMOTE')}
                        </button>
                      )}
                      {member.role === 'TENANT_ADMIN' && !self && (
                        <button
                          onClick={() => setPending({ userId: member.userId, action: 'DEMOTE' })}
                        >
                          {t('DEMOTE')}
                        </button>
                      )}
                      {member.status === 'ACTIVE' && !self && (
                        <button
                          onClick={() => setPending({ userId: member.userId, action: 'DISABLE' })}
                        >
                          {t('DISABLE')}
                        </button>
                      )}
                      {member.status === 'DISABLED' && (
                        <button onClick={() => void updateStatus(member.userId, 'ACTIVE')}>
                          {t('ENABLE')}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
                {pending?.userId === member.userId && (
                  <tr>
                    <td colSpan={6}>
                      <div className="stamp-box confirm" role="alertdialog">
                        <p>
                          {member.name} — {confirmLabel(pending.action)}
                        </p>
                        <div className="btn-row">
                          <button
                            className="primary"
                            onClick={() =>
                              pending.action === 'DISABLE'
                                ? void updateStatus(member.userId, 'DISABLED')
                                : void updateRole(member.userId, 'MEMBER')
                            }
                          >
                            {t('SUBMIT')}
                          </button>
                          <button onClick={() => setPending(null)}>{t('CANCEL')}</button>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
                {rowError?.userId === member.userId && (
                  <tr>
                    <td colSpan={6}>
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
  )
}
