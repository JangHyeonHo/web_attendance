import { useEffect, useRef, useState } from 'react'

export interface SelectOption {
  value: string
  label: string
}

interface SelectFieldProps {
  value: string
  options: SelectOption[]
  onChange: (value: string) => void
  ariaLabel: string
  /** 좁은 폭(시/분 등 짧은 값) */
  compact?: boolean
}

/**
 * 전용 셀렉트 — 네이티브 <select> 대체(Phase 5.1).
 * 옵션이 리스트 패널로 열리고, 부모 리렌더(모달 포커스 관리 등)에 영향받지 않는다.
 * 바깥 클릭/ESC로 닫히고, 열릴 때 선택 항목을 가운데로 스크롤한다.
 */
export function SelectField({ value, options, onChange, ariaLabel, compact }: SelectFieldProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const selectedRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation() //모달의 ESC 닫기보다 먼저 — 리스트만 닫는다
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    //캡처 단계에서 ESC를 가로채 모달 전체가 닫히는 사고 방지
    document.addEventListener('keydown', onKeyDown, true)
    selectedRef.current?.scrollIntoView({ block: 'center' })
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown, true)
    }
  }, [open])

  const current = options.find((option) => option.value === value)

  return (
    <div className={`field-select${compact ? ' compact' : ''}`} ref={rootRef}>
      <button
        type="button"
        className="field-select-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={ariaLabel}
        onClick={() => setOpen((v) => !v)}
      >
        <span>{current?.label ?? value}</span>
        <span className="field-select-chevron" aria-hidden="true" />
      </button>
      {open && (
        <div className="field-select-list" role="listbox" aria-label={ariaLabel}>
          {options.map((option) => {
            const selected = option.value === value
            return (
              <button
                key={option.value}
                ref={selected ? selectedRef : undefined}
                type="button"
                role="option"
                aria-selected={selected}
                className={selected ? 'selected' : ''}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
              >
                {option.label}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

const HOURS: SelectOption[] = Array.from({ length: 24 }, (_, h) => {
  const value = String(h).padStart(2, '0')
  return { value, label: value }
})

const MINUTES: SelectOption[] = Array.from({ length: 60 }, (_, m) => {
  const value = String(m).padStart(2, '0')
  return { value, label: value }
})

interface TimeFieldProps {
  /** "HH:mm" */
  value: string
  onChange: (value: string) => void
  ariaLabel: string
  disabled?: boolean
}

/**
 * 전용 시각 선택 — 네이티브 <input type="time"> 대체.
 * 시/분을 각각 리스트 셀렉트로 고른다(모바일에서도 동일한 UI).
 */
export function TimeField({ value, onChange, ariaLabel, disabled }: TimeFieldProps) {
  const [hour, minute] = value.split(':')
  return (
    <div className={`time-field${disabled ? ' disabled' : ''}`} aria-label={ariaLabel}>
      <SelectField
        compact
        value={hour}
        options={HOURS}
        ariaLabel={`${ariaLabel} (hour)`}
        onChange={(h) => onChange(`${h}:${minute}`)}
      />
      <span className="time-colon" aria-hidden="true">:</span>
      <SelectField
        compact
        value={minute}
        options={MINUTES}
        ariaLabel={`${ariaLabel} (minute)`}
        onChange={(m) => onChange(`${hour}:${m}`)}
      />
    </div>
  )
}
