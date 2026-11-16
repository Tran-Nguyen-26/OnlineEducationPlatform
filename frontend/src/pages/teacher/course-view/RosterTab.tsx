import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../lib/apiClient'
import type { RosterEntryResponse } from '../../../types/api'
import { Badge } from '../../../components/Badge'

export function RosterTab({ courseId }: { courseId: string }) {
  const rosterQuery = useQuery({
    queryKey: ['teacher', 'courses', courseId, 'students'],
    queryFn: () => apiClient.get<RosterEntryResponse[]>(`/api/instructors/courses/${courseId}/students`).then((r) => r.data),
  })

  const rows = rosterQuery.data ?? []

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Học sinh trong khóa</h2>
        <span className="text-sm text-slate-500">{rows.length} học sinh</span>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Học sinh</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Ngày ghi danh</th>
              <th className="px-4 py-3 font-medium">Tiến độ</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rosterQuery.isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Đang tải...
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.enrollmentId}>
                <td className="px-4 py-3 font-medium text-slate-900">{row.studentFullName ?? '—'}</td>
                <td className="px-4 py-3 text-slate-500">{row.studentEmail}</td>
                <td className="px-4 py-3">
                  <Badge status={row.status} />
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(row.enrolledAt).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="h-1.5 w-24 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full bg-slate-900" style={{ width: `${row.progressPercent}%` }} />
                    </div>
                    <span className="text-xs text-slate-500">
                      {row.completedLessons}/{row.totalLessons} ({row.progressPercent}%)
                    </span>
                  </div>
                </td>
              </tr>
            ))}
            {!rosterQuery.isLoading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Chưa có học sinh ghi danh khóa này.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
