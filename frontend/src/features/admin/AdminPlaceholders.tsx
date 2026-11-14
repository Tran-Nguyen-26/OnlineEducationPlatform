import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Settings, Users, UserCheck, BarChart3 } from 'lucide-react'
import CourseAnalyticsPage from '@/features/dashboard/CourseAnalyticsPage'
import { fetchAdminUserStats } from '@/api/adminUsers'
import { fetchInstructorApplicationStats } from '@/api/instructorApplications'

export function AdminDashboardPage() {
  const users = useQuery({ queryKey: ['admin-user-stats'], queryFn: fetchAdminUserStats })
  const applications = useQuery({ queryKey: ['instructorApplicationStats'], queryFn: fetchInstructorApplicationStats })

  const cards = [
    { title: 'Tổng tài khoản', value: users.data?.totalUsers, detail: `${users.data?.activeUsers ?? 0} đang hoạt động`, icon: Users },
    { title: 'Tài khoản bị khóa', value: users.data?.lockedUsers, detail: 'Cần rà soát quyền truy cập', icon: UserCheck },
    { title: 'Đơn Instructor chờ duyệt', value: applications.data?.pending, detail: `${applications.data?.total ?? 0} hồ sơ đã tiếp nhận`, icon: UserCheck },
  ]

  return <div className="space-y-8">
    <header>
      <h1 className="text-2xl font-bold text-slate-900">Tổng quan vận hành</h1>
      <p className="mt-1 text-sm text-slate-600">Tình hình tài khoản, hồ sơ Instructor và kết quả học tập.</p>
    </header>
    {(users.isError || applications.isError) && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">Không thể tải đầy đủ số liệu tổng quan. Hãy thử tải lại trang.</p>}
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
    <section aria-labelledby="admin-learning-analytics" className="space-y-4">
      <div className="flex items-center gap-2"><BarChart3 className="h-5 w-5 text-teal-800" /><h2 id="admin-learning-analytics" className="text-xl font-semibold text-slate-900">Phân tích học tập theo khóa</h2></div>
      <CourseAnalyticsPage admin />
    </section>
  </div>
}

export function AdminSettingsPage() {
  return (
    <div className="flex min-h-[400px] flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-12 text-center shadow-xs">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
        <Settings className="h-7 w-7" />
      </div>
      <h2 className="text-xl font-bold text-slate-900">Cài đặt Hệ thống</h2>
      <p className="mt-2 max-w-md text-sm text-slate-500">
        Thiết lập cấu hình chung và các tham số bảo mật của EduPlatform.
      </p>
    </div>
  )
}
