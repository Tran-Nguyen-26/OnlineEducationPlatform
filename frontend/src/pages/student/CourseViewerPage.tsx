import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import type { CourseOutlineResponse } from '../../types/api'
import { cn } from '../../lib/cn'
import { ContentTab } from './course-viewer/ContentTab'
import { QuizzesTab } from './course-viewer/QuizzesTab'
import { AssignmentsTab } from './course-viewer/AssignmentsTab'

const TABS = [
  { key: 'content', label: 'Nội dung' },
  { key: 'quizzes', label: 'Quiz' },
  { key: 'assignments', label: 'Bài tập' },
] as const

type TabKey = (typeof TABS)[number]['key']

export function CourseViewerPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [tab, setTab] = useState<TabKey>('content')

  const outlineQuery = useQuery({
    queryKey: ['student', 'courses', courseId, 'outline'],
    queryFn: () => apiClient.get<CourseOutlineResponse>(`/api/courses/${courseId}/outline`).then((r) => r.data),
  })

  if (!courseId) return null;
  if (outlineQuery.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (outlineQuery.isError) {
    return <p className="text-red-600">Không thể tải khóa học — bạn có thể chưa ghi danh khóa này.</p>
  }

  return (
    <div>
      <Link to="/student" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách khóa học
      </Link>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">{outlineQuery.data?.title}</h1>

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

      {tab === 'content' && <ContentTab courseId={courseId} outline={outlineQuery.data!} />}
      {tab === 'quizzes' && <QuizzesTab courseId={courseId} />}
      {tab === 'assignments' && <AssignmentsTab courseId={courseId} />}
    </div>
  )
}
