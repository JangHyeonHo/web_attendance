import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { languageApi, systemTenantApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type {
  BillingMethod,
  TenantBillingResponse,
  TenantProfileResponse,
} from '../api/types'

/** 빈 문자열 → null (선택 필드 전송 규약) */
function orNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function fieldErrorMap(e: ApiError): Record<string, string> {
  const byField: Record<string, string> = {}
  for (const fe of e.fieldErrors ?? []) {
    byField[fe.field] = fe.message
  }
  return byField
}

/**
 * W008 테넌트 상세(기업 정보/결제 정보) — SYSTEM_ADMIN 전용, W007에 임베드 전개.
 * W006과 동일하게 자기 화면 텍스트(W008)를 언어 마스터에서 직접 취득한다.
 *
 * - 조회 뷰: 서버가 내려준 마스킹 문자열을 그대로 표시(가공·복원 시도 금지).
 *   빌링키는 값이 어떤 API로도 오지 않으며 hasBillingKey로 존재 여부만 표시.
 * - 재입력 폼: 모든 필드가 빈 값에서 시작(마스킹 값을 초기값/placeholder로도 넣지 않는다).
 *   저장은 전체 필드 재입력 후 upsert(PUT) 1회.
 */
export function TenantDetailScreen({ tenantId }: { tenantId: number }) {
  const { t: commonT, lang } = useApp()
  const [texts, setTexts] = useState<Record<string, string>>({})

  //W008 화면 텍스트 취득(언어 변경시 재취득)
  useEffect(() => {
    let cancelled = false
    languageApi
      .texts('W008', lang)
      .then((response) => {
        if (!cancelled) setTexts(response)
      })
      .catch(() => {
        //텍스트 취득 실패시 키 이름이 그대로 표시된다(치명적이지 않음)
      })
    return () => {
      cancelled = true
    }
  }, [lang])

  const t = (key: string) => texts[key] ?? commonT(key)

  // ---- 기업 정보 ----
  const [profile, setProfile] = useState<TenantProfileResponse | null>(null)
  const [profileLoaded, setProfileLoaded] = useState(false)
  const [profileEdit, setProfileEdit] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [profileFieldErrors, setProfileFieldErrors] = useState<Record<string, string>>({})
  const [businessRegNo, setBusinessRegNo] = useState('')
  const [ceoName, setCeoName] = useState('')
  const [address, setAddress] = useState('')
  const [contactName, setContactName] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [contactPhone, setContactPhone] = useState('')

  // ---- 결제 정보 ----
  const [billing, setBilling] = useState<TenantBillingResponse | null>(null)
  const [billingLoaded, setBillingLoaded] = useState(false)
  const [billingEdit, setBillingEdit] = useState(false)
  const [billingError, setBillingError] = useState<string | null>(null)
  const [billingFieldErrors, setBillingFieldErrors] = useState<Record<string, string>>({})
  const [billingMethod, setBillingMethod] = useState<BillingMethod>('INVOICE')
  const [billingEmail, setBillingEmail] = useState('')
  const [pgCustomerKey, setPgCustomerKey] = useState('')
  const [cardLast4, setCardLast4] = useState('')
  const [cardBrand, setCardBrand] = useState('')
  const [plan, setPlan] = useState('')
  const [billedFrom, setBilledFrom] = useState('')
  const [memo, setMemo] = useState('')

  const load = useCallback(async () => {
    //미등록은 404로 내려온다 → null(NOT_REGISTERED 표시)로 흡수
    try {
      setProfile(await systemTenantApi.profile(tenantId))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setProfile(null)
      } else {
        setProfileError(e instanceof ApiError ? e.message : String(e))
      }
    } finally {
      setProfileLoaded(true)
    }
    try {
      setBilling(await systemTenantApi.billing(tenantId))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setBilling(null)
      } else {
        setBillingError(e instanceof ApiError ? e.message : String(e))
      }
    } finally {
      setBillingLoaded(true)
    }
  }, [tenantId])

  useEffect(() => {
    void load()
  }, [load])

  function openProfileEdit() {
    //전체 재입력: 항상 빈 값에서 시작(마스킹 문자열 재제출 사고 방지)
    setBusinessRegNo('')
    setCeoName('')
    setAddress('')
    setContactName('')
    setContactEmail('')
    setContactPhone('')
    setProfileError(null)
    setProfileFieldErrors({})
    setProfileEdit(true)
  }

  async function submitProfile(event: FormEvent) {
    event.preventDefault()
    setProfileError(null)
    setProfileFieldErrors({})
    try {
      const saved = await systemTenantApi.upsertProfile(tenantId, {
        businessRegNo: businessRegNo.trim(),
        ceoName: orNull(ceoName),
        address: orNull(address),
        contactName: orNull(contactName),
        contactEmail: orNull(contactEmail),
        contactPhone: orNull(contactPhone),
      })
      setProfile(saved)
      setProfileEdit(false)
    } catch (e) {
      if (e instanceof ApiError) {
        setProfileError(e.message)
        setProfileFieldErrors(fieldErrorMap(e))
      } else {
        setProfileError(String(e))
      }
    }
  }

  function openBillingEdit() {
    setBillingMethod('INVOICE')
    setBillingEmail('')
    setPgCustomerKey('')
    setCardLast4('')
    setCardBrand('')
    setPlan('')
    setBilledFrom('')
    setMemo('')
    setBillingError(null)
    setBillingFieldErrors({})
    setBillingEdit(true)
  }

  async function submitBilling(event: FormEvent) {
    event.preventDefault()
    setBillingError(null)
    setBillingFieldErrors({})
    const card = billingMethod === 'CARD'
    try {
      const saved = await systemTenantApi.upsertBilling(tenantId, {
        billingMethod,
        billingEmail: orNull(billingEmail),
        //INVOICE 저장시 카드 필드는 서버에서 무시(계약) — 프론트도 값을 보내지 않는다
        pgCustomerKey: card ? orNull(pgCustomerKey) : null,
        cardLast4: card ? orNull(cardLast4) : null,
        cardBrand: card ? orNull(cardBrand) : null,
        plan: plan.trim(),
        billedFrom: orNull(billedFrom),
        memo: orNull(memo),
      })
      setBilling(saved)
      setBillingEdit(false)
    } catch (e) {
      if (e instanceof ApiError) {
        setBillingError(e.message)
        setBillingFieldErrors(fieldErrorMap(e))
      } else {
        setBillingError(String(e))
      }
    } finally {
      //빌링키는 제출 후 즉시 state 클리어(성공/실패 무관 — 화면 잔존 방지)
      setPgCustomerKey('')
    }
  }

  return (
    <div className="tenant-detail">
      {/* ---- 기업 정보 카드 ---- */}
      <div className="panel">
        <div className="card-head">
          <h3>{t('PROFILE_TITLE')}</h3>
          {profileLoaded && !profileEdit && (
            <button onClick={openProfileEdit}>{t('EDIT')}</button>
          )}
        </div>
        {!profileEdit && profileLoaded && !profile && (
          <p className="muted">{t('NOT_REGISTERED')}</p>
        )}
        {!profileEdit && profile && (
          <dl className="kv">
            <dt>{t('BIZ_REG_NO')}</dt>
            <dd>{profile.businessRegNoMasked}</dd>
            <dt>{t('CEO_NAME')}</dt>
            <dd>{profile.ceoName ?? '-'}</dd>
            <dt>{t('ADDRESS')}</dt>
            <dd>{profile.address ?? '-'}</dd>
            <dt>{t('CONTACT_NAME')}</dt>
            <dd>{profile.contactName ?? '-'}</dd>
            <dt>{t('CONTACT_EMAIL')}</dt>
            <dd>{profile.contactEmail ?? '-'}</dd>
            <dt>{t('CONTACT_PHONE')}</dt>
            <dd>{profile.contactPhoneMasked ?? '-'}</dd>
            <dt>{t('UPDATED_AT')}</dt>
            <dd>{profile.updatedAt.replace('T', ' ').slice(0, 19)}</dd>
          </dl>
        )}
        {profileEdit && (
          <form onSubmit={submitProfile}>
            <p className="muted reenter-note">{t('REENTER_NOTE')}</p>
            <label>
              {t('BIZ_REG_NO')}
              <input
                value={businessRegNo}
                onChange={(e) => setBusinessRegNo(e.target.value)}
                autoComplete="off"
                required
              />
              {profileFieldErrors.businessRegNo && (
                <span className="error">{profileFieldErrors.businessRegNo}</span>
              )}
            </label>
            <label>
              {t('CEO_NAME')}
              <input value={ceoName} onChange={(e) => setCeoName(e.target.value)} />
              {profileFieldErrors.ceoName && (
                <span className="error">{profileFieldErrors.ceoName}</span>
              )}
            </label>
            <label>
              {t('ADDRESS')}
              <input value={address} onChange={(e) => setAddress(e.target.value)} />
              {profileFieldErrors.address && (
                <span className="error">{profileFieldErrors.address}</span>
              )}
            </label>
            <label>
              {t('CONTACT_NAME')}
              <input value={contactName} onChange={(e) => setContactName(e.target.value)} />
              {profileFieldErrors.contactName && (
                <span className="error">{profileFieldErrors.contactName}</span>
              )}
            </label>
            <label>
              {t('CONTACT_EMAIL')}
              <input
                type="email"
                value={contactEmail}
                onChange={(e) => setContactEmail(e.target.value)}
              />
              {profileFieldErrors.contactEmail && (
                <span className="error">{profileFieldErrors.contactEmail}</span>
              )}
            </label>
            <label>
              {t('CONTACT_PHONE')}
              <input
                value={contactPhone}
                onChange={(e) => setContactPhone(e.target.value)}
                autoComplete="off"
              />
              {profileFieldErrors.contactPhone && (
                <span className="error">{profileFieldErrors.contactPhone}</span>
              )}
            </label>
            {profileError && <p className="error" role="alert">{profileError}</p>}
            <div className="btn-row">
              <button type="submit" className="primary">
                {t('SUBMIT')}
              </button>
              <button type="button" onClick={() => setProfileEdit(false)}>
                {t('CANCEL')}
              </button>
            </div>
          </form>
        )}
        {!profileEdit && profileError && <p className="error" role="alert">{profileError}</p>}
      </div>

      {/* ---- 결제 정보 카드 ---- */}
      <div className="panel">
        <div className="card-head">
          <h3>{t('BILLING_TITLE')}</h3>
          {billingLoaded && !billingEdit && (
            <button onClick={openBillingEdit}>{t('EDIT')}</button>
          )}
        </div>
        {!billingEdit && billingLoaded && !billing && (
          <p className="muted">{t('NOT_REGISTERED')}</p>
        )}
        {!billingEdit && billing && (
          <dl className="kv">
            <dt>{t('BILLING_METHOD')}</dt>
            <dd>{billing.billingMethod === 'CARD' ? t('METHOD_CARD') : t('METHOD_INVOICE')}</dd>
            <dt>{t('BILLING_EMAIL')}</dt>
            <dd>{billing.billingEmail ?? '-'}</dd>
            <dt>{t('BILLING_KEY')}</dt>
            <dd>{billing.hasBillingKey ? t('BILLING_KEY_SET') : t('BILLING_KEY_UNSET')}</dd>
            <dt>{t('CARD_LAST4')}</dt>
            <dd>{billing.cardMasked ?? '-'}</dd>
            <dt>{t('CARD_BRAND')}</dt>
            <dd>{billing.cardBrand ?? '-'}</dd>
            <dt>{t('PLAN')}</dt>
            <dd>{billing.plan}</dd>
            <dt>{t('BILLED_FROM')}</dt>
            <dd>{billing.billedFrom ?? '-'}</dd>
            <dt>{t('MEMO')}</dt>
            <dd>{billing.memo ?? '-'}</dd>
            <dt>{t('UPDATED_AT')}</dt>
            <dd>{billing.updatedAt.replace('T', ' ').slice(0, 19)}</dd>
          </dl>
        )}
        {billingEdit && (
          <form onSubmit={submitBilling}>
            <p className="muted reenter-note">{t('REENTER_NOTE')}</p>
            <label>
              {t('BILLING_METHOD')}
              <select
                value={billingMethod}
                onChange={(e) => setBillingMethod(e.target.value as BillingMethod)}
              >
                <option value="INVOICE">{t('METHOD_INVOICE')}</option>
                <option value="CARD">{t('METHOD_CARD')}</option>
              </select>
            </label>
            <label>
              {t('BILLING_EMAIL')}
              <input
                type="email"
                value={billingEmail}
                onChange={(e) => setBillingEmail(e.target.value)}
              />
              {billingFieldErrors.billingEmail && (
                <span className="error">{billingFieldErrors.billingEmail}</span>
              )}
            </label>
            {billingMethod === 'CARD' && (
              <>
                <label>
                  {/* Phase 2에는 PG 결제위젯이 없다 — 운영자가 PG 콘솔에서 발급한 빌링키 수동 입력 */}
                  {t('BILLING_KEY')}
                  <input
                    type="password"
                    value={pgCustomerKey}
                    onChange={(e) => setPgCustomerKey(e.target.value)}
                    autoComplete="off"
                  />
                  {billingFieldErrors.pgCustomerKey && (
                    <span className="error">{billingFieldErrors.pgCustomerKey}</span>
                  )}
                </label>
                <label>
                  {t('CARD_LAST4')}
                  <input
                    value={cardLast4}
                    onChange={(e) => setCardLast4(e.target.value)}
                    maxLength={4}
                    inputMode="numeric"
                    autoComplete="off"
                  />
                  {billingFieldErrors.cardLast4 && (
                    <span className="error">{billingFieldErrors.cardLast4}</span>
                  )}
                </label>
                <label>
                  {t('CARD_BRAND')}
                  <input value={cardBrand} onChange={(e) => setCardBrand(e.target.value)} />
                  {billingFieldErrors.cardBrand && (
                    <span className="error">{billingFieldErrors.cardBrand}</span>
                  )}
                </label>
              </>
            )}
            <label>
              {t('PLAN')}
              <input value={plan} onChange={(e) => setPlan(e.target.value)} required />
              {billingFieldErrors.plan && (
                <span className="error">{billingFieldErrors.plan}</span>
              )}
            </label>
            <label>
              {t('BILLED_FROM')}
              <input
                type="date"
                value={billedFrom}
                onChange={(e) => setBilledFrom(e.target.value)}
              />
              {billingFieldErrors.billedFrom && (
                <span className="error">{billingFieldErrors.billedFrom}</span>
              )}
            </label>
            <label>
              {t('MEMO')}
              <input value={memo} onChange={(e) => setMemo(e.target.value)} />
              {billingFieldErrors.memo && (
                <span className="error">{billingFieldErrors.memo}</span>
              )}
            </label>
            {billingError && <p className="error" role="alert">{billingError}</p>}
            <div className="btn-row">
              <button type="submit" className="primary">
                {t('SUBMIT')}
              </button>
              <button type="button" onClick={() => setBillingEdit(false)}>
                {t('CANCEL')}
              </button>
            </div>
          </form>
        )}
        {!billingEdit && billingError && <p className="error" role="alert">{billingError}</p>}
      </div>
    </div>
  )
}
