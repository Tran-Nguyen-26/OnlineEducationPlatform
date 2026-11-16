import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AttemptReviewResponse, AttemptSummaryResponse, StartAttemptResponse, StudentQuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { QuizTakingModal } from './QuizTakingModal'
import { AppealModal } from '../../../components/AppealModal'
import { Modal } from '../../../components/ui/Modal'

export function QuizRow({ courseId, quiz }: { courseId: string; quiz: StudentQuizResponse }) {
  const [active, setActive] = useState<StartAttemptResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const [appeal, setAppeal] = useState<AttemptSummaryResponse | null>(null)
  const [reviewId, setReviewId] = useState<string | null>(null)
  const reviewQuery = useQuery({
    enabled: reviewId !== null,
    queryKey: ['student', 'attempt-review', reviewId],
    queryFn: () => apiClient.get<AttemptReviewResponse>(`/api/attempts/${reviewId}/review`).then((r) => r.data),
  })

  const historyQuery = useQuery({
    queryKey: ['student', 'quiz-attempts', quiz.id],
    queryFn: () => apiClient.get<AttemptSummaryResponse[]>(`/api/quizzes/${quiz.id}/attempts`).then((r) => r.data),
    enabled: showHistory,
  })

  const startMutation = useMutation({
    mutationFn: () => apiClient.post<StartAttemptResponse>(`/api/quizzes/${quiz.id}/attempts`),
    onSuccess: (res) => setActive(res.data),
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-medium text-slate-900">{quiz.title}</h3>
          {quiz.description && <p className="text-sm text-slate-500">{quiz.description}</p>}
          <p className="mt-1 text-xs text-slate-400">
            Đã làm {quiz.attemptsUsed}/{quiz.maxAttempts} lần
            {quiz.bestScore !== null ? ` · Điểm cao nhất: ${quiz.bestScore}` : ''}
            {quiz.timeLimitSeconds ? ` · ${Math.round(quiz.timeLimitSeconds / 60)} phút` : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowHistory((v) => !v)} className="text-xs text-slate-500 hover:underline">
            Lịch sử
          </button>
          <Button size="sm" disabled={!quiz.canAttempt || startMutation.isPending} onClick={() => startMutation.mutate()}>
            {quiz.locked ? 'Đã khóa' : quiz.canAttempt ? 'Làm bài' : 'Hết lượt'}
          </Button>
        </div>
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {showHistory && (
        <ul className="mt-3 space-y-1 border-t border-slate-100 pt-2 text-xs text-slate-500">
          {historyQuery.data?.map((a) => (
            <li key={a.attemptId}>
              Lần {a.attemptNumber}: {a.status} {a.score !== null ? `— ${a.score} điểm` : ''}
              {a.pendingManual && <span className="ml-1 text-amber-600">(chờ chấm tự luận)</span>}
              {a.status !== 'IN_PROGRESS' && a.score !== null && !a.pendingManual && (
                <>
                  <button className="ml-2 text-blue-600 hover:underline" onClick={() => setReviewId(a.attemptId)}>Nhận xét</button>
                  <button className="ml-2 text-blue-600 hover:underline" onClick={() => setAppeal(a)}>Phúc khảo</button>
                </>
              )}
            </li>
          ))}
          {historyQuery.data?.length === 0 && <li>Chưa có lần làm nào.</li>}
        </ul>
      )}
      {appeal && <AppealModal targetType="QUIZ_ATTEMPT" targetId={appeal.attemptId} title={`${quiz.title} (lần ${appeal.attemptNumber})`} onClose={() => setAppeal(null)} />}
      <Modal title="Nhận xét câu tự luận" open={reviewId !== null} onClose={() => setReviewId(null)}>
        {reviewQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
        {reviewQuery.data?.essays.length === 0 && <p className="text-sm text-slate-500">Lượt làm này không có câu tự luận.</p>}
        <div className="max-h-[60vh] space-y-3 overflow-y-auto">
          {reviewQuery.data?.essays.map((e, i) => (
            <div key={i} className="rounded-md border border-slate-200 p-3 text-sm">
              <div className="font-medium text-slate-900">{e.questionText}</div>
              <div className="mt-1 whitespace-pre-wrap rounded bg-slate-50 p-2 text-slate-600">{e.textAnswer || '(bỏ trống)'}</div>
              <div className="mt-2 text-slate-700">{e.graded ? <>Điểm: <strong>{e.awardedScore}/{e.maxPoints}</strong></> : <span className="text-amber-600">Đang chờ giáo viên chấm</span>}</div>
              {e.feedback && <div className="mt-1 text-slate-700"><span className="font-medium">Nhận xét của giáo viên:</span> {e.feedback}</div>}
            </div>
          ))}
        </div>
      </Modal>
      {active && <QuizTakingModal quizId={quiz.id} courseId={courseId} start={active} onClose={() => setActive(null)} />}
    </div>
  )
}

export function QuizzesTab({ courseId }: { courseId: string }) {
  const quizzesQuery = useQuery({
    queryKey: ['student', 'quizzes', courseId],
    queryFn: () => apiClient.get<StudentQuizResponse[]>(`/api/quizzes?courseId=${courseId}`).then((r) => r.data),
  })

  return (
    <div className="space-y-3">
      {quizzesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {quizzesQuery.data?.length === 0 && <p className="text-slate-400">Chưa có quiz nào trong khóa học này.</p>}
      {quizzesQuery.data?.map((quiz) => <QuizRow key={quiz.id} courseId={courseId} quiz={quiz} />)}
    </div>
  )
}
