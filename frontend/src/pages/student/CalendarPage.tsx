import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { CalendarDays, CheckCircle2, ClipboardList, FileQuestion } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import type { CalendarEvent } from '../../types/api'

const META = {
  SESSION: { label: 'Buổi học', icon: CalendarDays, cls: 'bg-blue-50 text-blue-700' },
  QUIZ_DEADLINE: { label: 'Hạn quiz', icon: FileQuestion, cls: 'bg-amber-50 text-amber-700' },
  ASSIGNMENT_DEADLINE: { label: 'Hạn bài tập', icon: ClipboardList, cls: 'bg-rose-50 text-rose-700' },
} as const

const dayKey = (iso: string) => new Date(iso).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })

/** One chronological agenda: class sessions plus quiz/assignment deadlines across all active courses. */
export function CalendarPage() {
  const query = useQuery({
    queryKey: ['student', 'calendar'],
    queryFn: () => apiClient.get<CalendarEvent[]>('/api/users/me/calendar').then((r) => r.data),
  })
  const now = Date.now()
  const groups = useMemo(() => {
    const m = new Map<string, CalendarEvent[]>()
    for (const e of query.data ?? []) {
      const k = dayKey(e.at)
      m.set(k, [...(m.get(k) ?? []), e])
    }
    return [...m.entries()]
  }, [query.data])

  return (
    <div className="max-w-3xl">
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Lịch của tôi</h1>
      <p className="mb-6 text-sm text-slate-500">Buổi học, hạn quiz và hạn bài tập của các khóa bạn đang học — 7 ngày qua đến 60 ngày tới.</p>
      {query.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {!query.isLoading && groups.length === 0 && <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-400">Không có sự kiện nào trong khoảng thời gian này.</p>}
      <div className="space-y-6">
        {groups.map(([day, events]) => (
          <div key={day}>
            <h2 className="mb-2 text-sm font-semibold text-slate-700 capitalize">{day}</h2>
            <div className="space-y-2">
              {events.map((e) => {
                const m = META[e.type]
                const past = new Date(e.at).getTime() < now
                return (
                  <Link key={e.type + e.itemId} to={`/student/courses/${e.courseId}${e.type === 'QUIZ_DEADLINE' ? '?view=quizzes' : e.type === 'ASSIGNMENT_DEADLINE' ? '?view=assignments' : '?view=sessions'}`}
                    className={`flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:border-slate-400 ${past && !e.done ? 'opacity-60' : ''}`}>
                    <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${m.cls}`}><m.icon size={17} /></span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium text-slate-900">{e.title}</div>
                      <div className="truncate text-xs text-slate-500">{m.label} · {e.courseTitle}{e.location ? ` · ${e.location}` : ''}</div>
                    </div>
                    <div className="text-right text-sm text-slate-700">
                      {new Date(e.at).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                      {e.done && <div className="flex items-center justify-end gap-1 text-xs text-green-700"><CheckCircle2 size={12} /> Đã làm</div>}
                    </div>
                  </Link>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
