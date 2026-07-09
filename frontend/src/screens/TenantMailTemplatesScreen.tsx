import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantMailTemplateApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { MailTemplatePreviewResponse, TenantMailTemplateResponse } from '../api/types'

/** 허용 변수 힌트(검증 정본은 서버 — W012와 동일 계약 문자열) */
const TEMPLATE_VARS: Record<TenantMailTemplateResponse['purpose'], string[]> = {
  INVITE: ['{memberName}', '{tenantName}', '{actionUrl}', '{expiresAt}', '{inviterName}'],
  RESET: ['{memberName}', '{tenantName}', '{actionUrl}', '{expiresAt}'],
}

interface EditTarget {
  purpose: TenantMailTemplateResponse['purpose']
  lang: TenantMailTemplateResponse['lang']
}

/**
 * W014 회사 메일 템플릿 — TENANT_ADMIN 전용.
 * 기본 템플릿(전역)이 항상 제공되고, 자기 회사 문구만 오버라이드한다:
 *  - 목록은 "유효 템플릿"(오버라이드가 있으면 그 내용) + 상태 뱃지(기본/회사 설정)
 *  - 저장 = 오버라이드 생성/갱신, [기본값으로 되돌리기] = 오버라이드 삭제(인라인 확인)
 */
export function TenantMailTemplatesScreen() {
  const { t } = useApp()
  const [templates, setTemplates] = useState<TenantMailTemplateResponse[]>([])
  const [listError, setListError] = useState<string | null>(null)

  //편집 폼
  const [target, setTarget] = useState<EditTarget | null>(null)
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [preview, setPreview] = useState<MailTemplatePreviewResponse | null>(null)
  const [busy, setBusy] = useState(false)
  /** 되돌리기는 파괴적 조작(회사 문구 소실) — 인라인 확인 패널 경유 */
  const [confirmRevert, setConfirmRevert] = useState<EditTarget | null>(null)
  const [rowError, setRowError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setTemplates(await tenantMailTemplateApi.list())
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  function openEdit(template: TenantMailTemplateResponse) {
    setTarget({ purpose: template.purpose, lang: template.lang })
    setSubject(template.subject)
    setBody(template.body)
    setFormError(null)
    setSaved(false)
    setPreview(null)
  }

  function closeEdit() {
    setTarget(null)
    setSubject('')
    setBody('')
    setFormError(null)
    setSaved(false)
    setPreview(null)
  }

  async function onPreview() {
    if (!target) return
    setFormError(null)
    setSaved(false)
    setBusy(true)
    try {
      setPreview(await tenantMailTemplateApi.preview({ ...target, subject, body }))
    } catch (e) {
      setPreview(null)
      setFormError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onSave(event: FormEvent) {
    event.preventDefault()
    if (!target) return
    setFormError(null)
    setSaved(false)
    setBusy(true)
    try {
      await tenantMailTemplateApi.update(target.purpose, target.lang, { subject, body })
      setSaved(true)
      await reload()
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onRevert(revertTarget: EditTarget) {
    setConfirmRevert(null)
    setRowError(null)
    try {
      await tenantMailTemplateApi.revert(revertTarget.purpose, revertTarget.lang)
      //편집 중이던 대상이 되돌려졌으면 폼도 닫는다(구 내용 재저장 사고 방지)
      if (target && target.purpose === revertTarget.purpose && target.lang === revertTarget.lang) {
        closeEdit()
      }
      await reload()
    } catch (e) {
      setRowError(e instanceof ApiError ? e.message : String(e))
    }
  }

  return (
    <div className="panel">
      <h2>{t('TPL_TITLE')}</h2>

      {listError && <p className="error" role="alert">{listError}</p>}
      {rowError && <p className="error" role="alert">{rowError}</p>}

      <table className="detail-table">
        <thead>
          <tr>
            <th>{t('TPL_PURPOSE')}</th>
            <th>{t('LANG')}</th>
            <th>{t('TPL_SOURCE')}</th>
            <th>{t('TPL_SUBJECT')}</th>
            <th>{t('UPDATED_AT')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {templates.map((template) => {
            const key = `${template.purpose}:${template.lang}`
            const isConfirming =
              confirmRevert?.purpose === template.purpose && confirmRevert?.lang === template.lang
            return (
              <tr key={key}>
                <td>{template.purpose}</td>
                <td>{template.lang}</td>
                <td>
                  <span className={template.overridden ? 'tenant-badge' : 'muted'}>
                    {template.overridden ? t('TPL_OVERRIDDEN') : t('TPL_DEFAULT')}
                  </span>
                </td>
                <td>{template.subject}</td>
                <td>{template.updatedAt.replace('T', ' ').slice(0, 16)}</td>
                <td>
                  <div className="row-actions">
                    <button onClick={() => openEdit(template)}>{t('EDIT')}</button>
                    {template.overridden && !isConfirming && (
                      <button onClick={() => setConfirmRevert({ purpose: template.purpose, lang: template.lang })}>
                        {t('TPL_REVERT')}
                      </button>
                    )}
                    {isConfirming && (
                      <>
                        <button className="primary" onClick={() => void onRevert(confirmRevert)}>
                          {t('SUBMIT')}
                        </button>
                        <button onClick={() => setConfirmRevert(null)}>{t('CANCEL')}</button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      {target && (
        <div className="tpl-edit">
          <form onSubmit={onSave}>
            <h3>
              {target.purpose} / {target.lang}
            </h3>
            <p className="hint">
              {t('TPL_VARS_HINT')}{' '}
              {TEMPLATE_VARS[target.purpose].map((variable) => (
                <code key={variable} className="tpl-var">
                  {variable}
                </code>
              ))}
            </p>
            <label>
              {t('TPL_SUBJECT')}
              <input value={subject} onChange={(e) => setSubject(e.target.value)} required />
            </label>
            <label>
              {t('TPL_BODY')}
              <textarea
                value={body}
                onChange={(e) => setBody(e.target.value)}
                rows={10}
                required
              />
            </label>
            {formError && <p className="error" role="alert">{formError}</p>}
            {saved && <p className="success" role="status">OK</p>}
            <div className="btn-row">
              <button type="button" onClick={() => void onPreview()} disabled={busy}>
                {t('PREVIEW')}
              </button>
              <button type="submit" className="primary" disabled={busy}>
                {t('SUBMIT')}
              </button>
              <button type="button" onClick={closeEdit}>
                {t('CANCEL')}
              </button>
            </div>
          </form>
          {preview && (
            <div className="stamp-box tpl-preview" role="status">
              <h3>{t('PREVIEW')}</h3>
              <dl className="kv">
                <dt>{t('TPL_SUBJECT')}</dt>
                <dd>{preview.subject}</dd>
              </dl>
              <pre className="tpl-preview-body">{preview.body}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
