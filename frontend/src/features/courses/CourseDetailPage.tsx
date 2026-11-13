import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { accessCodeError, fetchAccessibleCourse } from '@/api/accessCodes'

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const course = useQuery({
    queryKey: ['courses', id],
    queryFn: () => fetchAccessibleCourse(id ?? ''),
    enabled: !!id,
  })

  return (
    <section className="space-y-4">
      <Link to="/enrollment" className="text-sm font-medium text-teal-800 hover:underline">
        ← Khóa học của tôi
      </Link>
      {course.isFetching && <p role="status">Đang tải khóa học…</p>}
      {course.isError && <p role="alert">{accessCodeError(course.error, 'Không thể mở khóa học.')}</p>}
      {course.data && (
        <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-6">
          <h1 className="text-2xl font-bold text-slate-900">{course.data.title}</h1>
          {course.data.description && <p className="whitespace-pre-wrap text-slate-600">{course.data.description}</p>}
        </div>
      )}
    </section>
  )
}
