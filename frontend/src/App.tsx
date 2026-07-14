import { useState } from 'react'
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
import { LeaveScreen } from './screens/LeaveScreen'
import { AdminLeaveScreen } from './screens/AdminLeaveScreen'
import { AuditLogScreen } from './screens/AuditLogScreen'
import { BillingScreen } from './screens/BillingScreen'
import { SelectField } from './components/fields'
import { BottomNav } from './components/BottomNav'
import type { BottomNavItem } from './components/BottomNav'
import { BottomSheet } from './components/BottomSheet'
import { Button } from './components/Button'
import { useIsMobile } from './hooks/useIsMobile'
import type { Lang, Role, ScreenCode } from './api/types'

const LANGS: Lang[] = ['KOR', 'ENG', 'JPN']
const LANG_LABEL: Record<Lang, string> = { KOR: '한국어', ENG: 'English', JPN: '日本語' }

/** 화면 코드 → 헤더 라벨 키(t로 해석). */
const LABEL_KEY: Partial<Record<ScreenCode, string>> = {
  W005: 'ATTEND',
  W015: 'LEAVE',
  W009: 'MEMBERS',
  W013: 'HOLIDAYS',
  W016: 'LEAVE_ADMIN',
  W014: 'MAIL_TEMPLATES',
  W018: 'BILLING',
  W007: 'TENANTS',
  W012: 'MAIL_TEMPLATES',
  W017: 'AUDIT_LOG',
  W004: 'ADMIN',
}

/** role별 메뉴 — core(하단탭/주요 탭) + more(더보기 시트). 모바일 하단탭은 core+더보기≤4. */
function roleNav(role: Role | null): { core: ScreenCode[]; more: ScreenCode[] } {
  switch (role) {
    case 'MEMBER':
      return { core: ['W005', 'W015'], more: [] }
    case 'HR_ADMIN':
      return { core: ['W005', 'W015', 'W009'], more: ['W013', 'W016'] }
    case 'TENANT_ADMIN':
      return { core: ['W005', 'W015', 'W009'], more: ['W013', 'W016', 'W014', 'W018'] }
    case 'SYSTEM_ADMIN':
      return { core: ['W007', 'W017'], more: ['W012', 'W004'] }
    default:
      return { core: [], more: [] }
  }
}

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
    case 'W015':
      return <LeaveScreen />
    case 'W016':
      return <AdminLeaveScreen />
    case 'W017':
      return <AuditLogScreen />
    case 'W018':
      return <BillingScreen />
    case 'W000':
    default:
      return <LandingScreen />
  }
}

/** 헤더/시트 공용 — 언어 전환. 네이티브 select 대신 커스텀 SelectField(#2). */
function LanguageSelect() {
  const { lang, screen, navigate } = useApp()
  return (
    <SelectField
      value={lang}
      ariaLabel="언어"
      compact
      options={LANGS.map((l) => ({ value: l, label: LANG_LABEL[l] }))}
      onChange={(v) => void navigate(screen, v as Lang)}
    />
  )
}

/** 데스크톱 상단 탭 — 조용한 텍스트 탭(현재 화면만 밑줄). */
function DesktopNav() {
  const { screen, userName, role, navigate, t } = useApp()
  const current = (code: ScreenCode) =>
    screen === code || (code === 'W007' && screen === 'W008') ? 'page' : undefined
  const { core, more } = roleNav(role)
  const items = [...core, ...more]
  return (
    <nav>
      {!userName && (
        <>
          <button aria-current={current('W000')} onClick={() => void navigate('W000')}>
            {t('HOME')}
          </button>
          <button aria-current={current('W001')} onClick={() => void navigate('W001')}>
            {t('LOGIN')}
          </button>
        </>
      )}
      {items.map((code) => (
        <button key={code} aria-current={current(code)} onClick={() => void navigate(code)}>
          {t(LABEL_KEY[code] ?? code)}
        </button>
      ))}
      {userName && <button onClick={() => void navigate('W002')}>{t('LOGOUT')}</button>}
    </nav>
  )
}

/** 모바일 하단 탭 + 더보기 시트. */
function MobileNav() {
  const { screen, role, navigate, t } = useApp()
  const [moreOpen, setMoreOpen] = useState(false)
  const { core, more } = roleNav(role)

  const items: BottomNavItem[] = core.map((code) => ({
    key: code,
    label: t(LABEL_KEY[code] ?? code),
    active: screen === code,
    onClick: () => void navigate(code),
  }))
  items.push({
    key: 'more',
    label: t('MORE'),
    active: moreOpen,
    onClick: () => setMoreOpen(true),
  })

  return (
    <>
      <BottomNav items={items} />
      <BottomSheet open={moreOpen} onClose={() => setMoreOpen(false)} title={t('MORE')}>
        <div className="sheet-list">
          {more.map((code) => (
            <button
              key={code}
              className="sheet-item"
              onClick={() => {
                setMoreOpen(false)
                void navigate(code)
              }}
            >
              {t(LABEL_KEY[code] ?? code)}
            </button>
          ))}
          <div className="sheet-row">
            <span className="muted">{t('LANGUAGE') || '언어'}</span>
            <LanguageSelect />
          </div>
          <button
            className="sheet-item danger"
            onClick={() => {
              setMoreOpen(false)
              void navigate('W002')
            }}
          >
            {t('LOGOUT')}
          </button>
        </div>
      </BottomSheet>
    </>
  )
}

export default function App() {
  const { screen, userName, role, tenantName, navigate, ready, navError, getPasswordToken, t } = useApp()
  const isMobile = useIsMobile()

  if (!ready) {
    if (navError) {
      return (
        <div className="panel center">
          <p className="error" role="alert">
            서버에 연결할 수 없습니다 / Cannot reach the server / サーバーに接続できません
          </p>
          <Button variant="primary" onClick={() => void navigate(getPasswordToken() ? 'W010' : undefined)}>
            재시도 / Retry / 再試行
          </Button>
        </div>
      )
    }
    return <div className="panel center muted">...</div>
  }

  const showBottomNav = isMobile && !!userName && roleNav(role).core.length > 0

  return (
    <div className={`app${showBottomNav ? ' has-bottom-nav' : ''}`}>
      <header className="header">
        <span className="brand" aria-hidden="true">
          Web<em>Attendance</em>
        </span>
        {!isMobile && <DesktopNav />}
        <div className="header-right">
          {userName && <span className="user-name">{userName}</span>}
          {tenantName && role !== 'SYSTEM_ADMIN' && <span className="tenant-badge">{tenantName}</span>}
          {/* 모바일 비로그인은 상단에 로그인 버튼(하단탭 없음) */}
          {isMobile && !userName && (
            <Button size="sm" variant="primary" onClick={() => void navigate('W001')}>
              {t('LOGIN')}
            </Button>
          )}
          <LanguageSelect />
        </div>
      </header>
      <main>
        {navError && (
          <p className="error" role="alert">
            {navError}
          </p>
        )}
        <ScreenBody screen={screen} />
      </main>
      {showBottomNav && <MobileNav />}
    </div>
  )
}
