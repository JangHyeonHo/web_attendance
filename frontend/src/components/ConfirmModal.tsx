import type { ReactNode } from 'react'
import { Modal } from './Modal'
import { ModalSubject } from './fields'

/**
 * 확인 모달 공통 구조(디자인 공통화) — 대상 표시(ModalSubject) + 안내 한 줄 + [확인/취소].
 * 규칙: 파괴적/불가역 확인은 danger. 대상은 가운데 텍스트 조합이 아니라 subject로.
 * 입력(사유 등)이 필요한 모달은 이 컴포넌트 대상이 아니다 — Modal + 공용 필드로 직접 구성.
 */
export function ConfirmModal({
  title,
  subject,
  secondary,
  hint,
  danger = false,
  busy = false,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onClose,
  children,
}: {
  title: string
  /** 확인 대상(이름·기간 등). 없으면 children으로 본문 구성 */
  subject?: string
  secondary?: string
  /** 결과 설명 한 줄(예: '삭제 후 되돌릴 수 없습니다') */
  hint?: string
  danger?: boolean
  busy?: boolean
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  onClose: () => void
  children?: ReactNode
}) {
  return (
    <Modal title={title} onClose={onClose} danger={danger}>
      {subject != null && <ModalSubject primary={subject} secondary={secondary} />}
      {children}
      {hint != null && <p className="hint center">{hint}</p>}
      <div className="btn-row">
        <button className="primary" disabled={busy} onClick={onConfirm}>
          {confirmLabel}
        </button>
        <button disabled={busy} onClick={onClose}>
          {cancelLabel}
        </button>
      </div>
    </Modal>
  )
}
