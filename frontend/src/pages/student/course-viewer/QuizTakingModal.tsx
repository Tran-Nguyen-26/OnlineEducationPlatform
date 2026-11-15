import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AttemptResultResponse, StartAttemptResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'

type Answers = Record<string, { selectedOptionIds?: string[]; textAnswer?: string }>

export function QuizTakingModal({
  quizId,
  courseId,
  start,
  onClose,
}: {
  quizId: string
  courseId: string
  start: StartAttemptResponse
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [answers, setAnswers] = useState<Answers>({})
  const [result, setResult] = useState<AttemptResultResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

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
      queryClient.invalidateQueries({ queryKey: ['student', 'quizzes', courseId] })
      queryClient.invalidateQueries({ queryKey: ['student', 'quiz-attempts', quizId] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function setSingleOption(questionId: string, optionId: string) {
    setAnswers((prev) => ({ ...prev, [questionId]: { selectedOptionIds: [optionId] } }))
  }

  function toggleMultiOption(questionId: string, optionId: string) {
    setAnswers((prev) => {
      const current = new Set(prev[questionId]?.selectedOptionIds ?? [])
      if (current.has(optionId)) current.delete(optionId)
      else current.add(optionId)
      return { ...prev, [questionId]: { selectedOptionIds: Array.from(current) } }
    })
  }

  function setTextAnswer(questionId: string, text: string) {
    setAnswers((prev) => ({ ...prev, [questionId]: { textAnswer: text } }))
  }

  return (
    <Modal title="Làm bài" open onClose={onClose}>
      {result ? (
        <div>
          <p className="mb-4 text-lg font-semibold text-slate-900">
            {result.score !== null ? `Điểm của bạn: ${result.score}` : 'Đã nộp bài — chờ công bố điểm.'}
          </p>
          {result.questionResults.length > 0 && (
            <ul className="mb-4 space-y-1 text-sm">
              {result.questionResults.map((qr, idx) => (
                <li key={qr.questionId} className={qr.correct ? 'text-green-700' : 'text-red-600'}>
                  Câu {idx + 1}: {qr.correct ? 'Đúng' : 'Sai'} ({qr.awardedScore} điểm)
                </li>
              ))}
            </ul>
          )}
          <Button onClick={onClose}>Đóng</Button>
        </div>
      ) : (
        <div>
          <div className="mb-4 space-y-5 max-h-[60vh] overflow-y-auto pr-1">
            {start.questions.map((q, idx) => (
              <div key={q.questionId}>
                <p className="mb-2 text-sm font-medium text-slate-800">
                  Câu {idx + 1} ({q.points} điểm): {q.questionText}
                </p>
                {q.questionType === 'KEYWORD_FILL' ? (
                  <Input
                    value={answers[q.questionId]?.textAnswer ?? ''}
                    onChange={(e) => setTextAnswer(q.questionId, e.target.value)}
                    placeholder="Nhập câu trả lời"
                  />
                ) : (
                  <div className="space-y-1">
                    {q.options.map((opt) => (
                      <label key={opt.id} className="flex items-center gap-2 text-sm text-slate-700">
                        <input
                          type={q.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
                          name={q.questionId}
                          checked={(answers[q.questionId]?.selectedOptionIds ?? []).includes(opt.id)}
                          onChange={() =>
                            q.questionType === 'MULTIPLE_CHOICE'
                              ? toggleMultiOption(q.questionId, opt.id)
                              : setSingleOption(q.questionId, opt.id)
                          }
                        />
                        {opt.optionText}
                      </label>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button disabled={submitMutation.isPending} onClick={() => submitMutation.mutate()}>
              {submitMutation.isPending ? 'Đang nộp...' : 'Nộp bài'}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  )
}
