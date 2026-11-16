import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import type { NotificationResponse } from '../../types/api'
import { useCourseTasks } from './useCourseTasks'

export function DashboardPage() {
  const { user } = useAuth()
  const { tasks, progress } = useCourseTasks()
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
  })

  const active = progress.filter((p) => p.status === 'ACTIVE')
  const waitlisted = progress.filter((p) => p.status === 'WAITLISTED')
  const completed = progress.filter((p) => p.status === 'COMPLETED')
  const avg = active.length ? Math.round((active.reduce((s, p) => s + p.percent, 0) / active.length) * 10) / 10 : 0
  const todo = tasks.filter((t) => t.status === 'TODO').sort((a, b) => (a.deadline ?? '9999').localeCompare(b.deadline ?? '9999'))
  const unpaid = progress.filter((p) => p.paymentStatus === 'UNPAID' || p.paymentStatus === 'PARTIAL')
  const next = [...active].sort((a, b) => a.percent - b.percent).find((p) => p.percent < 100) ?? active[0]
  const unread = (notifications.data ?? []).filter((n) => !n.read)

  const stat = (label: string, value: string | number) => (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="text-2xl font-semibold text-slate-900">{value}</div>
    </div>
  )

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Xin chào, {user?.fullName ?? user?.email}</h1>
      <p className="mb-4 text-sm text-slate-500">Tổng quan việc học của bạn.</p>

      <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-4">
        {stat('Đang học', active.length)}
        {stat('Tiến độ trung bình', `${avg}%`)}
        {stat('Việc cần làm', todo.length)}
        {stat('Đã hoàn thành', completed.length)}
      </div>

      {waitlisted.length > 0 && (
        <p className="mb-4 rounded-md bg-blue-50 p-3 text-sm text-blue-700">
          Bạn đang trong danh sách chờ: {waitlisted.map((w) => w.courseTitle).join(', ')}.
        </p>
      )}
      {unpaid.length > 0 && (
        <p className="mb-4 rounded-md bg-amber-50 p-3 text-sm text-amber-800">
          Còn học phí chưa đóng đủ cho: {unpaid.map((u) => u.courseTitle).join(', ')}. <Link to="/student/tuition" className="underline">Xem chi tiết</Link>
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="mb-2 font-semibold text-slate-900">Tiếp tục học</h2>
          {next ? (
            <div>
              <div className="text-slate-900">{next.courseTitle}</div>
              <div className="mb-2 text-sm text-slate-500">{next.completedLessons}/{next.totalLessons} bài học ({next.percent}%)</div>
              <Link to={`/student/courses/${next.courseId}`} className="text-sm text-blue-600 hover:underline">Vào học</Link>
            </div>
          ) : (
            <p className="text-sm text-slate-400">Bạn chưa học khóa nào. <Link to="/student/explore" className="text-blue-600 hover:underline">Khám phá khóa học</Link></p>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="mb-2 font-semibold text-slate-900">Sắp đến hạn</h2>
          {todo.slice(0, 5).map((t) => (
            <div key={t.key} className="flex justify-between border-b border-slate-50 py-1.5 text-sm last:border-0">
              <Link to={`/student/courses/${t.courseId}`} className="text-slate-900 hover:underline">{t.title} <span className="text-slate-400">· {t.courseTitle}</span></Link>
              <span className="text-slate-500">{t.deadline ? new Date(t.deadline).toLocaleDateString('vi-VN') : 'Không hạn'}</span>
            </div>
          ))}
          {todo.length === 0 && <p className="text-sm text-slate-400">Không có việc nào cần làm.</p>}
          {todo.length > 0 && <Link to="/student/tasks" className="mt-2 inline-block text-sm text-blue-600 hover:underline">Xem tất cả</Link>}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2">
          <h2 className="mb-2 font-semibold text-slate-900">Thông báo chưa đọc ({unread.length})</h2>
          {unread.slice(0, 5).map((n) => (
            <div key={n.id} className="border-b border-slate-50 py-1.5 text-sm last:border-0">
              <span className="font-medium text-slate-900">{n.title}</span> <span className="text-slate-500">— {n.message}</span>
            </div>
          ))}
          {unread.length === 0 && <p className="text-sm text-slate-400">Không có thông báo mới.</p>}
          <Link to="/student/notifications" className="mt-2 inline-block text-sm text-blue-600 hover:underline">Tất cả thông báo</Link>
        </section>
      </div>
    </div>
  )
}
