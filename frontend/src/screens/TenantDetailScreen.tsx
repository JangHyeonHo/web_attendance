import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { languageApi, systemTenantApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type {
  BillingMethod,
  InvoiceEntry,
  ProfileCountry,
  TenantBillingResponse,
  TenantProfileResponse,
} from '../api/types'

/** 금액(원) 표시 — 천단위 구분 + '원'. */
function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/** 소재국별 사업자 식별번호 라벨 키(KR=사업자등록번호, JP=法人番号) */
const BIZ_REG_NO_LABEL_KEYS: Record<ProfileCountry, string> = {
  KR: 'BIZ_REG_NO_KR',
  JP: 'BIZ_REG_NO_JP',
}

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
export function TenantDetailScreen({ tenantId, country }: { tenantId: number; country: ProfileCountry }) {
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
  const [perSeatAmount, setPerSeatAmount] = useState('')
  const [freeSeats, setFreeSeats] = useState('')
  const [billedFrom, setBilledFrom] = useState('')
  const [memo, setMemo] = useState('')

  // ---- 청구서(월별) — 운영사 조회·마감 ----
  const [invoices, setInvoices] = useState<InvoiceEntry[]>([])
  const [invoiceError, setInvoiceError] = useState<string | null>(null)

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
    try {
      setInvoices(await systemTenantApi.invoices(tenantId))
      setInvoiceError(null)
    } catch (e) {
      setInvoiceError(e instanceof ApiError ? e.message : String(e))
    }
  }, [tenantId])

  async function closeMonth(ym: string) {
    if (!window.confirm(t('CLOSE_CONFIRM'))) {
      return
    }
    try {
      await systemTenantApi.closeInvoice(tenantId, ym)
      setInvoices(await systemTenantApi.invoices(tenantId))
      setInvoiceError(null)
    } catch (e) {
      setInvoiceError(e instanceof ApiError ? e.message : String(e))
    }
  }

  useEffect(() => {
    void load()
  }, [load])

  function openProfileEdit() {
    //전체 재입력: 항상 빈 값에서 시작(마스킹 문자열 재제출 사고 방지)
    //소재국은 tenant.country 승격으로 요청에서 제거 — 서버가 tenant에서 취득(holiday-plan §4-2)
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

  function cancelProfileEdit() {
    //취소 경로에서도 암호화 대상 입력값을 state에 남기지 않는다(제출 경로와 동일 정책)
    setBusinessRegNo('')
    setContactPhone('')
    setProfileEdit(false)
  }

  function cancelBillingEdit() {
    //빌링키 평문은 취소 즉시 폐기
    setPgCustomerKey('')
    setBillingEdit(false)
  }

  function openBillingEdit() {
    setBillingMethod('INVOICE')
    setBillingEmail('')
    setPgCustomerKey('')
    setCardLast4('')
    setCardBrand('')
    setPlan('')
    //인당 단가·무료 인원은 마스킹 대상이 아니므로 현재값을 채워 수정 편의(없으면 서버 기본 2000/5)
    setPerSeatAmount(String(billing?.perSeatAmount ?? 2000))
    setFreeSeats(String(billing?.freeSeats ?? 5))
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
        perSeatAmount: perSeatAmount.trim() === '' ? null : Number(perSeatAmount),
        freeSeats: freeSeats.trim() === '' ? null : Number(freeSeats),
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
            <dt>{t('COUNTRY')}</dt>
            <dd>{t(`COUNTRY_${profile.country}`)}</dd>
            <dt>{t(BIZ_REG_NO_LABEL_KEYS[profile.country])}</dt>
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
            {/* 소재국은 표시 전용(tenant.country) — 입력·변경 불가(holiday-plan §4-3) */}
            <p className="muted">
              {t('COUNTRY')}: {t(`COUNTRY_${country}`)}
            </p>
            <label>
              {/* 식별번호 라벨은 테넌트 소재국(prop — 프로필 미등록이어도 정확)을 따라간다 */}
              {t(BIZ_REG_NO_LABEL_KEYS[country])}
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
              <button type="button" onClick={cancelProfileEdit}>
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
            <dt>{t('PER_SEAT')}</dt>
            <dd>{won(billing.perSeatAmount)} <span className="muted">({t('PER_SEAT_HINT')})</span></dd>
            <dt>{t('FREE_SEATS')}</dt>
            <dd>{billing.freeSeats}</dd>
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
              {t('PER_SEAT')} <span className="muted">({t('PER_SEAT_HINT')})</span>
              <input
                type="number"
                min={0}
                value={perSeatAmount}
                onChange={(e) => setPerSeatAmount(e.target.value)}
              />
              {billingFieldErrors.perSeatAmount && (
                <span className="error">{billingFieldErrors.perSeatAmount}</span>
              )}
            </label>
            <label>
              {t('FREE_SEATS')}
              <input
                type="number"
                min={0}
                value={freeSeats}
                onChange={(e) => setFreeSeats(e.target.value)}
              />
              {billingFieldErrors.freeSeats && (
                <span className="error">{billingFieldErrors.freeSeats}</span>
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
              <button type="button" onClick={cancelBillingEdit}>
                {t('CANCEL')}
              </button>
            </div>
          </form>
        )}
        {!billingEdit && billingError && <p className="error" role="alert">{billingError}</p>}
      </div>

      {/* ---- 청구서(월별) — 진행 중=잠정, 마감하면 확정 스냅샷 ---- */}
      <div className="panel">
        <div className="card-head">
          <h3>{t('INVOICES')}</h3>
        </div>
        {invoiceError && <p className="error" role="alert">{invoiceError}</p>}
        {invoices.length === 0 && !invoiceError ? (
          <p className="muted">{t('EMPTY')}</p>
        ) : (
          <div className="table-wrap">
            <table className="detail-table">
              <thead>
                <tr>
                  <th>{t('BILL_MONTH')}</th>
                  <th>{t('BILL_SEATS_MAX')}</th>
                  <th>{t('BILL_SEATS_BILLED')}</th>
                  <th>{t('BILL_UNIT')}</th>
                  <th>{t('BILL_SUBTOTAL')}</th>
                  <th>{t('BILL_VAT')}</th>
                  <th>{t('BILL_TOTAL')}</th>
                  <th>{t('BILL_STATUS')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((r) => (
                  <tr key={r.ym}>
                    <td className="nowrap">{r.ym}</td>
                    <td>{r.maxSeats}</td>
                    <td>{r.billedSeats}</td>
                    <td className="nowrap">{won(r.unitPrice)}</td>
                    <td className="nowrap">{won(r.subtotal)}</td>
                    <td className="nowrap">{won(r.vat)}</td>
                    <td className="nowrap"><strong>{won(r.total)}</strong></td>
                    <td>
                      <span className={`badge ${r.status === 'ISSUED' ? 'ok' : 'muted'}`}>
                        {r.status === 'ISSUED' ? t('BILL_ISSUED') : t('BILL_PROVISIONAL')}
                      </span>
                    </td>
                    <td>
                      {r.status === 'PROVISIONAL' && (
                        <button onClick={() => void closeMonth(r.ym)}>{t('CLOSE')}</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
