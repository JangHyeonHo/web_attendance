import type { LeaveUnit } from '../api/types'

export interface LeaveUnitLabels {
  day: string
  hour: string
  min: string
}

/**
 * 휴가 수량(분) → '일 + 시간 + 분' 정확 표기(반올림 없음, 0인 단위는 생략).
 * DAY 종류는 dayMinutes로 일을 먼저 떼고 나머지를 시간·분으로, HOUR 종류는 시간·분만.
 * 예: 7590/480 → "15일 6시간 30분", 240/480 → "4시간", 0 → "0일". 음수(수동 조정)는 부호 유지.
 */
export function formatLeaveAmount(
  minutes: number,
  unit: LeaveUnit,
  dayMinutes: number,
  labels: LeaveUnitLabels,
): string {
  const negative = minutes < 0
  let remaining = Math.abs(minutes)
  const parts: string[] = []
  const dayBased = unit === 'DAY' && dayMinutes > 0

  if (dayBased) {
    const days = Math.floor(remaining / dayMinutes)
    if (days) parts.push(`${days}${labels.day}`)
    remaining -= days * dayMinutes
  }
  const hours = Math.floor(remaining / 60)
  const mins = remaining % 60
  if (hours) parts.push(`${hours}${labels.hour}`)
  if (mins) parts.push(`${mins}${labels.min}`)

  if (parts.length === 0) {
    parts.push(dayBased ? `0${labels.day}` : `0${labels.hour}`)
  }
  return (negative ? '-' : '') + parts.join(' ')
}
