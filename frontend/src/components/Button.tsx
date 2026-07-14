import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'default' | 'ghost' | 'danger'
type Size = 'md' | 'sm'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** primary=강조(액센트) · default=보조 · ghost=조용함 · danger=위험 */
  variant?: Variant
  /** md=폼·페이지 액션(40px) · sm=표 행·툴바 인라인(32px) */
  size?: Size
  /** 아이콘 전용(정사각 — 최소폭 규칙 예외) */
  icon?: boolean
  children?: ReactNode
}

/**
 * 공용 버튼 — 화면마다 제각각이던 버튼을 한 규격으로.
 * 최소폭을 둬(96/64px) 짧은 라벨도 폭이 들쭉날쭉하지 않는다(아이콘 전용만 정사각 예외).
 * 앱의 기존 button/button.primary 스타일 위에 .ui-btn이 폭·정렬만 보강한다.
 */
export function Button({
  variant = 'default',
  size = 'md',
  icon = false,
  className,
  type = 'button',
  children,
  ...rest
}: ButtonProps) {
  const classes = [
    'ui-btn',
    variant !== 'default' ? variant : '',
    size === 'sm' ? 'sm' : '',
    icon ? 'icon' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ')
  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  )
}
