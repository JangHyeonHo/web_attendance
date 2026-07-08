import type { Lang } from '../api/types'

/**
 * 시스템 언어 코드 → BCP 47 로케일 (시계/요일 등 Intl 표기용).
 * 화면 텍스트 자체는 서버 언어 마스터(DB 시드 V3)에서 내려오며,
 * 프론트는 텍스트 사전을 갖지 않는다.
 */
export function localeOf(lang: Lang): string {
  switch (lang) {
    case 'ENG':
      return 'en-US'
    case 'JPN':
      return 'ja-JP'
    default:
      return 'ko-KR'
  }
}

/**
 * 텍스트 해석기 생성: 서버 언어 마스터(texts) 조회, 미등록 키는 키 이름 그대로 표시
 * (누락된 번역이 화면에서 바로 드러나도록).
 */
export function makeT(serverTexts: Record<string, string>): (key: string) => string {
  return (key: string) => serverTexts[key] ?? key
}
