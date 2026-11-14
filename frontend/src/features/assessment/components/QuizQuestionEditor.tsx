import React, { useRef, useState } from 'react'
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
  QuestionBankItem,
  QuestionOutcomeItem,
} from '@/api/quiz'
import {
  createQuestion,
  updateQuestion,
  deleteQuestion,
  reorderQuestions,
} from '@/api/quiz'
import { exportQuestionBank, importQuestionBank, type TransferFormat } from '../questionBankTransfer'

interface QuizQuestionEditorProps {
  courseId: string
  quiz: QuizItem
  questions: QuestionItem[]
  banks: QuestionBankItem[]
  outcomes: QuestionOutcomeItem[]
  onClose: () => void
  onRefresh: () => void
}

export function QuizQuestionEditor({
  courseId,
  quiz,
  questions,
  banks,
  outcomes,
  onClose,
  onRefresh,
}: QuizQuestionEditorProps) {
  const [editingQ, setEditingQ] = useState<QuestionItem | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [loading, setLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [selectedBankId, setSelectedBankId] = useState('')
  const [selectedOutcomeId, setSelectedOutcomeId] = useState('')
  const [bloomLevel, setBloomLevel] = useState<'REMEMBER' | 'UNDERSTAND' | 'APPLY'>('UNDERSTAND')
  const [transferMessage, setTransferMessage] = useState('')
  const importInput = useRef<HTMLInputElement>(null)

  // Form states
  const [qType, setQType] = useState<QuestionType>('SINGLE_CHOICE')
  const [qText, setQText] = useState('')
  const [keywordAnswer, setKeywordAnswer] = useState('')
  const [qPoints, setQPoints] = useState(1.0)
  const [options, setOptions] = useState<OptionPayload[]>([
    { option_text: '', is_correct: false },
    { option_text: '', is_correct: false },
  ])

  const openCreateForm = () => {
    setEditingQ(null)
    setQType('SINGLE_CHOICE')
    setQText('')
    setKeywordAnswer('')
    setSelectedOutcomeId('')
    setBloomLevel('UNDERSTAND')
    setQPoints(1.0)
    setOptions([
      { option_text: '', is_correct: true },
      { option_text: '', is_correct: false },
      { option_text: '', is_correct: false },
      { option_text: '', is_correct: false },
    ])
    setActionError('')
    setTransferMessage('')
    setIsCreating(true)
  }

  const openEditForm = (q: QuestionItem) => {
    setEditingQ(q)
    setQType(q.question_type)
    setQText(q.question_text)
    setKeywordAnswer(q.keyword_answer ?? '')
    setQPoints(Number(q.points) || 1.0)
    setSelectedBankId(q.question_bank_id ?? '')
    setSelectedOutcomeId(q.learning_outcome_id ?? '')
    setBloomLevel(q.bloom_level ?? 'UNDERSTAND')
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
    if (newType === 'KEYWORD_FILL') {
      setOptions([])
    } else if (newType === 'TRUE_FALSE') {
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
    if (qType === 'KEYWORD_FILL') {
      if (!keywordAnswer.trim()) {
        setActionError('Vui lòng nhập đáp án từ khóa.')
        return
      }
    } else if (qType === 'TRUE_FALSE') {
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
        keyword_answer: qType === 'KEYWORD_FILL' ? keywordAnswer.trim() : undefined,
        points: qPoints,
        question_bank_id: selectedBankId || undefined,
        learning_outcome_id: selectedOutcomeId || undefined,
        bloom_level: bloomLevel,
        options: options.map((opt, idx) => ({
          option_text: opt.option_text.trim(),
          is_correct: opt.is_correct,
          position: idx,
        })),
      }

      if (editingQ) {
        await updateQuestion(courseId, quiz.id, editingQ.id, {
          ...payload,
          clear_question_bank: !selectedBankId,
          clear_learning_outcome: !selectedOutcomeId,
        })
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

  const downloadTransferFile = (format: TransferFormat) => {
    const bankQuestions = selectedBankId
      ? questions.filter((question) => question.question_bank_id === selectedBankId)
      : questions
    const contents = exportQuestionBank(bankQuestions, format)
    const blob = new Blob([contents], { type: format === 'json' ? 'application/json;charset=utf-8' : 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    const safeName = quiz.title.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '') || 'quiz'
    anchor.href = url
    anchor.download = `${safeName}-question-bank.${format}`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  const handleImportFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      setActionError('Tệp nhập không được vượt quá 5 MB.')
      event.target.value = ''
      return
    }
    setLoading(true)
    setActionError('')
    setTransferMessage('')
    let imported = 0
    try {
      const format: TransferFormat = file.name.toLowerCase().endsWith('.csv') ? 'csv' : 'json'
      const payloads = importQuestionBank(await file.text(), format)
      for (const payload of payloads) {
        await createQuestion(courseId, quiz.id, { ...payload, question_bank_id: selectedBankId || undefined })
        imported++
      }
      setTransferMessage(`Đã nhập ${imported} câu hỏi${selectedBankId ? ' vào ngân hàng đã chọn' : ''}.`)
    } catch (err) {
      if (imported > 0) {
        setTransferMessage(`Đã nhập ${imported} câu trước khi gặp lỗi.`)
      }
      setActionError(err instanceof Error ? err.message : 'Không thể nhập tệp câu hỏi.')
    } finally {
      setLoading(false)
      event.target.value = ''
    }
    if (imported > 0) await onRefresh()
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
        <header className="flex min-h-16 flex-wrap items-center justify-between gap-3 border-b bg-slate-50 px-6 py-3">
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

          <div className="flex flex-wrap items-center justify-end gap-2">
            {!isCreating && (
              <>
                <label className="flex items-center gap-2 text-xs font-medium text-slate-600">
                  Ngân hàng
                  <select value={selectedBankId} onChange={(event) => setSelectedBankId(event.target.value)}
                    className="max-w-44 rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs">
                    <option value="">{editingQ ? 'Giữ hiện trạng' : 'Không gắn'}</option>
                    {banks.map((bank) => <option key={bank.id} value={bank.id}>{bank.name}</option>)}
                  </select>
                </label>
                <button type="button" disabled={!(selectedBankId ? questions.some((question) => question.question_bank_id === selectedBankId) : questions.length) || loading} onClick={() => downloadTransferFile('csv')}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 disabled:opacity-50">Xuất CSV</button>
                <button type="button" disabled={!(selectedBankId ? questions.some((question) => question.question_bank_id === selectedBankId) : questions.length) || loading} onClick={() => downloadTransferFile('json')}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 disabled:opacity-50">Xuất JSON</button>
                <input ref={importInput} type="file" accept=".csv,.json,application/json,text/csv" className="hidden" onChange={(event) => void handleImportFile(event)} />
                <button type="button" disabled={loading} onClick={() => importInput.current?.click()}
                  className="rounded-lg border border-teal-700 px-3 py-2 text-xs font-semibold text-teal-800 disabled:opacity-50">Nhập CSV/JSON</button>
                <button
                  type="button"
                  onClick={openCreateForm}
                  className="flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-blue-700 transition"
                >
                  <Plus className="h-4 w-4" />
                  Thêm câu hỏi mới
                </button>
              </>
            )}
            <button
              onClick={onClose}
              className="rounded-lg p-2 text-gray-400 hover:bg-gray-200 transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </header>

        {transferMessage && <p role="status" className="border-b bg-emerald-50 px-6 py-2 text-xs text-emerald-800">{transferMessage}</p>}
        {actionError && !isCreating && <p role="alert" className="border-b bg-rose-50 px-6 py-2 text-xs text-rose-800">{actionError}</p>}

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
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Ngân hàng câu hỏi</label>
                  <select value={selectedBankId} onChange={(event) => setSelectedBankId(event.target.value)}
                    className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none">
                    <option value="">Không gắn ngân hàng</option>
                    {banks.map((bank) => <option key={bank.id} value={bank.id}>{bank.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Learning Outcome</label>
                  <select value={selectedOutcomeId} onChange={(event) => setSelectedOutcomeId(event.target.value)}
                    className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none">
                    <option value="">Chưa gắn outcome</option>
                    {outcomes.map((outcome) => <option key={outcome.id} value={outcome.id}>{outcome.code} · {outcome.description}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Mức Bloom</label>
                  <select value={bloomLevel} onChange={(event) => setBloomLevel(event.target.value as typeof bloomLevel)}
                    className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none">
                    <option value="REMEMBER">REMEMBER · Nhớ</option>
                    <option value="UNDERSTAND">UNDERSTAND · Hiểu</option>
                    <option value="APPLY">APPLY · Vận dụng</option>
                  </select>
                </div>
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
                    <option value="KEYWORD_FILL">Điền từ khóa</option>
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

              {qType === 'KEYWORD_FILL' && <label className="grid gap-1 text-xs font-semibold text-gray-700">
                Đáp án từ khóa <span className="text-red-500">*</span>
                <input value={keywordAnswer} onChange={(event) => setKeywordAnswer(event.target.value)} maxLength={255} required
                  className="w-full rounded-xl border border-gray-300 p-2.5 text-sm font-normal focus:border-blue-500 focus:outline-none" />
              </label>}

              {/* Options Section */}
              {qType !== 'KEYWORD_FILL' && <div className="space-y-3">
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
              </div>}

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
                                  : q.question_type === 'KEYWORD_FILL'
                                    ? 'Điền từ khóa'
                                    : 'Nhiều đáp án'}
                            </span>
                            <span>•</span>
                            <span>{q.points} điểm</span>
                          </div>
                          {(q.learning_outcome_id || q.bloom_level) && <p className="mt-1 text-xs text-slate-500">
                            {q.learning_outcome_id && outcomes.find((outcome) => outcome.id === q.learning_outcome_id)?.code}
                            {q.learning_outcome_id && q.bloom_level && ' · '}
                            {q.bloom_level && `Bloom ${q.bloom_level}`}
                          </p>}
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
