import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AttemptResultResponse, StartAttemptResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'

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
    <Modal title="Làm bài" open onClose={result ? onClose : () => { if (window.confirm('Thoát sẽ không lưu bài làm. Bạn chắc chắn?')) onClose() }}>
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
          {result.questionResults.length === 0 && result.score !== null && <p className="mb-3 text-sm text-slate-500">Giáo viên không công khai đáp án chi tiết của bài này.</p>}
          <ul className="mb-4 max-h-[50vh] space-y-2 overflow-y-auto text-sm">
            {result.questionResults.map((qr, idx) => {
              const question = start.questions.find((x) => x.questionId === qr.questionId)
              return (
                <li key={qr.questionId} className={`flex gap-2 rounded-md border p-3 ${qr.correct ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'}`}>
                  {qr.correct ? <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-green-600" /> : <XCircle size={16} className="mt-0.5 shrink-0 text-red-600" />}
                  <div>
                    <div className="text-slate-800">Câu {idx + 1}: {question?.questionText}</div>
                    <div className="text-xs text-slate-500">{qr.correct ? 'Đúng' : 'Chưa đúng'} · {qr.awardedScore ?? 0}/{question?.points} điểm</div>
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
          <div className="mb-3 flex items-center justify-between text-sm">
            <span className="text-slate-500">Đã trả lời {answeredCount}/{start.questions.length}</span>
            {remaining !== null && (
              <span className={`flex items-center gap-1 font-medium ${remaining <= 60 ? 'text-red-600' : 'text-slate-700'}`}><Clock size={14} /> {fmt(remaining)}</span>
            )}
          </div>
          <div className="mb-4 flex flex-wrap gap-1.5">
            {start.questions.map((x, i) => (
              <button key={x.questionId} onClick={() => setCurrent(i)}
                className={`h-8 w-8 rounded-md border text-xs font-medium ${i === current ? 'border-slate-900 bg-slate-900 text-white' : answered(x.questionId) ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-slate-300 text-slate-600'}`}>
                {i + 1}
              </button>
            ))}
          </div>
          {q && (
            <div className="mb-4 min-h-40">
              <p className="mb-3 text-sm font-medium text-slate-800">Câu {current + 1} ({q.points} điểm): {q.questionText}</p>
              {q.questionType === 'KEYWORD_FILL' ? (
                <Input value={answers[q.questionId]?.textAnswer ?? ''} onChange={(e) => setAnswers((p) => ({ ...p, [q.questionId]: { textAnswer: e.target.value } }))} placeholder="Nhập câu trả lời" />
              ) : (
                <div className="space-y-2">
                  {q.questionType === 'MULTIPLE_CHOICE' && <p className="text-xs text-slate-400">Chọn tất cả đáp án đúng.</p>}
                  {q.options.map((opt) => (
                    <label key={opt.id} className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
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
    </Modal>
  )
}
