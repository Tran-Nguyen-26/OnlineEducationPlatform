import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarDays, CheckCircle2, ClipboardCheck, FileQuestion, KeyRound, Layers, Percent, Users } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { CourseOverview, MyEnrollmentResponse } from '../../types/api'
import { UserAvatar } from '../../components/UserAvatar'
import { PhaseBadge } from '../../components/PhaseBadge'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString('vi-VN') : null)

/** Pre-enrollment course page: who teaches, schedule, outline, how it is graded, prerequisites, and the invite-code form. */
export function CourseOverviewPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [waitlisted, setWaitlisted] = useState(false)

  const query = useQuery({
    queryKey: ['student', 'overview', courseId],
    queryFn: () => apiClient.get<CourseOverview>(`/api/courses/${courseId}/overview`).then((r) => r.data),
  })
  const enroll = useMutation({
    mutationFn: () => apiClient.post<MyEnrollmentResponse>('/api/enrollments', { courseId, inviteCode: code }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['student'] })
      if (res.data.status === 'WAITLISTED') setWaitlisted(true)
      else navigate(`/student/courses/${courseId}`)
    },
    onError: (e) => setError(getApiErrorMessage(e)),
  })

  if (query.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (query.isError || !query.data) return <p className="text-red-600">Không tìm thấy khóa học.</p>
  const c = query.data
  const mine = c.myStatus
  const unmet = c.prerequisites.filter((p) => !p.met)

  function submit(e: FormEvent) { e.preventDefault(); setError(null); enroll.mutate() }

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/student/explore" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline"><ArrowLeft size={16} /> Danh mục khóa học</Link>
      <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
        <div>
          <div className="text-xs font-semibold tracking-wide text-blue-600 uppercase">{c.subjectName}</div>
          <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-900">{c.title}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-slate-600">
            <PhaseBadge phase={c.phase} />
            {(c.startAt || c.endAt) && <span className="flex items-center gap-1"><CalendarDays size={14} /> {fmt(c.startAt) ?? '…'} → {fmt(c.endAt) ?? '…'}</span>}
            <span className="flex items-center gap-1"><Users size={14} /> {c.enrolledCount}{c.capacity ? `/${c.capacity}` : ''} học viên</span>
          </div>
          {c.description && <p className="mt-5 leading-7 text-slate-700">{c.description}</p>}

          <h2 className="mt-8 mb-3 text-lg font-semibold text-slate-900">Giáo viên phụ trách</h2>
          {c.instructors.length === 0 && <p className="text-sm text-slate-400">Chưa phân công giáo viên.</p>}
          <div className="space-y-3">
            {c.instructors.map((t) => (
              <div key={t.name} className="flex gap-3 rounded-lg border border-slate-200 bg-white p-4">
                <UserAvatar url={t.avatarUrl} name={t.name} size={40} />
                <div>
                  <div className="font-medium text-slate-900">{t.name}</div>
                  {t.organization && <div className="text-xs text-slate-500">{t.organization}</div>}
                  {t.bio && <p className="mt-1 text-sm text-slate-600">{t.bio}</p>}
                </div>
              </div>
            ))}
          </div>

          <h2 className="mt-8 mb-3 text-lg font-semibold text-slate-900">Nội dung khóa học</h2>
          <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
            {c.modules.map((m, i) => (
              <details key={m.title + i} className="group" open={i === 0}>
                <summary className="flex cursor-pointer items-center justify-between px-4 py-3 font-medium text-slate-900">
                  <span>{i + 1}. {m.title}</span><span className="text-xs font-normal text-slate-400">{m.lessons.length} bài</span>
                </summary>
                <ul className="space-y-1 px-4 pb-3 text-sm text-slate-600">{m.lessons.map((l) => <li key={l} className="flex items-center gap-2"><CheckCircle2 size={13} className="text-slate-300" /> {l}</li>)}</ul>
              </details>
            ))}
            {c.modules.length === 0 && <p className="p-4 text-sm text-slate-400">Nội dung đang được xây dựng.</p>}
          </div>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-6 lg:self-start">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="text-2xl font-bold text-slate-900">{c.feeAmount ? `${c.feeAmount.toLocaleString('vi-VN')} đ` : 'Miễn phí'}</div>
            <ul className="mt-4 space-y-2 text-sm text-slate-600">
              <li className="flex items-center gap-2"><Layers size={15} /> {c.lessonCount} bài học · {c.learningMode === 'SEQUENTIAL' ? 'học tuần tự' : 'học tự do'}</li>
              <li className="flex items-center gap-2"><FileQuestion size={15} /> {c.quizCount} quiz</li>
              <li className="flex items-center gap-2"><ClipboardCheck size={15} /> {c.assignmentCount} bài tập</li>
              <li className="flex items-center gap-2"><CalendarDays size={15} /> {c.sessionCount} buổi học theo lịch</li>
              <li className="flex items-center gap-2"><Percent size={15} /> Tổng kết: quiz {c.quizWeightPercent}% · bài tập {100 - c.quizWeightPercent}%{c.passingPercent != null ? ` · đạt từ ${c.passingPercent}%` : ''}</li>
            </ul>
            {unmet.length > 0 && (
              <div className="mt-4 rounded-md bg-amber-50 p-3 text-xs text-amber-800">
                Cần hoàn thành trước: {unmet.map((p) => p.title).join(', ')}
              </div>
            )}
            {c.prerequisites.filter((p) => p.met).length > 0 && <div className="mt-2 text-xs text-green-700">Đã đạt điều kiện: {c.prerequisites.filter((p) => p.met).map((p) => p.title).join(', ')} ✓</div>}

            <div className="mt-5">
              {mine === 'ACTIVE' || mine === 'COMPLETED' ? (
                <Button className="w-full" onClick={() => navigate(`/student/courses/${c.id}`)}>{mine === 'COMPLETED' ? 'Xem lại khóa học' : 'Vào học'}</Button>
              ) : mine === 'WAITLISTED' || waitlisted ? (
                <p className="rounded-md bg-blue-50 p-3 text-center text-sm text-blue-700">Bạn đang trong danh sách chờ</p>
              ) : c.phase === 'ENDED' ? (
                <p className="rounded-md bg-slate-100 p-3 text-center text-sm text-slate-600">Khóa học đã kết thúc, không nhận ghi danh.</p>
              ) : (
                <form onSubmit={submit}>
                  <label className="mb-1 block text-sm font-medium text-slate-700">Mã mời</label>
                  <Input required value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="VD: KB7GJ4FE" className="mb-3" />
                  {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
                  <Button type="submit" className="w-full" disabled={enroll.isPending}><KeyRound size={16} /> {enroll.isPending ? 'Đang xác nhận...' : 'Ghi danh'}</Button>
                  <p className="mt-2 text-xs text-slate-400">Mã mời do trung tâm cung cấp. Khóa đầy chỗ sẽ đưa bạn vào danh sách chờ.</p>
                </form>
              )}
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
