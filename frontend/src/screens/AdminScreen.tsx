import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { languageApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { Lang, LanguageEntry } from '../api/types'

const LANGS: Lang[] = ['KOR', 'ENG', 'JPN']

/** W004 관리자 - 언어 마스터 관리 (v1의 /lang_mst 화면 계승) */
export function AdminScreen() {
  const { t } = useApp()
  const [entries, setEntries] = useState<LanguageEntry[]>([])
  const [windowId, setWindowId] = useState('')
  const [langKey, setLangKey] = useState('')
  const [lang, setLang] = useState<Lang>('KOR')
  const [langValue, setLangValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const reload = useCallback(async () => {
    try {
      setEntries(await languageApi.adminList())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSaved(false)
    try {
      await languageApi.adminUpsert({ windowId, langKey, lang, langValue })
      setSaved(true)
      setLangValue('')
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  return (
    <div className="panel">
      <h2>{t('ADMIN_I18N_TITLE')}</h2>
      <form className="inline-form" onSubmit={onSubmit}>
        <label>
          {t('WINDOW_ID')}
          <input value={windowId} onChange={(e) => setWindowId(e.target.value)} required />
        </label>
        <label>
          {t('LANG_KEY')}
          <input value={langKey} onChange={(e) => setLangKey(e.target.value)} required />
        </label>
        <label>
          {t('LANG')}
          <select value={lang} onChange={(e) => setLang(e.target.value as Lang)}>
            {LANGS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('LANG_VALUE')}
          <input value={langValue} onChange={(e) => setLangValue(e.target.value)} required />
        </label>
        <button type="submit" className="primary">
          {t('SUBMIT')}
        </button>
      </form>
      {saved && <p className="success">OK</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <table className="detail-table">
        <thead>
          <tr>
            <th>{t('WINDOW_ID')}</th>
            <th>{t('LANG_KEY')}</th>
            <th>{t('LANG')}</th>
            <th>{t('LANG_VALUE')}</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={`${entry.windowId}:${entry.langKey}:${entry.lang}`}>
              <td>{entry.windowId}</td>
              <td>{entry.langKey}</td>
              <td>{entry.lang}</td>
              <td>{entry.langValue}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
