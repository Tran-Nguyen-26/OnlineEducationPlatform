import { useState, useEffect, useCallback, useRef } from 'react'
import {
  Clock,
  CheckCircle2,
  AlertCircle,
  Flag,
  ArrowRight,
  ArrowLeft,
  X,
  Trophy,
} from 'lucide-react'
import { submitLearnerQuiz, syncExamDraft, type QuizItem, type LearnerAttempt, type QuestionItem } from '@/api/quiz'
import { readExamDraft, writeExamDraft } from '../examDraft'

interface StudentQuizPlayerProps {
  quiz: QuizItem
  attempt?: LearnerAttempt
  previewQuestions?: QuestionItem[]
  onClose: () => void
  onCompleted?: (score: number, totalPoints: number) => void
}

export function StudentQuizPlayer({
  quiz,
  attempt,
  previewQuestions,
  onClose,
  onCompleted,
}: StudentQuizPlayerProps) {
  const questions = attempt?.questions ?? previewQuestions ?? []
  const massExam = quiz.assessment_kind === 'MASS_EXAM' && !!attempt
  const serverClock = useRef({ server: Date.parse(attempt?.serverTime ?? ''), client: performance.now() })
  const remaining = useCallback(() => Math.max(0, Math.ceil((Date.parse(attempt?.expiresAt ?? '')
    - serverClock.current.server - (performance.now() - serverClock.current.client)) / 1000)), [attempt])
  const [currentIndex, setCurrentIndex] = useState(0)
  // answers map: questionId -> array of selected optionIds (or string for text)
  const [answers, setAnswers] = useState<Record<string, string[]>>(attempt?.draft ?? {})
  const [restored, setRestored] = useState(!massExam)
  const [syncState, setSyncState] = useState('')
  const sent = useRef(attempt?.draft ?? {})
  const sequence = useRef(attempt?.draftSequence ?? 0)
  const syncInFlight = useRef(false)
  const [flagged, setFlagged] = useState<Record<string, boolean>>({})
  const [showConfirmModal, setShowConfirmModal] = useState(false)
  const [isSubmitted, setIsSubmitted] = useState(false)
  const [scoreResult, setScoreResult] = useState<{
    achieved: number
    total: number
    correctCount: number
  } | null>(null)

  const [secondsLeft, setSecondsLeft] = useState(() => attempt
    ? remaining()
    : quiz.time_limit_seconds ?? 1800)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const inFlight = useRef(false)
  const autoSubmitted = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  const handleSubmitQuiz = useCallback(async () => {
    if (inFlight.current || isSubmitted) return
    inFlight.current = true
    setSubmitting(true)
    setShowConfirmModal(false)
    setSubmitError('')
    try {
      const result = attempt ? await submitLearnerQuiz(quiz.course_id, attempt.attemptId, answers)
        : (previewQuestions ?? []).reduce((score, question) => {
            const selected = answers[question.id] ?? []
            const correct = question.options.filter((option) => option.is_correct).map((option) => option.id)
            const matches = correct.length > 0 && selected.length === correct.length
              && selected.every((id) => correct.includes(id))
            return {
              achieved: score.achieved + (matches ? Number(question.points) : 0),
              total: score.total + Number(question.points),
              correctCount: score.correctCount + Number(matches),
            }
          }, { achieved: 0, total: 0, correctCount: 0 })
      setScoreResult(result)
      setIsSubmitted(true)
      if (massExam && attempt) void writeExamDraft(attempt.attemptId).catch(() => {})
      onCompleted?.(result.achieved, result.total)
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Không nộp được bài. Hãy thử lại.')
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }, [answers, attempt, isSubmitted, massExam, onCompleted, previewQuestions, quiz.course_id])

  useEffect(() => {
    if (!massExam || !attempt) return
    let active = true
    void readExamDraft(attempt.attemptId).then((saved) => {
      if (active && saved && remaining() > 0) setAnswers({ ...attempt.draft, ...saved })
    }).catch(() => { if (active) setSyncState('Không đọc được bản nháp trên thiết bị.') })
      .finally(() => { if (active) setRestored(true) })
    return () => { active = false }
  }, [attempt, massExam, remaining])

  useEffect(() => {
    if (!massExam || !attempt || !restored || isSubmitted || remaining() === 0) return
    void writeExamDraft(attempt.attemptId, answers).catch(() => setSyncState('Không lưu được bản nháp trên thiết bị.'))
  }, [answers, attempt, isSubmitted, massExam, remaining, restored])

  const currentAnswers = useRef(answers)
  useEffect(() => { currentAnswers.current = answers }, [answers])
  const nearEnd = secondsLeft <= 60
  useEffect(() => {
    if (!massExam || !attempt || !restored || isSubmitted) return
    async function sync() {
      if (!attempt || remaining() === 0 || syncInFlight.current || inFlight.current) return
      const delta = Object.fromEntries(Object.entries(currentAnswers.current)
        .filter(([id, value]) => JSON.stringify(value) !== JSON.stringify(sent.current[id])))
      if (!Object.keys(delta).length) return
      syncInFlight.current = true
      try {
        const result = await syncExamDraft(quiz.course_id, attempt.attemptId, ++sequence.current, delta)
        sequence.current = result.sequence
        sent.current = { ...sent.current, ...delta }
        setSyncState('Đã đồng bộ bản nháp.')
      } catch {
        setSyncState('Chưa đồng bộ. Bản nháp được giữ trên thiết bị.')
      } finally { syncInFlight.current = false }
    }
    const timer = window.setInterval(() => void sync(), nearEnd ? 5000 : 20000)
    return () => window.clearInterval(timer)
  }, [attempt, isSubmitted, massExam, nearEnd, quiz.course_id, remaining, restored])

  useEffect(() => {
    if (isSubmitted) return
    const update = () => setSecondsLeft((previous) => attempt
      ? remaining()
      : Math.max(0, previous - 1))
    const timer = window.setInterval(update, 1000)
    return () => window.clearInterval(timer)
  }, [attempt, isSubmitted, remaining])

  useEffect(() => {
    if (secondsLeft === 0 && !autoSubmitted.current) {
      autoSubmitted.current = true
      const delay = massExam ? Math.floor(Math.random() * 10001) : 0
      window.setTimeout(() => { if (mounted.current) void handleSubmitQuiz() }, delay)
    }
  }, [handleSubmitQuiz, massExam, secondsLeft])

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60)
    const s = secs % 60
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }

  const currentQ = questions[currentIndex]
  const totalQuestions = questions.length

  const handleSelectOption = (optionId: string, isSingleChoice: boolean) => {
    if (!currentQ || (massExam && (!restored || remaining() === 0))) return
    const qId = currentQ.id
    setAnswers((prev) => {
      const existing = prev[qId] || []
      if (isSingleChoice) {
        return { ...prev, [qId]: [optionId] }
      } else {
        // Toggle in array
        if (existing.includes(optionId)) {
          return { ...prev, [qId]: existing.filter((id) => id !== optionId) }
        } else {
          return { ...prev, [qId]: [...existing, optionId] }
        }
      }
    })
  }

  const toggleFlag = (qId: string) => {
    setFlagged((prev) => ({ ...prev, [qId]: !prev[qId] }))
  }

  const answeredCount = Object.keys(answers).filter(
    (k) => answers[k] && answers[k].length > 0
  ).length

  if (totalQuestions === 0) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
        <div className="w-full max-w-md rounded-2xl bg-white p-6 text-center shadow-2xl">
          <AlertCircle className="mx-auto mb-3 h-12 w-12 text-amber-500" />
          <h3 className="text-xl font-bold text-gray-900">Bài thi chưa có câu hỏi</h3>
          <p className="mt-2 text-sm text-gray-500">
            Giảng viên hiện chưa thêm câu hỏi vào bài thi này. Vui lòng quay lại sau!
          </p>
          <button
            onClick={onClose}
            className="mt-6 w-full rounded-xl bg-gray-900 py-2.5 text-sm font-semibold text-white transition hover:bg-gray-800"
          >
            Đóng
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-slate-900 text-slate-100">
      {/* ── Top Header ── */}
      <header className="flex h-16 items-center justify-between border-b border-slate-800 bg-slate-950 px-6">
        <div className="flex items-center gap-4">
          <span className="rounded-lg bg-blue-600/20 px-2.5 py-1 text-xs font-semibold text-blue-400 border border-blue-500/30">
            Exam Mode
          </span>
          <h1 className="text-base font-semibold text-white truncate max-w-md">{quiz.title}</h1>
        </div>

        <div className="flex items-center gap-6">
          {!isSubmitted && (
            <div
              className={`flex items-center gap-2 rounded-xl px-4 py-1.5 font-mono text-base font-bold shadow-sm ${
                secondsLeft < 300
                  ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30 animate-pulse'
                  : 'bg-slate-800 text-slate-200 border border-slate-700'
              }`}
            >
              <Clock className="h-4 w-4" />
              <span>{formatTime(secondsLeft)}</span>
            </div>
          )}

          <button
            onClick={onClose}
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition"
            title="Thoát"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      </header>

      {/* ── Main Content Area ── */}
      {!isSubmitted ? (
        <div className="flex flex-1 overflow-hidden">
          {/* Left / Center: Question Card */}
          <main className="flex-1 overflow-y-auto p-8 flex flex-col items-center">
            <div className="w-full max-w-3xl space-y-6">
              {/* Question Meta & Flag */}
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold tracking-wider uppercase text-blue-400">
                  Câu hỏi {currentIndex + 1} / {totalQuestions}
                  <span className="ml-2 text-xs font-normal text-slate-400">
                    ({currentQ.points} điểm)
                  </span>
                </span>

                <button
                  onClick={() => toggleFlag(currentQ.id)}
                  className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                    flagged[currentQ.id]
                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                      : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                  }`}
                >
                  <Flag className="h-3.5 w-3.5 fill-current" />
                  <span>{flagged[currentQ.id] ? 'Đã đánh dấu' : 'Đánh dấu xem lại'}</span>
                </button>
              </div>

              {/* Question Text */}
              <div className="rounded-2xl border border-slate-800 bg-slate-950 p-6 shadow-xl">
                <p className="text-lg font-medium leading-relaxed text-white">
                  {currentQ.question_text}
                </p>
                <div className="mt-2 text-xs text-slate-400">
                  Loại câu hỏi:{' '}
                  {currentQ.question_type === 'SINGLE_CHOICE'
                    ? 'Chọn 1 đáp án'
                    : currentQ.question_type === 'TRUE_FALSE'
                      ? 'Đúng hoặc Sai'
                      : currentQ.question_type === 'KEYWORD_FILL' ? 'Điền câu trả lời' : 'Chọn nhiều đáp án'}
                </div>
              </div>

              {/* Options */}
              <div className="space-y-3">
                {currentQ.question_type === 'KEYWORD_FILL' && (
                  <input aria-label="Câu trả lời" maxLength={500} value={answers[currentQ.id]?.[0] ?? ''}
                    disabled={massExam && (!restored || secondsLeft === 0)}
                    onChange={(event) => {
                      const value = event.target.value
                      setAnswers((previous) => ({ ...previous, [currentQ.id]: value ? [value] : [] }))
                    }}
                    className="w-full rounded-xl border border-slate-700 bg-slate-950 p-3" />
                )}
                {currentQ.options.map((opt, idx) => {
                  const letter = String.fromCharCode(65 + idx)
                  const isSelected = (answers[currentQ.id] || []).includes(opt.id)
                  const isSingle =
                    currentQ.question_type === 'SINGLE_CHOICE' ||
                    currentQ.question_type === 'TRUE_FALSE'

                  return (
                    <button
                      key={opt.id}
                      disabled={massExam && (!restored || secondsLeft === 0)}
                      onClick={() => handleSelectOption(opt.id, isSingle)}
                      className={`w-full flex items-center gap-4 rounded-xl border p-4 text-left transition-all ${
                        isSelected
                          ? 'border-blue-500 bg-blue-600/15 text-white shadow-md shadow-blue-900/20'
                          : 'border-slate-800 bg-slate-950/60 text-slate-300 hover:border-slate-700 hover:bg-slate-800/50'
                      }`}
                    >
                      <div
                        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-sm font-bold transition ${
                          isSelected
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        {letter}
                      </div>
                      <span className="flex-1 text-sm md:text-base font-normal">
                        {opt.option_text}
                      </span>
                    </button>
                  )
                })}
              </div>

              {/* Navigation Actions */}
              <div className="flex items-center justify-between pt-6 border-t border-slate-800">
                <button
                  disabled={currentIndex === 0}
                  onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
                  className="flex items-center gap-2 rounded-xl border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-30 disabled:pointer-events-none transition"
                >
                  <ArrowLeft className="h-4 w-4" />
                  Câu trước
                </button>

                {currentIndex < totalQuestions - 1 ? (
                  <button
                    onClick={() =>
                      setCurrentIndex((prev) => Math.min(totalQuestions - 1, prev + 1))
                    }
                    className="flex items-center gap-2 rounded-xl bg-blue-600 px-5 py-2 text-sm font-semibold text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500 transition"
                  >
                    Câu tiếp
                    <ArrowRight className="h-4 w-4" />
                  </button>
                ) : (
                  <button
                    onClick={() => setShowConfirmModal(true)}
                    className="flex items-center gap-2 rounded-xl bg-emerald-600 px-6 py-2.5 text-sm font-bold text-white shadow-lg shadow-emerald-600/30 hover:bg-emerald-500 transition"
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    Nộp bài thi
                  </button>
                )}
              </div>
            </div>
          </main>

          {/* Right Sidebar: Question Palette */}
          <aside className="w-80 border-l border-slate-800 bg-slate-950 p-6 flex flex-col justify-between">
            <div>
              <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider mb-4">
                Danh sách câu hỏi
              </h2>

              <div className="grid grid-cols-5 gap-2.5">
                {questions.map((q, idx) => {
                  const isCurrent = idx === currentIndex
                  const isAnswered = (answers[q.id] || []).length > 0
                  const isFlag = flagged[q.id]

                  let bgClass = 'bg-slate-800 text-slate-400 border-slate-700'
                  if (isAnswered) {
                    bgClass = 'bg-blue-600/30 text-blue-300 border-blue-500/50'
                  }
                  if (isCurrent) {
                    bgClass = 'ring-2 ring-blue-500 bg-blue-600 text-white font-bold'
                  }

                  return (
                    <button
                      key={q.id}
                      onClick={() => setCurrentIndex(idx)}
                      className={`relative flex h-11 items-center justify-center rounded-xl border text-sm font-medium transition hover:scale-105 ${bgClass}`}
                    >
                      <span>{idx + 1}</span>
                      {isFlag && (
                        <span className="absolute top-1 right-1 h-2 w-2 rounded-full bg-amber-400" />
                      )}
                    </button>
                  )
                })}
              </div>

              {/* Legend */}
              <div className="mt-8 space-y-2 border-t border-slate-800 pt-4 text-xs text-slate-400">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-sm bg-blue-600/40 border border-blue-500" />
                  <span>Đã trả lời ({answeredCount})</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-sm bg-slate-800 border border-slate-700" />
                  <span>Chưa trả lời ({totalQuestions - answeredCount})</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-amber-400" />
                  <span>Đánh dấu xem lại</span>
                </div>
              </div>
            </div>

            <button
              onClick={() => setShowConfirmModal(true)}
              className="mt-6 w-full rounded-xl bg-emerald-600 py-3 text-sm font-bold text-white shadow-lg shadow-emerald-600/20 hover:bg-emerald-500 transition"
            >
              Nộp bài ({answeredCount}/{totalQuestions})
            </button>
          </aside>
        </div>
      ) : (
        /* ── Result Screen ── */
        <main className="flex-1 overflow-y-auto p-8 flex items-center justify-center">
          <div className="w-full max-w-xl rounded-3xl border border-slate-800 bg-slate-950 p-8 text-center shadow-2xl">
            <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/40">
              <Trophy className="h-10 w-10" />
            </div>

            <h2 className="mt-6 text-2xl font-bold text-white">Bài thi đã được ghi nhận!</h2>
            <p className="mt-1 text-sm text-slate-400">
              Bạn đã hoàn thành bài thi <span className="text-slate-200">"{quiz.title}"</span>.
            </p>

            {quiz.show_score !== false && scoreResult && (
              <div className="mt-8 rounded-2xl bg-slate-900 border border-slate-800 p-6">
                <div className="text-4xl font-extrabold text-emerald-400">
                  {scoreResult.achieved.toFixed(1)}{' '}
                  <span className="text-lg font-medium text-slate-400">/ {scoreResult.total}</span>
                </div>
                <p className="mt-2 text-sm text-slate-300">
                  Đúng {scoreResult.correctCount} trên {totalQuestions} câu hỏi
                </p>
                <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-slate-800">
                  <div
                    className="h-full bg-emerald-500 rounded-full transition-all duration-1000"
                    style={{
                      width: `${(scoreResult.achieved / Math.max(1, scoreResult.total)) * 100}%`,
                    }}
                  />
                </div>
              </div>
            )}

            <div className="mt-8 flex gap-3">
              <button
                onClick={onClose}
                className="flex-1 rounded-xl bg-blue-600 py-3 text-sm font-semibold text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500 transition"
              >
                Hoàn tất & Thoát
              </button>
            </div>
          </div>
        </main>
      )}

      {massExam && syncState && !isSubmitted && <p role="status" className="bg-slate-950 px-6 py-2 text-sm">{syncState}</p>}
      {submitError && !isSubmitted && (
        <div role="alert" className="flex items-center gap-3 bg-red-950 px-6 py-3 text-sm text-white">
          {submitError}
          <button type="button" disabled={submitting} className="underline" onClick={() => void handleSubmitQuiz()}>Nộp lại</button>
        </div>
      )}

      {/* ── Submit Confirmation Dialog ── */}
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fadeIn">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-950 p-6 text-slate-100 shadow-2xl">
            <h3 className="text-lg font-bold text-white">Xác nhận nộp bài thi?</h3>
            <p className="mt-2 text-sm text-slate-400">
              Bạn đã trả lời{' '}
              <strong className="text-blue-400">
                {answeredCount}/{totalQuestions}
              </strong>{' '}
              câu hỏi.
              {answeredCount < totalQuestions && (
                <span className="block mt-1 text-amber-400">
                  ⚠️ Còn {totalQuestions - answeredCount} câu hỏi bạn chưa điền đáp án!
                </span>
              )}
            </p>

            <div className="mt-6 flex justify-end gap-3">
              <button
                onClick={() => setShowConfirmModal(false)}
                className="rounded-xl px-4 py-2 text-sm font-semibold text-slate-400 hover:bg-slate-800 hover:text-white transition"
              >
                Tiếp tục làm bài
              </button>
              <button
                onClick={() => void handleSubmitQuiz()} disabled={submitting}
                className="rounded-xl bg-emerald-600 px-5 py-2 text-sm font-bold text-white shadow-lg shadow-emerald-600/30 hover:bg-emerald-500 transition"
              >
                Đồng ý nộp bài
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
