import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import type { CourseContentResponse } from '../../types/api'
import { cn } from '../../lib/cn'
import { CourseQaTab } from '../../components/CourseQaTab'
import { ContentTab } from './course-view/ContentTab'
import { GradingTab } from './course-view/GradingTab'

const TABS = [
  { key: 'content', label: 'Nội dung' },
  { key: 'grading', label: 'Chấm điểm' },
  { key: 'qa', label: 'Hỏi đáp' },
] as const

type TabKey = (typeof TABS)[number]['key']

export function CourseViewPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [tab, setTab] = useState<TabKey>('content')

  const contentQuery = useQuery({
    queryKey: ['teacher', 'courses', courseId, 'content'],
    queryFn: () => apiClient.get<CourseContentResponse>(`/api/courses/${courseId}/content`).then((r) => r.data),
  })

  if (!courseId) return null
  if (contentQuery.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (contentQuery.isError) {
    return <p className="text-red-600">Không thể tải khóa học — bạn có thể chưa được gán dạy khóa này.</p>
  }

  return (
    <div>
      <Link to="/teacher" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách khóa học
      </Link>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">{contentQuery.data?.title}</h1>

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

      {tab === 'content' && <ContentTab content={contentQuery.data!} />}
      {tab === 'grading' && <GradingTab courseId={courseId} />}
      {tab === 'qa' && <CourseQaTab courseId={courseId} />}
    </div>
  )
}
