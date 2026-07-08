import { useApp } from '../app/AppContext'

/** W000 인덱스(홈) */
export function IndexScreen() {
  const { t } = useApp()
  return (
    <div className="panel center">
      <h1>{t('INDEX_TITLE')}</h1>
      <p className="muted">{t('INDEX_SUB')}</p>
    </div>
  )
}
