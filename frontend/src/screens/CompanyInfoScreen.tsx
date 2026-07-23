import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { tenantBillingApi, tenantProfileApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import { SelectField } from '../components/fields'
import { SectionHead } from '../components/SectionHead'
import type {
  BillingMethod,
  ContractSummaryResponse,
  TenantProfileResponse,
} from '../api/types'

function won(n: number): string {
  return `${n.toLocaleString('ko-KR')}원`
}

//입력 자동 포맷(숫자만 입력해도 하이픈 자동) — 국가별 형식. 저장값도 백엔드 검증 형식에 맞는다.
const onlyDigits = (s: string) => s.replace(/\D/g, '')

/** 사업자 식별번호 — KR ###-##-#####(하이픈), JP 13자리(하이픈 없음). */
function fmtBizRegNo(raw: string, jp: boolean): string {
  const d = onlyDigits(raw).slice(0, jp ? 13 : 10)
  if (jp) return d
  if (d.length <= 3) return d
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`
}

/** 우편번호 — KR 5자리, JP ###-####(7자리). */
function fmtPostal(raw: string, jp: boolean): string {
  const d = onlyDigits(raw).slice(0, jp ? 7 : 5)
  return jp && d.length > 3 ? `${d.slice(0, 3)}-${d.slice(3)}` : d
}

/** 연락처 — KR 휴대폰/서울번호 best-effort 하이픈. JP는 숫자만(형식이 다양해 자동 그룹화 안 함). */
function fmtPhone(raw: string, jp: boolean): string {
  if (jp) return onlyDigits(raw).slice(0, 15)
  const d = onlyDigits(raw).slice(0, 11)
  if (d.startsWith('02')) {
    if (d.length <= 2) return d
    if (d.length <= 5) return `${d.slice(0, 2)}-${d.slice(2)}`
    if (d.length <= 9) return `${d.slice(0, 2)}-${d.slice(2, 5)}-${d.slice(5)}`
    return `${d.slice(0, 2)}-${d.slice(2, 6)}-${d.slice(6)}`
  }
  if (d.length <= 3) return d
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`
}

/**
 * W019 회사 정보/결제 설정 — 회사 총관리자(TENANT_ADMIN) 전용(#14).
 * 운영사가 '계약(요금제·단가·무료좌석)'을 관리한다면, 회사는 자기 '사업자 정보'와 '결제 수단'을 스스로 관리한다.
 * 3섹션: ① 사업자 정보(수정) ② 결제 설정(수정) ③ 계약 요약(읽기전용 — 운영사가 정한 값).
 * 운영 설정(근태 보고서 등)은 회사 설정(W020, 인사관리자도 접근)으로 분리했다.
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
  const [postalCode, setPostalCode] = useState('')
  const [address, setAddress] = useState('')
  const [addressDetail, setAddressDetail] = useState('')
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
      if (!p) return //미등록(200 빈 응답)이면 빈 폼 — 최초 등록 경로
      setProfile(p)
      //마스킹 안 되는 필드는 채워두고, 사업자번호·연락처는 재입력 요구(마스킹값 재제출 차단)
      setCeoName(p.ceoName ?? '')
      setPostalCode(p.postalCode ?? '')
      setAddress(p.address ?? '')
      setAddressDetail(p.addressDetail ?? '')
      setContactName(p.contactName ?? '')
      setContactEmail(p.contactEmail ?? '')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
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
        postalCode: postalCode.trim() || null,
        address: address.trim() || null,
        addressDetail: addressDetail.trim() || null,
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
      <SectionHead title={t('BIZ_INFO')} />
      {/* 현재 등록값은 읽기전용 박스로만 보여준다(캡션 명시). 입력칸엔 마스킹값을 넣지 않아 '빈 칸처럼' 보이지 않게(S1-2·S1-4) */}
      {profile && (
        <div className="info-box">
          <p className="info-box-cap">{t('CURRENT_VALUE')}</p>
          <dl className="kv">
            <dt>{bizLabel}</dt>
            <dd>{profile.businessRegNoMasked || '—'}</dd>
            <dt>{t('CONTACT_PHONE')}</dt>
            <dd>{profile.contactPhoneMasked || '—'}</dd>
          </dl>
        </div>
      )}
      <form onSubmit={save}>
        {/* 짧은 필드는 2열로 묶어 가장자리가 들쭉날쭉하지 않게(S1-5) */}
        <div className="field-row">
          <label>
            {bizLabel}
            <input
              value={businessRegNo}
              onChange={(e) => { setBusinessRegNo(fmtBizRegNo(e.target.value, profile?.country === 'JP')); setSaved(false) }}
              placeholder={profile?.country === 'JP' ? '1234567890123' : '123-45-67890'}
              inputMode="numeric"
              maxLength={20}
              required
            />
            {fieldErrors.businessRegNo && <span className="error">{fieldErrors.businessRegNo}</span>}
          </label>
          <label>
            {t('CEO_NAME')}
            <input value={ceoName} onChange={(e) => { setCeoName(e.target.value); setSaved(false) }} maxLength={50} />
          </label>
        </div>
        <div className="field-row">
          <label>
            {t('CONTACT_NAME')}
            <input value={contactName} onChange={(e) => { setContactName(e.target.value); setSaved(false) }} maxLength={50} />
          </label>
          <label>
            {t('CONTACT_PHONE')}
            <input
              value={contactPhone}
              onChange={(e) => { setContactPhone(fmtPhone(e.target.value, profile?.country === 'JP')); setSaved(false) }}
              placeholder={profile?.country === 'JP' ? '0312345678' : '010-1234-5678'}
              inputMode="tel"
              maxLength={20}
            />
            {fieldErrors.contactPhone && <span className="error">{fieldErrors.contactPhone}</span>}
          </label>
        </div>
        <label>
          {t('CONTACT_EMAIL')}
          <input type="email" value={contactEmail} onChange={(e) => { setContactEmail(e.target.value); setSaved(false) }} maxLength={100} placeholder="biz@company.com" />
          {fieldErrors.contactEmail && <span className="error">{fieldErrors.contactEmail}</span>}
        </label>
        {/* 주소는 우편번호·기본주소·상세주소로 분리 — 청구서 공급받는자에 그대로 반영 */}
        <label className="field-narrow">
          {t('POSTAL_CODE')}
          <input value={postalCode} onChange={(e) => { setPostalCode(fmtPostal(e.target.value, profile?.country === 'JP')); setSaved(false) }} maxLength={10} placeholder={profile?.country === 'JP' ? '100-0001' : '06236'} inputMode="numeric" />
        </label>
        <label>
          {t('ADDRESS')}
          <input value={address} onChange={(e) => { setAddress(e.target.value); setSaved(false) }} maxLength={200} placeholder="서울시 강남구 테헤란로 123" />
        </label>
        <label>
          {t('ADDRESS_DETAIL')}
          <input value={addressDetail} onChange={(e) => { setAddressDetail(e.target.value); setSaved(false) }} maxLength={200} placeholder="4층 402호" />
        </label>
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
      <SectionHead title={t('PAYMENT_SETTINGS')} />
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
      <SectionHead title={t('CONTRACT_SUMMARY')} />
      <p className="hint">{t('CONTRACT_READONLY_HINT')}</p>
      {error && <p className="error" role="alert">{error}</p>}
      {contract && (
        /* 사업자 정보의 현재값 박스와 동일한 읽기전용 스타일로 통일(S1-3) */
        <div className="info-box">
          <dl className="kv">
            <dt>{t('PLAN')}</dt>
            <dd>{contract.plan}</dd>
            <dt>{t('SEAT_PRICE')}</dt>
            <dd className="tnum">{won(contract.perSeatAmount)}</dd>
            <dt>{t('FREE_SEATS')}</dt>
            <dd>{contract.freeSeats}</dd>
          </dl>
        </div>
      )}
    </section>
  )
}
