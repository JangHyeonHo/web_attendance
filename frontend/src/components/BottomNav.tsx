import type { ReactNode } from 'react'

export interface BottomNavItem {
  key: string
  label: string
  icon?: ReactNode
  active?: boolean
  onClick: () => void
}

/**
 * 모바일 하단 탭 바 — 핵심 3~5개만. 항목이 많으면 마지막을 '더보기'로 묶는다
 * (하단에 8개를 욱여넣어 글자가 잘리던 문제 해결). 데스크톱에서는 렌더하지 않는다.
 */
export function BottomNav({ items }: { items: BottomNavItem[] }) {
  return (
    <nav className="bottom-nav" aria-label="주요 메뉴">
      {items.map((item) => (
        <button
          key={item.key}
          className={item.active ? 'on' : ''}
          aria-current={item.active ? 'page' : undefined}
          onClick={item.onClick}
        >
          <span className="bn-ic" aria-hidden="true">
            {item.icon}
          </span>
          <span className="bn-label">{item.label}</span>
        </button>
      ))}
    </nav>
  )
}
