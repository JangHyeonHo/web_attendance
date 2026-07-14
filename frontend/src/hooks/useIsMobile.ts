import { useEffect, useState } from 'react'

/** 모바일로 취급하는 최대 폭(px). 이 값 이하 = 모바일 전용 트리로 렌더 */
export const MOBILE_MAX = 640

/**
 * 뷰포트가 모바일 규격인지. 반응형 CSS로 PC를 우겨넣지 않고,
 * 이 값으로 데스크톱/모바일 화면 트리를 아예 분기하기 위한 훅.
 */
export function useIsMobile(): boolean {
  const query = `(max-width: ${MOBILE_MAX}px)`
  const [isMobile, setIsMobile] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(query).matches,
  )

  useEffect(() => {
    const mql = window.matchMedia(query)
    const handler = (event: MediaQueryListEvent) => setIsMobile(event.matches)
    mql.addEventListener('change', handler)
    setIsMobile(mql.matches)
    return () => mql.removeEventListener('change', handler)
  }, [query])

  return isMobile
}
