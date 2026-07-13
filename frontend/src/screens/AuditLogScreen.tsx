import { useCallback, useEffect, useState } from 'react'
import { adminAuditApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { AuditLogEntry } from '../api/types'

type Filter = '' | 'AUTH' | 'ERROR'

/** ISO "2026-07-13T06:36:06" → "2026-07-13 06:36:06" (초까지). */
function timeText(iso: string): string {
  return iso.slice(0, 19).replace('T', ' ')
}

/**
 * W017 감사 로그 조회 — SYSTEM_ADMIN 전용(운영사).
 * 전역(모든 테넌트 + 비인증 이벤트) 최신순. 분류(인증/에러) 필터 + 새로고침.
 */
export function AuditLogScreen() {
  const { t } = useApp()
  const [filter, setFilter] = useState<Filter>('')
  const [rows, setRows] = useState<AuditLogEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      setRows(await adminAuditApi.list(filter || undefined, 200))
      setError(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [filter])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>{t('TITLE')}</h2>
        <div className="toolbar-actions">
          <div className="seg-toggle" role="tablist">
            <button className={filter === '' ? 'active' : ''} onClick={() => setFilter('')}>
              {t('FILTER_ALL')}
            </button>
            <button className={filter === 'AUTH' ? 'active' : ''} onClick={() => setFilter('AUTH')}>
              {t('CAT_AUTH')}
            </button>
            <button className={filter === 'ERROR' ? 'active' : ''} onClick={() => setFilter('ERROR')}>
              {t('CAT_ERROR')}
            </button>
          </div>
          <button onClick={() => void reload()}>{t('REFRESH')}</button>
        </div>
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {rows.length === 0 && !error ? (
        <p className="muted center">{t('EMPTY')}</p>
      ) : (
        <div className="table-wrap">
          <table className="detail-table">
            <thead>
              <tr>
                <th>{t('TIME')}</th>
                <th>{t('CATEGORY')}</th>
                <th>{t('EVENT')}</th>
                <th>{t('TENANT')}</th>
                <th>{t('ACTOR')}</th>
                <th>{t('IP')}</th>
                <th>{t('PATH')}</th>
                <th>{t('DETAIL')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.auditId}>
                  <td className="nowrap">{timeText(r.createdAt)}</td>
                  <td>
                    <span className={`badge audit-${r.category.toLowerCase()}`}>
                      {r.category === 'ERROR' ? t('CAT_ERROR') : t('CAT_AUTH')}
                    </span>
                  </td>
                  <td>{r.event}</td>
                  <td>{r.tenantName ?? (r.tenantId != null ? `#${r.tenantId}` : '—')}</td>
                  <td className="wrap">{r.actor ?? '—'}</td>
                  <td>{r.ip ?? '—'}</td>
                  <td className="wrap">{r.requestPath ?? '—'}</td>
                  <td className="wrap">{r.detail ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
