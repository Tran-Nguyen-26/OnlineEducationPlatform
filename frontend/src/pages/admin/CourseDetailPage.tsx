import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import type { CourseResponse } from '../../types/api'
import { Badge } from '../../components/Badge'
import { cn } from '../../lib/cn'
import { OverviewTab } from './course-detail/OverviewTab'
import { ContentTab } from './course-detail/ContentTab'
import { QuizzesTab } from './course-detail/QuizzesTab'
import { AssignmentsTab } from './course-detail/AssignmentsTab'

const TABS = [
  { key: 'overview', label: 'Tổng quan' },
  { key: 'content', label: 'Nội dung' },
  { key: 'quizzes', label: 'Quiz' },
  { key: 'assignments', label: 'Bài tập' },
] as const

type TabKey = (typeof TABS)[number]['key']

export function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [tab, setTab] = useState<TabKey>('overview')

  const courseQuery = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => apiClient.get<CourseResponse>(`/api/admin/courses/${courseId}`).then((r) => r.data),
  })

  const course = courseQuery.data
  if (courseQuery.isLoading || !course || !courseId) {
    return <p className="text-slate-400">Đang tải...</p>
  }

  return (
    <div>
      <Link to="/admin/courses" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách khóa học
      </Link>

      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{course.title}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {course.subjectName} · Tạo bởi {course.createdByEmail}
          </p>
          {course.description && <p className="mt-2 max-w-2xl text-sm text-slate-600">{course.description}</p>}
        </div>
        <Badge status={course.lifecycleStatus} />
      </div>

      <div className="mb-6 flex gap-1 border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'px-4 py-2 text-sm font-medium border-b-2 -mb-px',
              tab === t.key ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-700',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab course={course} />}
      {tab === 'content' && <ContentTab courseId={courseId} />}
      {tab === 'quizzes' && <QuizzesTab courseId={courseId} />}
      {tab === 'assignments' && <AssignmentsTab courseId={courseId} />}
    </div>
  )
}
