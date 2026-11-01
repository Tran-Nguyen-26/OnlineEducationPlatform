import { FileText, Clock, CheckCircle2, XCircle } from 'lucide-react'
import type { InstructorApplicationStats } from '@/api/instructorApplications'

interface ApplicationMetricsProps {
  stats: InstructorApplicationStats
  loading?: boolean
}

export function ApplicationMetrics({ stats, loading }: ApplicationMetricsProps) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
      {/* Total Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-slate-200/80 bg-white px-4 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
          <FileText className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">Tổng đơn đăng ký</div>
          <div className="text-xl font-bold tracking-tight text-slate-900">
            {loading ? '...' : stats.total.toLocaleString()}
          </div>
        </div>
      </div>

      {/* Pending Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-amber-200/60 bg-amber-50/40 px-4 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-100 text-amber-700">
          <Clock className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-semibold text-amber-700">Chờ xét duyệt</div>
          <div className="text-xl font-bold tracking-tight text-amber-900">
            {loading ? '...' : stats.pending.toLocaleString()}
          </div>
        </div>
      </div>

      {/* Approved Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-emerald-200/60 bg-emerald-50/40 px-4 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#e6f4ea] text-[#00695c]">
          <CheckCircle2 className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-semibold text-emerald-700">Đã phê duyệt</div>
          <div className="text-xl font-bold tracking-tight text-[#00695c]">
            {loading ? '...' : stats.approved.toLocaleString()}
          </div>
        </div>
      </div>

      {/* Rejected Card */}
      <div className="flex items-center gap-3.5 rounded-xl border border-rose-200/60 bg-rose-50/40 px-4 py-3.5 shadow-xs">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-100 text-rose-700">
          <XCircle className="h-5 w-5" />
        </div>
        <div>
          <div className="text-xs font-semibold text-rose-700">Đã từ chối</div>
          <div className="text-xl font-bold tracking-tight text-rose-900">
            {loading ? '...' : stats.rejected.toLocaleString()}
          </div>
        </div>
      </div>
    </div>
  )
}
