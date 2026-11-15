import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { AdminUserResponse, CourseResponse, SubjectResponse } from '../../types/api'

function StatCard({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="text-sm font-medium text-slate-500">{label}</div>
      <div className="mt-2 text-3xl font-bold text-slate-900">{value ?? '—'}</div>
    </div>
  )
}

export function DashboardPage() {
  const subjects = useQuery({
    queryKey: ['subjects'],
    queryFn: () => apiClient.get<SubjectResponse[]>('/api/admin/subjects').then((r) => r.data),
  })
  const courses = useQuery({
    queryKey: ['courses'],
    queryFn: () => apiClient.get<CourseResponse[]>('/api/admin/courses').then((r) => r.data),
  })
  const users = useQuery({
    queryKey: ['users'],
    queryFn: () => apiClient.get<AdminUserResponse[]>('/api/admin/users').then((r) => r.data),
  })

  const publishedCourses = courses.data?.filter((c) => c.lifecycleStatus === 'PUBLISHED').length
  const teachers = users.data?.filter((u) => u.role === 'INSTRUCTOR').length
  const students = users.data?.filter((u) => u.role === 'STUDENT').length

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Tổng quan</h1>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Môn học" value={subjects.data?.length} />
        <StatCard label="Khóa học đã publish" value={publishedCourses} />
        <StatCard label="Giáo viên" value={teachers} />
        <StatCard label="Học sinh" value={students} />
      </div>
    </div>
  )
}
