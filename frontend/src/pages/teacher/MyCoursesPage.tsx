import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import type { InstructorCourseResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Badge } from '../../components/Badge'

export function MyCoursesPage() {
  const navigate = useNavigate()
  const coursesQuery = useQuery({
    queryKey: ['teacher', 'my-courses'],
    queryFn: () => apiClient.get<InstructorCourseResponse[]>('/api/instructors/me/courses').then((r) => r.data),
  })

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Khóa học của tôi</h1>

      {coursesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {coursesQuery.data?.length === 0 && <p className="text-slate-400">Bạn chưa được gán dạy khóa học nào.</p>}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {coursesQuery.data?.map((course) => (
          <div key={course.id} className="flex flex-col rounded-lg border border-slate-200 bg-white p-5">
            <div className="mb-1 flex items-center justify-between">
              <span className="text-xs font-medium uppercase tracking-wide text-slate-400">{course.subjectName}</span>
              <Badge status={course.lifecycleStatus} />
            </div>
            <h2 className="text-lg font-semibold text-slate-900">{course.title}</h2>
            {course.description && <p className="mt-2 flex-1 text-sm text-slate-500">{course.description}</p>}
            <Button className="mt-4 w-full" onClick={() => navigate(`/teacher/courses/${course.id}`)}>
              Xem khóa học
            </Button>
          </div>
        ))}
      </div>
    </div>
  )
}
