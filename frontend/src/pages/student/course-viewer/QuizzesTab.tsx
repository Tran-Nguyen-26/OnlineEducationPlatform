import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AttemptSummaryResponse, StartAttemptResponse, StudentQuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { QuizTakingModal } from './QuizTakingModal'

function QuizRow({ courseId, quiz }: { courseId: string; quiz: StudentQuizResponse }) {
  const [active, setActive] = useState<StartAttemptResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState(false)

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
            </li>
          ))}
          {historyQuery.data?.length === 0 && <li>Chưa có lần làm nào.</li>}
        </ul>
      )}
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
