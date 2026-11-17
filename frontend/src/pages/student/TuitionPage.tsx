import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { MyProgressResponse } from '../../types/api'
import { Badge } from '../../components/Badge'

const vnd = (n: number | null) => (n == null ? '—' : n.toLocaleString('vi-VN') + ' đ')

export function TuitionPage() {
  const query = useQuery({
    queryKey: ['student', 'progress'],
    queryFn: () => apiClient.get<MyProgressResponse[]>('/api/enrollments/progress').then((r) => r.data),
  })
  const rows = (query.data ?? []).filter((p) => p.paymentStatus !== 'NOT_APPLICABLE' && p.status !== 'WITHDRAWN' && p.status !== 'REMOVED')
  const totalFee = rows.reduce((s, r) => s + (r.feeAmount ?? 0), 0)
  const totalPaid = rows.reduce((s, r) => s + (r.totalPaid ?? 0), 0)

  return (
    <div>
      <h1 className="mb-1 text-2xl font-extrabold tracking-tight text-slate-900">Học phí</h1>
      <p className="mb-4 text-sm text-slate-500">Các khoản thanh toán do trung tâm ghi nhận. Thanh toán trực tiếp tại trung tâm.</p>
      <div className="mb-4 grid grid-cols-3 gap-3 text-sm">
        <div className="rounded-lg border border-slate-200 bg-white p-4"><div className="text-slate-500">Tổng học phí</div><div className="text-lg font-semibold">{vnd(totalFee)}</div></div>
        <div className="rounded-lg border border-slate-200 bg-white p-4"><div className="text-slate-500">Đã đóng</div><div className="text-lg font-semibold">{vnd(totalPaid)}</div></div>
        <div className="rounded-lg border border-slate-200 bg-white p-4"><div className="text-slate-500">Còn lại</div><div className="text-lg font-semibold">{vnd(Math.max(totalFee - totalPaid, 0))}</div></div>
      </div>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Khóa học</th>
              <th className="px-4 py-3 font-medium">Học phí</th>
              <th className="px-4 py-3 font-medium">Đã đóng</th>
              <th className="px-4 py-3 font-medium">Còn lại</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((r) => (
              <tr key={r.enrollmentId}>
                <td className="px-4 py-3 font-medium text-slate-900">{r.courseTitle}</td>
                <td className="px-4 py-3">{vnd(r.feeAmount)}</td>
                <td className="px-4 py-3">{vnd(r.totalPaid)}</td>
                <td className="px-4 py-3">{vnd(Math.max((r.feeAmount ?? 0) - (r.totalPaid ?? 0), 0))}</td>
                <td className="px-4 py-3"><Badge status={r.paymentStatus} /></td>
              </tr>
            ))}
            {!query.isLoading && rows.length === 0 && (
              <tr><td className="px-4 py-6 text-slate-400" colSpan={5}>Không có khoản học phí nào.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
