import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import type { NotificationResponse } from '../../types/api'
import { useCourseTasks } from './useCourseTasks'
import { Award, Bell, BookOpen, CalendarClock, ClipboardList, PlayCircle, TrendingUp } from 'lucide-react'
import { TiltCard } from '../../components/ui/TiltCard'
import { FloatingElement } from '../../components/ui/FloatingElement'
import { AnimatedSection } from '../../components/ui/AnimatedSection'
import { ProgressRing } from '../../components/ui/ProgressRing'

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

  const stat = (label: string, value: string | number, Icon: typeof BookOpen, tone: string) => (
    <TiltCard max={5} className="h-full">
      <div className="surface flex h-full items-center gap-3 p-4">
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg ${tone}`}><Icon size={20} /></span>
        <div><div className="text-2xl leading-none font-extrabold text-slate-900">{value}</div><div className="mt-1 text-xs text-slate-500">{label}</div></div>
      </div>
    </TiltCard>
  )

  return (
    <div className="mx-auto max-w-6xl">
      <AnimatedSection>
        <div className="bg-mesh-dark relative overflow-hidden rounded-3xl p-6 text-white shadow-depth sm:p-8">
          <FloatingElement slow distance={10} className="pointer-events-none absolute -top-8 -right-8 h-44 w-44 rounded-full bg-violet-400/30 blur-2xl"><div className="h-full w-full" /></FloatingElement>
          <div className="relative flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <p className="text-sm font-medium text-indigo-200">Tổng quan việc học của bạn</p>
              <h1 className="mt-1 text-2xl font-extrabold tracking-tight sm:text-3xl">Xin chào, {user?.fullName ?? user?.email} 👋</h1>
              {next ? (
                <div className="mt-5 inline-flex max-w-full flex-wrap items-center gap-4 rounded-2xl bg-white/10 p-3 pr-4 ring-1 ring-white/15 backdrop-blur">
                  <ProgressRing value={next.percent} size={56} stroke={6} className="[&_div]:text-white" />
                  <div className="min-w-0">
                    <div className="text-[11px] font-semibold tracking-wide text-indigo-200 uppercase">Tiếp tục học</div>
                    <div className="truncate font-bold">{next.courseTitle}</div>
                    <div className="text-xs text-indigo-200">{next.completedLessons}/{next.totalLessons} bài học</div>
                  </div>
                  <Link to={`/student/courses/${next.courseId}`} className="inline-flex h-10 items-center gap-1.5 rounded-xl bg-white px-4 text-sm font-semibold text-indigo-700 shadow-lg transition hover:bg-indigo-50"><PlayCircle size={16} /> Vào học</Link>
                </div>
              ) : (
                <p className="mt-4 text-sm text-indigo-100">Bạn chưa học khóa nào. <Link to="/student/explore" className="font-semibold underline">Khám phá khóa học</Link></p>
              )}
            </div>
            <div className="hidden shrink-0 text-center sm:block">
              <ProgressRing value={avg} size={104} stroke={10} className="[&_div]:text-xl [&_div]:text-white" />
              <div className="mt-2 text-xs text-indigo-200">Tiến độ trung bình</div>
            </div>
          </div>
        </div>
      </AnimatedSection>

      <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {stat('Đang học', active.length, BookOpen, 'from-indigo-500 to-violet-500')}
        {stat('Tiến độ trung bình', `${avg}%`, TrendingUp, 'from-teal-500 to-emerald-500')}
        {stat('Việc cần làm', todo.length, ClipboardList, 'from-amber-400 to-orange-500')}
        {stat('Đã hoàn thành', completed.length, Award, 'from-sky-500 to-indigo-500')}
      </div>

      {waitlisted.length > 0 && (
        <p className="mt-4 rounded-xl bg-blue-50 p-3 text-sm text-blue-700 ring-1 ring-blue-100">
          Bạn đang trong danh sách chờ: {waitlisted.map((w) => w.courseTitle).join(', ')}.
        </p>
      )}
      {unpaid.length > 0 && (
        <p className="mt-4 rounded-xl bg-amber-50 p-3 text-sm text-amber-800 ring-1 ring-amber-100">
          Còn học phí chưa đóng đủ cho: {unpaid.map((u) => u.courseTitle).join(', ')}. <Link to="/student/tuition" className="font-semibold underline">Xem chi tiết</Link>
        </p>
      )}

      <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2">
        <AnimatedSection>
          <section className="surface h-full p-5">
            <h2 className="mb-3 flex items-center gap-2 font-bold text-slate-900"><CalendarClock size={17} className="text-indigo-600" /> Sắp đến hạn</h2>
            {todo.slice(0, 5).map((t) => (
              <div key={t.key} className="flex justify-between gap-3 border-b border-slate-100 py-2 text-sm last:border-0">
                <Link to={`/student/courses/${t.courseId}`} className="min-w-0 truncate text-slate-900 hover:text-indigo-600">{t.title} <span className="text-slate-400">· {t.courseTitle}</span></Link>
                <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{t.deadline ? new Date(t.deadline).toLocaleDateString('vi-VN') : 'Không hạn'}</span>
              </div>
            ))}
            {todo.length === 0 && <p className="text-sm text-slate-400">Không có việc nào cần làm. 🎉</p>}
            {todo.length > 0 && <Link to="/student/tasks" className="mt-3 inline-block text-sm font-semibold text-indigo-600 hover:underline">Xem tất cả</Link>}
          </section>
        </AnimatedSection>

        <AnimatedSection delay={90}>
          <section className="surface h-full p-5">
            <h2 className="mb-3 flex items-center gap-2 font-bold text-slate-900"><Bell size={17} className="text-indigo-600" /> Thông báo chưa đọc ({unread.length})</h2>
            {unread.slice(0, 5).map((n) => (
              <div key={n.id} className="border-b border-slate-100 py-2 text-sm last:border-0">
                <span className="font-semibold text-slate-900">{n.title}</span> <span className="text-slate-500">— {n.message}</span>
              </div>
            ))}
            {unread.length === 0 && <p className="text-sm text-slate-400">Không có thông báo mới.</p>}
            <Link to="/student/notifications" className="mt-3 inline-block text-sm font-semibold text-indigo-600 hover:underline">Tất cả thông báo</Link>
          </section>
        </AnimatedSection>
      </div>
    </div>
  )
}
