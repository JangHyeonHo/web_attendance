import { useEffect, useState } from 'react'
import { attendanceApi, tenantReportApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'

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

/** 근태 보고서 설정 — 결재(도장)란 표시 on/off. Excel·인쇄 근태 보고서에 반영된다. */
function ReportSettingSection() {
  const { t } = useApp()
  const [stampEnabled, setStampEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    attendanceApi
      .reportSetting()
      .then((r) => setStampEnabled(r.stampEnabled))
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  async function toggle(next: boolean) {
    setBusy(true)
    setSaved(false)
    setError(null)
    try {
      const r = await tenantReportApi.updateStamp(next)
      setStampEnabled(r.stampEnabled)
      setSaved(true)
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
          onChange={(e) => void toggle(e.target.checked)}
        />
        {t('REPORT_STAMP_TOGGLE')}
      </label>
      {saved && <p className="success" role="status">{t('SAVED')}</p>}
      {error && <p className="error" role="alert">{error}</p>}
    </section>
  )
}
