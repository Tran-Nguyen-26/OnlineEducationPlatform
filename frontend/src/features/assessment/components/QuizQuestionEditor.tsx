import React, { useState } from 'react'
import {
  X,
  Plus,
  Trash2,
  ArrowUp,
  ArrowDown,
  CheckCircle,
  HelpCircle,
  AlertCircle,
  Loader2,
  Edit2,
} from 'lucide-react'
import type {
  QuizItem,
  QuestionItem,
  QuestionType,
  CreateQuestionPayload,
  OptionPayload,
} from '@/api/quiz'
import {
  createQuestion,
  updateQuestion,
  deleteQuestion,
  reorderQuestions,
} from '@/api/quiz'

interface QuizQuestionEditorProps {
  courseId: string
  quiz: QuizItem
  questions: QuestionItem[]
  onClose: () => void
  onRefresh: () => void
}

export function QuizQuestionEditor({
  courseId,
  quiz,
  questions,
  onClose,
  onRefresh,
}: QuizQuestionEditorProps) {
  const [editingQ, setEditingQ] = useState<QuestionItem | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [loading, setLoading] = useState(false)
  const [actionError, setActionError] = useState('')

  // Form states
  const [qType, setQType] = useState<QuestionType>('SINGLE_CHOICE')
  const [qText, setQText] = useState('')
  const [qPoints, setQPoints] = useState(1.0)
  const [options, setOptions] = useState<OptionPayload[]>([
    { option_text: '', is_correct: false },
    { option_text: '', is_correct: false },
  ])

  const openCreateForm = () => {
    setEditingQ(null)
    setQType('SINGLE_CHOICE')
    setQText('')
    setQPoints(1.0)
    setOptions([
      { option_text: '', is_correct: true },
      { option_text: '', is_correct: false },
      { option_text: '', is_correct: false },
      { option_text: '', is_correct: false },
    ])
    setActionError('')
    setIsCreating(true)
  }

  const openEditForm = (q: QuestionItem) => {
    setEditingQ(q)
    setQType(q.question_type)
    setQText(q.question_text)
    setQPoints(Number(q.points) || 1.0)
    setOptions(
      q.options.map((o) => ({
        option_text: o.option_text,
        is_correct: o.is_correct,
        position: o.position,
      }))
    )
    setActionError('')
    setIsCreating(true)
  }

  const handleTypeChange = (newType: QuestionType) => {
    setQType(newType)
    if (newType === 'TRUE_FALSE') {
      setOptions([
        { option_text: 'Đúng', is_correct: true, position: 0 },
        { option_text: 'Sai', is_correct: false, position: 1 },
      ])
    } else if (options.length < 2) {
      setOptions([
        { option_text: '', is_correct: true, position: 0 },
        { option_text: '', is_correct: false, position: 1 },
      ])
    }
  }

  const toggleOptionCorrect = (index: number) => {
    if (qType === 'SINGLE_CHOICE' || qType === 'TRUE_FALSE') {
      setOptions((prev) =>
        prev.map((opt, i) => ({
          ...opt,
          is_correct: i === index,
        }))
      )
    } else {
      setOptions((prev) =>
        prev.map((opt, i) => (i === index ? { ...opt, is_correct: !opt.is_correct } : opt))
      )
    }
  }

  const updateOptionText = (index: number, text: string) => {
    setOptions((prev) =>
      prev.map((opt, i) => (i === index ? { ...opt, option_text: text } : opt))
    )
  }

  const addOption = () => {
    setOptions((prev) => [...prev, { option_text: '', is_correct: false }])
  }

  const removeOption = (index: number) => {
    if (options.length <= 2) return
    setOptions((prev) => prev.filter((_, i) => i !== index))
  }

  const handleSaveQuestion = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionError('')

    if (!qText.trim()) {
      setActionError('Vui lòng nhập nội dung câu hỏi.')
      return
    }

    // Validate options
    if (qType === 'TRUE_FALSE') {
      if (options.length !== 2) {
        setActionError('Câu hỏi Đúng/Sai phải có chính xác 2 phương án.')
        return
      }
      const correctCount = options.filter((o) => o.is_correct).length
      if (correctCount !== 1) {
        setActionError('Câu hỏi Đúng/Sai phải chọn đúng 1 đáp án chính xác.')
        return
      }
    } else if (qType === 'SINGLE_CHOICE') {
      if (options.length < 2) {
        setActionError('Câu hỏi trắc nghiệm 1 đáp án phải có ít nhất 2 phương án.')
        return
      }
      const correctCount = options.filter((o) => o.is_correct).length
      if (correctCount !== 1) {
        setActionError('Vui lòng chọn chính xác 1 đáp án đúng.')
        return
      }
    } else if (qType === 'MULTIPLE_CHOICE') {
      if (options.length < 2) {
        setActionError('Câu hỏi nhiều đáp án phải có ít nhất 2 phương án.')
        return
      }
      const correctCount = options.filter((o) => o.is_correct).length
      if (correctCount < 1) {
        setActionError('Vui lòng chọn ít nhất 1 đáp án đúng.')
        return
      }
    }

    for (let i = 0; i < options.length; i++) {
      if (!options[i].option_text.trim()) {
        setActionError(`Phương án ${i + 1} chưa có nội dung.`)
        return
      }
    }

    setLoading(true)
    try {
      const payload: CreateQuestionPayload = {
        question_type: qType,
        question_text: qText.trim(),
        points: qPoints,
        options: options.map((opt, idx) => ({
          option_text: opt.option_text.trim(),
          is_correct: opt.is_correct,
          position: idx,
        })),
      }

      if (editingQ) {
        await updateQuestion(courseId, quiz.id, editingQ.id, payload)
      } else {
        await createQuestion(courseId, quiz.id, payload)
      }

      setIsCreating(false)
      setEditingQ(null)
      onRefresh()
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message)
      } else {
        setActionError('Lỗi lưu câu hỏi. Vui lòng thử lại.')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteQuestion = async (qId: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa câu hỏi này khỏi đề thi?')) return
    setLoading(true)
    try {
      await deleteQuestion(courseId, quiz.id, qId)
      onRefresh()
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi xóa câu hỏi')
    } finally {
      setLoading(false)
    }
  }

  const handleMoveQuestion = async (index: number, direction: 'UP' | 'DOWN') => {
    if (direction === 'UP' && index === 0) return
    if (direction === 'DOWN' && index === questions.length - 1) return

    const newOrder = [...questions]
    const targetIdx = direction === 'UP' ? index - 1 : index + 1
    const temp = newOrder[index]
    newOrder[index] = newOrder[targetIdx]
    newOrder[targetIdx] = temp

    const orderedIds = newOrder.map((q) => q.id)
    setLoading(true)
    try {
      await reorderQuestions(courseId, quiz.id, orderedIds)
      onRefresh()
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi đổi thứ tự câu hỏi')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-fadeIn">
      <div className="flex h-[90vh] w-full max-w-5xl flex-col rounded-3xl bg-white shadow-2xl overflow-hidden">
        {/* Header */}
        <header className="flex h-16 items-center justify-between border-b px-6 bg-slate-50">
          <div>
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <span>Soạn câu hỏi đề thi:</span>
              <span className="text-blue-600 font-semibold">{quiz.title}</span>
            </h2>
            <p className="text-xs text-gray-500">
              Tổng số câu: {questions.length} | Tổng điểm:{' '}
              {questions.reduce((sum, q) => sum + (Number(q.points) || 1), 0).toFixed(1)}
            </p>
          </div>

          <div className="flex items-center gap-3">
            {!isCreating && (
              <button
                onClick={openCreateForm}
                className="flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-blue-700 transition"
              >
                <Plus className="h-4 w-4" />
                Thêm câu hỏi mới
              </button>
            )}
            <button
              onClick={onClose}
              className="rounded-lg p-2 text-gray-400 hover:bg-gray-200 transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </header>

        {/* Content Body */}
        <div className="flex flex-1 overflow-hidden">
          {isCreating ? (
            /* ── Add / Edit Form ── */
            <form onSubmit={handleSaveQuestion} className="flex-1 overflow-y-auto p-8 space-y-6">
              <div className="flex items-center justify-between border-b pb-4">
                <h3 className="text-base font-bold text-gray-900">
                  {editingQ ? 'Chỉnh sửa câu hỏi' : 'Soạn câu hỏi mới'}
                </h3>
                <button
                  type="button"
                  onClick={() => setIsCreating(false)}
                  className="text-xs font-medium text-gray-500 hover:text-gray-800"
                >
                  Hủy bỏ
                </button>
              </div>

              {actionError && (
                <div className="flex items-center gap-2 rounded-xl bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{actionError}</span>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Loại câu hỏi
                  </label>
                  <select
                    value={qType}
                    onChange={(e) => handleTypeChange(e.target.value as QuestionType)}
                    className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  >
                    <option value="SINGLE_CHOICE">Trắc nghiệm chọn 1 đáp án</option>
                    <option value="MULTIPLE_CHOICE">Trắc nghiệm chọn nhiều đáp án</option>
                    <option value="TRUE_FALSE">Đúng / Sai</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Điểm số
                  </label>
                  <input
                    type="number"
                    step="0.5"
                    min="0.1"
                    value={qPoints}
                    onChange={(e) => setQPoints(parseFloat(e.target.value) || 1)}
                    className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Nội dung câu hỏi <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows={3}
                  value={qText}
                  onChange={(e) => setQText(e.target.value)}
                  placeholder="Nhập nội dung câu hỏi tại đây..."
                  className="w-full rounded-xl border border-gray-300 p-3 text-sm focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              {/* Options Section */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-bold text-gray-800 uppercase tracking-wider">
                    Các phương án trả lời ({options.length})
                  </label>
                  {qType !== 'TRUE_FALSE' && (
                    <button
                      type="button"
                      onClick={addOption}
                      className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1"
                    >
                      <Plus className="h-3.5 w-3.5" />
                      Thêm phương án
                    </button>
                  )}
                </div>

                <div className="space-y-2.5">
                  {options.map((opt, idx) => (
                    <div
                      key={idx}
                      className={`flex items-center gap-3 rounded-xl border p-2.5 transition ${
                        opt.is_correct
                          ? 'border-emerald-400 bg-emerald-50/50'
                          : 'border-gray-200 bg-white'
                      }`}
                    >
                      {/* Checkbox / Radio indicator */}
                      <button
                        type="button"
                        onClick={() => toggleOptionCorrect(idx)}
                        className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-lg transition ${
                          opt.is_correct
                            ? 'bg-emerald-600 text-white'
                            : 'border border-gray-300 bg-white text-transparent hover:border-gray-400'
                        }`}
                        title="Đánh dấu đáp án đúng"
                      >
                        <CheckCircle className="h-4 w-4" />
                      </button>

                      <span className="text-xs font-bold text-gray-500 shrink-0">
                        {String.fromCharCode(65 + idx)}.
                      </span>

                      <input
                        type="text"
                        value={opt.option_text}
                        disabled={qType === 'TRUE_FALSE'}
                        onChange={(e) => updateOptionText(idx, e.target.value)}
                        placeholder={`Nội dung phương án ${String.fromCharCode(65 + idx)}`}
                        className="flex-1 text-sm bg-transparent border-0 focus:outline-none"
                        required
                      />

                      {qType !== 'TRUE_FALSE' && options.length > 2 && (
                        <button
                          type="button"
                          onClick={() => removeOption(idx)}
                          className="text-gray-400 hover:text-red-500 p-1"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Form Buttons */}
              <div className="flex justify-end gap-3 pt-6 border-t">
                <button
                  type="button"
                  onClick={() => setIsCreating(false)}
                  className="rounded-xl border border-gray-300 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-100 transition"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white shadow-md hover:bg-blue-700 disabled:opacity-50 transition"
                >
                  {loading && <Loader2 className="h-4 w-4 animate-spin" />}
                  {editingQ ? 'Cập nhật câu hỏi' : 'Lưu câu hỏi'}
                </button>
              </div>
            </form>
          ) : (
            /* ── Questions List ── */
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
              {questions.length === 0 ? (
                <div className="py-16 text-center text-gray-400">
                  <HelpCircle className="mx-auto mb-3 h-12 w-12 text-gray-300" />
                  <p className="text-base font-semibold text-gray-700">Chưa có câu hỏi nào</p>
                  <p className="text-sm mt-1">Bấm nút "Thêm câu hỏi mới" phía trên để bắt đầu soạn đề.</p>
                </div>
              ) : (
                questions.map((q, idx) => (
                  <div
                    key={q.id}
                    className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs hover:border-gray-300 transition"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex items-start gap-3">
                        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-xs font-bold text-blue-700">
                          {idx + 1}
                        </span>
                        <div>
                          <p className="text-sm font-semibold text-gray-900">{q.question_text}</p>
                          <div className="mt-1 flex items-center gap-3 text-xs text-gray-500">
                            <span className="font-medium text-blue-600">
                              {q.question_type === 'SINGLE_CHOICE'
                                ? 'Trắc nghiệm 1 đáp án'
                                : q.question_type === 'TRUE_FALSE'
                                  ? 'Đúng/Sai'
                                  : 'Nhiều đáp án'}
                            </span>
                            <span>•</span>
                            <span>{q.points} điểm</span>
                          </div>
                        </div>
                      </div>

                      {/* Actions */}
                      <div className="flex items-center gap-1">
                        <button
                          disabled={idx === 0 || loading}
                          onClick={() => handleMoveQuestion(idx, 'UP')}
                          className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-20"
                          title="Di chuyển lên"
                        >
                          <ArrowUp className="h-4 w-4" />
                        </button>
                        <button
                          disabled={idx === questions.length - 1 || loading}
                          onClick={() => handleMoveQuestion(idx, 'DOWN')}
                          className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-20"
                          title="Di chuyển xuống"
                        >
                          <ArrowDown className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => openEditForm(q)}
                          className="rounded-lg p-1.5 text-gray-400 hover:bg-blue-50 hover:text-blue-600 ml-2"
                          title="Sửa"
                        >
                          <Edit2 className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => handleDeleteQuestion(q.id)}
                          className="rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600"
                          title="Xóa"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    {/* Options Preview */}
                    <div className="mt-3 grid grid-cols-2 gap-2 pt-3 border-t border-gray-100 text-xs">
                      {q.options.map((opt, oIdx) => (
                        <div
                          key={opt.id}
                          className={`flex items-center gap-2 rounded-lg px-2.5 py-1.5 ${
                            opt.is_correct
                              ? 'bg-emerald-50 text-emerald-800 font-medium'
                              : 'text-gray-600'
                          }`}
                        >
                          <span className="font-bold">{String.fromCharCode(65 + oIdx)}.</span>
                          <span className="truncate">{opt.option_text}</span>
                          {opt.is_correct && (
                            <CheckCircle className="h-3 w-3 shrink-0 text-emerald-600 ml-auto" />
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
