import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, Award, Bell, CalendarDays, CheckCircle2, ChevronDown, ChevronRight, ClipboardList, Circle, FileQuestion, Menu, MessageCircle, Lock, Search, X,
} from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { extractHeadings } from '../../lib/markdown'
import type { CourseOutlineResponse, StudentAssignmentResponse, StudentQuizResponse } from '../../types/api'
import { cn } from '../../lib/cn'
import { ProgressRing } from '../../components/ui/ProgressRing'
import { CourseQaTab } from '../../components/CourseQaTab'
import { SessionsPanel } from '../../components/SessionsPanel'
import { AnnouncementsPanel } from '../../components/AnnouncementsPanel'
import { QuizzesTab } from './course-viewer/QuizzesTab'
import { AssignmentsTab } from './course-viewer/AssignmentsTab'
import { LessonView } from './learn/LessonView'
import { CompletionPanel } from './learn/CompletionPanel'
import { Toc } from './learn/Toc'
import type { FlatLesson } from './learn/types'

const VIEWS = [
  { key: 'completion', label: 'Hoàn thành khóa học', icon: Award },
  { key: 'quizzes', label: 'Bài kiểm tra', icon: FileQuestion },
  { key: 'assignments', label: 'Bài tập', icon: ClipboardList },
  { key: 'sessions', label: 'Lịch học & điểm danh', icon: CalendarDays },
  { key: 'announcements', label: 'Thông báo của khóa', icon: Bell },
  { key: 'qa', label: 'Hỏi đáp chung', icon: MessageCircle },
] as const
type ViewKey = (typeof VIEWS)[number]['key']

const lastKey = (courseId: string) => `oep:last-lesson:${courseId}`

