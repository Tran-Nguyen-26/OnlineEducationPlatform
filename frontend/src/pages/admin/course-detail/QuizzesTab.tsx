import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient } from '../../../lib/apiClient'
import type { QuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { QuizItem } from './QuizItem'
import { QuizFormModal } from './QuizFormModal'

export function QuizzesTab({ courseId }: { courseId: string }) {
  const quizzesQuery = useQuery({
    queryKey: ['courses', courseId, 'quizzes'],
    queryFn: () => apiClient.get<QuizResponse[]>(`/api/admin/courses/${courseId}/quizzes`).then((r) => r.data),
  })

  const [open, setOpen] = useState(false)

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">Mỗi quiz có thể gắn nhiều câu hỏi: trắc nghiệm, đúng/sai, điền từ và tự luận (giáo viên chấm tay).</p>
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus size={16} /> Tạo quiz
        </Button>
      </div>

      {quizzesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {quizzesQuery.data?.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-400">
          Chưa có quiz nào.
        </p>
      )}

      <div className="space-y-3">
        {quizzesQuery.data?.map((quiz) => (
          <QuizItem key={quiz.id} quiz={quiz} courseId={courseId} />
        ))}
      </div>

      <QuizFormModal courseId={courseId} open={open} onClose={() => setOpen(false)} />
    </div>
  )
}
