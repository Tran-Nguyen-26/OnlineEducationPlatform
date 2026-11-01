import { Search, RotateCcw, Info } from 'lucide-react'

interface AdminUserFiltersProps {
  search: string
  role: string
  status: string
  totalCount: number
  page: number
  size: number
  onSearchChange: (value: string) => void
  onRoleChange: (value: string) => void
  onStatusChange: (value: string) => void
  onReset: () => void
}

export function AdminUserFilters({
  search,
  role,
  status,
  totalCount,
  page,
  size,
  onSearchChange,
  onRoleChange,
  onStatusChange,
  onReset,
}: AdminUserFiltersProps) {
  const startItem = totalCount === 0 ? 0 : page * size + 1
  const endItem = Math.min((page + 1) * size, totalCount)

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 py-2">
      {/* Filters Group */}
      <div className="flex flex-wrap items-center gap-3">
        {/* Search input */}
        <div className="relative min-w-[300px]">
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Nhập họ tên hoặc email..."
            className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-800 placeholder-slate-400 transition-all focus:border-[#00695c] focus:outline-none focus:ring-2 focus:ring-[#00695c]/20"
          />
        </div>

        {/* Role select */}
        <div className="relative">
          <select
            value={role}
            onChange={(e) => onRoleChange(e.target.value)}
            className="appearance-none rounded-xl border border-slate-200 bg-white py-2.5 pl-4 pr-10 text-sm font-medium text-slate-700 transition-all focus:border-[#00695c] focus:outline-none focus:ring-2 focus:ring-[#00695c]/20"
          >
            <option value="ALL">Tất cả vai trò</option>
            <option value="STUDENT">Học viên (STUDENT)</option>
            <option value="INSTRUCTOR">Giảng viên (INSTRUCTOR)</option>
            <option value="ADMIN">Quản trị viên (ADMIN)</option>
          </select>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-3 text-slate-400">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>

        {/* Status select */}
        <div className="relative">
          <select
            value={status}
            onChange={(e) => onStatusChange(e.target.value)}
            className="appearance-none rounded-xl border border-slate-200 bg-white py-2.5 pl-4 pr-10 text-sm font-medium text-slate-700 transition-all focus:border-[#00695c] focus:outline-none focus:ring-2 focus:ring-[#00695c]/20"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đã khóa</option>
          </select>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-3 text-slate-400">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>

        {/* Reset button */}
        <button
          type="button"
          onClick={onReset}
          className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 active:bg-slate-100"
        >
          <RotateCcw className="h-4 w-4 text-slate-500" />
          <span>Đặt lại</span>
        </button>
      </div>

      {/* Info indicator */}
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
          người dùng
        </span>
      </div>
    </div>
  )
}
