import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { adminUiThemeApi, languageApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { Modal } from '../components/Modal'
import { ScreenGuide } from '../components/ScreenGuide'
import type { Lang, LanguageEntry, UiThemeSetting } from '../api/types'

const LANGS: Lang[] = ['KOR', 'ENG', 'JPN']

/** 테마 선택지 — 라벨 키(A005 시드)와 스와치 클래스(색으로만 계절 표현). AUTO가 기본 권장이라 선두 */
const THEME_OPTIONS: { value: UiThemeSetting; labelKey: string; swatch: string }[] = [
  { value: 'AUTO', labelKey: 'THEME_AUTO', swatch: 'auto' },
  { value: 'SPRING', labelKey: 'THEME_SPRING', swatch: 'spring' },
  { value: 'SUMMER', labelKey: 'THEME_SUMMER', swatch: 'summer' },
  { value: 'AUTUMN', labelKey: 'THEME_AUTUMN', swatch: 'autumn' },
  { value: 'WINTER', labelKey: 'THEME_WINTER', swatch: 'winter' },
]

/** A005 관리자 — 테마 설정(Phase 4) + 언어 마스터 관리(v1의 /lang_mst 화면 계승) */
export function AdminScreen() {
  const { t, applyTheme } = useApp()

  //테마 설정
  const [themeSetting, setThemeSetting] = useState<UiThemeSetting | null>(null)
  const [themeError, setThemeError] = useState<string | null>(null)
  const [themeSaved, setThemeSaved] = useState(false)

  //언어 마스터
  const [entries, setEntries] = useState<LanguageEntry[]>([])
  const [formOpen, setFormOpen] = useState(false)
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

  useEffect(() => {
    let cancelled = false
    adminUiThemeApi
      .get()
      .then((response) => {
        if (!cancelled) setThemeSetting(response.theme)
      })
      .catch((e: unknown) => {
        if (!cancelled) setThemeError(e instanceof ApiError ? e.message : String(e))
      })
    return () => {
      cancelled = true
    }
  }, [])

  /** 선택 즉시 저장 + 화면에 바로 반영(다음 navigation 응답이 서버값으로 재동기화) */
  async function onSelectTheme(next: UiThemeSetting) {
    const previous = themeSetting
    setThemeSetting(next)
    setThemeError(null)
    setThemeSaved(false)
    try {
      const response = await adminUiThemeApi.update({ theme: next })
      applyTheme(response.resolved)
      setThemeSaved(true)
    } catch (e) {
      setThemeSetting(previous) //실패 시 선택 롤백
      setThemeError(e instanceof ApiError ? e.message : String(e))
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSaved(false)
    try {
      await languageApi.adminUpsert({ windowId, langKey, lang, langValue })
      setSaved(true)
      setLangValue('')
      setFormOpen(false)
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  return (
    <>
      <div className="panel">
        <ScreenGuide>{t('SCREEN_GUIDE')}</ScreenGuide>
        <h2>{t('THEME_TITLE')}</h2>
        <p className="muted">{t('THEME_DESC')}</p>
        <div className="theme-options" role="radiogroup" aria-label={t('THEME_TITLE')}>
          {THEME_OPTIONS.map((option) => (
            <label
              key={option.value}
              className={`theme-option${themeSetting === option.value ? ' selected' : ''}`}
            >
              <input
                type="radio"
                name="ui-theme"
                value={option.value}
                checked={themeSetting === option.value}
                disabled={themeSetting === null}
                onChange={() => void onSelectTheme(option.value)}
              />
              <span className={`theme-swatch ${option.swatch}`} aria-hidden="true" />
              {t(option.labelKey)}
            </label>
          ))}
        </div>
        {themeSaved && <p className="success" role="status">{t('SAVED')}</p>}
        {themeError && <p className="error" role="alert">{themeError}</p>}
      </div>

      <div className="panel">
        <div className="toolbar">
          <h2>{t('ADMIN_I18N_TITLE')}</h2>
          <div className="toolbar-actions">
            <button className="primary" onClick={() => setFormOpen(true)}>
              {t('I18N_ADD')}
            </button>
          </div>
        </div>
        {saved && <p className="success" role="status">{t('SAVED')}</p>}
        {error && <p className="error" role="alert">{error}</p>}

        {formOpen && (
          <Modal title={t('I18N_ADD')} onClose={() => setFormOpen(false)}>
            <form onSubmit={onSubmit}>
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
          </Modal>
        )}

        <div className="table-wrap">
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
                  <td className="wrap">{entry.langValue}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}
