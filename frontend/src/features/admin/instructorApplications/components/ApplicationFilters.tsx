import { RotateCcw } from 'lucide-react'

interface ApplicationFiltersProps {
  status: string
  onStatusChange: (value: string) => void
  onReset: () => void
}

export function ApplicationFilters({
  status,
  onStatusChange,
  onReset,
}: ApplicationFiltersProps) {
  const isFiltered = status !== 'ALL'

  return (
    <div className="flex flex-wrap items-center gap-3">
      {/* Status select */}
      <div className="relative">
        <select
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
          className="appearance-none rounded-md border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-medium text-slate-700 transition-colors focus:border-slate-400 focus:outline-none"
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="PENDING">Chờ xét duyệt</option>
          <option value="APPROVED">Đã phê duyệt</option>
          <option value="REJECTED">Đã từ chối</option>
        </select>
        <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-slate-400">
          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>

      {/* Reset button if filtered */}
      {isFiltered && (
        <button
          type="button"
          onClick={onReset}
          className="flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-500 hover:text-slate-800 hover:bg-slate-50 transition-colors"
          title="Đặt lại bộ lọc"
        >
          <RotateCcw className="h-3.5 w-3.5" />
          <span>Đặt lại</span>
        </button>
      )}
    </div>
  )
}
