import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Settings, Users, UserCheck, BarChart3 } from 'lucide-react'
import CourseAnalyticsPage from '@/features/dashboard/CourseAnalyticsPage'
import { fetchAdminUserStats } from '@/api/adminUsers'
import { fetchInstructorApplicationStats } from '@/api/instructorApplications'
import { request, type TeachingCourse } from '@/api/quiz'

interface OperationsOverview {
  totalCourses: number
  totalEnrollments: number
  completedEnrollments: number
  databaseStatus: 'UP' | 'DOWN'
  redisStatus: 'UP' | 'DOWN'
  pendingOutboxEvents: number
  failedEmailDeliveries: number
}

export function AdminDashboardPage() {
  const cache = useQueryClient()
  const [disableCourseId, setDisableCourseId] = useState('')
  const [disableReason, setDisableReason] = useState('')
  const users = useQuery({ queryKey: ['admin-user-stats'], queryFn: fetchAdminUserStats })
  const applications = useQuery({ queryKey: ['instructorApplicationStats'], queryFn: fetchInstructorApplicationStats })
  const overview = useQuery({
    queryKey: ['admin-operations-overview'],
    queryFn: () => request<OperationsOverview>('/api/admin/overview'),
  })
  const courses = useQuery({
    queryKey: ['admin-overview-courses'],
    queryFn: async () => (await request<{ items: TeachingCourse[] }>('/api/v1/my/courses?page=1&page_size=100')).items,
  })
  const disable = useMutation({
    mutationFn: (input: { courseId: string; reason: string }) => request<void>(`/api/v1/courses/${input.courseId}/disable`, {
      method: 'POST', body: JSON.stringify({ reason: input.reason }),
    }),
    onSuccess: async () => {
      setDisableCourseId('')
      setDisableReason('')
      await Promise.all([
        cache.invalidateQueries({ queryKey: ['admin-operations-overview'] }),
        cache.invalidateQueries({ queryKey: ['admin-overview-courses'] }),
        cache.invalidateQueries({ queryKey: ['course-builder'] }),
      ])
    },
  })

  const cards = [
    { title: 'Tổng tài khoản', value: users.data?.totalUsers, detail: `${users.data?.activeUsers ?? 0} đang hoạt động`, icon: Users },
    { title: 'Tài khoản bị khóa', value: users.data?.lockedUsers, detail: 'Cần rà soát quyền truy cập', icon: UserCheck },
    { title: 'Đơn Instructor chờ duyệt', value: applications.data?.pending, detail: `${applications.data?.total ?? 0} hồ sơ đã tiếp nhận`, icon: UserCheck },
    { title: 'Tổng khóa học', value: overview.data?.totalCourses, detail: 'Tất cả trạng thái', icon: BarChart3 },
    { title: 'Lượt ghi danh', value: overview.data?.totalEnrollments, detail: `${overview.data?.completedEnrollments ?? 0} đã hoàn thành`, icon: BarChart3 },
  ]

  return <div className="space-y-8">
    <header>
      <h1 className="text-2xl font-bold text-slate-900">Tổng quan vận hành</h1>
      <p className="mt-1 text-sm text-slate-600">Tình hình tài khoản, hồ sơ Instructor và kết quả học tập.</p>
    </header>
    {(users.isError || applications.isError || overview.isError) && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">Không thể tải đầy đủ số liệu tổng quan. Hãy thử tải lại trang.</p>}
    <section aria-label="Chỉ số vận hành" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {cards.map(({ title, value, detail, icon: Icon }) => <article key={title} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-sm font-medium text-slate-600">{title}</h2>
            <p className="mt-2 text-3xl font-bold text-slate-900" aria-live="polite">{value == null ? (users.isPending || applications.isPending ? '…' : '—') : value.toLocaleString('vi-VN')}</p>
            <p className="mt-1 text-xs text-slate-500">{detail}</p>
          </div>
          <span className="rounded-lg bg-teal-50 p-2 text-teal-800"><Icon aria-hidden="true" className="h-5 w-5" /></span>
        </div>
      </article>)}
    </section>
    <nav aria-label="Tác vụ quản trị" className="flex flex-wrap gap-3">
      <Link to="/admin/users" className="inline-flex items-center gap-2 rounded-lg bg-teal-800 px-4 py-2 text-sm font-medium text-white hover:bg-teal-900"><Users className="h-4 w-4" />Quản lý tài khoản</Link>
      <Link to="/admin/instructor-applications" className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"><UserCheck className="h-4 w-4" />Xử lý hồ sơ Instructor</Link>
    </nav>
    {overview.data && <section aria-labelledby="admin-system-health" className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
      <div><h2 id="admin-system-health" className="text-lg font-semibold text-slate-900">Sức khỏe dịch vụ</h2><p className="mt-1 text-sm text-slate-600">Trạng thái kiểm tra kết nối và lượng việc chờ xử lý từ lần tải này.</p></div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[['PostgreSQL', overview.data.databaseStatus], ['Redis', overview.data.redisStatus]].map(([label, status]) => <div key={label} className="rounded-lg border p-3">
          <p className="text-xs text-slate-500">{label}</p><p className={`mt-1 font-semibold ${status === 'UP' ? 'text-emerald-700' : 'text-rose-700'}`}>{status === 'UP' ? 'Hoạt động' : 'Không kết nối'}</p>
        </div>)}
        <div className="rounded-lg border p-3"><p className="text-xs text-slate-500">Outbox đang chờ</p><p className="mt-1 font-semibold text-slate-900">{overview.data.pendingOutboxEvents.toLocaleString('vi-VN')}</p></div>
        <div className="rounded-lg border p-3"><p className="text-xs text-slate-500">Email gửi lỗi</p><p className={`mt-1 font-semibold ${overview.data.failedEmailDeliveries ? 'text-rose-700' : 'text-slate-900'}`}>{overview.data.failedEmailDeliveries.toLocaleString('vi-VN')}</p></div>
      </div>
    </section>}
    <section aria-labelledby="admin-course-governance" className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
      <div>
        <h2 id="admin-course-governance" className="text-lg font-semibold text-slate-900">Khóa học toàn trường</h2>
        <p className="mt-1 text-sm text-slate-600">Admin có thể vô hiệu hóa khóa học; thao tác yêu cầu lý do và được ghi vào audit trail.</p>
      </div>
      {courses.isPending && <p role="status" className="text-sm text-slate-500">Đang tải khóa học…</p>}
      {courses.isError && <p role="alert" className="text-sm text-rose-700">Không tải được danh sách khóa học.</p>}
      {courses.data?.length === 0 && <p className="text-sm text-slate-500">Chưa có khóa học.</p>}
      <ul className="divide-y divide-slate-100">
        {courses.data?.map((course) => <li key={course.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
          <div><p className="font-medium text-slate-800">{course.title}</p><p className="text-xs text-slate-500">{course.lifecycle_status}</p></div>
          {course.lifecycle_status === 'PUBLISHED' || course.lifecycle_status === 'DRAFT' ? <button type="button" onClick={() => { setDisableCourseId(course.id); setDisableReason(''); disable.reset() }} className="rounded-lg border border-rose-300 px-3 py-2 text-sm font-medium text-rose-700 hover:bg-rose-50">Vô hiệu hóa</button> :
            <span className="text-xs text-slate-500">Không có thao tác khóa</span>}
        </li>)}
      </ul>
      {disableCourseId && <form onSubmit={(event) => { event.preventDefault(); if (disableReason.trim()) disable.mutate({ courseId: disableCourseId, reason: disableReason.trim() }) }} className="space-y-3 rounded-lg border border-rose-200 bg-rose-50/50 p-4">
        <h3 className="font-semibold text-slate-900">Xác nhận vô hiệu hóa</h3>
        <label className="grid gap-1 text-sm">Lý do bắt buộc
          <textarea required minLength={3} maxLength={1000} value={disableReason} onChange={(event) => setDisableReason(event.target.value)} rows={3} className="rounded-lg border bg-white px-3 py-2" />
        </label>
        {disable.isError && <p role="alert" className="text-sm text-rose-700">{disable.error.message}</p>}
        <div className="flex justify-end gap-2"><button type="button" onClick={() => setDisableCourseId('')} className="rounded-lg border bg-white px-3 py-2 text-sm">Hủy</button><button type="submit" disabled={disable.isPending || !disableReason.trim()} className="rounded-lg bg-rose-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{disable.isPending ? 'Đang lưu…' : 'Vô hiệu hóa khóa học'}</button></div>
      </form>}
    </section>
    <section aria-labelledby="admin-learning-analytics" className="space-y-4">
      <div className="flex items-center gap-2"><BarChart3 className="h-5 w-5 text-teal-800" /><h2 id="admin-learning-analytics" className="text-xl font-semibold text-slate-900">Phân tích học tập theo khóa</h2></div>
      <CourseAnalyticsPage admin />
    </section>
  </div>
}

export function AdminSettingsPage() {
  const cache = useQueryClient()
  const [reason, setReason] = useState('')
  const setting = useQuery({
    queryKey: ['admin-exam-panic'],
    queryFn: () => request<{ enabled: boolean; reason: string; updatedBy: string | null; updatedAt: string }>('/api/admin/settings/exam-panic'),
  })
  const update = useMutation({
    mutationFn: (enabled: boolean) => request('/api/admin/settings/exam-panic', {
      method: 'PUT', body: JSON.stringify({ enabled, reason: reason.trim() }),
    }),
    onSuccess: async () => {
      setReason('')
      await cache.invalidateQueries({ queryKey: ['admin-exam-panic'] })
      await cache.invalidateQueries({ queryKey: ['available-quizzes'] })
    },
  })
  return <div className="mx-auto max-w-3xl space-y-5">
    <header><p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Quản trị · Hệ thống</p><h1 className="mt-1 text-2xl font-bold text-slate-900">Cài đặt và an toàn kỳ thi</h1><p className="mt-2 text-sm text-slate-600">Tạm dừng ca thi mới cùng các dịch vụ AI và phân tích phụ trợ khi hệ thống gặp áp lực. Học viên đang thi vẫn được lưu và nộp bài.</p></header>
    {setting.isPending && <p role="status" className="rounded-lg bg-white p-4 text-sm text-slate-600">Đang tải cấu hình…</p>}
    {setting.isError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{setting.error.message}</p>}
    {setting.data && <section className={`space-y-4 rounded-xl border bg-white p-5 ${setting.data.enabled ? 'border-rose-300' : 'border-slate-200'}`}>
      <div className="flex items-start gap-3"><div className={`rounded-lg p-2 ${setting.data.enabled ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'}`}><Settings className="h-5 w-5" /></div><div><h2 className="font-semibold text-slate-900">Exam Panic Circuit Breaker</h2><p className={`mt-1 text-sm font-medium ${setting.data.enabled ? 'text-rose-700' : 'text-emerald-700'}`}>{setting.data.enabled ? 'ĐANG BẬT · Tạm dừng ca thi mới, AI và analytics' : 'ĐANG TẮT · Hoạt động bình thường'}</p><p className="mt-1 text-sm text-slate-600">Khi bật: lượt thi đang diễn ra vẫn tiếp tục; ca thi mới, AI gia sư/sinh câu hỏi/giải thích và API analytics bị tạm dừng.</p></div></div>
      {setting.data.updatedAt && <p className="text-xs text-slate-500">Cập nhật {new Date(setting.data.updatedAt).toLocaleString('vi-VN')}{setting.data.updatedBy ? ` bởi ${setting.data.updatedBy}` : ''}{setting.data.reason ? ` · ${setting.data.reason}` : ''}</p>}
      <label className="grid gap-1 text-sm">Lý do thay đổi (bắt buộc)
        <textarea required minLength={3} maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} rows={3} className="rounded-lg border px-3 py-2" />
      </label>
      {update.isError && <p role="alert" className="text-sm text-rose-700">{update.error.message}</p>}
      {update.isSuccess && <p role="status" className="text-sm text-emerald-700">Đã cập nhật cài đặt và ghi audit log.</p>}
      <button type="button" disabled={update.isPending || reason.trim().length < 3} onClick={() => update.mutate(!setting.data.enabled)} className={`rounded-lg px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50 ${setting.data.enabled ? 'bg-emerald-700 hover:bg-emerald-800' : 'bg-rose-700 hover:bg-rose-800'}`}>
        {update.isPending ? 'Đang lưu…' : setting.data.enabled ? 'Tắt và mở lại ca thi mới' : 'Bật chế độ dừng ca thi'}
      </button>
    </section>}
  </div>
}
