import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { AdminDashboardStatsResponse } from '../../types/api'

function StatCard({ label, value }: { label: string; value: number | string | undefined }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="text-sm font-medium text-slate-500">{label}</div>
      <div className="mt-2 text-3xl font-bold text-slate-900">{value ?? '—'}</div>
    </div>
  )
}

function Breakdown({ title, rows }: { title: string; rows: { label: string; value: number }[] }) {
  const total = rows.reduce((sum, r) => sum + r.value, 0)
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 text-sm font-semibold text-slate-900">{title}</div>
      <div className="space-y-3">
        {rows.map((r) => (
          <div key={r.label}>
            <div className="mb-1 flex items-center justify-between text-sm">
              <span className="text-slate-600">{r.label}</span>
              <span className="font-medium text-slate-900">{r.value}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-slate-900"
                style={{ width: total === 0 ? '0%' : `${(r.value / total) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function DashboardPage() {
  const statsQuery = useQuery({
    queryKey: ['admin', 'stats'],
    queryFn: () => apiClient.get<AdminDashboardStatsResponse>('/api/admin/stats').then((r) => r.data),
  })
  const s = statsQuery.data

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Tổng quan</h1>

      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Môn học" value={s?.totalSubjects} />
        <StatCard label="Khóa học" value={s?.totalCourses} />
        <StatCard label="Giáo viên" value={s?.totalTeachers} />
        <StatCard label="Học sinh" value={s?.totalStudents} />
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Lượt ghi danh" value={s?.totalEnrollments} />
        <StatCard label="Đang học" value={s?.activeEnrollments} />
        <StatCard label="Đã hoàn thành" value={s?.completedEnrollments} />
        <StatCard label="Tỷ lệ hoàn thành" value={s ? `${s.completionRatePercent}%` : undefined} />
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Breakdown
          title="Khóa học theo trạng thái"
          rows={[
            { label: 'Lưu nháp', value: s?.draftCourses ?? 0 },
            { label: 'Công khai', value: s?.publishedCourses ?? 0 },
            { label: 'Lưu trữ', value: s?.archivedCourses ?? 0 },
            { label: 'Vô hiệu hóa', value: s?.disabledCourses ?? 0 },
          ]}
        />
        <Breakdown
          title="Ghi danh theo trạng thái"
          rows={[
            { label: 'Đang học', value: s?.activeEnrollments ?? 0 },
            { label: 'Đã hoàn thành', value: s?.completedEnrollments ?? 0 },
            { label: 'Đã rút', value: s?.withdrawnEnrollments ?? 0 },
            { label: 'Đã bị loại', value: s?.removedEnrollments ?? 0 },
          ]}
        />
      </div>
    </div>
  )
}
