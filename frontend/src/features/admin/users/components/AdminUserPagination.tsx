import { ChevronLeft, ChevronRight } from 'lucide-react'

interface AdminUserPaginationProps {
  currentPage: number
  totalPages: number
  size: number
  onPageChange: (newPage: number) => void
}

export function AdminUserPagination({
  currentPage,
  totalPages,
  size,
  onPageChange,
}: AdminUserPaginationProps) {
  // Generate smart pagination numbers
  const renderPageButtons = () => {
    const pages: (number | string)[] = []
    const total = Math.max(1, totalPages)

    if (total <= 6) {
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
            className="flex h-8 w-8 items-center justify-center text-xs text-slate-400"
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
          className={`flex h-8 min-w-[32px] items-center justify-center rounded-lg px-2 text-xs font-semibold transition-colors ${
            isActive
              ? 'bg-[#00695c] text-white shadow-xs'
              : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
          }`}
        >
          {pageNum + 1}
        </button>
      )
    })
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 py-4 text-xs text-slate-500">
      {/* Page Info */}
      <div className="font-medium">
        Trang <strong className="text-slate-800">{currentPage + 1}</strong> /{' '}
        <strong className="text-slate-800">{totalPages}</strong> •{' '}
        <span>{size} kết quả mỗi trang</span>
      </div>

      {/* Navigation Controls */}
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 0}
          className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <ChevronLeft className="h-3.5 w-3.5" />
          <span>Trước</span>
        </button>

        <div className="flex items-center gap-1">{renderPageButtons()}</div>

        <button
          type="button"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <span>Sau</span>
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  )
}
