import { useCallback, useEffect, useState } from 'react'
import { tenantCloseApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { languageApi } from '../api/endpoints'
import { Modal } from '../components/Modal'
import type { PendingCloseResponse } from '../api/types'

/**
 * W021 근태 마감 관리 — 멤버가 신청한 월 마감을 인사관리자가 승인/반려한다.
 * 승인되면 그 (멤버, 월)의 근태 정정이 잠기고, 보고서에 도장이 날인된다.
 */
export function AttendanceCloseAdminScreen() {
  const { t: commonT, lang } = useApp()
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [rows, setRows] = useState<PendingCloseResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  //반려 사유 입력 모달
  const [rejecting, setRejecting] = useState<PendingCloseResponse | null>(null)
  const [rejectNote, setRejectNote] = useState('')

  const t = useCallback((k: string) => texts[k] ?? commonT(k), [texts, commonT])

  useEffect(() => {
    languageApi.texts('W021', lang).then(setTexts).catch(() => {})
  }, [lang])

  const reload = useCallback(async () => {
    try {
      setRows(await tenantCloseApi.pending())
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function decide(closeId: number, approve: boolean, note?: string) {
    setBusy(true)
    setError(null)
    try {
      await tenantCloseApi.decide(closeId, approve, note)
      setRejecting(null)
      setRejectNote('')
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel">
      <h2>{t('CLOSE_ADMIN_TITLE')}</h2>
      <p className="muted">{t('CLOSE_ADMIN_SUB')}</p>
      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 ? (
        <p className="muted center">{t('CLOSE_PENDING_NONE')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('CLOSE_MEMBER')}</th>
                <th>{t('CLOSE_TARGET')}</th>
                <th>{t('CLOSE_REQUESTED_AT')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.closeId}>
                  <td>{r.userName}</td>
                  <td>{r.year}. {String(r.month).padStart(2, '0')}</td>
                  <td>{r.requestedAt.slice(0, 10)}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        className="primary"
                        disabled={busy}
                        onClick={() => void decide(r.closeId, true)}
                      >
                        {t('CLOSE_APPROVE')}
                      </button>
                      <button disabled={busy} onClick={() => setRejecting(r)}>
                        {t('CLOSE_REJECT')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rejecting && (
        <Modal
          title={`${rejecting.userName} — ${t('CLOSE_REJECT')}`}
          onClose={() => setRejecting(null)}
          danger
        >
          <p className="center">
            {rejecting.year}. {String(rejecting.month).padStart(2, '0')}
          </p>
          <input
            className="reason-input"
            value={rejectNote}
            maxLength={200}
            placeholder={commonT('REASON')}
            onChange={(e) => setRejectNote(e.target.value)}
            autoFocus
          />
          <div className="btn-row">
            <button
              className="primary"
              disabled={busy}
              onClick={() => void decide(rejecting.closeId, false, rejectNote.trim() || undefined)}
            >
              {t('CLOSE_REJECT')}
            </button>
            <button onClick={() => setRejecting(null)}>{commonT('CANCEL')}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
