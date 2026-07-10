import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

interface ModalProps {
  /** 다이얼로그 제목(aria-label 겸용) */
  title: string
  onClose: () => void
  children: ReactNode
  /** 파괴적 조작 확인 — 강조 테두리 + role=alertdialog */
  danger?: boolean
}

/**
 * 공통 모달 — PC는 중앙 다이얼로그, 모바일은 바텀시트(CSS 미디어쿼리 분기).
 * 등록 폼·파괴적 조작 확인 패널의 단일 컨테이너(Phase 4 — 테이블 내 확장 행 패널 대체).
 * ESC/배경 클릭으로 닫힌다. 열려 있는 동안 본문 스크롤을 잠근다.
 */
export function Modal({ title, onClose, children, danger }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    //본문 스크롤 잠금(중첩 모달은 쓰지 않는 계약이므로 단순 복원으로 충분)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panelRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [onClose])

  return (
    <div
      className="modal-overlay"
      onMouseDown={(event) => {
        //배경(오버레이 자신) 클릭만 닫기 — 패널 내부에서 시작한 드래그는 무시
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        ref={panelRef}
        className={`modal${danger ? ' modal-danger' : ''}`}
        role={danger ? 'alertdialog' : 'dialog'}
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
      >
        <div className="modal-head">
          <h3>{title}</h3>
          <button type="button" className="modal-close" aria-label="close" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}
