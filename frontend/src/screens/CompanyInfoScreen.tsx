import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantBillingApi, tenantProfileApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import type {
  BillingMethod,
  ContractSummaryResponse,
  TenantProfileResponse,
} from '../api/types'

function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

/**
 * W019 회사 정보/결제 설정 — 회사 총관리자(TENANT_ADMIN) 전용(#14).
 * 운영사가 '계약(요금제·단가·무료좌석)'을 관리한다면, 회사는 자기 '사업자 정보'와 '결제 수단'을 스스로 관리한다.
 * 3섹션: ① 사업자 정보(수정) ② 결제 설정(수정) ③ 계약 요약(읽기전용 — 운영사가 정한 값).
 */
export function CompanyInfoScreen() {
  const { t } = useApp()
  return (
    <div className="panel">
      <h2>{t('COMPANY_INFO_TITLE')}</h2>
      <p className="muted">{t('COMPANY_INFO_NOTE')}</p>
      <BusinessProfileSection />
      <PaymentSection />
      <ContractSection />
    </div>
  )
}

/** ① 사업자 정보 — 회사가 직접 등록/수정(사업자번호·대표자·주소·담당자). */
function BusinessProfileSection() {
  const { t } = useApp()
  const [profile, setProfile] = useState<TenantProfileResponse | null>(null)
  const [businessRegNo, setBusinessRegNo] = useState('')
  const [ceoName, setCeoName] = useState('')
  const [address, setAddress] = useState('')
  const [contactName, setContactName] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    try {
      const p = await tenantProfileApi.get()
      setProfile(p)
      //마스킹 안 되는 필드는 채워두고, 사업자번호·연락처는 재입력 요구(마스킹값 재제출 차단)
      setCeoName(p.ceoName ?? '')
      setAddress(p.address ?? '')
      setContactName(p.contactName ?? '')
      setContactEmail(p.contactEmail ?? '')
    } catch (e) {
      //미등록(404)이면 빈 폼 — 최초 등록 경로
      if (!(e instanceof ApiError && e.status === 404)) {
        setError(e instanceof ApiError ? e.message : String(e))
      }
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  //소재국에 따라 사업자 식별번호 라벨/자리표시(KR 사업자등록번호 / JP 法人番号)
  const bizLabel = profile?.country === 'JP' ? t('BIZ_REG_NO_JP') : t('BIZ_REG_NO_KR')

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    setFieldErrors({})
    try {
      const updated = await tenantProfileApi.update({
        businessRegNo: businessRegNo.trim(),
        ceoName: ceoName.trim() || null,
        address: address.trim() || null,
        contactName: contactName.trim() || null,
        contactEmail: contactEmail.trim() || null,
        contactPhone: contactPhone.trim() || null,
      })
      setProfile(updated)
      setBusinessRegNo('')
      setContactPhone('')
      setSaved(true)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        if (e.fieldErrors) {
          const byField: Record<string, string> = {}
          for (const fe of e.fieldErrors) byField[fe.field] = fe.message
          setFieldErrors(byField)
        }
      } else {
        setError(String(e))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="ci-section">
      <h3 className="section-head">{t('BIZ_INFO')}</h3>
      {profile && (
        <dl className="kv ci-current">
          <dt>{bizLabel}</dt>
          <dd>{profile.businessRegNoMasked || '—'}</dd>
          <dt>{t('CONTACT_PHONE')}</dt>
          <dd>{profile.contactPhoneMasked || '—'}</dd>
        </dl>
      )}
      <form onSubmit={save}>
        <label>
          {bizLabel}
          <input
            value={businessRegNo}
            onChange={(e) => { setBusinessRegNo(e.target.value); setSaved(false) }}
            placeholder={profile?.businessRegNoMasked || (profile?.country === 'JP' ? '1234567890123' : '123-45-67890')}
            maxLength={20}
            required
          />
          {fieldErrors.businessRegNo && <span className="error">{fieldErrors.businessRegNo}</span>}
        </label>
        <div className="field-row">
          <label>
            {t('CEO_NAME')}
            <input value={ceoName} onChange={(e) => { setCeoName(e.target.value); setSaved(false) }} maxLength={50} />
          </label>
          <label>
            {t('CONTACT_NAME')}
            <input value={contactName} onChange={(e) => { setContactName(e.target.value); setSaved(false) }} maxLength={50} />
          </label>
        </div>
        <label>
          {t('ADDRESS')}
          <input value={address} onChange={(e) => { setAddress(e.target.value); setSaved(false) }} maxLength={200} />
        </label>
        <div className="field-row">
          <label>
            {t('CONTACT_EMAIL')}
            <input type="email" value={contactEmail} onChange={(e) => { setContactEmail(e.target.value); setSaved(false) }} maxLength={100} />
            {fieldErrors.contactEmail && <span className="error">{fieldErrors.contactEmail}</span>}
          </label>
          <label>
            {t('CONTACT_PHONE')}
            <input
              value={contactPhone}
              onChange={(e) => { setContactPhone(e.target.value); setSaved(false) }}
              placeholder={profile?.contactPhoneMasked || '010-1234-5678'}
              maxLength={20}
            />
            {fieldErrors.contactPhone && <span className="error">{fieldErrors.contactPhone}</span>}
          </label>
        </div>
        <p className="hint">{t('BIZ_REENTER_HINT')}</p>
        {error && <p className="error" role="alert">{error}</p>}
        {saved && <p className="success" role="status">{t('SAVED')}</p>}
        <button type="submit" className="primary" disabled={busy}>{t('SUBMIT')}</button>
      </form>
    </section>
  )
}

/** ② 결제 설정 — 결제 수단·청구 이메일·비고(회사 자체 관리). */
function PaymentSection() {
  const { t } = useApp()
  const [method, setMethod] = useState<BillingMethod>('INVOICE')
  const [email, setEmail] = useState('')
  const [memo, setMemo] = useState('')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    tenantBillingApi
      .profile()
      .then((p) => { setMethod(p.billingMethod); setEmail(p.billingEmail ?? ''); setMemo(p.memo ?? '') })
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true); setError(null); setSaved(false)
    try {
      await tenantBillingApi.updateProfile({
        billingMethod: method,
        billingEmail: email.trim() || null,
        memo: memo.trim() || null,
      })
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="ci-section">
      <h3 className="section-head">{t('PAYMENT_SETTINGS')}</h3>
      <form onSubmit={save}>
        <div className="field-row">
          <label>
            {t('BILL_METHOD')}
            <SelectField
              value={method}
              ariaLabel={t('BILL_METHOD')}
              options={[
                { value: 'INVOICE', label: t('BILL_METHOD_INVOICE') },
                { value: 'CARD', label: t('BILL_METHOD_CARD') },
              ]}
              onChange={(v) => { setMethod(v as BillingMethod); setSaved(false) }}
            />
          </label>
          <label>
            {t('BILL_EMAIL')}
            <input type="email" value={email} onChange={(e) => { setEmail(e.target.value); setSaved(false) }} placeholder="billing@company.com" maxLength={100} />
          </label>
        </div>
        <label>
          {t('BILL_MEMO')}
          <input value={memo} onChange={(e) => { setMemo(e.target.value); setSaved(false) }} maxLength={500} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        {saved && <p className="success" role="status">{t('SAVED')}</p>}
        <button type="submit" className="primary" disabled={busy}>{t('SUBMIT')}</button>
      </form>
    </section>
  )
}

/** ③ 계약 요약 — 읽기전용(운영사가 정한 요금제·단가·무료 좌석). */
function ContractSection() {
  const { t } = useApp()
  const [contract, setContract] = useState<ContractSummaryResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    tenantBillingApi
      .contract()
      .then(setContract)
      .catch((e) => setError(e instanceof ApiError ? e.message : String(e)))
  }, [])

  return (
    <section className="ci-section">
      <h3 className="section-head">{t('CONTRACT_SUMMARY')}</h3>
      <p className="hint">{t('CONTRACT_READONLY_HINT')}</p>
      {error && <p className="error" role="alert">{error}</p>}
      {contract && (
        <dl className="kv">
          <dt>{t('PLAN')}</dt>
          <dd>{contract.plan}</dd>
          <dt>{t('SEAT_PRICE')}</dt>
          <dd className="tnum">{won(contract.perSeatAmount)}</dd>
          <dt>{t('FREE_SEATS')}</dt>
          <dd>{contract.freeSeats}</dd>
        </dl>
      )}
    </section>
  )
}
