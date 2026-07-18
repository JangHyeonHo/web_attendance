import { useEffect, useRef, useState } from 'react'
import { useAnchoredPopover, PopoverPanel } from './Popover'

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
 * 목록은 포털 팝오버로 열려 모달 overflow에 잘리지 않고(#7·#8), 바깥 클릭/ESC로 닫힌다.
 * 열릴 때 선택 항목을 가운데로 스크롤한다.
 */
export function SelectField({ value, options, onChange, ariaLabel, compact }: SelectFieldProps) {
  const [open, setOpen] = useState(false)
  const { anchorRef, panelRef, placed } = useAnchoredPopover(open, () => setOpen(false))
  const selectedRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (open) selectedRef.current?.scrollIntoView({ block: 'center' })
  }, [open])

  const current = options.find((option) => option.value === value)

  return (
    <div className={`field-select${compact ? ' compact' : ''}`} ref={anchorRef}>
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
        <PopoverPanel
          panelRef={panelRef}
          placed={placed}
          className="field-select-list"
          matchWidth
          role="listbox"
          ariaLabel={ariaLabel}
        >
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
        </PopoverPanel>
      )}
    </div>
  )
}

const HOURS = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'))
const MINUTES = Array.from({ length: 60 }, (_, m) => String(m).padStart(2, '0'))

interface TimeFieldProps {
  /** "HH:mm" */
  value: string
  onChange: (value: string) => void
  ariaLabel: string
  disabled?: boolean
}

/**
 * 전용 시각 선택 — 네이티브 <input type="time"> 대체.
 * "09:30" 하나의 필드이고, 누르면 시·분 2열이 한 패널(포털)로 열린다(분 선택 시 닫힘).
 */
export function TimeField({ value, onChange, ariaLabel, disabled }: TimeFieldProps) {
  const [open, setOpen] = useState(false)
  const { anchorRef, panelRef, placed } = useAnchoredPopover(open, () => setOpen(false))
  const hourRef = useRef<HTMLButtonElement>(null)
  const minuteRef = useRef<HTMLButtonElement>(null)
  const [hour, minute] = value.split(':')

  useEffect(() => {
    if (!open) return
    hourRef.current?.scrollIntoView({ block: 'center' })
    minuteRef.current?.scrollIntoView({ block: 'center' })
  }, [open])

  return (
    <div className="time-picker" ref={anchorRef}>
      <button
        type="button"
        className="field-select-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="time-value">{value}</span>
        <span className="field-select-chevron" aria-hidden="true" />
      </button>
      {open && (
        <PopoverPanel panelRef={panelRef} placed={placed} className="time-picker-panel">
          <div className="time-col" role="listbox" aria-label={`${ariaLabel} (hour)`}>
            {HOURS.map((h) => (
              <button
                key={h}
                ref={h === hour ? hourRef : undefined}
                type="button"
                role="option"
                aria-selected={h === hour}
                className={h === hour ? 'selected' : ''}
                onClick={() => onChange(`${h}:${minute}`)}
              >
                {h}
              </button>
            ))}
          </div>
          <div className="time-col" role="listbox" aria-label={`${ariaLabel} (minute)`}>
            {MINUTES.map((m) => (
              <button
                key={m}
                ref={m === minute ? minuteRef : undefined}
                type="button"
                role="option"
                aria-selected={m === minute}
                className={m === minute ? 'selected' : ''}
                onClick={() => {
                  onChange(`${hour}:${m}`)
                  setOpen(false) //분까지 고르면 완성 — 닫는다
                }}
              >
                {m}
              </button>
            ))}
          </div>
        </PopoverPanel>
      )}
    </div>
  )
}
