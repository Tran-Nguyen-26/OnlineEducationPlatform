import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { BankQuestionResponse } from '../../../types/api'
import { Modal } from '../../../components/ui/Modal'
import { Button } from '../../../components/ui/Button'

/** Pick reusable questions from the course's bank into a quiz; unused questions can be deleted from the bank. */
export function QuestionBankModal({ courseId, quizId, nextPosition, existingIds, onClose }: {
  courseId: string; quizId: string; nextPosition: number; existingIds: string[]; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const bank = useQuery({
    queryKey: ['courses', courseId, 'question-bank'],
    queryFn: () => apiClient.get<BankQuestionResponse[]>(`/api/admin/courses/${courseId}/question-bank`).then((r) => r.data),
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'question-bank'] })
    queryClient.invalidateQueries({ queryKey: ['quizzes', quizId, 'questions'] })
    queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'quizzes'] })
  }
  const add = useMutation({
    mutationFn: (questionId: string) => apiClient.post(`/api/admin/quizzes/${quizId}/questions/from-bank`, { questionId, position: nextPosition, points: 1 }),
    onSuccess: () => { setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const del = useMutation({
    mutationFn: (questionId: string) => apiClient.delete(`/api/admin/question-bank/${questionId}`),
    onSuccess: () => { setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const items = (bank.data ?? []).filter((q) => q.questionText.toLowerCase().includes(search.trim().toLowerCase()))

  return (
    <Modal title="Ngân hàng câu hỏi của khóa" open onClose={onClose}>
      <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm câu hỏi..." className="mb-3 h-9 w-full rounded-md border border-slate-300 px-3 text-sm" />
      {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
      <div className="max-h-96 space-y-2 overflow-y-auto">
        {bank.isLoading && <p className="text-slate-400">Đang tải...</p>}
        {items.map((q) => {
          const inQuiz = existingIds.includes(q.questionId)
          return (
            <div key={q.questionId} className="rounded-md border border-slate-100 p-3">
              <p className="text-sm text-slate-800">{q.questionText}</p>
              <p className="mt-0.5 text-xs text-slate-400">{q.questionType} · dùng trong {q.usedInQuizzes} quiz</p>
              <div className="mt-2 flex gap-2">
                <Button size="sm" variant="secondary" disabled={inQuiz || add.isPending} onClick={() => add.mutate(q.questionId)}>{inQuiz ? 'Đã có trong quiz' : 'Thêm vào quiz'}</Button>
                {q.usedInQuizzes === 0 && <Button size="sm" variant="danger" disabled={del.isPending} onClick={() => del.mutate(q.questionId)}>Xóa khỏi ngân hàng</Button>}
              </div>
            </div>
          )
        })}
        {!bank.isLoading && items.length === 0 && <p className="text-sm text-slate-400">Ngân hàng chưa có câu hỏi nào. Câu hỏi bạn tạo trong các quiz sẽ tự động vào đây.</p>}
      </div>
      <div className="mt-4 flex justify-end"><Button onClick={onClose}>Đóng</Button></div>
    </Modal>
  )
}
