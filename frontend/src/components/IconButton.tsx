/**
 * 테이블 행 액션 공용 아이콘 버튼(디자인 공통화).
 * 규칙: 행 단위 반복 조작(편집·삭제·관리·스케줄 등)은 텍스트 버튼이 아니라 이 컴포넌트를 쓴다.
 * - 라벨은 aria-label + title로 들어가고, CSS 툴팁(.icon-btn:hover::after)이 즉시 표시된다.
 * - 결재성 액션(승인/반려)은 라벨 버튼 유지 — 아이콘화 대상이 아니다.
 */

const ICON_PATHS = {
  /** 연필 — 편집 */
  edit: 'M4 20h4L18.5 9.5a1.5 1.5 0 0 0 0-2.1l-1.9-1.9a1.5 1.5 0 0 0-2.1 0L4 16v4Z',
  /** 휴지통 — 삭제(danger와 함께 사용) */
  delete: 'M5 7h14M10 7V5h4v2M6 7l1 13h10l1-13',
  /** 톱니 — 관리(역할·상태·급여 등 편집 패널) */
  manage:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-2-1.2L14.5 3h-5l-.4 2.6a7.4 7.4 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7.4 7.4 0 0 0 2 1.2l.4 2.6h5l.4-2.6a7.4 7.4 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.07-.4.1-.8.1-1.2Z',
  /** 달력 — 스케줄 */
  schedule:
    'M7 3v3m10-3v3M4 8h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm3 7h2m3 0h2m-7 4h2m3 0h2',
} as const

export type IconName = keyof typeof ICON_PATHS

export function IconButton({
  icon,
  label,
  danger = false,
  disabled = false,
  onClick,
}: {
  icon: IconName
  /** 툴팁·접근성 라벨(필수) — 라벨 없는 아이콘 버튼 금지 */
  label: string
  danger?: boolean
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={`icon-btn${danger ? ' danger' : ''}`}
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          d={ICON_PATHS[icon]}
        />
      </svg>
    </button>
  )
}
