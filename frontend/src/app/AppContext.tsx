import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi, navigationApi } from '../api/endpoints'
import { setUnauthorizedHandler } from '../api/client'
import { makeT } from '../i18n/lang'
import type { Lang, Role, ScreenCode, UiTheme } from '../api/types'

/**
 * 서버 주도 화면 전개의 클라이언트 측 절반.
 *
 * - URL 라우팅을 사용하지 않는다. 화면 상태는 navigation API의 응답(screen 코드)만으로 결정된다.
 * - navigate()가 유일한 화면 전환 수단이며, 서버가 로그인/권한을 보고 다른 화면을 돌려줄 수 있다.
 * - 401(세션 만료)은 API 클라이언트 훅으로 감지해 로그인 화면을 전개한다.
 */
interface AppState {
  screen: ScreenCode
  userName: string | null
  /** 세션 role (navigation 응답 기준, 비로그인 null) — 헤더 메뉴 분기용 */
  role: Role | null
  /** 헤더 뱃지용 테넌트명 (auth/me 기준, SYSTEM_ADMIN·비로그인은 null) */
  tenantName: string | null
  /** 본인 부서(코드) — auth/me 기준. 근무표 인쇄 머리말 등에 사용 */
  userDepartment: string | null
  /** 테넌트 서브도메인 접속 시 그 테넌트명 — 로그인 화면이 코드 입력란을 숨기고 회사명을 표시 */
  hostTenantName: string | null
  lang: Lang
  /** 화면 적용 확정 테마(navigation 응답 — 첫 응답 전에는 null=기본 토큰) */
  theme: UiTheme | null
  /** 테마 즉시 반영(A005 설정 저장 직후 — 다음 navigation 응답이 오면 서버값으로 재동기화) */
  applyTheme: (theme: UiTheme) => void
  /** 화면 초기 데이터(출결 화면이면 StatusResponse) */
  data: unknown
  /** 텍스트 해석: 서버 언어 마스터 > 키 이름 */
  t: (key: string) => string
  navigate: (screen?: ScreenCode, lang?: Lang) => Promise<void>
  ready: boolean
  /** 직전 navigate 실패 메시지(서버 텍스트 부재 시에도 표시 가능해야 하므로 원문 그대로) */
  navError: string | null
  /** 기동 시 캡처한 비밀번호 설정 토큰(W010 전용 — 메모리에만 존재, 리렌더와 무관) */
  getPasswordToken: () => string | null
  /** W010 이탈/완료 시 즉시 폐기(민감 입력 클리어 정책 — D18) */
  clearPasswordToken: () => void
}

const AppContext = createContext<AppState | null>(null)

/**
 * 기동 시 1회 — 메일 링크의 ?token= 캡처(email-onboarding §8.1).
 * 주소창·히스토리에서 즉시 제거하고 메모리(ref)에만 보관한다(로그/Referer/히스토리 유출 방지).
 * URL 라우팅 도입이 아니다 — 이 파라미터만 읽고 서버 주도 화면 전개(navigate)로 합류한다.
 */
