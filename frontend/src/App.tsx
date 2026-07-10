import { useApp } from './app/AppContext'
import { LandingScreen } from './screens/LandingScreen'
import { LoginScreen } from './screens/LoginScreen'
import { AttendanceScreen } from './screens/AttendanceScreen'
import { DetailsScreen } from './screens/DetailsScreen'
import { AdminScreen } from './screens/AdminScreen'
import { TenantsScreen } from './screens/TenantsScreen'
import { MembersScreen } from './screens/MembersScreen'
import { PasswordSetupScreen } from './screens/PasswordSetupScreen'
import { PasswordResetRequestScreen } from './screens/PasswordResetRequestScreen'
import { MailTemplatesScreen } from './screens/MailTemplatesScreen'
import { HolidaysScreen } from './screens/HolidaysScreen'
import { TenantMailTemplatesScreen } from './screens/TenantMailTemplatesScreen'
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
    case 'W004':
      return <AdminScreen />
    case 'W005':
      return <AttendanceScreen />
    case 'W006':
      return <DetailsScreen />
    case 'W007':
      return <TenantsScreen />
    case 'W008':
      //테넌트 상세는 W007에 임베드 전개된다(W005→W006 패턴). 단독 전개 요청은 목록으로.
      return <TenantsScreen />
    case 'W009':
      return <MembersScreen />
    case 'W010':
      return <PasswordSetupScreen />
    case 'W011':
      return <PasswordResetRequestScreen />
    case 'W012':
      return <MailTemplatesScreen />
    case 'W013':
      return <HolidaysScreen />
    case 'W014':
      return <TenantMailTemplatesScreen />
    case 'W000':
    default:
      return <LandingScreen />
  }
}

export default function App() {
  const { screen, userName, role, tenantName, lang, t, navigate, ready, navError, getPasswordToken } =
    useApp()

  if (!ready) {
    //첫 navigation 응답 전에는 서버 텍스트가 없다 — 실패시에만 언어 중립(3개국어 병기) 재시도 화면
    if (navError) {
      return (
        <div className="panel center">
          <p className="error" role="alert">
            서버에 연결할 수 없습니다 / Cannot reach the server / サーバーに接続できません
          </p>
          {/* 토큰 진입(메일 링크) 실패 재시도는 W010 의도를 유지한다 — 기본 화면으로 새지 않게 */}
          <button className="primary" onClick={() => void navigate(getPasswordToken() ? 'W010' : undefined)}>
            재시도 / Retry / 再試行
          </button>
        </div>
      )
    }
    return <div className="panel center muted">...</div>
  }

  //현재 화면의 내비 필 강조(aria-current). W008은 W007에 임베드 전개되므로 W007로 취급
  const current = (code: ScreenCode) =>
    (screen === code || (code === 'W007' && screen === 'W008') ? 'page' : undefined)

  return (
    <div className="app">
      <header className="header">
        <nav>
          <button aria-current={current('W000')} onClick={() => void navigate('W000')}>
            {t('HOME')}
          </button>
          {!userName && (
            <button aria-current={current('W001')} onClick={() => void navigate('W001')}>
              {t('LOGIN')}
            </button>
          )}
          {(role === 'MEMBER' || role === 'TENANT_ADMIN') && (
            <button aria-current={current('W005')} onClick={() => void navigate('W005')}>
              {t('ATTEND')}
            </button>
          )}
          {role === 'TENANT_ADMIN' && (
            <>
              <button aria-current={current('W009')} onClick={() => void navigate('W009')}>
                {t('MEMBERS')}
              </button>
              <button aria-current={current('W013')} onClick={() => void navigate('W013')}>
                {t('HOLIDAYS')}
              </button>
              <button aria-current={current('W014')} onClick={() => void navigate('W014')}>
                {t('MAIL_TEMPLATES')}
              </button>
            </>
          )}
          {role === 'SYSTEM_ADMIN' && (
            <>
              <button aria-current={current('W007')} onClick={() => void navigate('W007')}>
                {t('TENANTS')}
              </button>
              <button aria-current={current('W012')} onClick={() => void navigate('W012')}>
                {t('MAIL_TEMPLATES')}
              </button>
              <button aria-current={current('W004')} onClick={() => void navigate('W004')}>
                {t('ADMIN')}
              </button>
            </>
          )}
          {userName && <button onClick={() => void navigate('W002')}>{t('LOGOUT')}</button>}
        </nav>
        <div className="header-right">
          {userName && <span className="user-name">{userName}</span>}
          {tenantName && role !== 'SYSTEM_ADMIN' && (
            <span className="tenant-badge">{tenantName}</span>
          )}
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
        {navError && (
          //화면 전환 실패(네트워크/5xx) — 현재 화면은 유지하고 배너로만 알린다
          <p className="error" role="alert">
            {navError}
          </p>
        )}
        <ScreenBody screen={screen} />
      </main>
    </div>
  )
}
