import type { SyntheticEvent } from 'react'

/** 태그가 하나라도 있으면 HTML로 간주(평문 템플릿과 구분 — 실제 발송 규칙과 동일). */
function looksHtml(s: string): boolean {
  return /<[a-z][^>]*>/i.test(s)
}

/**
 * 메일 템플릿 미리보기 — HTML은 <iframe srcDoc>으로 '격리' 렌더한다.
 * 앱 전역 CSS가 메일에 침범하거나(색·폰트·margin) 메일의 스타일이 앱으로 새는 것을 막아
 * 실제 수신함 화면에 가깝게 보여준다. 평문은 pre-wrap로 줄바꿈만 보존한다.
 * sandbox는 allow-same-origin만 부여(스크립트 실행 차단) — 높이 자동 측정만 허용한다.
 */
export function MailPreview({ body, title }: { body: string; title: string }) {
  if (!looksHtml(body)) {
    return <div className="tpl-preview-body tpl-preview-text">{body}</div>
  }
  const fitHeight = (e: SyntheticEvent<HTMLIFrameElement>) => {
    const frame = e.currentTarget
    try {
      const doc = frame.contentDocument
      if (doc) frame.style.height = `${doc.documentElement.scrollHeight + 8}px`
    } catch {
      //접근 불가(안전장치) — 기본 높이 유지
    }
  }
  return (
    <iframe
      title={title}
      className="tpl-preview-frame"
      sandbox="allow-same-origin"
      srcDoc={body}
      onLoad={fitHeight}
    />
  )
}
