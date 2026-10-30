import { Users, ShieldCheck } from 'lucide-react'
import type { AdminUserStats } from '@/api/adminUsers'

interface AdminUserMetricsProps {
  stats: AdminUserStats
  loading?: boolean
}

export function AdminUserMetrics({ stats, loading }: AdminUserMetricsProps) {
  return (
    <div className="flex items-center gap-4">
      {/* Total Users Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-slate-200/80 bg-white px-5 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#e6f4ea] text-[#00695c]">
          <Users className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">Tổng người dùng</div>
          <div className="text-xl font-bold tracking-tight text-slate-900">
            {loading ? '...' : stats.totalUsers.toLocaleString()}
          </div>
        </div>
      </div>

      {/* Active Users Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-slate-200/80 bg-white px-5 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-50 text-sky-600">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">Đang hoạt động</div>
          <div className="text-xl font-bold tracking-tight text-slate-900">
            {loading ? '...' : stats.activeUsers.toLocaleString()}
          </div>
        </div>
      </div>
    </div>
  )
}
