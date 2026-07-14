import { useEffect } from 'react'
import type { ReactNode } from 'react'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  title?: string
  children: ReactNode
}

/**
 * 모바일 바텀시트 — 모달을 화면에 우겨넣지 않고 아래에서 올라오는 시트로.
 * 시각(TimeField)·날짜(DateField)·선택지 등 모바일 입력에 사용. ESC/스크림 클릭으로 닫힘.
 */
export function BottomSheet({ open, onClose, title, children }: BottomSheetProps) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', onKey)
    //시트가 열린 동안 뒤 배경 스크롤 잠금
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, onClose])

  if (!open) {
    return null
  }
  return (
    <div className="sheet-overlay" onClick={onClose}>
      <div
        className="sheet"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sheet-grip" aria-hidden="true" />
        {title && <h3 className="sheet-title">{title}</h3>}
        {children}
      </div>
    </div>
  )
}
