import { useState } from 'react'
import { useApp } from '../app/AppContext'

/**
 * 비밀번호 입력 + 표시/숨김 눈 아이콘 토글.
 * 라벨은 호출측 <label>이 감싸므로 여기서는 입력 래퍼만 그린다(form label 스타일 상속).
 */
export function PasswordInput({
  value,
  onChange,
  autoComplete = 'current-password',
  required,
  autoFocus,
}: {
  value: string
  onChange: (v: string) => void
  autoComplete?: string
  required?: boolean
  autoFocus?: boolean
}) {
  const { t } = useApp()
  const [show, setShow] = useState(false)
  const toggleLabel = t(show ? 'PWD_HIDE' : 'PWD_SHOW')
  return (
    <span className="pw-field">
      <input
        type={show ? 'text' : 'password'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={required}
        autoFocus={autoFocus}
      />
      <button
        type="button"
        className="pw-toggle"
        onClick={() => setShow((v) => !v)}
        aria-label={toggleLabel}
        title={toggleLabel}
        aria-pressed={show}
        tabIndex={-1}
      >
        {show ? (
          //표시 중 → 숨김 아이콘(눈 + 사선)
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M17.94 17.94A10.5 10.5 0 0 1 12 20C5 20 1 12 1 12a19.6 19.6 0 0 1 5.06-5.94M9.9 4.24A9.6 9.6 0 0 1 12 4c7 0 11 8 11 8a19.5 19.5 0 0 1-2.16 3.19M1 1l22 22" />
            <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
          </svg>
        ) : (
          //숨김 중 → 표시(눈) 아이콘
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </span>
  )
}
