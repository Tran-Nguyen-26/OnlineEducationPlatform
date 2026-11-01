import { Search, RotateCcw, Info } from 'lucide-react'

interface ApplicationFiltersProps {
  status: string
  search: string
  totalCount: number
  pendingCount?: number
  page: number
  size: number
  onStatusChange: (status: string) => void
  onSearchChange: (search: string) => void
  onReset: () => void
}

const STATUS_TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xét duyệt' },
  { key: 'APPROVED', label: 'Đã phê duyệt' },
  { key: 'REJECTED', label: 'Đã từ chối' },
]

export function ApplicationFilters({
  status,
  search,
  totalCount,
  pendingCount = 0,
  page,
  size,
  onStatusChange,
  onSearchChange,
  onReset,
}: ApplicationFiltersProps) {
  const startItem = totalCount === 0 ? 0 : page * size + 1
  const endItem = Math.min((page + 1) * size, totalCount)

  return (
    <div className="space-y-4">
      {/* ── Status Tabs Bar ── */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200/80 pb-2">
        <div className="flex items-center gap-1.5">
          {STATUS_TABS.map((tab) => {
            const isActive = status === tab.key
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => onStatusChange(tab.key)}
                className={`relative flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition-all ${
                  isActive
                    ? 'bg-[#00695c] text-white shadow-xs'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`}
              >
                <span>{tab.label}</span>
                {tab.key === 'PENDING' && pendingCount > 0 && (
                  <span
                    className={`rounded-full px-2 py-0.2 text-[11px] font-bold ${
                      isActive
                        ? 'bg-amber-400 text-slate-900'
                        : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    {pendingCount}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {/* Counter Info */}
        <div className="flex items-center gap-1.5 text-xs text-slate-500">
          <Info className="h-4 w-4 text-slate-400" />
          <span>
            Hiển thị{' '}
            <strong className="font-semibold text-slate-700">
              {startItem} - {endItem}
            </strong>{' '}
            trong tổng số{' '}
            <strong className="font-semibold text-slate-700">
              {totalCount.toLocaleString()}
            </strong>{' '}
            hồ sơ
          </span>
        </div>
      </div>

      {/* ── Search & Action Bar ── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative min-w-[320px] flex-1 sm:max-w-md">
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Tìm theo họ tên, email, cơ quan, chuyên môn..."
            className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-800 placeholder-slate-400 transition-all focus:border-[#00695c] focus:outline-none focus:ring-2 focus:ring-[#00695c]/20"
          />
        </div>

        <button
          type="button"
          onClick={onReset}
          className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 active:bg-slate-100"
        >
          <RotateCcw className="h-4 w-4 text-slate-500" />
          <span>Đặt lại</span>
        </button>
      </div>
    </div>
  )
}
