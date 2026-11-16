import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Lock, Plus, Trash2, Unlock } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { QuestionType, QuizQuestionResponse, QuizResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { QuestionBankModal } from './QuestionBankModal'
import { QuizFormModal } from './QuizFormModal'

const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE_CHOICE: 'Một đáp án đúng',
  MULTIPLE_CHOICE: 'Nhiều đáp án đúng',
  TRUE_FALSE: 'Đúng / Sai',
  KEYWORD_FILL: 'Điền từ',
  ESSAY: 'Tự luận (chấm tay)',
}

const CHOICE_TYPES: QuestionType[] = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE']

interface OptionDraft {
  optionText: string
  correct: boolean
}

export function QuizItem({ quiz, courseId }: { quiz: QuizResponse; courseId: string }) {
  const [expanded, setExpanded] = useState(false)
  const queryClient = useQueryClient()

  const invalidateQuizzes = () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'quizzes'] })
  const questionsQuery = useQuery({
    queryKey: ['quizzes', quiz.id, 'questions'],
    queryFn: () => apiClient.get<QuizQuestionResponse[]>(`/api/admin/quizzes/${quiz.id}/questions`).then((r) => r.data),
    enabled: expanded,
  })
  const invalidateQuestions = () => queryClient.invalidateQueries({ queryKey: ['quizzes', quiz.id, 'questions'] })

  const lockMutation = useMutation({
    mutationFn: (locked: boolean) => apiClient.patch(`/api/admin/quizzes/${quiz.id}/lock`, { locked }),
    onSuccess: invalidateQuizzes,
  })

  const deleteQuizMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/admin/quizzes/${quiz.id}`),
    onSuccess: invalidateQuizzes,
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  const removeQuestionMutation = useMutation({
    mutationFn: (questionId: string) => apiClient.delete(`/api/admin/quizzes/${quiz.id}/questions/${questionId}`),
    onSuccess: () => {
      invalidateQuestions()
      invalidateQuizzes()
    },
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  // --- Add question modal ---
  const [open, setOpen] = useState(false)
  const [bankOpen, setBankOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [questionType, setQuestionType] = useState<QuestionType>('SINGLE_CHOICE')
  const [questionText, setQuestionText] = useState('')
  const [keywordAnswer, setKeywordAnswer] = useState('')
  const [points, setPoints] = useState('1')
  const [options, setOptions] = useState<OptionDraft[]>([
    { optionText: '', correct: true },
    { optionText: '', correct: false },
  ])
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setQuestionType('SINGLE_CHOICE')
    setQuestionText('')
    setKeywordAnswer('')
    setPoints('1')
    setOptions([
      { optionText: '', correct: true },
      { optionText: '', correct: false },
    ])
    setError(null)
    setOpen(true)
  }

  function changeType(type: QuestionType) {
    setQuestionType(type)
    if (type === 'TRUE_FALSE') {
      setOptions([
        { optionText: 'Đúng', correct: true },
        { optionText: 'Sai', correct: false },
      ])
    } else if (CHOICE_TYPES.includes(type) && options.length === 0) {
      setOptions([
        { optionText: '', correct: true },
        { optionText: '', correct: false },
      ])
    }
  }

  function setOptionCorrect(index: number) {
    if (questionType === 'MULTIPLE_CHOICE') {
      setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, correct: !o.correct } : o)))
    } else {
      setOptions((prev) => prev.map((o, i) => ({ ...o, correct: i === index })))
    }
  }

  const addQuestionMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/admin/quizzes/${quiz.id}/questions`, {
        questionType,
        questionText,
        keywordAnswer: questionType === 'KEYWORD_FILL' ? keywordAnswer : null,
        position: questionsQuery.data?.length ?? 0,
        points: Number(points) || 1,
        options: CHOICE_TYPES.includes(questionType)
          ? options.map((o, i) => ({ optionText: o.optionText, correct: o.correct, position: i }))
          : null,
      }),
    onSuccess: () => {
      invalidateQuestions()
      invalidateQuizzes()
      setOpen(false)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    addQuestionMutation.mutate()
  }

  const isChoiceType = CHOICE_TYPES.includes(questionType)

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="flex items-center justify-between px-4 py-3">
        <button onClick={() => setExpanded((v) => !v)} className="flex flex-1 items-center gap-2 text-left">
          {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          <span className="font-medium text-slate-900">{quiz.title}</span>
          <span className="text-xs text-slate-400">{quiz.questionCount} câu hỏi{quiz.randomQuestionCount ? ` (rút ${quiz.randomQuestionCount})` : ''} · tối đa {quiz.maxAttempts} lần làm</span>
        </button>
        <div className="flex items-center gap-2">
          {!quiz.locked && <button onClick={() => setEditOpen(true)} className="px-1 text-xs text-blue-600 hover:underline">Sửa</button>}
          {quiz.locked && <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">Đã khóa</span>}
          <button
            onClick={() => lockMutation.mutate(!quiz.locked)}
            className="p-1 text-slate-400 hover:text-slate-700"
            aria-label={quiz.locked ? 'Mở khóa' : 'Khóa'}
            title={quiz.locked ? 'Mở khóa quiz để sửa' : 'Khóa quiz'}
          >
            {quiz.locked ? <Unlock size={16} /> : <Lock size={16} />}
          </button>
          <button onClick={() => deleteQuizMutation.mutate()} className="p-1 text-slate-400 hover:text-red-600" aria-label="Xóa quiz">
            <Trash2 size={16} />
          </button>
        </div>
      </div>

      {expanded && (
        <div className="space-y-2 border-t border-slate-100 px-4 py-3">
          {questionsQuery.data?.map((q, idx) => (
            <div key={q.questionId} className="rounded-md border border-slate-100 bg-slate-50 px-3 py-2">
              <div className="flex items-start justify-between">
                <div>
                  <span className="text-xs text-slate-400">Câu {idx + 1} · {QUESTION_TYPE_LABEL[q.questionType]} · {q.points} điểm</span>
                  <p className="text-sm text-slate-800">{q.questionText}</p>
                  {q.questionType === 'KEYWORD_FILL' ? (
                    <p className="mt-1 text-xs text-slate-500">Đáp án: {q.keywordAnswer}</p>
                  ) : (
                    <ul className="mt-1 space-y-0.5 text-xs text-slate-500">
                      {q.options.map((o) => (
                        <li key={o.id} className={o.correct ? 'font-medium text-green-700' : ''}>
                          {o.correct ? '✓ ' : '• '}
                          {o.optionText}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <button
                  onClick={() => removeQuestionMutation.mutate(q.questionId)}
                  className="p-1 text-slate-400 hover:text-red-600"
                  aria-label="Xóa câu hỏi"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}

          {!quiz.locked && (
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={openCreate}>
                <Plus size={14} /> Thêm câu hỏi
              </Button>
              <Button size="sm" variant="secondary" onClick={() => setBankOpen(true)}>
                Chọn từ ngân hàng
              </Button>
            </div>
          )}
          {quiz.locked && <p className="text-xs text-slate-400">Quiz đang khóa — mở khóa để thêm/sửa câu hỏi.</p>}
        </div>
      )}

      <QuizFormModal courseId={courseId} quiz={quiz} open={editOpen} onClose={() => setEditOpen(false)} />

      {bankOpen && (
        <QuestionBankModal courseId={courseId} quizId={quiz.id} nextPosition={questionsQuery.data?.length ?? 0}
          existingIds={(questionsQuery.data ?? []).map((q) => q.questionId)} onClose={() => setBankOpen(false)} />
      )}

      <Modal title="Thêm câu hỏi" open={open} onClose={() => setOpen(false)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Loại câu hỏi</label>
          <select
            value={questionType}
            onChange={(e) => changeType(e.target.value as QuestionType)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            {Object.entries(QUESTION_TYPE_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>

          <label className="mb-1 block text-sm font-medium text-slate-700">Câu hỏi</label>
          <textarea
            required
            value={questionText}
            onChange={(e) => setQuestionText(e.target.value)}
            rows={2}
            className="mb-4 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />

          <label className="mb-1 block text-sm font-medium text-slate-700">Điểm</label>
          <Input type="number" min={0.25} step={0.25} value={points} onChange={(e) => setPoints(e.target.value)} className="mb-4" />

          {questionType === 'KEYWORD_FILL' && (
            <>
              <label className="mb-1 block text-sm font-medium text-slate-700">Đáp án đúng (từ khóa)</label>
              <Input required value={keywordAnswer} onChange={(e) => setKeywordAnswer(e.target.value)} className="mb-4" />
            </>
          )}

          {isChoiceType && (
            <div className="mb-4">
              <label className="mb-1 block text-sm font-medium text-slate-700">Các đáp án (chọn đáp án đúng)</label>
              <div className="space-y-2">
                {options.map((option, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <input
                      type={questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'}
                      name="correct-option"
                      checked={option.correct}
                      onChange={() => setOptionCorrect(index)}
                    />
                    <Input
                      required
                      value={option.optionText}
                      onChange={(e) =>
                        setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, optionText: e.target.value } : o)))
                      }
                      disabled={questionType === 'TRUE_FALSE'}
                      placeholder={`Đáp án ${index + 1}`}
                    />
                    {questionType !== 'TRUE_FALSE' && options.length > 2 && (
                      <button
                        type="button"
                        onClick={() => setOptions((prev) => prev.filter((_, i) => i !== index))}
                        className="p-1 text-slate-400 hover:text-red-600"
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
              {questionType !== 'TRUE_FALSE' && (
                <button
                  type="button"
                  onClick={() => setOptions((prev) => [...prev, { optionText: '', correct: false }])}
                  className="mt-2 text-xs text-slate-500 hover:underline"
                >
                  + Thêm đáp án
                </button>
              )}
            </div>
          )}

          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={addQuestionMutation.isPending}>
              {addQuestionMutation.isPending ? 'Đang lưu...' : 'Thêm câu hỏi'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
