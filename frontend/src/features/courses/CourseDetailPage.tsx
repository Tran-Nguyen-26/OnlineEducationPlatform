import axios from 'axios'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import LessonPlayer from './LessonPlayer'
import OutcomeMasteryPanel from './OutcomeMasteryPanel'
import GraduationPanel from './GraduationPanel'
import AssignmentPanel from './AssignmentPanel'
import { fetchEnrollments } from '@/api/accessCodes'

interface Course {
  id: string
  title: string
  description: string | null
  lifecycle_status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'DISABLED'
}

function authHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
}

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const course = useQuery({
    queryKey: ['course-detail', id],
    enabled: !!id,
    queryFn: async ({ signal }) => {
      const response = await axios.get<Course>(`/api/v1/courses/${encodeURIComponent(id!)}`, {
        headers: authHeaders(), signal,
      })
      return response.data
    },
  })
  const enrollments = useQuery({ queryKey: ['enrollments'], queryFn: fetchEnrollments })

  if (course.isPending) return <p role="status" className="rounded-xl bg-white p-6 text-slate-600">Đang tải khóa học…</p>
  if (course.isError || !course.data) {
    const status = axios.isAxiosError(course.error) ? course.error.response?.status : undefined
    const message = status === 403 || status === 404
      ? 'Khóa học không tồn tại hoặc tài khoản của bạn chưa được cấp quyền truy cập.'
      : 'Không tải được khóa học. Vui lòng thử lại.'
    return <section className="space-y-3 rounded-xl border border-rose-200 bg-white p-6">
      <p role="alert" className="text-rose-800">{message}</p>
      <Link to="/enrollment" className="font-medium text-teal-800 hover:underline">Quay lại khóa học của tôi</Link>
    </section>
  }

  const enrollment = enrollments.data?.find((item) => item.courseId === course.data.id)
  const hasActiveAccess = enrollment?.accessState === 'ACTIVE'
  const canReadProgress = enrollment?.accessState === 'ACTIVE' || enrollment?.accessState === 'ARCHIVED_STATIC'

  return <div className="space-y-5">
    <header className="rounded-xl border border-slate-200 bg-white p-5">
      <Link to="/enrollment" className="text-sm font-medium text-teal-800 hover:underline">← Khóa học của tôi</Link>
      <h1 className="mt-3 text-2xl font-bold text-slate-900">{course.data.title}</h1>
      {course.data.description && <p className="mt-2 whitespace-pre-wrap text-slate-600">{course.data.description}</p>}
      {course.data.lifecycle_status === 'ARCHIVED' && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
        Khóa học đã lưu trữ. Bạn vẫn có thể xem nội dung theo quyền truy cập hiện có.
      </p>}
    </header>
    <LessonPlayer courseId={course.data.id} />
    {enrollments.isError && <p role="alert" className="text-sm text-rose-700">Không tải được trạng thái quyền học để hiển thị bài tập và kết quả khóa học.</p>}
    {canReadProgress && <OutcomeMasteryPanel courseId={course.data.id}
      activeAccess={hasActiveAccess && course.data.lifecycle_status === 'PUBLISHED'} />}
    {hasActiveAccess && course.data.lifecycle_status === 'PUBLISHED' && <>
      <AssignmentPanel courseId={course.data.id} />
      <GraduationPanel courseId={course.data.id} />
    </>}
  </div>
}
