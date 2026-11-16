import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { CourseContentResponse, QuizGradingPolicy, QuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'

const GRADING_POLICY_LABEL: Record<QuizGradingPolicy, string> = {
  HIGHEST: 'Lấy điểm cao nhất',
  LATEST: 'Lấy lần làm gần nhất',
  AVERAGE: 'Lấy điểm trung bình',
}

function toLocal(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

/** Create or edit a quiz (quiz = existing quiz → edit mode). */
export function QuizFormModal({ courseId, quiz, open, onClose }: { courseId: string; quiz?: QuizResponse; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const content = useQuery({
    enabled: open,
    queryKey: ['courses', courseId, 'content-outline'],
    queryFn: () => apiClient.get<CourseContentResponse>(`/api/courses/${courseId}/content`).then((r) => r.data),
  })
  const lessons = (content.data?.modules ?? []).flatMap((m) => m.lessons.map((l) => ({ id: l.id, label: `${m.title} › ${l.title}` })))

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [lessonId, setLessonId] = useState('')
  const [maxAttempts, setMaxAttempts] = useState('1')
  const [policy, setPolicy] = useState<QuizGradingPolicy>('HIGHEST')
  const [minutes, setMinutes] = useState('')
  const [deadline, setDeadline] = useState('')
  const [randomCount, setRandomCount] = useState('')
  const [shuffleQ, setShuffleQ] = useState(false)
  const [shuffleA, setShuffleA] = useState(false)
  const [showScore, setShowScore] = useState(true)
  const [showCorrect, setShowCorrect] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setTitle(quiz?.title ?? '')
    setDescription(quiz?.description ?? '')
    setLessonId(quiz?.lessonId ?? '')
    setMaxAttempts(String(quiz?.maxAttempts ?? 1))
    setPolicy(quiz?.gradingPolicy ?? 'HIGHEST')
    setMinutes(quiz?.timeLimitSeconds ? String(Math.round(quiz.timeLimitSeconds / 60)) : '')
    setDeadline(toLocal(quiz?.deadline ?? null))
    setRandomCount(quiz?.randomQuestionCount ? String(quiz.randomQuestionCount) : '')
    setShuffleQ(quiz?.randomizeQuestions ?? false)
    setShuffleA(quiz?.randomizeAnswers ?? false)
    setShowScore(quiz?.showScore ?? true)
    setShowCorrect(quiz?.showCorrectAnswers ?? false)
    setError(null)
  }, [open, quiz])

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title,
        description: description || null,
        lessonId: lessonId || null,
        maxAttempts: Number(maxAttempts) || 1,
        gradingPolicy: policy,
        timeLimitSeconds: minutes ? Number(minutes) * 60 : null,
        deadline: deadline ? new Date(deadline).toISOString() : null,
        randomQuestionCount: randomCount ? Number(randomCount) : null,
        randomizeQuestions: shuffleQ,
        randomizeAnswers: shuffleA,
        showScore,
        showCorrectAnswers: showCorrect,
      }
      return quiz ? apiClient.patch(`/api/admin/quizzes/${quiz.id}`, body) : apiClient.post(`/api/admin/courses/${courseId}/quizzes`, body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'quizzes'] })
      onClose()
    },
    onError: (e) => setError(getApiErrorMessage(e)),
  })

  function submit(e: FormEvent) { e.preventDefault(); save.mutate() }
  const lbl = 'mb-1 block text-sm font-medium text-slate-700'
  const check = 'flex items-center gap-2 text-sm text-slate-700'

  return (
    <Modal title={quiz ? 'Sửa quiz' : 'Tạo quiz'} open={open} onClose={onClose}>
      <form onSubmit={submit} className="max-h-[75vh] overflow-y-auto pr-1">
        <label className={lbl}>Tên quiz</label>
        <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-3" />
        <label className={lbl}>Mô tả</label>
        <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-3" />

        <label className={lbl}>Gắn với bài học (hiện cuối bài đó trên trang học)</label>
        <select value={lessonId} onChange={(e) => setLessonId(e.target.value)} className="mb-3 h-10 w-full rounded-md border border-slate-300 px-3 text-sm">
          <option value="">— Không gắn bài học (chỉ ở mục Bài kiểm tra) —</option>
          {lessons.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
        </select>

        <div className="mb-3 grid grid-cols-2 gap-3">
          <div><label className={lbl}>Số lần làm tối đa</label><Input type="number" min={1} value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} /></div>
          <div><label className={lbl}>Thời gian (phút)</label><Input type="number" min={1} value={minutes} onChange={(e) => setMinutes(e.target.value)} /></div>
        </div>
        <label className={lbl}>Hạn làm bài (tùy chọn)</label>
        <Input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} className="mb-3" />

        <label className={lbl}>Cách tính điểm khi làm nhiều lần</label>
        <select value={policy} onChange={(e) => setPolicy(e.target.value as QuizGradingPolicy)} className="mb-3 h-10 w-full rounded-md border border-slate-300 px-3 text-sm">
          {Object.entries(GRADING_POLICY_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>

        <div className="mb-3 rounded-md border border-slate-200 bg-slate-50 p-3">
          <label className={lbl}>Rút ngẫu nhiên N câu từ các câu hỏi của quiz (để trống = dùng tất cả)</label>
          <Input type="number" min={1} value={randomCount} onChange={(e) => setRandomCount(e.target.value)} className="mb-2" />
          <p className="text-xs text-slate-400">Mỗi lượt làm bốc N câu khác nhau; điểm được quy về thang tổng điểm của toàn bộ câu hỏi.</p>
          <div className="mt-3 space-y-1.5">
            <label className={check}><input type="checkbox" checked={shuffleQ} onChange={(e) => setShuffleQ(e.target.checked)} /> Trộn thứ tự câu hỏi</label>
            <label className={check}><input type="checkbox" checked={shuffleA} onChange={(e) => setShuffleA(e.target.checked)} /> Trộn thứ tự đáp án</label>
          </div>
        </div>
        <div className="mb-4 space-y-1.5">
          <label className={check}><input type="checkbox" checked={showScore} onChange={(e) => setShowScore(e.target.checked)} /> Cho học sinh xem điểm sau khi nộp</label>
          <label className={check}><input type="checkbox" checked={showCorrect} onChange={(e) => setShowCorrect(e.target.checked)} /> Cho xem đúng/sai từng câu</label>
        </div>

        {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Hủy</Button>
          <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Đang lưu...' : quiz ? 'Lưu' : 'Tạo quiz'}</Button>
        </div>
      </form>
    </Modal>
  )
}
