import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import type { MyProgressResponse } from '../../types/api'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/ui/Button'

export function MyProgressPage() {
  const navigate = useNavigate()
  const progressQuery = useQuery({
    queryKey: ['student', 'progress'],
    queryFn: () => apiClient.get<MyProgressResponse[]>('/api/enrollments/progress').then((r) => r.data),
  })

  const items = progressQuery.data ?? []
  const overallPercent = items.length === 0 ? 0
    : Math.round((items.reduce((sum, i) => sum + i.percent, 0) / items.length) * 10) / 10

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Tiến độ của tôi</h1>
      <p className="mb-6 text-sm text-slate-500">Tổng hợp tiến độ học tập trên tất cả khóa học đã ghi danh.</p>

      {progressQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}

      {items.length > 0 && (
        <div className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="font-medium text-slate-900">Tiến độ trung bình</span>
            <span className="font-semibold text-slate-900">{overallPercent}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-slate-900" style={{ width: `${overallPercent}%` }} />
          </div>
        </div>
      )}

      <div className="space-y-3">
        {items.map((item) => (
          <div key={item.enrollmentId} className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-xs font-medium uppercase tracking-wide text-slate-400">{item.subjectName}</div>
                <h2 className="mt-1 text-lg font-semibold text-slate-900">{item.courseTitle}</h2>
              </div>
              <Badge status={item.status} />
            </div>

            <div className="mt-4">
              <div className="mb-1 flex items-center justify-between text-sm text-slate-600">
                <span>
                  {item.completedLessons}/{item.totalLessons} bài học đã hoàn thành
                </span>
                <span className="font-medium text-slate-900">{item.percent}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                <div className="h-full rounded-full bg-slate-900" style={{ width: `${item.percent}%` }} />
              </div>
            </div>

            <Button
              variant="secondary"
              size="sm"
              className="mt-4"
              onClick={() => navigate(`/student/courses/${item.courseId}`)}
            >
              Tiếp tục học
            </Button>
          </div>
        ))}
        {!progressQuery.isLoading && items.length === 0 && (
          <p className="text-slate-400">Chưa ghi danh khóa học nào.</p>
        )}
      </div>
    </div>
  )
}
