/**
 * 데이터 로딩 베일(디자인 공통화) — 목록/패널 컨테이너 위를 덮어
 * 데이터 도착 전 상호작용(이중 클릭 등)을 차단하고 스피너를 표시한다.
 * 규칙:
 * - 부모 컨테이너에 position 기준이 있어야 한다(.table-wrap은 기본 적용됨).
 * - 빈 결과 표시는 EmptyState의 몫 — 로딩 중에는 EmptyState를 그리지 않는다
 *   (!loading && 목록.length === 0 조건으로 분리).
 * - label에는 언어 마스터 LOADING(W999)을 넘긴다(스크린리더 안내).
 */
export function LoadingOverlay({ show, label }: { show: boolean; label: string }) {
  if (!show) return null
  return (
    <div className="loading-veil" role="status" aria-label={label}>
      <span className="spinner" aria-hidden="true" />
    </div>
  )
}
