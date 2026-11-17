import { BookOpen, ClipboardCheck, Clock, Users } from 'lucide-react'
import { PageHeader, StatCard } from '../../components/ui/Page'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import type { PendingGradingResponse, TeacherDashboardResponse } from '../../types/api'
import { Badge } from '../../components/Badge'

export function TeacherDashboardPage() {
  const { user } = useAuth()
  const dash = useQuery({
    queryKey: ['teacher', 'dashboard'],
    queryFn: () => apiClient.get<TeacherDashboardResponse>('/api/instructors/me/dashboard').then((r) => r.data),
  })
  const pending = useQuery({
    queryKey: ['teacher', 'pending-grading'],
    queryFn: () => apiClient.get<PendingGradingResponse[]>('/api/gradebook/pending-grading').then((r) => r.data),
  })
  const d = dash.data
  const waiting = pending.data ?? []

  const stat = (label: string, value: number | string, icon: typeof BookOpen, tone: 'indigo' | 'teal' | 'amber' | 'rose') => (
    <StatCard label={label} value={value} icon={icon} tone={tone} />
  )

  return (
    <div>
      <PageHeader title={`Xin chào, ${user?.fullName ?? user?.email}`} subtitle="Tổng quan các khóa bạn đang phụ trách." />

      <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-4">
        {stat('Khóa học phụ trách', d?.courseCount ?? '—', BookOpen, 'indigo')}
        {stat('Học sinh đang học', d?.activeStudents ?? '—', Users, 'teal')}
        {stat('Bài chờ chấm', waiting.length, ClipboardCheck, 'amber')}
        {stat('Bài nộp muộn chờ chấm', waiting.filter((w) => w.late).length, Clock, 'rose')}
      </div>

      <section className="mb-6 rounded-lg border border-slate-200 bg-white p-4">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">Bài nộp cần chấm (cũ nhất trước)</h2>
          <Link to="/teacher/grading" className="text-sm text-blue-600 hover:underline">Xem tất cả</Link>
        </div>
        {waiting.slice(0, 5).map((w) => (
          <div key={w.submissionId} className="flex justify-between border-b border-slate-50 py-1.5 text-sm last:border-0">
            <Link to={`/teacher/courses/${w.courseId}?tab=grading`} className="text-slate-900 hover:underline">
              {w.studentName} — {w.assignmentTitle} <span className="text-slate-400">· {w.courseTitle}</span>
            </Link>
            <span className="text-slate-500">{new Date(w.submittedAt).toLocaleString('vi-VN')}</span>
          </div>
        ))}
        {waiting.length === 0 && <p className="text-sm text-slate-400">Không có bài nào đang chờ chấm.</p>}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white">
        <h2 className="px-4 pt-4 font-semibold text-slate-900">Khóa học của tôi</h2>
        <table className="mt-2 w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Khóa học</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Đang học</th>
              <th className="px-4 py-3 font-medium">Hoàn thành</th>
              <th className="px-4 py-3 font-medium">Danh sách chờ</th>
              <th className="px-4 py-3 font-medium">Sĩ số tối đa</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {d?.courses.map((c) => (
              <tr key={c.courseId}>
                <td className="px-4 py-3">
                  <Link to={`/teacher/courses/${c.courseId}`} className="font-medium text-slate-900 hover:underline">{c.title}</Link>
                  <div className="text-xs text-slate-400">{c.subjectName}</div>
                </td>
                <td className="px-4 py-3"><Badge status={c.lifecycleStatus} /></td>
                <td className="px-4 py-3">{c.activeStudents}</td>
                <td className="px-4 py-3">{c.completedStudents}</td>
                <td className="px-4 py-3">{c.waitlistedStudents}</td>
                <td className="px-4 py-3">{c.capacity ?? '—'}</td>
              </tr>
            ))}
            {d?.courses.length === 0 && (
              <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Bạn chưa được gán dạy khóa học nào.</td></tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  )
}
