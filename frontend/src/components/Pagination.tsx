import { useApp } from '../app/AppContext'

/**
 * 페이지 번호 방식 페이지네이션(#9) — 무한 증가 리스트 공통.
 * 1페이지뿐이면 아무것도 그리지 않는다(불필요한 컨트롤 노출 방지).
 * 페이지 수가 많으면 현재 페이지 주변만 표시(1 … 4 5 6 … 20).
 */
export function Pagination({
  page,
  totalPages,
  totalCount,
  onChange,
}: {
  page: number
  totalPages: number
  totalCount: number
  onChange: (page: number) => void
}) {
  const { t } = useApp()
  if (totalPages <= 1) {
    return null
  }

  //현재 페이지 ±2 + 양 끝. 사이가 벌어지면 말줄임표.
  const pages: (number | 'gap')[] = []
  let prev = 0
  for (let p = 1; p <= totalPages; p++) {
    if (p === 1 || p === totalPages || Math.abs(p - page) <= 2) {
      if (prev && p - prev > 1) {
        pages.push('gap')
      }
      pages.push(p)
      prev = p
    }
  }

  return (
    <nav className="pagination" aria-label={`${page}/${totalPages}`}>
      <span className="pagination-total muted">
        {t('PAGE_TOTAL').replace('{0}', totalCount.toLocaleString())}
      </span>
      <button
        type="button"
        disabled={page <= 1}
        aria-label={t('PAGE_PREV')}
        onClick={() => onChange(page - 1)}
      >
        ‹
      </button>
      {pages.map((p, i) =>
        p === 'gap' ? (
          <span key={`gap-${i}`} className="pagination-gap muted">…</span>
        ) : (
          <button
            key={p}
            type="button"
            className={p === page ? 'active' : ''}
            aria-current={p === page ? 'page' : undefined}
            onClick={() => onChange(p)}
          >
            {p}
          </button>
        ),
      )}
      <button
        type="button"
        disabled={page >= totalPages}
        aria-label={t('PAGE_NEXT')}
        onClick={() => onChange(page + 1)}
      >
        ›
      </button>
    </nav>
  )
}
