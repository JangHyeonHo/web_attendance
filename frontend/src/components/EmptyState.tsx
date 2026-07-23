import type { ReactNode } from 'react'

/**
 * 목록/조회 결과가 비었을 때의 공통 표시(디자인 공통화).
 * 규칙: 빈 목록 문구는 화면마다 p 태그를 손으로 만들지 말고 이 컴포넌트를 쓴다.
 * 표시할 내용이 아예 없는 컨테이너는 이 컴포넌트가 아니라 렌더링 자체를 생략한다(빈 껍데기 금지).
 */
export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="muted center">{children}</p>
}
