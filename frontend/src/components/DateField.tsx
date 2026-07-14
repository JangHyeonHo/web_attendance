import { useEffect, useRef, useState } from 'react'

interface DateFieldProps {
  /** "YYYY-MM-DD" (빈 문자열이면 미선택 → 오늘 달을 연다) */
  value: string
  onChange: (value: string) => void
  ariaLabel: string
  /** 빨간색으로 표시할 공휴일(ISO yyyy-MM-dd) */
  holidays?: string[]
  /** 이 날짜 이전은 선택 불가(ISO yyyy-MM-dd) */
  min?: string
  disabled?: boolean
  /** 표시용 플레이스홀더(미선택 시) */
  placeholder?: string
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function pad(n: number): string {
  return String(n).padStart(2, '0')
}
function iso(year: number, month0: number, day: number): string {
  return `${year}-${pad(month0 + 1)}-${pad(day)}`
}
function todayIso(): string {
  const now = new Date()
  return iso(now.getFullYear(), now.getMonth(), now.getDate())
}

/**
 * 전용 날짜 선택 — 네이티브 <input type="date"> 대체(브라우저·모바일 편차 제거).
 * 트리거를 누르면 달력 패널이 열리고, 월 이동·날짜 선택으로 값이 정해진다.
 * SelectField/TimeField와 동일한 팝오버 규약(바깥 클릭·ESC로 닫힘, 모달 안에서도 안전).
 */
export function DateField({ value, onChange, ariaLabel, holidays, min, disabled, placeholder }: DateFieldProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const holidaySet = holidays ? new Set(holidays) : null

  //보고 있는 달(1일 기준). 선택값이 있으면 그 달, 없으면 오늘 달.
  const base = value ? new Date(`${value}T00:00:00`) : new Date()
  const [viewYear, setViewYear] = useState(base.getFullYear())
  const [viewMonth, setViewMonth] = useState(base.getMonth())

  useEffect(() => {
    if (!open) return
    //열 때마다 선택값(없으면 오늘) 달로 맞춘다
    const d = value ? new Date(`${value}T00:00:00`) : new Date()
    setViewYear(d.getFullYear())
    setViewMonth(d.getMonth())
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation() //모달 전체가 닫히지 않게 — 달력만 닫는다
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown, true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  function shiftMonth(delta: number) {
    const next = new Date(viewYear, viewMonth + delta, 1)
    setViewYear(next.getFullYear())
    setViewMonth(next.getMonth())
  }

  //6주(42칸) 그리드 — 첫 주는 일요일까지 앞달로 채운다
  const firstWeekday = new Date(viewYear, viewMonth, 1).getDay()
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate()
  const cells: { day: number; month0: number; year: number; inMonth: boolean }[] = []
  for (let i = 0; i < firstWeekday; i++) {
    const d = new Date(viewYear, viewMonth, i - firstWeekday + 1)
    cells.push({ day: d.getDate(), month0: d.getMonth(), year: d.getFullYear(), inMonth: false })
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ day: d, month0: viewMonth, year: viewYear, inMonth: true })
  }
  while (cells.length % 7 !== 0 || cells.length < 42) {
    const last = cells[cells.length - 1]
    const d = new Date(last.year, last.month0, last.day + 1)
    cells.push({ day: d.getDate(), month0: d.getMonth(), year: d.getFullYear(), inMonth: false })
    if (cells.length >= 42) break
  }

  const today = todayIso()

  return (
    <div className="date-field" ref={rootRef}>
      <button
        type="button"
        className="field-select-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={value ? 'date-value' : 'date-value muted'}>{value || placeholder || '날짜 선택'}</span>
        <span className="field-select-chevron" aria-hidden="true" />
      </button>
      {open && (
        <div className="date-panel" role="dialog" aria-label={ariaLabel}>
          <div className="date-panel-head">
            <button type="button" className="date-nav" aria-label="이전 달" onClick={() => shiftMonth(-1)}>
              ‹
            </button>
            <b>
              {viewYear}년 {viewMonth + 1}월
            </b>
            <button type="button" className="date-nav" aria-label="다음 달" onClick={() => shiftMonth(1)}>
              ›
            </button>
          </div>
          <div className="date-grid">
            {WEEKDAYS.map((w) => (
              <div key={w} className="date-wd">
                {w}
              </div>
            ))}
            {cells.map((cell, idx) => {
              const cellIso = iso(cell.year, cell.month0, cell.day)
              const blocked = min ? cellIso < min : false
              const cls = [
                'date-cell',
                cell.inMonth ? '' : 'off',
                cellIso === value ? 'sel' : '',
                cellIso === today ? 'today' : '',
                holidaySet?.has(cellIso) ? 'hol' : '',
                blocked ? 'blocked' : '',
              ]
                .filter(Boolean)
                .join(' ')
              return (
                <button
                  key={idx}
                  type="button"
                  className={cls}
                  disabled={blocked}
                  onClick={() => {
                    onChange(cellIso)
                    setOpen(false)
                  }}
                >
                  {cell.day}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
