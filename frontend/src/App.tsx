import { useState } from 'react'
import type { ReactNode } from 'react'
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
import { CompanyInfoScreen } from './screens/CompanyInfoScreen'
import { CompanySettingsScreen } from './screens/CompanySettingsScreen'
import { AttendanceCloseAdminScreen } from './screens/AttendanceCloseAdminScreen'
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
  M001: 'ATTEND',
  M003: 'LEAVE',
  T001: 'MEMBERS',
  T002: 'HOLIDAYS',
  T003: 'LEAVE_ADMIN',
  T005: 'MAIL_TEMPLATES',
  T006: 'BILLING',
  T007: 'COMPANY_INFO',
  T008: 'COMPANY_SETTINGS',
  T004: 'CLOSE_NAV',
  A001: 'TENANTS',
  A004: 'MAIL_TEMPLATES',
  A003: 'AUDIT_LOG',
  A005: 'ADMIN',
}

/**
 * 상단 헤더에 남기는 개인 업무 탭(출결·휴가).
 * 관리 메뉴는 헤더가 아니라 좌측 사이드바(adminSections)로 내린다(내비 방향 A).
 * SYSTEM_ADMIN(운영사)은 개인 출결/휴가가 없어 상단 탭이 비고, 전부 사이드바로 간다.
 */
function topTabs(role: Role | null): ScreenCode[] {
  switch (role) {
    case 'MEMBER':
    case 'HR_ADMIN':
    case 'TENANT_ADMIN':
      return ['M001', 'M003']
    default:
      return []
  }
}

/** 좌측 사이드바 한 묶음 — 섹션 라벨 키(W999) + 화면 코드들. */
type NavSection = { key: string; items: ScreenCode[] }

/**
 * 관리 메뉴 — 좌측 사이드바(PC + 관리자 이상 전용, 내비 방향 A).
 * 섹션(조직/휴가·근태/설정/운영)으로 묶어 세로로 전부 노출한다(더보기·스크롤 은닉 없음).
 * 빈 배열이면 사이드바를 렌더하지 않는다(= MEMBER, 비로그인).
 */
function adminSections(role: Role | null): NavSection[] {
  switch (role) {
    case 'HR_ADMIN':
      return [
        { key: 'NAV_SEC_ORG', items: ['T001', 'T002'] },
        { key: 'NAV_SEC_LEAVE', items: ['T003', 'T004'] },
        //회사 설정(T008)만 인사관리자에게 노출 — 정보/결제(T007)는 총관리자 전용
        { key: 'NAV_SEC_SETTINGS', items: ['T008'] },
      ]
    case 'TENANT_ADMIN':
      return [
        { key: 'NAV_SEC_ORG', items: ['T001', 'T002'] },
        { key: 'NAV_SEC_LEAVE', items: ['T003', 'T004'] },
        { key: 'NAV_SEC_SETTINGS', items: ['T005', 'T006', 'T007', 'T008'] },
      ]
    case 'SYSTEM_ADMIN':
      return [
        { key: 'NAV_SEC_OPS', items: ['A001', 'A003'] },
        { key: 'NAV_SEC_SETTINGS', items: ['A004', 'A005'] },
      ]
    default:
      return []
  }
}

/**
 * 관리자 밀집 화면 — 표/다중 컬럼이 많아 모바일에서 억지로 욱여넣지 않고 PC 전용으로 안내(#4).
 * 멤버 본인용(출근 M001·휴가 M003·상세 M002)만 모바일 네이티브로 제공한다.
 */
const PC_ONLY_SCREENS = new Set<ScreenCode>([
  'A005', 'A001', 'A002', 'T001', 'A004', 'T002', 'T005', 'T003', 'A003', 'T006', 'T007', 'T008', 'T004',
])

/** 모바일 하단 탭 — 멤버 본인용 화면만(관리 화면은 PC 전용, #4). SYSTEM_ADMIN은 하단탭 없음. */
function mobileTabs(role: Role | null): ScreenCode[] {
  switch (role) {
    case 'MEMBER':
    case 'HR_ADMIN':
    case 'TENANT_ADMIN':
      return ['M001', 'M003']
    default:
      return []
  }
}

