import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CheckCircle2, Circle, Clock, Lock, PartyPopper } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AssignmentResponseLike, FlatLesson } from './types'
import type { StudentQuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { CourseQaTab } from '../../../components/CourseQaTab'
import { LessonContent } from './LessonContent'
import { NotesPanel } from './NotesPanel'
import { useVideoProgress } from './useVideoProgress'
import { QuizRow } from '../course-viewer/QuizzesTab'
import { AssignmentRow } from '../course-viewer/AssignmentsTab'

export function readingMinutes(lesson: FlatLesson) {
  const words = lesson.contents.filter((c) => c.contentType === 'TEXT').reduce((n, c) => n + (c.textContent?.split(/\s+/).length ?? 0), 0)
  return Math.max(1, Math.round(words / 200))
}

export function LessonView({ courseId, lesson, prev, next, onGo, quizzes, assignments, readOnly }: {
  courseId: string
  lesson: FlatLesson
  prev: FlatLesson | null
  next: FlatLesson | null
  onGo: (lessonId: string) => void
  quizzes: StudentQuizResponse[]
  assignments: AssignmentResponseLike[]
  readOnly: boolean
}) {
  if (lesson.locked) {
    return (
      <article>
        <div className="mb-2 text-sm text-slate-500">{lesson.moduleTitle}</div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">{lesson.title}</h1>
        <div className="mt-8 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-5 text-amber-900">
          <Lock size={20} className="mt-0.5 shrink-0" />
          <div className="text-sm"><div className="font-semibold">Bài học chưa mở</div>Khóa học này học tuần tự — hãy hoàn thành các bài bắt buộc phía trước để mở bài này.</div>
        </div>
        {prev && <Button variant="secondary" className="mt-6" onClick={() => onGo(prev.id)}><ArrowLeft size={16} /> Về bài trước: {prev.title}</Button>}
      </article>
    )
  }
  return <LessonBody courseId={courseId} lesson={lesson} prev={prev} next={next} onGo={onGo} quizzes={quizzes} assignments={assignments} readOnly={readOnly} />
}

function LessonBody({ courseId, lesson, prev, next, onGo, quizzes, assignments, readOnly }: {
  courseId: string
  lesson: FlatLesson
  prev: FlatLesson | null
  next: FlatLesson | null
  onGo: (lessonId: string) => void
  quizzes: StudentQuizResponse[]
  assignments: AssignmentResponseLike[]
  readOnly: boolean
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [finished, setFinished] = useState(false)
  const completed = lesson.progressStatus === 'COMPLETED'
  const started = useRef<string | null>(null)
  const [livePercent, setLivePercent] = useState(0)
  const [elapsed, setElapsed] = useState(0)
  const reportVideo = useVideoProgress(courseId, lesson.id, !readOnly)
  const onVideoPercent = (p: number) => { setLivePercent((v) => Math.max(v, Math.floor(p))); reportVideo(p) }

  const progress = useMutation({
    mutationFn: (status: 'IN_PROGRESS' | 'COMPLETED') => apiClient.patch(`/api/lessons/${lesson.id}/progress`, { status }).then(() => status),
    onSuccess: (status) => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['student', 'courses', courseId, 'outline'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'my-progress'] })
      if (status === 'COMPLETED') {
        if (next) onGo(next.id)
        else setFinished(true)
      }
    },
    onError: (e) => setError(getApiErrorMessage(e)),
  })

  // First visit marks the lesson IN_PROGRESS so the server starts counting time on it (completion rules, GAPS G2).
  useEffect(() => {
    setFinished(false)
    setError(null)
    setLivePercent(0)
    setElapsed(0)
    const tick = window.setInterval(() => setElapsed((n) => n + 1), 1000)
    if (!readOnly && lesson.progressStatus === 'NOT_STARTED' && started.current !== lesson.id) {
      started.current = lesson.id // guards against the duplicate effect run (StrictMode) racing to create the progress row
      progress.mutate('IN_PROGRESS')
    }
    return () => window.clearInterval(tick)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lesson.id])

  const lessonQuizzes = quizzes.filter((q) => q.lessonId === lesson.id)
  const lessonAssignments = assignments.filter((a) => a.lessonId === lesson.id)

  return (
    <article>
      <div className="mb-2 text-sm text-slate-500">{lesson.moduleTitle}</div>
      <h1 className="text-3xl font-bold tracking-tight text-slate-900">{lesson.title}</h1>
      <div className="mt-3 flex flex-wrap items-center gap-4 text-sm text-slate-500">
        <span className="flex items-center gap-1"><Clock size={14} /> ~{readingMinutes(lesson)} phút đọc</span>
        <span>{lesson.required ? 'Bài bắt buộc' : 'Bài tham khảo'}</span>
        {completed && <span className="flex items-center gap-1 font-medium text-green-700"><CheckCircle2 size={14} /> Đã hoàn thành</span>}
      </div>
      {lesson.description && <p className="mt-5 text-lg leading-8 text-slate-600">{lesson.description}</p>}

      <div className="mt-4">
        {lesson.contents.map((c, i) => <LessonContent key={c.id} content={c} idPrefix={`c${i}`} onVideoPercent={onVideoPercent} />)}
        {lesson.contents.length === 0 && <p className="mt-8 text-slate-400">Bài học này chưa có nội dung.</p>}
      </div>

      {(lessonQuizzes.length > 0 || lessonAssignments.length > 0) && (
        <section className="mt-12">
          <h2 className="mb-3 text-xl font-semibold text-slate-900">Kiểm tra kiến thức</h2>
          <div className="space-y-3">
            {lessonQuizzes.map((q) => <QuizRow key={q.id} courseId={courseId} quiz={q} />)}
            {lessonAssignments.map((a) => <AssignmentRow key={a.id} courseId={courseId} assignment={a as never} />)}
          </div>
        </section>
      )}

      <NotesPanel lessonId={lesson.id} />

      <section className="mt-12 border-t border-slate-200 pt-6">
        <h2 className="mb-2 text-base font-semibold text-slate-800">Hỏi đáp về bài học này</h2>
        <CourseQaTab courseId={courseId} lessonId={lesson.id} compact />
      </section>


      {!readOnly && !completed && lesson.completionRequirement && (
        <Requirements
          lesson={lesson}
          elapsed={elapsed}
          livePercent={livePercent}
          quizDone={lessonQuizzes.length === 0 || lessonQuizzes.every((q) => q.attemptsUsed > 0)}
          assignmentDone={lessonAssignments.length === 0 || lessonAssignments.every((a) => a.submissionCount > 0)}
        />
      )}
      {finished && (
        <div className="mt-10 flex items-center gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-900">
          <PartyPopper size={20} />
          <div className="text-sm"><div className="font-semibold">Bạn đã học xong bài cuối cùng!</div>Hãy làm các bài kiểm tra / bài tập còn lại. Khi đủ điều kiện, vào mục <Link to={`?view=completion`} className="font-medium underline">Hoàn thành khóa học</Link> để nhận chứng chỉ.</div>
        </div>
      )}
      {error && <p className="mt-6 rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="mt-10 flex flex-col gap-3 border-t border-slate-200 pt-6 sm:flex-row sm:items-stretch sm:justify-between">
        {prev ? (
          <button onClick={() => onGo(prev.id)} className="flex flex-1 items-center gap-3 rounded-lg border border-slate-200 p-4 text-left hover:border-slate-400">
            <ArrowLeft size={18} className="text-slate-400" />
            <div><div className="text-xs text-slate-400">Bài trước</div><div className="text-sm font-medium text-slate-800">{prev.title}</div></div>
          </button>
        ) : <div className="flex-1" />}
        <div className="flex flex-1 flex-col gap-2">
          {!completed && !readOnly && (
            <Button disabled={progress.isPending} onClick={() => progress.mutate('COMPLETED')} className="h-full min-h-14">
              <CheckCircle2 size={16} /> {next ? 'Hoàn thành & bài tiếp theo' : 'Hoàn thành bài học'}
            </Button>
          )}
          {(completed || readOnly) && next && (
            <button onClick={() => onGo(next.id)} className="flex flex-1 items-center justify-end gap-3 rounded-lg border border-slate-200 p-4 text-right hover:border-slate-400">
              <div><div className="text-xs text-slate-400">Bài tiếp theo</div><div className="text-sm font-medium text-slate-800">{next.title}</div></div>
              <ArrowRight size={18} className="text-slate-400" />
            </button>
          )}
        </div>
      </div>
    </article>
  )
}

