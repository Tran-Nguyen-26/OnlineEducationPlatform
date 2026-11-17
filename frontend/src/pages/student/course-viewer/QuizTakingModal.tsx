import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ClipboardCheck, Clock, XCircle } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AttemptResultResponse, StartAttemptResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'

type Answers = Record<string, { selectedOptionIds?: string[]; textAnswer?: string }>

const fmt = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

export function QuizTakingModal({ quizId, courseId, start, onClose }: {
  quizId: string
  courseId: string
  start: StartAttemptResponse
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [answers, setAnswers] = useState<Answers>({})
  const [result, setResult] = useState<AttemptResultResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [current, setCurrent] = useState(0)

  const deadlineMs = useMemo(
    () => (start.timeLimitSeconds ? new Date(start.startedAt).getTime() + start.timeLimitSeconds * 1000 : null),
    [start.startedAt, start.timeLimitSeconds],
  )
  const [remaining, setRemaining] = useState<number | null>(deadlineMs ? Math.max(0, Math.round((deadlineMs - Date.now()) / 1000)) : null)

  const answered = (qid: string) => {
    const a = answers[qid]
    return !!a && ((a.selectedOptionIds?.length ?? 0) > 0 || !!a.textAnswer?.trim())
  }
  const answeredCount = start.questions.filter((q) => answered(q.questionId)).length

  const submitMutation = useMutation({
    mutationFn: () =>
      apiClient.post<AttemptResultResponse>(`/api/attempts/${start.attemptId}/submit`, {
        answers: start.questions.map((q) => ({
          questionId: q.questionId,
          selectedOptionIds: answers[q.questionId]?.selectedOptionIds ?? [],
          textAnswer: answers[q.questionId]?.textAnswer ?? null,
        })),
      }),
    onSuccess: (res) => {
      setResult(res.data)
      setConfirming(false)
      queryClient.invalidateQueries({ queryKey: ['student', 'quizzes', courseId] })
      queryClient.invalidateQueries({ queryKey: ['student', 'quiz-attempts', quizId] })
      queryClient.invalidateQueries({ queryKey: ['student', 'completion', courseId] })
    },
    onError: (err) => { setError(getApiErrorMessage(err)); setConfirming(false) },
  })

  // Countdown; auto-submits whatever has been answered when the time limit is reached.
  const submitRef = useRef(submitMutation.mutate)
  submitRef.current = submitMutation.mutate
  useEffect(() => {
    if (!deadlineMs || result) return
    const t = window.setInterval(() => {
      const left = Math.max(0, Math.round((deadlineMs - Date.now()) / 1000))
      setRemaining(left)
      if (left === 0) { window.clearInterval(t); submitRef.current() }
    }, 1000)
    return () => window.clearInterval(t)
  }, [deadlineMs, result])

  function setSingleOption(qid: string, optionId: string) {
    setAnswers((prev) => ({ ...prev, [qid]: { selectedOptionIds: [optionId] } }))
  }
  function toggleMultiOption(qid: string, optionId: string) {
    setAnswers((prev) => {
      const cur = new Set(prev[qid]?.selectedOptionIds ?? [])
      if (cur.has(optionId)) cur.delete(optionId); else cur.add(optionId)
      return { ...prev, [qid]: { selectedOptionIds: Array.from(cur) } }
    })
  }

  const q = start.questions[current]

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-canvas" role="dialog" aria-modal="true" aria-label="Làm bài">
      <div className="glass flex h-16 shrink-0 items-center justify-between border-x-0 border-t-0 px-5">
        <div className="flex items-center gap-2.5 font-extrabold tracking-tight text-slate-900">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><ClipboardCheck size={18} /></span>
          Làm bài
        </div>
        <button onClick={result ? onClose : () => { if (window.confirm('Thoát sẽ không lưu bài làm. Bạn chắc chắn?')) onClose() }} className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100" aria-label="Đóng">{result ? 'Đóng' : 'Thoát'}</button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 sm:p-8">
        <div className="surface mx-auto w-full max-w-3xl p-6 sm:p-8">
      {result ? (
        <div>
          <p className="mb-1 text-lg font-semibold text-slate-900">
            {result.score !== null ? `Điểm của bạn: ${result.score}` : 'Đã nộp bài — chờ công bố điểm.'}
          </p>
          {result.questionResults.length > 0 && (
            <p className="mb-3 text-sm text-slate-500">
              Đúng {result.questionResults.filter((r) => r.correct).length}/{result.questionResults.length} câu
            </p>
          )}
          {result.pendingManual > 0 && <p className="mb-3 rounded-md bg-amber-50 p-3 text-sm text-amber-800">Có {result.pendingManual} câu tự luận đang chờ giáo viên chấm — điểm hiện tại là điểm tạm tính, bạn sẽ nhận thông báo khi chấm xong.</p>}
          {result.questionResults.length === 0 && result.score !== null && <p className="mb-3 text-sm text-slate-500">Giáo viên không công khai đáp án chi tiết của bài này.</p>}
          <ul className="mb-4 max-h-none space-y-2 overflow-y-auto text-sm">
            {result.questionResults.map((qr, idx) => {
              const question = start.questions.find((x) => x.questionId === qr.questionId)
              return (
                <li key={qr.questionId} className={`flex gap-2 rounded-md border p-3 ${qr.correct === null ? 'border-amber-200 bg-amber-50' : qr.correct ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'}`}>
                  {qr.correct ? <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-green-600" /> : <XCircle size={16} className="mt-0.5 shrink-0 text-red-600" />}
                  <div>
                    <div className="text-slate-800">Câu {idx + 1}: {question?.questionText}</div>
                    <div className="text-xs text-slate-500">{qr.correct === null ? 'Chờ chấm' : qr.correct ? 'Đúng' : 'Chưa đúng'} · {qr.awardedScore ?? 0}/{question?.points} điểm</div>
                  </div>
                </li>
              )
            })}
          </ul>
          <Button onClick={onClose}>Đóng</Button>
        </div>
      ) : confirming ? (
        <div>
          <p className="mb-2 text-slate-800">Bạn đã trả lời {answeredCount}/{start.questions.length} câu.</p>
          {answeredCount < start.questions.length && <p className="mb-2 text-sm text-amber-700">Còn {start.questions.length - answeredCount} câu chưa trả lời sẽ bị tính là sai.</p>}
          <p className="mb-4 text-sm text-slate-500">Sau khi nộp bạn không thể sửa bài làm này.</p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirming(false)}>Quay lại làm bài</Button>
            <Button disabled={submitMutation.isPending} onClick={() => submitMutation.mutate()}>{submitMutation.isPending ? 'Đang nộp...' : 'Nộp bài'}</Button>
          </div>
        </div>
      ) : (
        <div>
          <div className="mb-2 flex items-center justify-between text-sm">
            <span className="font-medium text-slate-600">Đã trả lời {answeredCount}/{start.questions.length}</span>
            {remaining !== null && (
              <span className={`flex items-center gap-1 font-medium ${remaining <= 60 ? 'text-red-600' : 'text-slate-700'}`}><Clock size={14} /> {fmt(remaining)}</span>
            )}
          </div>
          <div className="mb-5 h-1.5 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-teal-400 transition-all" style={{ width: `${(answeredCount / Math.max(1, start.questions.length)) * 100}%` }} /></div>
          <div className="mb-5 flex flex-wrap gap-1.5">
            {start.questions.map((x, i) => (
              <button key={x.questionId} onClick={() => setCurrent(i)}
                className={`h-8 w-8 rounded-md border text-xs font-medium ${i === current ? 'border-transparent bg-brand-gradient text-white shadow-md shadow-indigo-500/25' : answered(x.questionId) ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-slate-300 bg-white text-slate-600 hover:border-indigo-300'}`}>
                {i + 1}
              </button>
            ))}
          </div>
          {q && (
            <div className="mb-4 min-h-40">
              <p className="mb-4 text-lg font-semibold text-slate-900">Câu {current + 1} ({q.points} điểm): {q.questionText}</p>
              {q.questionType === 'ESSAY' ? (
                <>
                  <textarea
                    value={answers[q.questionId]?.textAnswer ?? ''}
                    onChange={(e) => setAnswers((p) => ({ ...p, [q.questionId]: { textAnswer: e.target.value } }))}
                    rows={7}
                    placeholder="Viết câu trả lời của bạn..."
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                  <p className="mt-1 text-xs text-slate-400">Câu tự luận do giáo viên chấm — điểm cuối cùng sẽ có sau khi chấm xong.</p>
                </>
              ) : q.questionType === 'KEYWORD_FILL' ? (
                <Input value={answers[q.questionId]?.textAnswer ?? ''} onChange={(e) => setAnswers((p) => ({ ...p, [q.questionId]: { textAnswer: e.target.value } }))} placeholder="Nhập câu trả lời" />
              ) : (
                <div className="space-y-2">
                  {q.questionType === 'MULTIPLE_CHOICE' && <p className="text-xs text-slate-400">Chọn tất cả đáp án đúng.</p>}
                  {q.options.map((opt) => (
                    <label key={opt.id} className="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-base text-slate-800 transition hover:border-indigo-300 hover:bg-indigo-50/50 has-[:checked]:border-indigo-500 has-[:checked]:bg-indigo-50 has-[:checked]:ring-2 has-[:checked]:ring-indigo-500/20">
                      <input
                        type={q.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
                        name={q.questionId}
                        checked={(answers[q.questionId]?.selectedOptionIds ?? []).includes(opt.id)}
                        onChange={() => (q.questionType === 'MULTIPLE_CHOICE' ? toggleMultiOption(q.questionId, opt.id) : setSingleOption(q.questionId, opt.id))}
                      />
                      {opt.optionText}
                    </label>
                  ))}
                </div>
              )}
            </div>
          )}
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
          <div className="flex items-center justify-between">
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={current === 0} onClick={() => setCurrent((c) => c - 1)}>Câu trước</Button>
              <Button variant="secondary" size="sm" disabled={current >= start.questions.length - 1} onClick={() => setCurrent((c) => c + 1)}>Câu sau</Button>
            </div>
            <Button onClick={() => setConfirming(true)}>Nộp bài</Button>
          </div>
        </div>
      )}
        </div>
      </div>
    </div>
  )
}
