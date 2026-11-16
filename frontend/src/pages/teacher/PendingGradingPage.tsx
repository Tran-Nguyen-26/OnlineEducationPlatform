import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import type { PendingGradingResponse } from '../../types/api'
import { Badge } from '../../components/Badge'

export function PendingGradingPage() {
  const query = useQuery({
    queryKey: ['teacher', 'pending-grading'],
    queryFn: () => apiClient.get<PendingGradingResponse[]>('/api/gradebook/pending-grading').then((r) => r.data),
  })
  const rows = query.data ?? []

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Cần chấm bài</h1>
      <p className="mb-4 text-sm text-slate-500">Bài nộp chưa được chấm hoặc đang chờ chấm lại, từ tất cả khóa bạn phụ trách — bài nộp lâu nhất lên đầu.</p>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Học sinh</th>
              <th className="px-4 py-3 font-medium">Bài tập</th>
              <th className="px-4 py-3 font-medium">Khóa học</th>
              <th className="px-4 py-3 font-medium">Nộp lúc</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((r) => (
              <tr key={r.submissionId}>
                <td className="px-4 py-3"><div className="font-medium text-slate-900">{r.studentName}</div><div className="text-xs text-slate-400">{r.studentEmail}</div></td>
                <td className="px-4 py-3">{r.assignmentTitle}</td>
                <td className="px-4 py-3 text-slate-500">{r.courseTitle}</td>
                <td className="px-4 py-3 text-slate-500">{new Date(r.submittedAt).toLocaleString('vi-VN')}{r.late && <span className="ml-1 text-amber-600">(muộn)</span>}</td>
                <td className="px-4 py-3"><Badge status={r.status} /></td>
                <td className="px-4 py-3 text-right"><Link to={`/teacher/courses/${r.courseId}?tab=grading`} className="text-blue-600 hover:underline">Chấm bài</Link></td>
              </tr>
            ))}
            {!query.isLoading && rows.length === 0 && (
              <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Không có bài nào đang chờ chấm.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