/** 모바일에서 관리(PC 전용) 화면 진입 시 — 잘못된 접근 안내 + 출결 화면으로 복귀 버튼.
 *  서버 텍스트에 의존하지 않도록 현재 언어(lang)로 직접 분기한다(로그인 세션이라 lang은 확정). */
function MobilePcOnlyNotice() {
  const { navigate, lang } = useApp()
  const L = (ko: string, en: string, ja: string) => (lang === 'ENG' ? en : lang === 'JPN' ? ja : ko)
  return (
    <div className="panel center pc-only-notice">
      <p className="pc-only-emoji" aria-hidden="true">🚫</p>
      <p className="pc-only-title">{L('잘못된 접근입니다', 'Invalid access', '不正なアクセスです')}</p>
      <p className="pc-only-sub">
        {L(
          '이 화면은 PC(넓은 화면)에서만 이용할 수 있어요.',
          'This screen is available on desktop (wide screen) only.',
          'この画面はPC（広い画面）専用です。',
        )}
      </p>
      <button type="button" className="primary" onClick={() => void navigate('M001')}>
        {L('출결 화면으로 이동', 'Go to attendance', '出勤画面へ移動')}
      </button>
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
    case 'A005':
      return <AdminScreen />
    case 'M001':
      return <AttendanceScreen />
    case 'M002':
      return <DetailsScreen />
    case 'A001':
      return <TenantsScreen />
    case 'A002':
      //테넌트 상세는 A001에 임베드 전개된다(M001→M002 패턴). 단독 전개 요청은 목록으로.
      return <TenantsScreen />
    case 'T001':
      return <MembersScreen />
    case 'W010':
      return <PasswordSetupScreen />
    case 'W011':
      return <PasswordResetRequestScreen />
    case 'A004':
      return <MailTemplatesScreen />
    case 'T002':
      return <HolidaysScreen />
    case 'T005':
      return <TenantMailTemplatesScreen />
    case 'M003':
      return <LeaveScreen />
    case 'T003':
      return <AdminLeaveScreen />
    case 'A003':
      return <AuditLogScreen />
    case 'T006':
      return <BillingScreen />
    case 'T007':
      return <CompanyInfoScreen />
    case 'T008':
      return <CompanySettingsScreen />
    case 'T004':
      return <AttendanceCloseAdminScreen />
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

/**
 * 데스크톱 상단 탭 — 개인 업무 탭(출결·휴가)만. 조용한 텍스트 탭(현재 화면만 밑줄).
 * 관리 메뉴는 AdminSidebar로, 로그아웃/언어는 헤더 우측 계정 영역으로 분리했다(내비 방향 A).
 */
function DesktopNav() {
  const { screen, userName, role, navigate, t } = useApp()
  const current = (code: ScreenCode) =>
    screen === code || (code === 'A001' && screen === 'A002') ? 'page' : undefined
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
      {topTabs(role).map((code) => (
        <button key={code} aria-current={current(code)} onClick={() => void navigate(code)}>
          {t(LABEL_KEY[code] ?? code)}
        </button>
      ))}
    </nav>
  )
}

/**
 * 좌측 관리자 사이드바 — PC + 관리자 이상 전용, 여닫이 가능(collapsed).
 * 섹션별로 관리 메뉴를 세로 전개. 접으면 렌더하지 않고 본문이 전체 폭을 쓴다.
 */
function AdminSidebar() {
  const { screen, role, navigate, t } = useApp()
  const isActive = (code: ScreenCode) => screen === code || (code === 'A001' && screen === 'A002')
  return (
    <aside className="sidebar" aria-label={t('NAV_ADMIN')}>
      {adminSections(role).map((sec) => (
        <div className="sidebar-group" key={sec.key}>
          <p className="sidebar-group-label">{t(sec.key)}</p>
          {sec.items.map((code) => (
            <button
              key={code}
              className={isActive(code) ? 'sidebar-item is-active' : 'sidebar-item'}
              aria-current={isActive(code) ? 'page' : undefined}
              onClick={() => void navigate(code)}
            >
              {t(LABEL_KEY[code] ?? code)}
            </button>
          ))}
        </div>
      ))}
    </aside>
  )
}

/** 하단 탭 아이콘 — 텍스트만이면 탭 타깃이 작아 누르기 어려움. 출근=시계, 휴가=달력(라인 아이콘). */
const TAB_ICON: Partial<Record<ScreenCode, ReactNode>> = {
  M001: (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  ),
  M003: (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4.5" width="18" height="16.5" rx="2" />
      <path d="M3 9.5h18M8 2.5v4M16 2.5v4M9 14l2 2 4-4" />
    </svg>
  ),
}

/** 모바일 하단 탭 — 멤버 본인용 화면(출근·휴가)만. 더보기 시트 폐지(언어=헤더, 로그아웃=헤더, 관리=PC전용). */
function MobileNav() {
  const { screen, role, navigate, t } = useApp()
  const items: BottomNavItem[] = mobileTabs(role).map((code) => ({
    key: code,
    label: t(LABEL_KEY[code] ?? code),
    icon: TAB_ICON[code],
    active: screen === code,
    onClick: () => void navigate(code),
  }))
  return <BottomNav items={items} />
}

const SIDEBAR_KEY = 'nav.sidebar'

export default function App() {
  const { screen, userName, role, tenantName, navigate, ready, navError, getPasswordToken, t } = useApp()
  const isMobile = useIsMobile()
  //사이드바 여닫이 상태 — 새로고침 후에도 유지(localStorage)
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === 'collapsed'
    } catch {
      return false
    }
  })
  const toggleSidebar = () =>
    setSidebarCollapsed((prev) => {
      const next = !prev
      try {
        localStorage.setItem(SIDEBAR_KEY, next ? 'collapsed' : 'open')
      } catch {
        //storage 접근 불가(사생활 모드 등)면 세션 내 상태만 유지
      }
      return next
    })

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
  //좌측 관리 사이드바 — PC + 관리자 이상(관리 메뉴가 있는 role)만(내비 방향 A)
  const hasSidebar = !isMobile && !!userName && adminSections(role).length > 0
  const sidebarOpen = hasSidebar && !sidebarCollapsed

  return (
    <div
      className={`app${showBottomNav ? ' has-bottom-nav' : ''}${hasSidebar ? ' app--admin' : ''}${
        sidebarOpen ? ' sidebar-open' : ''
      }`}
    >
      <header className="header">
        {/* 사이드바 여닫이 토글 — 접어도 다시 열 수 있게 헤더에 고정 */}
        {hasSidebar && (
          <button
            type="button"
            className="sidebar-toggle"
            aria-label={t('NAV_TOGGLE')}
            aria-expanded={sidebarOpen}
            onClick={toggleSidebar}
          >
            <span aria-hidden="true">☰</span>
          </button>
        )}
        {/* 가제 MT(미라이타임) — 정식 서비스명 확정 시 교체 */}
        <span className="brand" aria-hidden="true">
          M<em>T</em>
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
          {/* 로그아웃은 우측 계정 영역에 항상 고정(PC·모바일 공통) — 탭 사이에 묻히던 문제 해소(내비 방향 A) */}
          {userName && (
            <Button size="sm" onClick={() => void navigate('W002')}>
              {t('LOGOUT')}
            </Button>
          )}
        </div>
      </header>
      <div className="app-body">
        {sidebarOpen && <AdminSidebar />}
        <main>
          {navError && (
            <p className="error" role="alert">
              {navError}
            </p>
          )}
          {showPcOnly ? <MobilePcOnlyNotice /> : <ScreenBody screen={screen} />}
        </main>
      </div>
      {showBottomNav && <MobileNav />}
    </div>
  )
}
