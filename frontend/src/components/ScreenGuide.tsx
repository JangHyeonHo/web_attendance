import type { ReactNode } from 'react'

/**
 * 관리자 화면 상단 가이드(디자인 공통화) — '이 화면이 무엇을 하는 화면인지'를
 * 비개발자 눈높이로 안내하는 한 단락.
 * 규칙:
 * - 관리자(T/A) 화면의 제목(툴바) 바로 아래 1개만 둔다.
 * - 문구는 언어 마스터의 SCREEN_GUIDE 키(화면별)로 관리 — 실제 화면 기능과 일치해야
 *   하며 추측 문구 금지(동작 확인 후 작성).
 * - 스타일은 .screen-guide 전용(본문보다 작고 연하게, 읽기 폭 제한) —
 *   범용 .muted를 화면 설명에 그대로 쓰지 않는다.
 */
export function ScreenGuide({ children }: { children: ReactNode }) {
  return <p className="screen-guide">{children}</p>
}
