interface AdminUserPaginationProps {
  currentPage: number
  totalPages: number
  size: number
  totalCount?: number
  onPageChange: (newPage: number) => void
}

export function AdminUserPagination({
  currentPage,
  totalPages,
  size,
  totalCount = 0,
  onPageChange,
}: AdminUserPaginationProps) {
  const startItem = totalCount === 0 ? 0 : currentPage * size + 1
  const endItem = Math.min((currentPage + 1) * size, totalCount)

  // Generate smart pagination numbers
  const renderPageButtons = () => {
    const pages: (number | string)[] = []
    const total = Math.max(1, totalPages)

    if (total <= 5) {
      for (let i = 0; i < total; i++) pages.push(i)
    } else {
      pages.push(0) // First page
      if (currentPage > 2) {
        pages.push('...')
      }

      const start = Math.max(1, currentPage - 1)
      const end = Math.min(total - 2, currentPage + 1)
      for (let i = start; i <= end; i++) {
        if (!pages.includes(i)) pages.push(i)
      }

      if (currentPage < total - 3) {
        pages.push('...')
      }
      if (!pages.includes(total - 1)) {
        pages.push(total - 1) // Last page
      }
    }

    return pages.map((p, idx) => {
      if (p === '...') {
        return (
          <span
            key={`dots-${idx}`}
            className="flex h-7 w-7 items-center justify-center text-xs text-slate-400"
          >
            ...
          </span>
        )
      }

      const pageNum = p as number
      const isActive = pageNum === currentPage

      return (
        <button
          key={pageNum}
          type="button"
          onClick={() => onPageChange(pageNum)}
          className={`flex h-7 min-w-[28px] items-center justify-center rounded px-2 text-xs font-bold transition-colors ${
            isActive
              ? 'bg-[#f5b823] text-slate-950'
              : 'text-slate-600 hover:bg-slate-200/70 hover:text-slate-900'
          }`}
        >
          {pageNum + 1}
        </button>
      )
    })
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 py-4 text-xs text-slate-500">
      {/* Left: Showing X-Y of Z users */}
      <div>
        {totalCount > 0 ? (
          <>Hiển thị {startItem}-{endItem} trên {totalCount.toLocaleString('vi-VN')} kết quả</>
        ) : (
          <>Trang {currentPage + 1} / {totalPages}</>
        )}
      </div>

      {/* Right: Pagination Controls */}
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 0}
          className="rounded px-2.5 py-1 text-xs font-bold uppercase tracking-wider text-slate-600 hover:bg-slate-200/70 disabled:cursor-not-allowed disabled:opacity-30 transition-colors"
        >
          TRƯỚC
        </button>

        <div className="flex items-center gap-1">{renderPageButtons()}</div>

        <button
          type="button"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          className="rounded px-2.5 py-1 text-xs font-bold uppercase tracking-wider text-slate-600 hover:bg-slate-200/70 disabled:cursor-not-allowed disabled:opacity-30 transition-colors"
        >
          TIẾP
        </button>
      </div>
    </div>
  )
}
