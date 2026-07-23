import type { ReactNode } from 'react'

/**
 * 섹션 제목 + 안내문 슬롯(디자인 공통화).
 * 규칙: 섹션 공통 안내는 제목 바로 아래 hint 한 줄로 — 행마다 반복 표기 금지(열 폭·가로 스크롤 원인).
 * 두 번째 이후 섹션의 위 간격은 inline style이 아니라 spaced로(값 통일).
 * 화면에서 h3.section-head + p.hint를 손으로 조합하지 말고 이 컴포넌트를 쓴다.
 */
export function SectionHead({
  title,
  hint,
  spaced = false,
}: {
  title: ReactNode
  hint?: ReactNode
  /** 앞 섹션과의 표준 간격(연속 섹션의 2번째부터 true) */
  spaced?: boolean
}) {
  return (
    <>
      <h3 className={`section-head${spaced ? ' spaced' : ''}`}>{title}</h3>
      {hint != null && <p className="hint">{hint}</p>}
    </>
  )
}
