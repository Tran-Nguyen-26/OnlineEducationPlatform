import { TabBar } from '../../components/ui/Page'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import type { CourseContentResponse } from '../../types/api'
import { CourseQaTab } from '../../components/CourseQaTab'
import { ContentTab } from './course-view/ContentTab'
import { GradingTab } from './course-view/GradingTab'
import { RosterTab } from './course-view/RosterTab'
import { GradebookView } from '../../components/GradebookView'
import { SessionsPanel } from '../../components/SessionsPanel'
import { AnnouncementsPanel } from '../../components/AnnouncementsPanel'

const TABS = [
  { key: 'content', label: 'Nội dung' },
  { key: 'students', label: 'Học sinh' },
  { key: 'grading', label: 'Chấm bài tập' },
  { key: 'gradebook', label: 'Bảng điểm' },
  { key: 'sessions', label: 'Lịch học & điểm danh' },
  { key: 'announcements', label: 'Thông báo' },
  { key: 'qa', label: 'Hỏi đáp' },
] as const

type TabKey = (typeof TABS)[number]['key']

export function CourseViewPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [searchParams] = useSearchParams()
  const requested = TABS.find((t) => t.key === searchParams.get('tab'))?.key
  const [tab, setTab] = useState<TabKey>(requested ?? 'content')

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
      <Link to="/teacher/courses" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách khóa học
      </Link>
      <h1 className="mb-6 text-2xl font-extrabold tracking-tight text-slate-900">{contentQuery.data?.title}</h1>

      <TabBar tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'content' && <ContentTab content={contentQuery.data!} />}
      {tab === 'students' && <RosterTab courseId={courseId} />}
      {tab === 'grading' && <GradingTab courseId={courseId} />}
      {tab === 'gradebook' && <GradebookView courseId={courseId} />}
      {tab === 'sessions' && <SessionsPanel courseId={courseId} staff={true} />}
      {tab === 'announcements' && <AnnouncementsPanel courseId={courseId} canPost />}
      {tab === 'qa' && <CourseQaTab courseId={courseId} />}
    </div>
  )
}
