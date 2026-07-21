import { useEffect, useRef, useState } from 'react'
import { tenantReportApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { ReportSetting } from '../api/types'

/**
 * W020 회사 설정 — 근태 보고서 등 회사 공통 운영 설정. 총관리자+인사관리자.
 * 회사 정보/결제(W019, 총관리자 전용 — 재무·기밀)와 분리해, 앞으로 개발되는 설정이 이 화면에 누적된다.
 */
export function CompanySettingsScreen() {
  const { t } = useApp()
  return (
    <div className="panel">
      <h2>{t('COMPANY_SETTINGS_TITLE')}</h2>
      <p className="muted">{t('COMPANY_SETTINGS_NOTE')}</p>
      <ReportSettingSection />
    </div>
  )
}

/**
 * 근태 보고서 설정 — 결재(도장)란 표시·가산수당 적용·도장 이미지/크기.
 * 토글·크기는 [저장] 버튼으로 반영(#7), 도장 이미지는 파일 선택 즉시 업로드/삭제한다.
 */
function ReportSettingSection() {
  const { t } = useApp()
  const [setting, setSetting] = useState<ReportSetting | null>(null)
  const [stampEnabled, setStampEnabled] = useState(false)
  const [premiumEnabled, setPremiumEnabled] = useState(true)
  const [stampSize, setStampSize] = useState('MEDIUM')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  function apply(r: ReportSetting) {
    setSetting(r)
    setStampEnabled(r.stampEnabled)
    setPremiumEnabled(r.premiumEnabled)
    setStampSize(r.stampSize || 'MEDIUM')
  }

  useEffect(() => {
    tenantReportApi
      .get()
      .then(apply)
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  const dirty =
    !setting ||
    stampEnabled !== setting.stampEnabled ||
    premiumEnabled !== setting.premiumEnabled ||
    stampSize !== (setting.stampSize || 'MEDIUM')

  async function save() {
    setBusy(true)
    setSaved(false)
    setError(null)
    try {
      const r = await tenantReportApi.update({ stampEnabled, premiumEnabled, stampSize })
      apply(r)
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onPickFile(file: File) {
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      const dataUrl: string = await new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result))
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(file)
      })
      const r = await tenantReportApi.uploadStamp(dataUrl, file.type)
      apply(r)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function removeStamp() {
    setBusy(true)
    setError(null)
    try {
      await tenantReportApi.removeStamp()
      const r = await tenantReportApi.get()
      apply(r)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="ci-section">
      <h3 className="section-head">{t('REPORT_SETTINGS')}</h3>

      <p className="hint">{t('REPORT_STAMP_HINT')}</p>
      <label className="check-inline">
        <input
          type="checkbox"
          checked={stampEnabled}
          disabled={busy}
          onChange={(e) => {
            setStampEnabled(e.target.checked)
            setSaved(false)
          }}
        />
        {t('REPORT_STAMP_TOGGLE')}
      </label>

      <p className="hint" style={{ marginTop: 16 }}>{t('PAY_PREMIUM_HINT')}</p>
      <label className="check-inline">
        <input
          type="checkbox"
          checked={premiumEnabled}
          disabled={busy}
          onChange={(e) => {
            setPremiumEnabled(e.target.checked)
            setSaved(false)
          }}
        />
        {t('PAY_PREMIUM_TOGGLE')}
      </label>

      {/* 결재 도장 이미지 */}
      <div className="stamp-setting">
        <h4 className="section-subhead">{t('STAMP_IMAGE')}</h4>
        <p className="hint">{t('STAMP_IMAGE_HINT')}</p>
        <div className="stamp-row">
          <div className="stamp-preview" aria-hidden>
            {setting?.stampImageUrl ? (
              <img src={setting.stampImageUrl} alt="stamp" />
            ) : (
              <span className="stamp-circle" />
            )}
          </div>
          <div className="stamp-controls">
            <span className="muted">{setting?.stampImageUrl ? '' : t('STAMP_NONE')}</span>
            <div className="btn-row" style={{ margin: 0, justifyContent: 'flex-start' }}>
              <button type="button" disabled={busy} onClick={() => fileRef.current?.click()}>
                {t('STAMP_UPLOAD')}
              </button>
              {setting?.stampImageUrl && (
                <button type="button" className="danger" disabled={busy} onClick={() => void removeStamp()}>
                  {t('STAMP_REMOVE')}
                </button>
              )}
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) void onPickFile(f)
              }}
            />
          </div>
        </div>
        <label className="stamp-size">
          {t('STAMP_SIZE')}
          <select
            value={stampSize}
            disabled={busy}
            onChange={(e) => {
              setStampSize(e.target.value)
              setSaved(false)
            }}
          >
            <option value="SMALL">{t('STAMP_SIZE_S')}</option>
            <option value="MEDIUM">{t('STAMP_SIZE_M')}</option>
            <option value="LARGE">{t('STAMP_SIZE_L')}</option>
          </select>
        </label>
      </div>

      <div className="ci-actions">
        <button className="primary" onClick={() => void save()} disabled={busy || !dirty}>
          {t('SAVE')}
        </button>
        {saved && !dirty && <span className="success" role="status">{t('SAVED')}</span>}
      </div>
      {error && <p className="error" role="alert">{error}</p>}
    </section>
  )
}
