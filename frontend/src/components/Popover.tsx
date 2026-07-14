import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode, RefObject } from 'react'
import { createPortal } from 'react-dom'

interface Placed {
  left: number
  top: number
  minWidth: number
}

/**
 * 앵커에 붙는 팝오버(셀렉트/시간/달력 공용).
 *
 * 왜 포털 + position:fixed 인가:
 *  - 예전엔 패널이 트리거의 position:absolute 자식이라, overflow-y:auto 인 모달 본문 안에서
 *    (1) 아래로 넘치면 모달이 스크롤되며 레이아웃이 밀리고(#7),
 *    (2) 모바일 바텀시트에선 화면 밖으로 잘렸다(#8).
 *  - 이제 document.body 로 포털해 화면 좌표(fixed)로 띄우고, 뷰포트 안으로 clamp 한다.
 *    공간이 부족하면 위로 뒤집고, 그래도 넘치면 뷰포트 안으로 당겨 항상 전부 보이게 한다.
 *
 * 닫힘 규약은 종전과 동일: 바깥 pointerdown / ESC(캡처 — 모달 전체가 닫히지 않게).
 */
export function useAnchoredPopover(open: boolean, onClose: () => void) {
  const anchorRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const [placed, setPlaced] = useState<Placed | null>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (anchorRef.current?.contains(target)) return
      if (panelRef.current?.contains(target)) return
      onClose()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation() //패널만 닫고 모달의 ESC 닫기로 번지지 않게
        onClose()
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown, true)
    }
  }, [open, onClose])

  useLayoutEffect(() => {
    if (!open) {
      setPlaced(null)
      return
    }
    const compute = () => {
      const anchor = anchorRef.current?.getBoundingClientRect()
      if (!anchor) return
      const margin = 8
      const vw = window.innerWidth
      const vh = window.innerHeight
      const panelW = panelRef.current?.offsetWidth ?? anchor.width
      const panelH = panelRef.current?.offsetHeight ?? 0
      //아래 공간이 부족하고 위가 더 넓으면 위로 뒤집는다
      const spaceBelow = vh - anchor.bottom
      const spaceAbove = anchor.top
      const below = spaceBelow >= panelH + margin || spaceBelow >= spaceAbove
      let top = below ? anchor.bottom + 4 : anchor.top - panelH - 4
      //그래도 넘치면 뷰포트 안으로 당긴다(항상 전부 보이게)
      top = Math.max(margin, Math.min(top, vh - panelH - margin))
      const left = Math.max(margin, Math.min(anchor.left, vw - panelW - margin))
      setPlaced({ left, top, minWidth: anchor.width })
    }
    compute()
    //패널이 실제로 마운트돼 높이가 잡힌 뒤 한 번 더(첫 계산은 높이 0일 수 있음)
    const raf = requestAnimationFrame(compute)
    window.addEventListener('resize', compute)
    window.addEventListener('scroll', compute, true)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', compute)
      window.removeEventListener('scroll', compute, true)
    }
  }, [open])

  return { anchorRef, panelRef, placed }
}

/**
 * 팝오버 패널 포털. placed 가 잡히기 전(첫 프레임)에는 화면 밖에 숨겨 깜빡임을 막는다.
 * matchWidth=true 면 앵커 너비를 최소폭으로(셀렉트 목록이 트리거만큼 넓게).
 */
export function PopoverPanel({
  panelRef,
  placed,
  className,
  matchWidth,
  ariaLabel,
  role,
  children,
}: {
  panelRef: RefObject<HTMLDivElement | null>
  placed: Placed | null
  className: string
  matchWidth?: boolean
  ariaLabel?: string
  role?: string
  children: ReactNode
}) {
  return createPortal(
    <div
      ref={panelRef}
      className={`popover-portal ${className}`}
      role={role}
      aria-label={ariaLabel}
      style={{
        left: placed ? placed.left : -9999,
        top: placed ? placed.top : -9999,
        minWidth: matchWidth && placed ? placed.minWidth : undefined,
        visibility: placed ? 'visible' : 'hidden',
      }}
    >
      {children}
    </div>,
    document.body,
  )
}
