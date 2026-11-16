import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import { useGradingQueues } from '../../lib/useGradingCounts'
import type { AppealResponse, PendingEssay } from '../../types/api'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/ui/Button'

const th = 'px-4 py-3 font-medium'

function EssayRow({ e }: { e: PendingEssay }) {
  const queryClient = useQueryClient()
  const [score, setScore] = useState('')
  const [feedback, setFeedback] = useState('')
  const [error, setError] = useState<string | null>(null)
  const grade = useMutation({
    mutationFn: () => apiClient.post(`/api/attempts/answers/${e.answerId}/grade`, { score: Number(score), feedback: feedback || null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teacher', 'pending-essays'] })
      queryClient.invalidateQueries({ queryKey: ['gradebook', e.courseId] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-1 text-xs text-slate-400">{e.courseTitle} · {e.quizTitle} · {e.studentName} ({e.studentEmail}) · {new Date(e.submittedAt).toLocaleString('vi-VN')}</div>
      <div className="font-medium text-slate-900">{e.questionText}</div>
      <div className="mt-2 whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-700">{e.textAnswer || <span className="text-slate-400">(Học sinh bỏ trống)</span>}</div>
      <div className="mt-3 flex flex-wrap items-end gap-2">
        <div>
          <label className="mb-1 block text-xs text-slate-500">Điểm (tối đa {e.maxPoints})</label>
          <input type="number" min={0} max={e.maxPoints} step="0.25" value={score} onChange={(ev) => setScore(ev.target.value)} className="h-9 w-24 rounded-md border border-slate-300 px-2 text-sm" />
        </div>
        <div className="min-w-48 flex-1">
          <label className="mb-1 block text-xs text-slate-500">Nhận xét (tùy chọn)</label>
          <input value={feedback} onChange={(ev) => setFeedback(ev.target.value)} className="h-9 w-full rounded-md border border-slate-300 px-2 text-sm" />
        </div>
        <Button size="sm" disabled={grade.isPending || score === ''} onClick={() => grade.mutate()}>Lưu điểm</Button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
    </div>
  )
}

function AppealCard({ a }: { a: AppealResponse }) {
  const queryClient = useQueryClient()
  const [response, setResponse] = useState('')
  const [newScore, setNewScore] = useState('')
  const [error, setError] = useState<string | null>(null)
  const resolve = useMutation({
    mutationFn: (accepted: boolean) => apiClient.post(`/api/appeals/${a.id}/resolve`, { accepted, response, newScore: accepted && newScore !== '' ? Number(newScore) : null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teacher', 'appeals'] })
      queryClient.invalidateQueries({ queryKey: ['gradebook', a.courseId] })
      queryClient.invalidateQueries({ queryKey: ['teacher', 'pending-grading'] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-1 text-xs text-slate-400">{a.courseTitle} · {a.studentName} ({a.studentEmail}) · {new Date(a.createdAt).toLocaleString('vi-VN')}</div>
      <div className="font-medium text-slate-900">{a.targetType === 'QUIZ_ATTEMPT' ? 'Quiz' : 'Bài tập'}: {a.targetTitle}</div>
      <div className="mt-2 rounded-md bg-amber-50 p-3 text-sm text-slate-700"><span className="font-medium">Lý do học sinh nêu:</span> {a.reason}</div>
      {a.status === 'OPEN' ? (
        <div className="mt-3 space-y-2">
          <input value={response} onChange={(e) => setResponse(e.target.value)} placeholder="Phản hồi cho học sinh (bắt buộc)" className="h-9 w-full rounded-md border border-slate-300 px-2 text-sm" />
          <div className="flex flex-wrap items-center gap-2">
            <input type="number" step="0.25" min={0} value={newScore} onChange={(e) => setNewScore(e.target.value)} placeholder="Điểm mới (tùy chọn)" className="h-9 w-44 rounded-md border border-slate-300 px-2 text-sm" />
            <Button size="sm" disabled={resolve.isPending || !response.trim()} onClick={() => resolve.mutate(true)}>Chấp nhận</Button>
            <Button size="sm" variant="secondary" disabled={resolve.isPending || !response.trim()} onClick={() => resolve.mutate(false)}>Từ chối</Button>
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>
      ) : (
        <div className="mt-2 text-sm text-slate-600"><Badge status={a.status} /> {a.response}</div>
      )}
    </div>
  )
}

/** Grader's inbox: assignments to grade, essay answers to score, and grade appeals to answer. Used by teachers and admins. */
export function PendingGradingPage() {
  const { user } = useAuth()
  const base = user?.role === 'ADMIN' ? '/admin/courses' : '/teacher/courses'
  const q = useGradingQueues()
  const rows = q.assignments.data ?? []
  const resolved = (q.appeals.data ?? []).filter((a) => a.status !== 'OPEN').slice(0, 10)

  return (
    <div className="space-y-10">
      <div>
        <h1 className="mb-1 text-2xl font-bold text-slate-900">Cần chấm bài</h1>
        <p className="text-sm text-slate-500">Bài tập, câu tự luận và yêu cầu phúc khảo đang chờ bạn xử lý — lâu nhất lên đầu.</p>
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Bài tập chờ chấm ({rows.length})</h2>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Học sinh</th><th className={th}>Bài tập</th><th className={th}>Khóa học</th><th className={th}>Nộp lúc</th><th className={th}>Trạng thái</th><th className="px-4 py-3" />
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.submissionId}>
                  <td className="px-4 py-3"><div className="font-medium text-slate-900">{r.studentName}</div><div className="text-xs text-slate-400">{r.studentEmail}</div></td>
                  <td className="px-4 py-3">{r.assignmentTitle}</td>
                  <td className="px-4 py-3 text-slate-500">{r.courseTitle}</td>
                  <td className="px-4 py-3 text-slate-500">{new Date(r.submittedAt).toLocaleString('vi-VN')}{r.late && <span className="ml-1 text-amber-600">(muộn)</span>}</td>
                  <td className="px-4 py-3"><Badge status={r.status} /></td>
                  <td className="px-4 py-3 text-right">
                    {user?.role === 'ADMIN' ? <span className="text-xs text-slate-400">Giáo viên phụ trách chấm</span> : <Link to={`${base}/${r.courseId}?tab=grading`} className="text-blue-600 hover:underline">Chấm bài</Link>}
                  </td>
                </tr>
              ))}
              {!q.assignments.isLoading && rows.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Không có bài tập nào đang chờ chấm.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Câu tự luận chờ chấm ({q.essays.data?.length ?? 0})</h2>
        <div className="space-y-3">
          {q.essays.data?.map((e) => <EssayRow key={e.answerId} e={e} />)}
          {!q.essays.isLoading && q.essays.data?.length === 0 && <p className="rounded-lg border border-dashed border-slate-300 p-5 text-sm text-slate-400">Không có câu tự luận nào đang chờ chấm.</p>}
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Yêu cầu phúc khảo ({q.openAppeals.length})</h2>
        <div className="space-y-3">
          {q.openAppeals.map((a) => <AppealCard key={a.id} a={a} />)}
          {!q.appeals.isLoading && q.openAppeals.length === 0 && <p className="rounded-lg border border-dashed border-slate-300 p-5 text-sm text-slate-400">Không có yêu cầu phúc khảo nào đang mở.</p>}
        </div>
        {resolved.length > 0 && (
          <>
            <h3 className="mt-6 mb-2 text-sm font-semibold text-slate-600">Đã xử lý gần đây</h3>
            <div className="space-y-3">{resolved.map((a) => <AppealCard key={a.id} a={a} />)}</div>
          </>
        )}
      </section>
    </div>
  )
}
