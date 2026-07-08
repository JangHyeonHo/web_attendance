import { useApp } from './app/AppContext'
import { IndexScreen } from './screens/IndexScreen'
import { LoginScreen } from './screens/LoginScreen'
import { SignupScreen } from './screens/SignupScreen'
import { AttendanceScreen } from './screens/AttendanceScreen'
import { DetailsScreen } from './screens/DetailsScreen'
import { AdminScreen } from './screens/AdminScreen'
import type { Lang, ScreenCode } from './api/types'

const LANGS: Lang[] = ['KOR', 'ENG', 'JPN']

/**
 * 화면 코드 → 컴포넌트 매핑.
 * URL 라우팅을 사용하지 않으며, 서버(navigation API)가 결정한 화면 코드로만 렌더링한다.
 */
function ScreenBody({ screen }: { screen: ScreenCode }) {
  switch (screen) {
    case 'W001':
      return <LoginScreen />
    case 'W003':
      return <SignupScreen />
    case 'W004':
      return <AdminScreen />
    case 'W005':
      return <AttendanceScreen />
    case 'W006':
      return <DetailsScreen />
    case 'W000':
    default:
      return <IndexScreen />
  }
}

export default function App() {
  const { screen, userName, lang, t, navigate, ready } = useApp()

  if (!ready) {
    return <div className="panel center muted">{t('LOADING')}</div>
  }

  return (
    <div className="app">
      <header className="header">
        <nav>
          <button onClick={() => void navigate('W000')}>{t('HOME')}</button>
          {userName ? (
            <>
              <button onClick={() => void navigate('W005')}>{t('ATTEND')}</button>
              <button onClick={() => void navigate('W004')}>{t('ADMIN')}</button>
              <button onClick={() => void navigate('W002')}>{t('LOGOUT')}</button>
            </>
          ) : (
            <>
              <button onClick={() => void navigate('W001')}>{t('LOGIN')}</button>
              <button onClick={() => void navigate('W003')}>{t('SIGNUP')}</button>
            </>
          )}
        </nav>
        <div className="header-right">
          {userName && <span className="user-name">{userName}</span>}
          <select
            aria-label="language"
            value={lang}
            onChange={(e) => void navigate(screen, e.target.value as Lang)}
          >
            {LANGS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </div>
      </header>
      <main>
        <ScreenBody screen={screen} />
      </main>
    </div>
  )
}
