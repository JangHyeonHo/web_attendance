import { useEffect, useState } from 'react'
import { attendanceApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useApp } from '../app/AppContext'
import type { AttendanceType, CheckRequest, StatusResponse } from '../api/types'
import { DetailsScreen } from './DetailsScreen'

const TYPE_LABEL_KEYS: Record<AttendanceType, string> = {
  GO_TO_WORK: 'ATTEND',
  OFF_WORK: 'OFFWORK',
  EARLY_DEPARTURE: 'EARLY',
  BREAK: 'BREAKTIME',
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

/** W005 출결 */
export function AttendanceScreen() {
  const { t, data } = useApp()
  //화면 전개시 navigation 응답에 동봉된 초기 상태를 사용하고, 이후 갱신은 status API로
  const [status, setStatus] = useState<StatusResponse | null>((data as StatusResponse) ?? null)
  const [pending, setPending] = useState<PendingStamp | null>(null)
  const [confirmation, setConfirmation] = useState<PendingConfirmation | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showDetails, setShowDetails] = useState(false)
  const [now, setNow] = useState(new Date())

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  //언어 전환 등으로 화면이 재전개되면 navigation 응답의 초기 데이터로 상태를 동기화
  useEffect(() => {
    if (data) {
      setStatus(data as StatusResponse)
    }
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
      await refreshStatus()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
      setConfirmation(null)
    }
  }

  return (
    <div className="panel">
      <div className="center">
        <div className="today">
          {now.getMonth() + 1}/{now.getDate()}{' '}
          <span className="clock">{now.toLocaleTimeString()}</span>
        </div>
        <p>
          {t('STATUS_PREFIX')}{' '}
          <strong className="status-label" data-status={status?.status ?? 'WAITING'}>
            {status?.statusLabel ?? '-'}
          </strong>{' '}
          {t('STATUS_SUFFIX')}
        </p>
        {status?.stampedAt && (
          <p className="muted">
            {t('STAMPED_AT')}: {status.stampedAt.replace('T', ' ').slice(0, 19)}
          </p>
        )}
        {status?.alertLabel && <p className="alert">{status.alertLabel}</p>}
      </div>

      <div className="btn-row">
        {(Object.keys(TYPE_LABEL_KEYS) as AttendanceType[]).map((type) => (
          <button key={type} onClick={() => selectType(type)}>
            {t(TYPE_LABEL_KEYS[type])}
          </button>
        ))}
      </div>

      {pending && !confirmation && (
        <div className="stamp-box">
          <h3>{t(TYPE_LABEL_KEYS[pending.type])}</h3>
          <p>
            {t('CURRENT_TIME')}: {now.toLocaleTimeString()}
          </p>
          <p className="muted">
            {t('LATITUDE')}: {pending.latitude ?? '-'} / {t('LONGITUDE')}: {pending.longitude ?? '-'}
          </p>
          {pending.geoError && <p className="error">{pending.geoError}</p>}
          <p>{t('CONFIRM_STAMP')}</p>
          <div className="btn-row">
            <button className="primary" onClick={() => void submit()}>
              {t('SUBMIT')}
            </button>
            <button onClick={() => selectType(pending.type)}>{t('RETRY')}</button>
            <button onClick={() => setPending(null)}>{t('CANCEL')}</button>
          </div>
        </div>
      )}

      {confirmation && (
        <div className="stamp-box confirm" role="alertdialog">
          <p>{confirmation.message}</p>
          <div className="btn-row">
            <button
              className="primary"
              onClick={() => void confirmStamp(confirmation.request, confirmation.token)}
            >
              {t('SUBMIT')}
            </button>
            <button onClick={() => setConfirmation(null)}>{t('CANCEL')}</button>
          </div>
        </div>
      )}

      {message && <p className="success center" role="status">{message}</p>}
      {error && <p className="error center" role="alert">{error}</p>}

      <div className="center">
        <button className="wide" onClick={() => setShowDetails((v) => !v)}>
          {t('ATTDETAILS')}
        </button>
      </div>
      {showDetails && <DetailsScreen />}
    </div>
  )
}