/** Docs/codelab-style learning page: outline on the left, article in the middle, "on this page" on the right, previous/next at the bottom. */
export function CourseViewerPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [menuOpen, setMenuOpen] = useState(false)
  const [filter, setFilter] = useState('')
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const [scrollEl, setScrollEl] = useState<HTMLElement | null>(null)
  const scrollRef = useRef<HTMLElement | null>(null)

  const outlineQuery = useQuery({
    enabled: !!courseId,
    queryKey: ['student', 'courses', courseId, 'outline'],
    queryFn: () => apiClient.get<CourseOutlineResponse>(`/api/courses/${courseId}/outline`).then((r) => r.data),
  })
  const quizzesQuery = useQuery({
    enabled: !!courseId,
    queryKey: ['student', 'quizzes', courseId],
    queryFn: () => apiClient.get<StudentQuizResponse[]>(`/api/quizzes?courseId=${courseId}`).then((r) => r.data),
  })
  const assignmentsQuery = useQuery({
    enabled: !!courseId,
    queryKey: ['student', 'assignments', courseId],
    queryFn: () => apiClient.get<StudentAssignmentResponse[]>(`/api/assignments?courseId=${courseId}`).then((r) => r.data),
  })

  const outline = outlineQuery.data
  const flat: FlatLesson[] = useMemo(
    () => (outline?.modules ?? []).flatMap((m) => m.lessons.map((l) => ({ ...l, moduleId: m.id, moduleTitle: m.title }))),
    [outline],
  )
  const done = flat.filter((l) => l.progressStatus === 'COMPLETED').length
  const percent = flat.length ? Math.round((done / flat.length) * 100) : 0

  const lessonParam = params.get('lesson')
  const viewParam = params.get('view') as ViewKey | null
  const current = flat.find((l) => l.id === lessonParam) ?? null
  const view = VIEWS.find((v) => v.key === viewParam)?.key ?? null

  // Land on the learner's last lesson (or the first unfinished one) when no explicit target is in the URL.
  useEffect(() => {
    if (!courseId || flat.length === 0 || current || view) return
    let saved: string | null = null
    try { saved = localStorage.getItem(lastKey(courseId)) } catch { /* storage unavailable */ }
    const target = flat.find((l) => l.id === saved) ?? flat.find((l) => l.progressStatus !== 'COMPLETED') ?? flat[0]
    setParams({ lesson: target.id }, { replace: true })
  }, [courseId, flat, current, view, setParams])

  useEffect(() => {
    if (current && courseId) { try { localStorage.setItem(lastKey(courseId), current.id) } catch { /* ignore */ } }
    scrollRef.current?.scrollTo({ top: 0 })
    setMenuOpen(false)
  }, [current?.id, view, courseId])

  const headings = useMemo(
    () => (current ? current.contents.flatMap((c, i) => (c.contentType === 'TEXT' ? extractHeadings(c.textContent ?? '', `c${i}`) : [])) : []),
    [current],
  )

  if (!courseId) return null
  if (outlineQuery.isLoading) return <p className="p-8 text-slate-400">Đang tải...</p>
  if (outlineQuery.isError || !outline) {
    return (
      <div className="p-8">
        <p className="mb-3 text-red-600">{outlineQuery.isError ? getApiErrorMessage(outlineQuery.error) : 'Không thể tải khóa học.'}</p>
        <Link to="/student/explore" className="text-sm text-blue-600 hover:underline">Về danh mục khóa học</Link>
      </div>
    )
  }

  const idx = current ? flat.findIndex((l) => l.id === current.id) : -1
  const go = (lessonId: string) => setParams({ lesson: lessonId })
  const q = filter.trim().toLowerCase()

  const sidebar = (
    <div className="flex h-full flex-col">
      <div className="border-b border-slate-200 p-3">
        <div className="relative">
          <Search size={14} className="absolute top-2.5 left-2.5 text-slate-400" />
          <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Tìm bài học..." className="h-9 w-full rounded-md border border-slate-300 pr-3 pl-8 text-sm" />
        </div>
      </div>
      <nav className="flex-1 overflow-y-auto p-2">
        {outline.modules.map((m, mi) => {
          const lessons = m.lessons.filter((l) => !q || l.title.toLowerCase().includes(q))
          if (q && lessons.length === 0) return null
          const open = q ? true : !collapsed.has(m.id)
          const mDone = m.lessons.filter((l) => l.progressStatus === 'COMPLETED').length
          return (
            <div key={m.id} className="mb-1">
              <button
                onClick={() => setCollapsed((s) => { const n = new Set(s); if (n.has(m.id)) n.delete(m.id); else n.add(m.id); return n })}
                className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm font-semibold text-slate-800 hover:bg-slate-100"
              >
                {open ? <ChevronDown size={14} className="shrink-0" /> : <ChevronRight size={14} className="shrink-0" />}
                <span className="flex-1">{mi + 1}. {m.title}</span>
                <span className="text-xs font-normal text-slate-400">{mDone}/{m.lessons.length}</span>
              </button>
              <div className="collapse-grid" data-open={open}><div>
                <ul className="ml-3 border-l border-slate-200">
                  {lessons.map((l) => {
                    const active = l.id === current?.id
                    const hasCheck = quizzesQuery.data?.some((x) => x.lessonId === l.id) || assignmentsQuery.data?.some((x) => x.lessonId === l.id)
                    return (
                      <li key={l.id}>
                        <button
                          onClick={() => go(l.id)}
                          className={cn('-ml-px flex w-full items-start gap-2 border-l-2 py-1.5 pr-2 pl-3 text-left text-sm', l.locked && 'opacity-60',
                            active ? 'border-blue-600 bg-blue-50 font-medium text-blue-800' : 'border-transparent text-slate-600 hover:bg-slate-50')}
                        >
                          {l.locked ? <Lock size={15} className="mt-0.5 shrink-0 text-slate-400" /> : l.progressStatus === 'COMPLETED' ? <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-green-600" />
                            : l.progressStatus === 'IN_PROGRESS' ? <Circle size={15} className="mt-0.5 shrink-0 fill-amber-200 text-amber-500" />
                            : <Circle size={15} className="mt-0.5 shrink-0 text-slate-300" />}
                          <span className="flex-1">{l.title}</span>
                          {hasCheck && <FileQuestion size={13} className="mt-1 shrink-0 text-slate-400" aria-label="Có bài kiểm tra" />}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </div></div>
            </div>
          )
        })}
        {outline.modules.length === 0 && <p className="p-3 text-sm text-slate-400">Khóa học chưa có nội dung.</p>}

        <div className="mt-3 border-t border-slate-200 pt-3">
          <div className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">Khóa học</div>
          {VIEWS.map((v) => (
            <button
              key={v.key}
              onClick={() => setParams({ view: v.key })}
              className={cn('flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm', view === v.key ? 'bg-blue-50 font-medium text-blue-800' : 'text-slate-600 hover:bg-slate-100')}
            >
              <v.icon size={15} /> {v.label}
            </button>
          ))}
        </div>
      </nav>
    </div>
  )

  return (
    <div className="flex h-screen flex-col bg-white">
      <header className="glass relative z-30 flex h-16 shrink-0 items-center gap-3 border-x-0 border-t-0 border-b-slate-200/80 px-4">
        <button className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setMenuOpen((v) => !v)} aria-label="Mục lục">
          {menuOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
        <button onClick={() => navigate('/student/my-courses')} className="flex items-center gap-1 text-sm text-slate-500 hover:text-slate-900">
          <ArrowLeft size={16} /> <span className="hidden sm:inline">Khóa học của tôi</span>
        </button>
        <div className="mx-2 hidden h-5 w-px bg-slate-200 sm:block" />
        <h1 className="flex-1 truncate text-base font-bold text-slate-900">{outline.title}</h1>
        <div className="flex items-center gap-3">
          <ProgressRing value={percent} size={42} stroke={5} />
          <span className="hidden text-sm font-medium text-slate-600 sm:inline">{done}/{flat.length} bài hoàn thành</span>
        </div>
      </header>

      {outline.readOnly && (
        <div className="shrink-0 bg-emerald-50 px-4 py-1.5 text-center text-xs text-emerald-800">Bạn đã hoàn thành khóa học này — đang ở chế độ xem lại, tiến độ không thay đổi.</div>
      )}
      <div className="relative flex min-h-0 flex-1">
        <aside className={cn('absolute inset-y-0 left-0 z-20 w-80 border-r border-slate-200 bg-white/95 backdrop-blur lg:static lg:block', menuOpen ? 'block shadow-xl' : 'hidden')}>{sidebar}</aside>

        <main ref={(el) => { scrollRef.current = el; if (el !== scrollEl) setScrollEl(el) }} className="min-w-0 flex-1 overflow-y-auto">
          <div className="mx-auto flex max-w-6xl gap-10 px-6 py-8 lg:px-10">
            <div className="min-w-0 max-w-3xl flex-1">
              {current && (
                <div key={current.id} className="animate-fade-up">
                <LessonView
                  courseId={courseId}
                  lesson={current}
                  prev={idx > 0 ? flat[idx - 1] : null}
                  next={idx >= 0 && idx < flat.length - 1 ? flat[idx + 1] : null}
                  onGo={go}
                  quizzes={quizzesQuery.data ?? []}
                  assignments={assignmentsQuery.data ?? []}
                  readOnly={outline.readOnly}
                />
                </div>
              )}
              {!current && view === 'completion' && <ViewShell title="Hoàn thành khóa học"><CompletionPanel courseId={courseId} /></ViewShell>}
              {!current && view === 'quizzes' && <ViewShell title="Bài kiểm tra"><QuizzesTab courseId={courseId} /></ViewShell>}
              {!current && view === 'assignments' && <ViewShell title="Bài tập"><AssignmentsTab courseId={courseId} /></ViewShell>}
              {!current && view === 'sessions' && <ViewShell title="Lịch học & điểm danh"><SessionsPanel courseId={courseId} staff={false} /></ViewShell>}
              {!current && view === 'announcements' && <ViewShell title="Thông báo của khóa"><AnnouncementsPanel courseId={courseId} canPost={false} /></ViewShell>}
              {!current && view === 'qa' && <ViewShell title="Hỏi đáp chung"><CourseQaTab courseId={courseId} /></ViewShell>}
              {!current && !view && flat.length === 0 && <p className="text-slate-400">Khóa học chưa có bài học nào.</p>}
            </div>
            {current && headings.length > 1 && <aside className="hidden w-56 shrink-0 xl:block"><Toc headings={headings} scrollEl={scrollEl} /></aside>}
          </div>
        </main>
      </div>
    </div>
  )
}

function ViewShell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h1 className="mb-6 text-3xl font-bold tracking-tight text-slate-900">{title}</h1>
      {children}
    </div>
  )
}
