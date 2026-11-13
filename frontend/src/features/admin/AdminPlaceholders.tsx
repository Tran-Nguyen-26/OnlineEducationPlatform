import { Settings } from 'lucide-react'
import CourseAnalyticsPage from '@/features/dashboard/CourseAnalyticsPage'

export function AdminDashboardPage() {
  return <CourseAnalyticsPage admin />
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
