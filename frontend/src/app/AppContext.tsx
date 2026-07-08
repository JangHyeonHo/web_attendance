import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { navigationApi } from '../api/endpoints'
import { setUnauthorizedHandler } from '../api/client'
import { makeT } from '../i18n/builtin'
import type { Lang, ScreenCode } from '../api/types'

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
  lang: Lang
  /** 화면 초기 데이터(출결 화면이면 StatusResponse) */
  data: unknown
  /** 텍스트 해석: 서버 언어 마스터 > 내장 사전 > 키 */
  t: (key: string) => string
  navigate: (screen?: ScreenCode, lang?: Lang) => Promise<void>
  ready: boolean
}

const AppContext = createContext<AppState | null>(null)

export function AppProvider({ children }: { children: ReactNode }) {
  const [screen, setScreen] = useState<ScreenCode>('W000')
  const [userName, setUserName] = useState<string | null>(null)
  const [lang, setLang] = useState<Lang>('KOR')
  const [texts, setTexts] = useState<Record<string, string>>({})
  const [headers, setHeaders] = useState<Record<string, string>>({})
  const [data, setData] = useState<unknown>(null)
  const [ready, setReady] = useState(false)
  const langRef = useRef<Lang>('KOR')

  const navigate = useCallback(async (nextScreen?: ScreenCode, nextLang?: Lang) => {
    const response = await navigationApi.navigate({
      screen: nextScreen ?? null,
      lang: nextLang ?? null,
    })
    if (nextLang) {
      langRef.current = nextLang
      setLang(nextLang)
    }
    setScreen(response.screen)
    setUserName(response.userName)
    setTexts(response.texts)
    setHeaders(response.headers)
    setData(response.data)
    setReady(true)
  }, [])

  //세션 만료(401) → 로그인 화면 전개
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void navigate('W001')
    })
  }, [navigate])

  //기동시: 서버에 화면 결정을 위임(로그인 상태면 홈, 아니면 인덱스)
  useEffect(() => {
    void navigate()
  }, [navigate])

  const t = useMemo(() => {
    const merged = { ...headers, ...texts }
    return makeT(lang, merged)
  }, [lang, texts, headers])

  const value = useMemo<AppState>(
    () => ({ screen, userName, lang, data, t, navigate, ready }),
    [screen, userName, lang, data, t, navigate, ready],
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
