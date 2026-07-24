import { useEffect, useMemo, useState } from 'react'
import { attendanceApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { AttendanceType, CheckRequest, StatusResponse } from '../api/types'
import { DetailsScreen } from './DetailsScreen'
import { Modal } from '../components/Modal'
import { localeOf } from '../i18n/lang'

const TYPE_LABEL_KEYS: Record<AttendanceType, string> = {
  GO_TO_WORK: 'ATTEND',
  OFF_WORK: 'OFFWORK',
  EARLY_DEPARTURE: 'EARLY', //확정 모달 제목 등 과거 데이터 표기용(버튼은 폐지)
  BREAK: 'BREAKTIME',
}

/** 스탬프 버튼 — 조퇴 폐지(Phase 5.2): 출근/퇴근/휴식 3버튼 */
const STAMP_TYPES: AttendanceType[] = ['GO_TO_WORK', 'OFF_WORK', 'BREAK']

/**
 * 상태에 따라 '지금 할 동작'을 주요 버튼(상단·채움)으로, 나머지 둘을 보조로 배치한다.
 * - 근무 중(WORKING·ON_BREAK·BREAK_ENDED): 퇴근이 주요, 출근·휴식이 보조
 * - 그 외(출근 대기·퇴근 완료 등): 출근이 주요, 퇴근·휴식이 보조
 * 어떤 버튼도 비활성화하지 않는다(재출근 등으로 출근을 다시 찍을 수 있게 위치만 바뀐다).
 */
function orderStamps(status: StatusResponse | null): {
  primary: AttendanceType
  secondary: AttendanceType[]
} {
  const s = status?.status
  const working = s === 'WORKING' || s === 'ON_BREAK' || s === 'BREAK_ENDED'
  const primary: AttendanceType = working ? 'OFF_WORK' : 'GO_TO_WORK'
  //보조는 STAMP_TYPES 순서를 유지해 휴식이 항상 마지막에 오게 한다
  const secondary = STAMP_TYPES.filter((type) => type !== primary)
  return { primary, secondary }
}

/** 위치 취득 결과 + 선택한 타입(등록 패널 상태) */
interface PendingStamp {
  type: AttendanceType
  latitude: number | null
  longitude: number | null
  geoError: string | null
}

/** 확정 전 사용자 확인(덮어쓰기/재출근) 대기 상태 */
interface PendingConfirmation {
  message: string
  request: CheckRequest
  token: string
}

/** M001 출결 */
export function AttendanceScreen() {
  const { t, data, lang } = useApp()
  //화면 전개시 navigation 응답에 동봉된 초기 상태를 사용하고, 이후 갱신은 status API로
  const [status, setStatus] = useState<StatusResponse | null>((data as StatusResponse) ?? null)
  const [pending, setPending] = useState<PendingStamp | null>(null)
  const [confirmation, setConfirmation] = useState<PendingConfirmation | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showDetails, setShowDetails] = useState(true) //출결 조회 기본 표시(#9)
  //스탬프 확정 횟수 — 아래 출결 조회(DetailsScreen)가 확정 즉시 재조회하게 하는 신호
  const [stampCount, setStampCount] = useState(0)
  const [now, setNow] = useState(new Date())

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  //요일 명칭은 사전 없이 Intl 표준으로(언어별) — 날짜 옆에 표기(#3)
  const weekdayFmt = useMemo(
    () => new Intl.DateTimeFormat(localeOf(lang), { weekday: 'short' }),
    [lang],
  )

  //언어 전환 등으로 화면이 재전개되면 navigation 응답의 초기 데이터로 상태를 동기화하고,
  //이전 언어로 받아둔 일회성 메시지(성공/에러/확인 패널)는 정리한다
  useEffect(() => {
    if (data) {
      setStatus(data as StatusResponse)
    }
    setMessage(null)
    setError(null)
    setPending(null)
    setConfirmation(null)
  }, [data])

  async function refreshStatus() {
    try {
      setStatus(await attendanceApi.status())
    } catch {
      //상태 갱신 실패는 치명적이지 않으므로 무시(401은 클라이언트 훅이 처리)
    }
  }

  /** 타입 버튼 클릭: 위치 취득 후 등록 패널 표시 */
  function selectType(type: AttendanceType) {
    setMessage(null)
    setError(null)
    setConfirmation(null)
    if (!('geolocation' in navigator)) {
      setPending({ type, latitude: null, longitude: null, geoError: t('GEO_FAIL') })
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setPending({
          type,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          geoError: null,
        })
      },
      () => {
        //위치 취득 실패시에도 등록은 가능(좌표 없이)
        setPending({ type, latitude: null, longitude: null, geoError: t('GEO_FAIL') })
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 },
    )
  }

  /** 등록: 체크 → (필요시 확인) → 확정 */
  async function submit() {
    if (!pending) return
    setError(null)
    setMessage(null)
    const request: CheckRequest = {
      type: pending.type,
      latitude: pending.latitude,
      longitude: pending.longitude,
      placeInfo: null,
      terminal: navigator.userAgent.slice(0, 100),
    }
    try {
      const check = await attendanceApi.check(request)
      if (!check.allowed) {
        setError(check.message)
        setPending(null)
        return
      }
      if (check.requiresConfirmation && check.token) {
        //덮어쓰기/재출근: 사용자 확인 후 확정
        setConfirmation({ message: check.message ?? '', request, token: check.token })
        return
      }
      if (check.token) {
        await confirmStamp(request, check.token)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    }
  }

  async function confirmStamp(request: CheckRequest, token: string) {
    try {
      const stamped = await attendanceApi.confirm({ ...request, token })
      setMessage(stamped.message)
      setPending(null)
      setConfirmation(null)
      setStampCount((n) => n + 1) //출결 조회 목록 즉시 갱신
      await refreshStatus()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
      setConfirmation(null)
    }
  }

  const { primary, secondary } = orderStamps(status)

  return (
    <div className="panel">
      <div className="center">
        <div className="att-today">
          <span className="today-date">
            {now.getMonth() + 1}/{now.getDate()} ({weekdayFmt.format(now)})
          </span>
          <span className="clock">{now.toLocaleTimeString(localeOf(lang))}</span>
        </div>
        <p>
          {t('STATUS_PREFIX')}{' '}
          <strong className="status-label" data-status={status?.status ?? 'WAITING'}>
            {status?.statusLabel ?? '-'}
          </strong>{' '}
          {t('STATUS_SUFFIX')}
        </p>
        {/* 오늘의 해석된 근무 스케줄(work_schedule > 개인 기본값) — 휴일이면 null이라 비표시 */}
        {status?.todayScheduleStart && status?.todayScheduleEnd && (
          <p className="muted">
            {t('TODAY_SCHEDULE')}: {status.todayScheduleStart} ~ {status.todayScheduleEnd}
          </p>
        )}
        {status?.stampedAt && (
          <p className="muted">
            {t('STAMPED_AT')}: {status.stampedAt.replace('T', ' ').slice(0, 19)}
          </p>
        )}
        {status?.alertLabel && <p className="alert">{status.alertLabel}</p>}
      </div>

      {/* 등록 결과 메시지는 버튼 묶음 위(상태 블록 아래)에 둔다 —
          버튼 1+2 묶음과 '출결 조회'가 끊기지 않게(디자인 검증 반영) */}
      {message && <p className="success center" role="status">{message}</p>}
      {error && <p className="error center" role="alert">{error}</p>}

      {/* 주요 동작(lead)만 채움 버튼 — 나머지는 조용한 아웃라인.
          모바일: 주요=전폭 상단, 보조 2개=아래 반반(1+2). 데스크톱: 3열 유지. */}
      <div className="stamp-grid">
        {[{ type: primary, lead: true }, ...secondary.map((type) => ({ type, lead: false }))].map(
          ({ type, lead }) => (
            <button
              key={type}
              className={lead ? 'primary lead' : ''}
              onClick={() => selectType(type)}
            >
              {t(TYPE_LABEL_KEYS[type])}
            </button>
          ),
        )}
      </div>

      {pending && !confirmation && (
        <Modal title={t(TYPE_LABEL_KEYS[pending.type])} onClose={() => setPending(null)}>
          {/* form 래핑 — 엔터로 바로 등록(#1) */}
          <form className="center" onSubmit={(e) => { e.preventDefault(); void submit() }}>
            <p className="stamp-meta">
              {t('CURRENT_TIME')}: {now.toLocaleTimeString(localeOf(lang))}
            </p>
            <p className="stamp-meta muted">
              {t('LATITUDE')}: {pending.latitude ?? '-'} / {t('LONGITUDE')}:{' '}
              {pending.longitude ?? '-'}
            </p>
            {pending.geoError && <p className="error">{pending.geoError}</p>}
            <p>{t('CONFIRM_STAMP')}</p>
            <div className="btn-row">
              <button type="submit" className="primary">
                {t('SUBMIT')}
              </button>
              <button type="button" onClick={() => selectType(pending.type)}>{t('RETRY')}</button>
              <button type="button" onClick={() => setPending(null)}>{t('CANCEL')}</button>
            </div>
          </form>
        </Modal>
      )}

      {confirmation && (
        <Modal
          title={t(TYPE_LABEL_KEYS[confirmation.request.type])}
          onClose={() => setConfirmation(null)}
          danger
        >
          {/* form 래핑 — 엔터로 바로 확정(#1) */}
          <form
            className="center"
            onSubmit={(e) => { e.preventDefault(); void confirmStamp(confirmation.request, confirmation.token) }}
          >
            <p>{confirmation.message}</p>
            <div className="btn-row">
              {/* 확인 버튼은 행위 라벨(출근/퇴근/휴식) — 범용 SUBMIT('등록') 재사용 금지 */}
              <button type="submit" className="primary">
                {t(TYPE_LABEL_KEYS[confirmation.request.type])}
              </button>
              <button type="button" onClick={() => setConfirmation(null)}>{t('CANCEL')}</button>
            </div>
          </form>
        </Modal>
      )}

      <div className="center">
        <button className="wide" onClick={() => setShowDetails((v) => !v)}>
          {t('ATTDETAILS')}
        </button>
      </div>
      {showDetails && <DetailsScreen refreshSignal={stampCount} />}
    </div>
  )
}