function Requirements({ lesson, elapsed, livePercent, quizDone, assignmentDone }: {
  lesson: FlatLesson; elapsed: number; livePercent: number; quizDone: boolean; assignmentDone: boolean
}) {
  const r = lesson.completionRequirement!
  // Server measures time since the lesson was first opened; local counter is a conservative estimate of that.
  const seconds = Math.max(lesson.timeSpentSeconds, elapsed)
  const items: { ok: boolean; text: string }[] = []
  if (r.minimumTimeSeconds > 0) items.push({ ok: seconds >= r.minimumTimeSeconds, text: `Dành ít nhất ${r.minimumTimeSeconds} giây cho bài học (${Math.min(seconds, r.minimumTimeSeconds)}/${r.minimumTimeSeconds}s)` })
  if (r.minVideoPercentage) { const w = Math.max(lesson.watchedPercent, livePercent); items.push({ ok: w >= r.minVideoPercentage, text: `Xem ít nhất ${r.minVideoPercentage}% video (hiện ${w}%)` }) }
  if (r.requireQuiz) items.push({ ok: quizDone, text: 'Làm quiz của bài học' })
  if (r.requireAssignment) items.push({ ok: assignmentDone, text: 'Nộp bài tập của bài học' })
  if (items.length === 0) return null
  return (
    <div className="mt-10 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div className="mb-2 text-sm font-semibold text-slate-800">Điều kiện để hoàn thành bài học</div>
      <ul className="space-y-1 text-sm">
        {items.map((it) => (
          <li key={it.text} className={`flex items-center gap-2 ${it.ok ? 'text-green-700' : 'text-slate-600'}`}>
            {it.ok ? <CheckCircle2 size={15} /> : <Circle size={15} className="text-slate-300" />} {it.text}
          </li>
        ))}
      </ul>
    </div>
  )
}
