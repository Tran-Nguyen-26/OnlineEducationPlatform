import { useQuery } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { downloadCsv } from '../../lib/csv'
import type { ReportOverview } from '../../types/api'
import { Button } from '../../components/ui/Button'

const pct = (n: number | null) => (n === null ? '—' : `${n}%`)
const th = 'px-4 py-3 font-medium'

function Stat({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-bold text-slate-900">{value}</div>
      {sub && <div className="text-xs text-slate-400">{sub}</div>}
    </div>
  )
}

export function ReportsPage() {
  const query = useQuery({
    queryKey: ['admin-report'],
    queryFn: () => apiClient.get<ReportOverview>('/api/admin/reports/overview').then((r) => r.data),
  })
  const d = query.data
  if (query.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (query.isError) return <p className="text-red-600">{getApiErrorMessage(query.error)}</p>
  if (!d) return null
  const maxMonth = Math.max(1, ...d.months.map((m) => Math.max(m.enrollments, m.completions)))

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Báo cáo & thống kê</h1>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={() => downloadCsv('bao-cao-khoa-hoc.csv', [
            ['Khóa học', 'Trạng thái', 'Đang học', 'Hoàn thành', 'Rút', 'Bị xóa', 'Danh sách chờ', 'Tỷ lệ hoàn thành (%)', 'Tỷ lệ bỏ học (%)'],
            ...d.courses.map((c) => [c.title, c.lifecycleStatus, c.active, c.completed, c.withdrawn, c.removed, c.waitlisted, c.completionRate ?? '', c.dropoutRate ?? '']),
          ])}>Xuất báo cáo khóa học</Button>
          <Button size="sm" variant="secondary" onClick={() => downloadCsv('bao-cao-giao-vien.csv', [
            ['Giáo viên', 'Email', 'Số khóa', 'Đang học', 'Hoàn thành', 'Tỷ lệ hoàn thành (%)', 'Bài chờ chấm'],
            ...d.instructors.map((t) => [t.name, t.email, t.courses, t.activeStudents, t.completedStudents, t.completionRate ?? '', t.pendingGrading]),
          ])}>Xuất báo cáo giáo viên</Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Tổng lượt ghi danh" value={d.totalEnrollments} sub="không tính danh sách chờ" />
        <Stat label="Đang học" value={d.activeEnrollments} />
        <Stat label="Tỷ lệ hoàn thành" value={pct(d.overallCompletionRate)} sub={`${d.completedEnrollments} lượt hoàn thành`} />
        <Stat label="Tỷ lệ bỏ học" value={pct(d.overallDropoutRate)} sub={`${d.dropouts} lượt rút/bị xóa`} />
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Xu hướng 6 tháng</h2>
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex h-40 items-end gap-6">
            {d.months.map((m) => (
              <div key={m.month} className="flex flex-1 flex-col items-center gap-1">
                <div className="flex h-32 w-full items-end justify-center gap-1">
                  <div className="w-1/3 rounded-t bg-slate-800" style={{ height: `${(m.enrollments / maxMonth) * 100}%` }} title={`Ghi danh: ${m.enrollments}`} />
                  <div className="w-1/3 rounded-t bg-emerald-500" style={{ height: `${(m.completions / maxMonth) * 100}%` }} title={`Hoàn thành: ${m.completions}`} />
                </div>
                <div className="text-xs text-slate-500">{m.month}</div>
                <div className="text-xs text-slate-400">{m.enrollments} / {m.completions}</div>
              </div>
            ))}
          </div>
          <div className="mt-2 flex gap-4 text-xs text-slate-500">
            <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-slate-800" />Ghi danh mới</span>
            <span><span className="mr-1 inline-block h-2 w-2 rounded-sm bg-emerald-500" />Hoàn thành</span>
          </div>
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Theo khóa học</h2>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Khóa học</th><th className={th}>Đang học</th><th className={th}>Hoàn thành</th><th className={th}>Rút / xóa</th><th className={th}>Chờ</th><th className={th}>Hoàn thành %</th><th className={th}>Bỏ học %</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {d.courses.map((c) => (
                <tr key={c.courseId}>
                  <td className="px-4 py-3 font-medium text-slate-900">{c.title}</td>
                  <td className="px-4 py-3">{c.active}</td><td className="px-4 py-3">{c.completed}</td><td className="px-4 py-3">{c.withdrawn + c.removed}</td><td className="px-4 py-3">{c.waitlisted}</td>
                  <td className="px-4 py-3">{pct(c.completionRate)}</td>
                  <td className={`px-4 py-3 ${c.dropoutRate !== null && c.dropoutRate >= 30 ? 'font-medium text-red-600' : ''}`}>{pct(c.dropoutRate)}</td>
                </tr>
              ))}
              {d.courses.length === 0 && <tr><td colSpan={7} className="px-4 py-6 text-slate-400">Chưa có khóa học.</td></tr>}
            </tbody>
          </table>
        </div>
        <p className="mt-1 text-xs text-slate-400">Tỷ lệ tính trên ACTIVE + COMPLETED + rút + bị xóa. Bỏ học ≥ 30% được tô đỏ.</p>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Theo giáo viên</h2>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Giáo viên</th><th className={th}>Số khóa</th><th className={th}>Đang học</th><th className={th}>Hoàn thành</th><th className={th}>Hoàn thành %</th><th className={th}>Bài chờ chấm</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {d.instructors.map((t) => (
                <tr key={t.instructorId}>
                  <td className="px-4 py-3"><div className="font-medium text-slate-900">{t.name}</div><div className="text-xs text-slate-400">{t.email}</div></td>
                  <td className="px-4 py-3">{t.courses}</td><td className="px-4 py-3">{t.activeStudents}</td><td className="px-4 py-3">{t.completedStudents}</td>
                  <td className="px-4 py-3">{pct(t.completionRate)}</td>
                  <td className={`px-4 py-3 ${t.pendingGrading > 0 ? 'font-medium text-amber-600' : ''}`}>{t.pendingGrading}</td>
                </tr>
              ))}
              {d.instructors.length === 0 && <tr><td colSpan={6} className="px-4 py-6 text-slate-400">Chưa có giáo viên.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
