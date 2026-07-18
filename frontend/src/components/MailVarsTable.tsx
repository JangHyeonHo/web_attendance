import { useState } from 'react'

/** 목적별 변수 정의 — 토큰 + 설명 i18n 키. INVITE는 inviterName 포함, RESET은 4종. */
const VARS: Record<'INVITE' | 'RESET', { token: string; descKey: string }[]> = {
  INVITE: [
    { token: '{memberName}', descKey: 'VAR_MEMBER_NAME' },
    { token: '{tenantName}', descKey: 'VAR_TENANT_NAME' },
    { token: '{actionUrl}', descKey: 'VAR_ACTION_URL' },
    { token: '{expiresAt}', descKey: 'VAR_EXPIRES_AT' },
    { token: '{inviterName}', descKey: 'VAR_INVITER_NAME' },
  ],
  RESET: [
    { token: '{memberName}', descKey: 'VAR_MEMBER_NAME' },
    { token: '{tenantName}', descKey: 'VAR_TENANT_NAME' },
    { token: '{actionUrl}', descKey: 'VAR_ACTION_URL' },
    { token: '{expiresAt}', descKey: 'VAR_EXPIRES_AT' },
  ],
}

/**
 * 메일 템플릿 변수 안내표(#12) — 변수를 클릭하면 클립보드에 복사된다.
 * 검증 정본은 서버(허용 외 변수는 저장 400) — 이 표는 편의 안내다.
 */
export function MailVarsTable({
  purpose,
  t,
}: {
  purpose: 'INVITE' | 'RESET'
  t: (key: string) => string
}) {
  const [copied, setCopied] = useState<string | null>(null)

  async function copy(token: string) {
    try {
      await navigator.clipboard.writeText(token)
      setCopied(token)
      window.setTimeout(() => setCopied((cur) => (cur === token ? null : cur)), 1500)
    } catch {
      //클립보드 권한 거부 등은 조용히 무시(수동 입력 가능)
    }
  }

  return (
    <div className="mail-vars">
      <div className="mail-vars-head">
        <strong>{t('VARIABLES')}</strong>
        <span className="muted">{t('VAR_HINT')}</span>
      </div>
      {/* 사용자 친화 표현(#12): 뜻(친화적 이름)이 크게, 실제 넣을 코드는 작게.
          누르면 코드가 복사되고, "복사됨"은 절대배치 배지로 떠서 레이아웃을 밀지 않는다. */}
      <ul className="mail-vars-list">
        {VARS[purpose].map((v) => (
          <li key={v.token}>
            <button type="button" className="var-row" onClick={() => void copy(v.token)}>
              <span className="var-row-name">{t(v.descKey)}</span>
              <code className="var-row-token">{v.token}</code>
              <span className="var-row-copied" aria-hidden={copied !== v.token}>
                {copied === v.token ? t('COPIED') : ''}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
