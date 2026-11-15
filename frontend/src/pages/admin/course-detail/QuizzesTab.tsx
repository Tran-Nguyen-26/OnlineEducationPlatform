import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { QuizGradingPolicy, QuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { QuizItem } from './QuizItem'

const GRADING_POLICY_LABEL: Record<QuizGradingPolicy, string> = {
  HIGHEST: 'Lấy điểm cao nhất',
  LATEST: 'Lấy lần làm gần nhất',
  AVERAGE: 'Lấy điểm trung bình',
}

export function QuizzesTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const quizzesQuery = useQuery({
    queryKey: ['courses', courseId, 'quizzes'],
    queryFn: () => apiClient.get<QuizResponse[]>(`/api/admin/courses/${courseId}/quizzes`).then((r) => r.data),
  })

  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [maxAttempts, setMaxAttempts] = useState('1')
  const [gradingPolicy, setGradingPolicy] = useState<QuizGradingPolicy>('HIGHEST')
  const [timeLimitMinutes, setTimeLimitMinutes] = useState('')
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setTitle('')
    setDescription('')
    setMaxAttempts('1')
    setGradingPolicy('HIGHEST')
    setTimeLimitMinutes('')
    setError(null)
    setOpen(true)
  }

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/admin/courses/${courseId}/quizzes`, {
        title,
        description: description || null,
        maxAttempts: Number(maxAttempts) || 1,
        gradingPolicy,
        timeLimitSeconds: timeLimitMinutes ? Number(timeLimitMinutes) * 60 : null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'quizzes'] })
      setOpen(false)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    createMutation.mutate()
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">Mỗi quiz có thể gắn nhiều câu hỏi trắc nghiệm/điền từ.</p>
        <Button size="sm" onClick={openCreate}>
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

      <Modal title="Tạo quiz" open={open} onClose={() => setOpen(false)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên quiz</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-4" />

          <div className="mb-4 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Số lần làm tối đa</label>
              <Input type="number" min={1} value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Thời gian (phút, tùy chọn)</label>
              <Input type="number" min={1} value={timeLimitMinutes} onChange={(e) => setTimeLimitMinutes(e.target.value)} />
            </div>
          </div>

          <label className="mb-1 block text-sm font-medium text-slate-700">Cách tính điểm khi làm nhiều lần</label>
          <select
            value={gradingPolicy}
            onChange={(e) => setGradingPolicy(e.target.value as QuizGradingPolicy)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            {Object.entries(GRADING_POLICY_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>

          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Đang tạo...' : 'Tạo quiz'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
