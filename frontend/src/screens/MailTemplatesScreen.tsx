import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { mailTemplateApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { MailVarsTable } from '../components/MailVarsTable'
import type { MailTemplatePreviewResponse, MailTemplateResponse } from '../api/types'

/** 편집 대상 식별(행 집합은 시드 6행 고정 — purpose×lang이 자연키) */
interface EditTarget {
  purpose: MailTemplateResponse['purpose']
  lang: MailTemplateResponse['lang']
}

/**
 * W012 메일 템플릿 관리 — SYSTEM_ADMIN 전용(글로벌 제품 자산).
 * 템플릿 키×언어 목록 → 제목/본문 수정 + 샘플 값 미리보기(저장 안 함).
 * 치환 오류(미지 변수/{actionUrl} 누락)는 400으로 내려와 폼 인라인 표시.
 */
export function MailTemplatesScreen() {
  const { t } = useApp()
  const [templates, setTemplates] = useState<MailTemplateResponse[]>([])
  const [listError, setListError] = useState<string | null>(null)

  //편집 폼
  const [target, setTarget] = useState<EditTarget | null>(null)
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [preview, setPreview] = useState<MailTemplatePreviewResponse | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setTemplates(await mailTemplateApi.list())
      setListError(null)
    } catch (e) {
      setListError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  function openEdit(template: MailTemplateResponse) {
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
      setPreview(await mailTemplateApi.preview({ ...target, subject, body }))
    } catch (e) {
      //400 MAIL_TEMPLATE_UNKNOWN_VAR / MAIL_TEMPLATE_ACTION_URL_REQUIRED — 인라인 표시
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
      await mailTemplateApi.update(target.purpose, target.lang, { subject, body })
      setSaved(true)
      await reload()
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel">
      <h2>{t('TPL_TITLE')}</h2>

      {listError && <p className="error" role="alert">{listError}</p>}

      <div className="table-wrap">
      <table className="detail-table">
        <thead>
          <tr>
            <th>{t('TPL_PURPOSE')}</th>
            <th>{t('LANG')}</th>
            <th>{t('TPL_SUBJECT')}</th>
            <th>{t('UPDATED_AT')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {templates.map((template) => (
            <tr key={`${template.purpose}:${template.lang}`}>
              <td>{template.purpose}</td>
              <td>{template.lang}</td>
              <td className="wrap">{template.subject}</td>
              <td>{template.updatedAt.replace('T', ' ').slice(0, 16)}</td>
              <td>
                <div className="row-actions">
                  <button onClick={() => openEdit(template)}>{t('EDIT')}</button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>

      {target && (
        <div className="tpl-edit">
          <form onSubmit={onSave}>
            <h3>
              {target.purpose} / {target.lang}
            </h3>
            <MailVarsTable purpose={target.purpose} t={t} />
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
            {saved && <p className="success" role="status">{t('SAVED')}</p>}
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
              {/* HTML 지원(#11) — 태그 렌더 + 평문 줄바꿈 보존(pre-wrap) */}
              <div
                className="tpl-preview-body tpl-preview-html"
                dangerouslySetInnerHTML={{ __html: preview.body }}
              />
            </div>
          )}
        </div>
      )}
    </div>
  )
}