function captureTokenFromUrl(): string | null {
  const token = new URLSearchParams(window.location.search).get('token')
  if (token !== null) {
    history.replaceState(null, '', window.location.pathname)
  }
  return token
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [screen, setScreen] = useState<ScreenCode>('W000')
  const [userName, setUserName] = useState<string | null>(null)
  const [role, setRole] = useState<Role | null>(null)
  const [tenantName, setTenantName] = useState<string | null>(null)
  const [userDepartment, setUserDepartment] = useState<string | null>(null)
  const [hostTenantName, setHostTenantName] = useState<string | null>(null)
  const [lang, setLang] = useState<Lang>('KOR')
  const [theme, setTheme] = useState<UiTheme | null>(null)
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [headers, setHeaders] = useState<Record<string, string>>({})
  const [data, setData] = useState<unknown>(null)
  const [ready, setReady] = useState(false)
  const [navError, setNavError] = useState<string | null>(null)
  const langRef = useRef<Lang>('KOR')
  /** 동시 navigate 경합 방지 — 최신 요청의 응답만 상태에 반영한다 */
  const navSeqRef = useRef(0)
  /** 토큰은 state가 아닌 ref — 직렬화·리렌더 없이 W010에만 전달, 이탈/완료 시 클리어 */
  const passwordTokenRef = useRef<string | null | undefined>(undefined)
  if (passwordTokenRef.current === undefined) {
    //첫 렌더에서 1회만 캡처(즉시 URL에서 제거되므로 재실행돼도 무해)
    passwordTokenRef.current = captureTokenFromUrl()
  }

  const navigate = useCallback(async (nextScreen?: ScreenCode, nextLang?: Lang) => {
    const seq = ++navSeqRef.current
    try {
      const response = await navigationApi.navigate({
        screen: nextScreen ?? null,
        lang: nextLang ?? null,
      })
      if (seq !== navSeqRef.current) {
        //이후에 시작된 navigate가 있음 — 이 응답은 폐기(늦은 응답이 최종 화면을 덮지 않게)
        return
      }
      //언어는 서버 확정값으로 동기화(리로드 후 세션 언어와의 어긋남 방지)
      const appliedLang = response.lang
      langRef.current = appliedLang
      setLang(appliedLang)
      setScreen(response.screen)
      setTheme(response.theme)
      setUserName(response.userName)
      setRole(response.role)
      setHostTenantName(response.hostTenantName)
      setTexts(response.texts)
      setHeaders(response.headers)
      setData(response.data)
      setNavError(null)
      setReady(true)
    } catch (e) {
      if (seq === navSeqRef.current) {
        //ready 전이면 App이 재시도 화면을, 이후면 배너를 렌더한다
        setNavError(e instanceof Error ? e.message : String(e))
      }
    }
  }, [])

  //확정 테마를 문서 루트에 반영 — CSS [data-theme=...] 토큰 오버라이드의 스위치
  useEffect(() => {
    if (theme) {
      document.documentElement.dataset.theme = theme
    }
  }, [theme])

  const applyTheme = useCallback((next: UiTheme) => {
    setTheme(next)
  }, [])

  //세션 만료(401) → 로그인 화면 전개
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void navigate('W001')
    })
  }, [navigate])

  //기동시: 토큰 진입이면 W010(비밀번호 설정 — 공개 화면이라 서버가 그대로 전개),
  //아니면 서버에 화면 결정을 위임(로그인 상태면 홈, 아니면 랜딩)
  useEffect(() => {
    void navigate(passwordTokenRef.current ? 'W010' : undefined)
  }, [navigate])

  const getPasswordToken = useCallback(() => passwordTokenRef.current ?? null, [])
  const clearPasswordToken = useCallback(() => {
    passwordTokenRef.current = null
  }, [])

  //테넌트명 뱃지: navigation 응답에는 tenantName이 없으므로 auth/me로 1회 취득
  //(SYSTEM_ADMIN은 뱃지 미표시 계약이라 취득 자체를 생략)
  useEffect(() => {
    if (!role || role === 'SYSTEM_ADMIN') {
      setTenantName(null)
      setUserDepartment(null)
      return
    }
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (!cancelled) {
          setTenantName(me.tenantName)
          setUserDepartment(me.departCd)
        }
      })
      .catch(() => {
        //뱃지는 부가 정보 — 취득 실패는 무시(401은 클라이언트 훅이 처리)
      })
    return () => {
      cancelled = true
    }
  }, [role])

  const t = useMemo(() => makeT({ ...headers, ...texts }), [texts, headers])

  const value = useMemo<AppState>(
    () => ({
      screen, userName, role, tenantName, userDepartment, hostTenantName, lang, theme, applyTheme, data, t,
      navigate, ready, navError, getPasswordToken, clearPasswordToken,
    }),
    [screen, userName, role, tenantName, userDepartment, hostTenantName, lang, theme, applyTheme, data, t,
      navigate, ready, navError, getPasswordToken, clearPasswordToken],
  )

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export function useApp(): AppState {
  const context = useContext(AppContext)
  if (!context) {
    throw new Error('useApp must be used within AppProvider')
  }
  return context
}
