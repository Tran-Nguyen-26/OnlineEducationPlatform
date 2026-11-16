import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { AppealResponse } from '../../types/api'
import { Badge } from '../../components/Badge'
import { useCourseTasks } from './useCourseTasks'

export function GradesPage() {
  const { tasks, progress, loading } = useCourseTasks()
  const appeals = useQuery({
    queryKey: ['student', 'appeals'],
    queryFn: () => apiClient.get<AppealResponse[]>('/api/appeals/mine').then((r) => r.data),
  })

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Điểm số</h1>
      <p className="mb-4 text-sm text-slate-500">Điểm quiz (theo chính sách tính điểm của từng quiz) và điểm bài tập đã được chấm, theo từng khóa.</p>
      {loading && <p className="text-slate-400">Đang tải...</p>}
      <div className="space-y-4">
        {progress.filter((p) => p.status === 'ACTIVE').map((course) => {
          const rows = tasks.filter((t) => t.courseId === course.courseId && t.score != null)
          return (
            <div key={course.enrollmentId} className="rounded-lg border border-slate-200 bg-white p-4">
              <h2 className="mb-2 font-semibold text-slate-900">{course.courseTitle}</h2>
              {rows.length === 0 ? (
                <p className="text-sm text-slate-400">Chưa có điểm nào.</p>
              ) : (
                <table className="w-full text-left text-sm">
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((t) => (
                      <tr key={t.key}>
                        <td className="py-2 text-slate-500">{t.kind === 'QUIZ' ? 'Quiz' : 'Bài tập'}</td>
                        <td className="py-2 text-slate-900">{t.title}</td>
                        <td className="py-2 text-right font-medium">{t.maxScore ? `${t.score}/${t.maxScore}` : t.score}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )
        })}
      </div>

      <h2 className="mt-10 mb-2 text-lg font-semibold text-slate-900">Yêu cầu phúc khảo của tôi</h2>
      <p className="mb-3 text-sm text-slate-500">Muốn xem lại một điểm? Mở bài tập hoặc lịch sử quiz trong khóa học và chọn "Phúc khảo".</p>
      <div className="space-y-2">
        {appeals.data?.map((a) => (
          <div key={a.id} className="rounded-lg border border-slate-200 bg-white p-4 text-sm">
            <div className="flex items-center justify-between">
              <div className="font-medium text-slate-900">{a.targetTitle} <span className="font-normal text-slate-400">· {a.courseTitle}</span></div>
              <Badge status={a.status} />
            </div>
            <p className="mt-1 text-slate-600">Lý do: {a.reason}</p>
            {a.response && <p className="mt-1 text-slate-700"><span className="font-medium">Phản hồi:</span> {a.response}</p>}
          </div>
        ))}
        {appeals.data?.length === 0 && <p className="text-sm text-slate-400">Bạn chưa gửi yêu cầu phúc khảo nào.</p>}
      </div>
    </div>
  )
}
