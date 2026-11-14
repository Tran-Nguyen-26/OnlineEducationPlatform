import axios from 'axios'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

interface DashboardSummary {
  activeCourses: number
  completedCourses: number
  completedLessons: number
  studyHours: number
  courses: {
    courseId: string
    courseTitle: string
    completedLessons: number
    totalLessons: number
    lastAccessedAt: string | null
    accessState: 'ACTIVE' | 'ARCHIVED_STATIC'
  }[]
  recentActivity: {
    courseId: string
    courseTitle: string
    lessonTitle: string
    status: 'IN_PROGRESS' | 'COMPLETED'
    occurredAt: string
  }[]
}

function useSummary() {
  return useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: async () => {
      const { data } = await axios.get<DashboardSummary>('/api/dashboard/summary', {
        headers: { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` },
      })
      return data
    },
  })
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa bắt đầu học'
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

const statCards = (summary: DashboardSummary) => [
  { label: 'Khóa học đang học', value: summary.activeCourses, icon: '📚', color: 'from-blue-500 to-blue-600' },
  { label: 'Khóa học hoàn thành', value: summary.completedCourses, icon: '✅', color: 'from-green-500 to-green-600' },
  { label: 'Bài học hoàn thành', value: summary.completedLessons, icon: '🎯', color: 'from-orange-500 to-orange-600' },
  { label: 'Giờ học tích lũy', value: summary.studyHours, icon: '⏱', color: 'from-purple-500 to-purple-600' },
]

export default function DashboardPage() {
  const { data, isPending, isError } = useSummary()

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Tổng quan</h1>
        <p className="mt-1 text-sm text-gray-500">Tiến độ và hoạt động học tập của bạn.</p>
      </header>

      {isPending && <div role="status" className="h-28 animate-pulse rounded-xl bg-gray-200" />}
      {isError && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">Không tải được tổng quan học tập. Vui lòng thử tải lại trang.</p>}

      {data && <>
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {statCards(data).map((card) => (
            <div key={card.label} className={`flex min-h-32 flex-col justify-between rounded-xl bg-gradient-to-br p-5 text-white shadow-sm ${card.color}`}>
              <div className="text-2xl" aria-hidden="true">{card.icon}</div>
              <div>
                <div className="text-3xl font-bold">{card.value.toLocaleString('vi-VN')}</div>
                <div className="mt-1 text-sm opacity-90">{card.label}</div>
              </div>
            </div>
          ))}
        </div>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <div className="mb-4 flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-gray-900">Tiếp tục học</h2>
            <Link to="/enrollments" className="text-sm font-medium text-teal-700 hover:underline">Tất cả khóa học</Link>
          </div>
          {data.courses.length === 0 ? (
            <p className="text-sm text-gray-600">Bạn chưa có khóa học. Nhập mã kích hoạt để bắt đầu.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {data.courses.map((course) => {
                const progress = course.totalLessons > 0 ? Math.round(course.completedLessons * 100 / course.totalLessons) : 0
                return <li key={course.courseId} className="py-4 first:pt-0 last:pb-0">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      {course.accessState === 'ACTIVE'
                        ? <Link to={`/courses/${course.courseId}`} className="font-semibold text-gray-900 hover:text-teal-800">{course.courseTitle}</Link>
                        : <h3 className="font-semibold text-gray-900">{course.courseTitle} <span className="text-xs font-normal text-amber-700">(lưu trữ)</span></h3>}
                      <p className="mt-1 text-xs text-gray-500">{course.completedLessons} / {course.totalLessons} bài học · Truy cập gần nhất: {formatDate(course.lastAccessedAt)}</p>
                    </div>
                    <span className="text-sm font-medium text-teal-800">{progress}%</span>
                  </div>
                  <div className="mt-3 h-2 overflow-hidden rounded-full bg-gray-100" role="progressbar" aria-label={`Tiến độ ${course.courseTitle}`} aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
                    <div className="h-full rounded-full bg-teal-600" style={{ width: `${progress}%` }} />
                  </div>
                </li>
              })}
            </ul>
          )}
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-gray-900">Hoạt động gần đây</h2>
          {data.recentActivity.length === 0 ? <p className="text-sm text-gray-600">Hoạt động học tập sẽ xuất hiện tại đây khi bạn bắt đầu một bài học.</p> :
            <ul className="divide-y divide-gray-50">
              {data.recentActivity.map((item, index) => <li key={`${item.courseId}-${item.lessonTitle}-${item.occurredAt}-${index}`} className="flex items-start gap-3 py-3">
                <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-gray-100 text-lg" aria-hidden="true">{item.status === 'COMPLETED' ? '✅' : '📖'}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-gray-700">{item.status === 'COMPLETED' ? 'Hoàn thành' : 'Đang học'} bài “{item.lessonTitle}”</p>
                  <p className="truncate text-xs text-gray-500">{item.courseTitle}</p>
                </div>
                <time className="flex-shrink-0 text-xs text-gray-400" dateTime={item.occurredAt}>{formatDate(item.occurredAt)}</time>
              </li>)}
            </ul>}
        </section>
      </>}
    </div>
  )
}
