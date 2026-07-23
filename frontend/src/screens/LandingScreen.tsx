import { useApp } from '../app/AppContext'

/**
 * W000 랜딩(비로그인 홈) — 기존 인덱스 화면 개편.
 * 텍스트는 navigation 응답의 W000 texts(LANDING_* 키, DB 언어 마스터 단일 출처)로 동봉되며
 * 자체 API 호출은 없다. 섹션 구성·카피의 정본은 docs/plan/landing-page.md §1~§2.
 */
export function LandingScreen() {
  const { t, userName, navigate } = useApp()

  const contactEmail = t('CONTACT_EMAIL')
  //문의 메일 주소는 언어 마스터 키(CONTACT_EMAIL)로 관리 — 하드코딩 금지
  const mailtoHref = `mailto:${contactEmail}?subject=${encodeURIComponent(
    `[Web Attendance] ${t('LANDING_CTA_CONTACT')}`,
  )}`

  //방어 분기: 로그인 상태로 랜딩이 렌더되면 주 CTA를 출결 화면 이동으로 대체(헤더 ATTEND 키 재사용)
  const primaryCta = userName ? (
    <button className="cta" onClick={() => void navigate('M001')}>
      {t('ATTEND')}
    </button>
  ) : (
    <a className="cta" href={mailtoHref}>
      {t('LANDING_CTA_CONTACT')}
    </a>
  )

  const features = [1, 2, 3, 4].map((n) => ({
    num: String(n).padStart(2, '0'), //아이콘 대신 번호(01~04) — 장식 없는 위계
    title: t(`LANDING_FEAT${n}_TITLE`),
    desc: t(`LANDING_FEAT${n}_DESC`),
  }))

  const trusts = [1, 2, 3].map((n) => ({
    title: t(`LANDING_TRUST${n}_TITLE`),
    desc: t(`LANDING_TRUST${n}_DESC`),
  }))

  const steps = [1, 2, 3].map((n) => ({
    no: n,
    //카피에 "1. " 접두어가 있으면 제거 — 원형 번호와 중복 표기 방지
    title: t(`LANDING_STEP${n}_TITLE`).replace(/^\d+\.\s*/, ''),
    desc: t(`LANDING_STEP${n}_DESC`),
  }))

  return (
    <div className="landing">
      <section className="hero">
        <span className="hero-eyebrow">{t('LANDING_HERO_BADGE')}</span>
        <h1>{t('LANDING_HERO_TITLE')}</h1>
        <p className="hero-sub">{t('LANDING_HERO_SUB')}</p>
        <div className="hero-actions">
          {primaryCta}
          {!userName && (
            <button className="cta-secondary" onClick={() => void navigate('W001')}>
              {t('LANDING_CTA_LOGIN')}
            </button>
          )}
        </div>
      </section>

      <section className="features-section">
        <h2>{t('LANDING_FEATURES_TITLE')}</h2>
        <div className="features">
          {features.map((feature) => (
            <div className="feature-card" key={feature.title}>
              <span className="feature-num" aria-hidden="true">
                {feature.num}
              </span>
              <h3>{feature.title}</h3>
              <p>{feature.desc}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="trust">
        <h2>{t('LANDING_TRUST_TITLE')}</h2>
        <div className="trust-cards">
          {trusts.map((trust) => (
            <div className="trust-card" key={trust.title}>
              <h3>{trust.title}</h3>
              <p>{trust.desc}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="steps">
        <h2>{t('LANDING_STEPS_TITLE')}</h2>
        <div className="step-cards">
          {steps.map((step) => (
            <div className="step-card" key={step.no}>
              <span className="step-num" aria-hidden="true">
                {step.no}
              </span>
              <h3>{step.title}</h3>
              <p>{step.desc}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="cta-band">
        <h2>{t('LANDING_CTA_TITLE')}</h2>
        <p>{t('LANDING_CTA_SUB')}</p>
        <div className="hero-actions">
          <a className="cta" href={mailtoHref}>
            {t('LANDING_CTA_CONTACT')}
          </a>
          {!userName && (
            <button className="cta-secondary" onClick={() => void navigate('W001')}>
              {t('LANDING_CTA_LOGIN')}
            </button>
          )}
        </div>
      </section>

      <footer className="landing-footer">
        <p>{t('LANDING_FOOTER_PRODUCT')}</p>
        <p>
          {/* {CONTACT_EMAIL} 플레이스홀더는 언어 마스터 CONTACT_EMAIL 키 값으로 렌더 시 치환 */}
          {t('LANDING_FOOTER_CONTACT').replace('{CONTACT_EMAIL}', contactEmail)}
        </p>
        <p className="muted">{t('LANDING_FOOTER_COPYRIGHT')}</p>
      </footer>
    </div>
  )
}
