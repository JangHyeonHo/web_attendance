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

/** role별 메뉴 — core(주요 탭) + more(부가 탭). 데스크톱 상단 내비 구성용. */
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
 * 관리자 밀집 화면 — 표/다중 컬럼이 많아 모바일에서 억지로 욱여넣지 않고 PC 전용으로 안내(#4).
 * 멤버 본인용(출근 W005·휴가 W015·상세 W006)만 모바일 네이티브로 제공한다.
 */
const PC_ONLY_SCREENS = new Set<ScreenCode>([
  'W004', 'W007', 'W008', 'W009', 'W012', 'W013', 'W014', 'W016', 'W017', 'W018',
])

/** 모바일 하단 탭 — 멤버 본인용 화면만(관리 화면은 PC 전용, #4). SYSTEM_ADMIN은 하단탭 없음. */
function mobileTabs(role: Role | null): ScreenCode[] {
  switch (role) {
    case 'MEMBER':
    case 'HR_ADMIN':
    case 'TENANT_ADMIN':
      return ['W005', 'W015']
    default:
      return []
  }
}

/** 모바일에서 관리 화면 진입 시 — 표가 깨지지 않도록 PC 이용 안내(하드코딩 3개국어, 서버 텍스트 불요). */
function MobilePcOnlyNotice() {
  return (
    <div className="panel center pc-only-notice">
      <p className="pc-only-emoji" aria-hidden="true">🖥️</p>
      <p>
        이 화면은 PC(넓은 화면)에서 이용해 주세요.
        <br />
        Please use this screen on a desktop (wide screen).
        <br />
        この画面はPC（広い画面）でご利用ください。
      </p>
    </div>
  )
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

/** 모바일 하단 탭 — 멤버 본인용 화면(출근·휴가)만. 더보기 시트 폐지(언어=헤더, 로그아웃=헤더, 관리=PC전용). */
function MobileNav() {
  const { screen, role, navigate, t } = useApp()
  const items: BottomNavItem[] = mobileTabs(role).map((code) => ({
    key: code,
    label: t(LABEL_KEY[code] ?? code),
    active: screen === code,
    onClick: () => void navigate(code),
  }))
  return <BottomNav items={items} />
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

  const showBottomNav = isMobile && !!userName && mobileTabs(role).length > 0
  //모바일에서 관리 화면 진입 시 표가 깨지므로 PC 이용 안내로 대체(#4)
  const showPcOnly = isMobile && !!userName && PC_ONLY_SCREENS.has(screen)

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
          {/* 로그아웃은 상단 고정 — 더보기에서 오조작(2번 탭)되던 문제 해소(#5) */}
          {isMobile && userName && (
            <Button size="sm" onClick={() => void navigate('W002')}>
              {t('LOGOUT')}
            </Button>
          )}
        </div>
      </header>
      <main>
        {navError && (
          <p className="error" role="alert">
            {navError}
          </p>
        )}
        {showPcOnly ? <MobilePcOnlyNotice /> : <ScreenBody screen={screen} />}
      </main>
      {showBottomNav && <MobileNav />}
    </div>
  )
}
