import React, { useState } from 'react'
import {
  Plus,
  BookOpen,
  Clock,
  HelpCircle,
  Trash2,
  Award,
  Layers,
  Search,
  Lock,
  Unlock,
  Play,
  Settings,
  Database,
  Loader2,
  AlertCircle,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type {
  QuizItem,
  TeachingCourse,
  QuestionBankItem,
  CreateQuizPayload,
  QuizGradingPolicy,
} from '@/api/quiz'
import {
  fetchTeachingCourses,
  fetchQuizzes,
  createQuiz,
  updateQuiz,
  deleteQuiz,
  fetchQuestions,
  fetchQuestionBanks,
  createQuestionBank,
  deleteQuestionBank,
} from '@/api/quiz'
import { QuizQuestionEditor } from './QuizQuestionEditor'
import { QuizResultsViewer } from './QuizResultsViewer'
import { StudentQuizPlayer } from './StudentQuizPlayer'

export function InstructorQuizManager() {
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeSubTab, setActiveSubTab] = useState<'quizzes' | 'banks'>('quizzes')

  // Modals
  const [showCreateQuizModal, setShowCreateQuizModal] = useState(false)
  const [editingQuiz, setEditingQuiz] = useState<QuizItem | null>(null)
  const [activeEditorQuiz, setActiveEditorQuiz] = useState<QuizItem | null>(null)
  const [activeEditorQuestions, setActiveEditorQuestions] = useState<any[]>([])
  const [activeResultsQuiz, setActiveResultsQuiz] = useState<QuizItem | null>(null)
  const [previewQuiz, setPreviewQuiz] = useState<QuizItem | null>(null)
  const [previewQuestions, setPreviewQuestions] = useState<any[]>([])

  // New Question Bank modal
  const [showCreateBankModal, setShowCreateBankModal] = useState(false)
  const [bankName, setBankName] = useState('')

  // Form state for Quiz
  const [quizForm, setQuizForm] = useState<CreateQuizPayload>({
    title: '',
    description: '',
    question_source: 'FIXED',
    time_limit_seconds: 1800,
    max_attempts: 1,
    grading_policy: 'HIGHEST',
    randomize_questions: false,
    randomize_answers: false,
    show_score: true,
    show_correct_answers: false,
  })
  const [formSubmitting, setFormSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  // Query Courses
  const { data: courses = [] } = useQuery<TeachingCourse[]>({
    queryKey: ['instructorTeachingCourses'],
    queryFn: fetchTeachingCourses,
    staleTime: 60 * 1000,
  })

  // Derive active course ID cleanly without setState in effect
  const activeCourseId = selectedCourseId || (courses.length > 0 ? courses[0].id : '')

  // Query Quizzes
  const {
    data: quizData,
    isLoading: loadingQuizzes,
    error: quizQueryError,
    refetch: refetchQuizzes,
  } = useQuery({
    queryKey: ['instructorQuizzes', activeCourseId],
    queryFn: () => fetchQuizzes(activeCourseId),
    enabled: Boolean(activeCourseId),
    staleTime: 10 * 1000,
  })

  // Query Question Banks
  const {
    data: bankData,
    refetch: refetchBanks,
  } = useQuery({
    queryKey: ['instructorBanks', activeCourseId],
    queryFn: () => fetchQuestionBanks(activeCourseId),
    enabled: Boolean(activeCourseId),
    staleTime: 10 * 1000,
  })

  const quizzes: QuizItem[] = quizData?.items || []
  const banks: QuestionBankItem[] = bankData?.items || []
  const loading = loadingQuizzes
  const error = quizQueryError instanceof Error ? quizQueryError.message : ''

  const openCreateQuizModal = () => {
    setEditingQuiz(null)
    setQuizForm({
      title: '',
      description: '',
      question_source: 'FIXED',
      time_limit_seconds: 1800,
      max_attempts: 1,
      grading_policy: 'HIGHEST',
      randomize_questions: false,
      randomize_answers: false,
      show_score: true,
      show_correct_answers: false,
    })
    setFormError('')
    setShowCreateQuizModal(true)
  }

  const openEditQuizModal = (q: QuizItem) => {
    setEditingQuiz(q)
    setQuizForm({
      title: q.title,
      description: q.description || '',
      question_source: q.question_source || 'FIXED',
      time_limit_seconds: q.time_limit_seconds || 1800,
      max_attempts: q.max_attempts || 1,
      grading_policy: q.grading_policy || 'HIGHEST',
      randomize_questions: q.randomize_questions || false,
      randomize_answers: q.randomize_answers || false,
      show_score: q.show_score !== false,
      show_correct_answers: q.show_correct_answers || false,
    })
    setFormError('')
    setShowCreateQuizModal(true)
  }

  const handleSaveQuiz = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!quizForm.title.trim()) {
      setFormError('Vui lòng nhập tiêu đề bài thi.')
      return
    }

    setFormSubmitting(true)
    setFormError('')
    try {
      if (editingQuiz) {
        await updateQuiz(selectedCourseId, editingQuiz.id, quizForm)
      } else {
        await createQuiz(selectedCourseId, quizForm)
      }
      setShowCreateQuizModal(false)
      refetchQuizzes()
    } catch (err: unknown) {
      setFormError(err instanceof Error ? err.message : 'Lỗi lưu bài thi')
    } finally {
      setFormSubmitting(false)
    }
  }

  const handleDeleteQuiz = async (quizId: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa bài thi này?')) return
    try {
      await deleteQuiz(selectedCourseId, quizId)
      refetchQuizzes()
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi xóa bài thi')
    }
  }

  const handleOpenQuestionEditor = async (q: QuizItem) => {
    try {
      const res = await fetchQuestions(selectedCourseId, q.id)
      setActiveEditorQuiz(q)
      setActiveEditorQuestions(res.items || [])
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi tải danh sách câu hỏi')
    }
  }

  const handlePreviewQuiz = async (q: QuizItem) => {
    try {
      const res = await fetchQuestions(selectedCourseId, q.id)
      setPreviewQuiz(q)
      setPreviewQuestions(res.items || [])
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi khởi động bài thi')
    }
  }

  const handleCreateBank = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!bankName.trim()) return
    try {
      await createQuestionBank(selectedCourseId, bankName.trim())
      setBankName('')
      setShowCreateBankModal(false)
      refetchBanks()
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi tạo ngân hàng câu hỏi')
    }
  }

  const handleDeleteBank = async (bankId: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa ngân hàng câu hỏi này?')) return
    try {
      await deleteQuestionBank(selectedCourseId, bankId)
      refetchBanks()
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Lỗi xóa ngân hàng câu hỏi')
    }
  }

  const filteredQuizzes = quizzes.filter((q) =>
    q.title.toLowerCase().includes(searchQuery.toLowerCase())
  )

  return (
    <div className="space-y-6">
      {/* ── Top Bar Controls: Course Selector & Action ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 rounded-2xl bg-white p-5 border border-slate-200 shadow-xs">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
            <BookOpen className="h-5 w-5" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Khóa học đang quản lý
            </label>
            <select
              value={selectedCourseId}
              onChange={(e) => setSelectedCourseId(e.target.value)}
              className="mt-0.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold text-slate-800 shadow-xs focus:border-blue-500 focus:outline-none"
            >
              {courses.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex rounded-xl bg-slate-100 p-1">
            <button
              onClick={() => setActiveSubTab('quizzes')}
              className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                activeSubTab === 'quizzes'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Đề thi ({quizzes.length})
            </button>
            <button
              onClick={() => setActiveSubTab('banks')}
              className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                activeSubTab === 'banks'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Ngân hàng câu hỏi ({banks.length})
            </button>
          </div>

          {activeSubTab === 'quizzes' ? (
            <button
              onClick={openCreateQuizModal}
              className="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white shadow-md shadow-blue-600/20 hover:bg-blue-700 transition"
            >
              <Plus className="h-4 w-4" />
              Tạo bài thi mới
            </button>
          ) : (
            <button
              onClick={() => setShowCreateBankModal(true)}
              className="flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-bold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-700 transition"
            >
              <Plus className="h-4 w-4" />
              Thêm ngân hàng
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm text-red-700 border border-red-200">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* ── SubTab 1: Quizzes List ── */}
      {activeSubTab === 'quizzes' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <input
                type="text"
                placeholder="Tìm kiếm bài thi theo tên..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white py-2 pl-10 pr-4 text-sm focus:border-blue-500 focus:outline-none shadow-2xs"
              />
            </div>
            <span className="text-xs font-medium text-slate-400">
              Hiển thị {filteredQuizzes.length} bài thi
            </span>
          </div>

          {loading ? (
            <div className="flex h-64 items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
            </div>
          ) : filteredQuizzes.length === 0 ? (
            <div className="rounded-3xl border border-dashed border-slate-300 bg-white py-20 text-center">
              <Layers className="mx-auto mb-3 h-12 w-12 text-slate-300" />
              <h3 className="text-base font-bold text-slate-700">Chưa có bài thi nào</h3>
              <p className="mt-1 text-xs text-slate-400">
                Hãy tạo bài kiểm tra đầu tiên để đánh giá năng lực học viên.
              </p>
              <button
                onClick={openCreateQuizModal}
                className="mt-4 inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700"
              >
                <Plus className="h-4 w-4" />
                Tạo bài thi ngay
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {filteredQuizzes.map((q) => (
                <div
                  key={q.id}
                  className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-xs hover:border-slate-300 hover:shadow-md transition"
                >
                  <div>
                    {/* Header: Title + Status */}
                    <div className="flex items-start justify-between gap-3">
                      <h4 className="font-bold text-slate-900 line-clamp-1 text-base">{q.title}</h4>
                      {q.is_locked ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-[10px] font-bold text-slate-600">
                          <Lock className="h-3 w-3" /> Đã khóa
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[10px] font-bold text-emerald-700 border border-emerald-200">
                          <Unlock className="h-3 w-3" /> Đang mở
                        </span>
                      )}
                    </div>

                    <p className="mt-1.5 text-xs text-slate-500 line-clamp-2">
                      {q.description || 'Không có mô tả chi tiết'}
                    </p>

                    {/* Metadata Chips */}
                    <div className="mt-4 grid grid-cols-2 gap-2 text-xs text-slate-600">
                      <div className="flex items-center gap-1.5 rounded-lg bg-slate-50 p-2 border border-slate-100">
                        <Clock className="h-3.5 w-3.5 text-blue-500" />
                        <span>
                          {q.time_limit_seconds
                            ? `${Math.round(q.time_limit_seconds / 60)} phút`
                            : 'Không giới hạn'}
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5 rounded-lg bg-slate-50 p-2 border border-slate-100">
                        <Layers className="h-3.5 w-3.5 text-purple-500" />
                        <span>Tối đa {q.max_attempts} lần</span>
                      </div>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="mt-6 pt-4 border-t border-slate-100 flex flex-col gap-2">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleOpenQuestionEditor(q)}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-xl bg-blue-50 py-2 text-xs font-bold text-blue-700 hover:bg-blue-100 transition"
                      >
                        <HelpCircle className="h-3.5 w-3.5" />
                        Soạn câu hỏi
                      </button>

                      <button
                        onClick={() => setActiveResultsQuiz(q)}
                        className="flex-1 flex items-center justify-center gap-1.5 rounded-xl bg-purple-50 py-2 text-xs font-bold text-purple-700 hover:bg-purple-100 transition"
                      >
                        <Award className="h-3.5 w-3.5" />
                        Bảng điểm
                      </button>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <button
                        onClick={() => handlePreviewQuiz(q)}
                        className="flex items-center gap-1 text-xs font-semibold text-emerald-600 hover:text-emerald-700"
                        title="Làm thử bài thi như học viên"
                      >
                        <Play className="h-3.5 w-3.5" />
                        Làm thử
                      </button>

                      <div className="flex items-center gap-1">
                        <button
                          disabled={q.is_locked}
                          onClick={() => openEditQuizModal(q)}
                          className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
                          title="Cấu hình bài thi"
                        >
                          <Settings className="h-4 w-4" />
                        </button>
                        <button
                          disabled={q.is_locked}
                          onClick={() => handleDeleteQuiz(q.id)}
                          className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-30"
                          title="Xóa bài thi"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── SubTab 2: Question Banks ── */}
      {activeSubTab === 'banks' && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          <div className="mb-4">
            <h3 className="text-base font-bold text-slate-900">Ngân hàng câu hỏi của khóa học</h3>
            <p className="text-xs text-slate-500">
              Lưu trữ kho câu hỏi phân loại theo chủ đề để tái sử dụng trong các đề thi.
            </p>
          </div>

          {banks.length === 0 ? (
            <div className="py-12 text-center text-slate-400">
              <Database className="mx-auto mb-2 h-10 w-10 text-slate-300" />
              <p className="text-sm font-semibold text-slate-600">Chưa có ngân hàng câu hỏi nào</p>
              <button
                onClick={() => setShowCreateBankModal(true)}
                className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white"
              >
                <Plus className="h-3.5 w-3.5" /> Tạo ngân hàng
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {banks.map((b) => (
                <div
                  key={b.id}
                  className="flex items-center justify-between rounded-xl border border-slate-200 p-4 hover:border-slate-300 transition"
                >
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
                      <Database className="h-4 w-4" />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-800">{b.name}</h4>
                      <span className="text-[11px] text-slate-400">
                        {new Date(b.created_at).toLocaleDateString('vi-VN')}
                      </span>
                    </div>
                  </div>

                  <button
                    onClick={() => handleDeleteBank(b.id)}
                    className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                    title="Xóa ngân hàng"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── Modal: Create / Edit Quiz ── */}
      {showCreateQuizModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-fadeIn">
          <div className="w-full max-w-xl rounded-3xl bg-white p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-bold text-slate-900 border-b pb-3">
              {editingQuiz ? 'Cập nhật cấu hình bài thi' : 'Tạo bài thi mới'}
            </h3>

            {formError && (
              <div className="mt-3 flex items-center gap-2 rounded-xl bg-rose-50 p-3 text-xs text-rose-700 border border-rose-200">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <form onSubmit={handleSaveQuiz} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Tiêu đề bài thi <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={quizForm.title}
                  onChange={(e) => setQuizForm({ ...quizForm, title: e.target.value })}
                  placeholder="Ví dụ: Kiểm tra giữa kỳ Lập trình Java"
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Mô tả bài thi</label>
                <textarea
                  rows={2}
                  value={quizForm.description || ''}
                  onChange={(e) => setQuizForm({ ...quizForm, description: e.target.value })}
                  placeholder="Hướng dẫn hoặc lưu ý cho học viên khi làm bài..."
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Thời gian làm bài (phút)
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={Math.round((quizForm.time_limit_seconds || 1800) / 60)}
                    onChange={(e) =>
                      setQuizForm({
                        ...quizForm,
                        time_limit_seconds: (parseInt(e.target.value) || 30) * 60,
                      })
                    }
                    className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Số lần làm bài tối đa
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={quizForm.max_attempts || 1}
                    onChange={(e) =>
                      setQuizForm({ ...quizForm, max_attempts: parseInt(e.target.value) || 1 })
                    }
                    className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Chính sách tính điểm
                </label>
                <select
                  value={quizForm.grading_policy}
                  onChange={(e) =>
                    setQuizForm({
                      ...quizForm,
                      grading_policy: e.target.value as QuizGradingPolicy,
                    })
                  }
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                >
                  <option value="HIGHEST">Lấy điểm cao nhất (Highest)</option>
                  <option value="LATEST">Lấy điểm lần làm cuối (Latest)</option>
                  <option value="AVERAGE">Lấy điểm trung bình (Average)</option>
                </select>
              </div>

              {/* Toggles */}
              <div className="grid grid-cols-2 gap-3 pt-2">
                <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-xs font-medium text-slate-700 cursor-pointer hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={quizForm.randomize_questions || false}
                    onChange={(e) =>
                      setQuizForm({ ...quizForm, randomize_questions: e.target.checked })
                    }
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span>Xáo trộn thứ tự câu hỏi</span>
                </label>

                <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-xs font-medium text-slate-700 cursor-pointer hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={quizForm.randomize_answers || false}
                    onChange={(e) =>
                      setQuizForm({ ...quizForm, randomize_answers: e.target.checked })
                    }
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span>Xáo trộn thứ tự đáp án</span>
                </label>

                <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-xs font-medium text-slate-700 cursor-pointer hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={quizForm.show_score !== false}
                    onChange={(e) => setQuizForm({ ...quizForm, show_score: e.target.checked })}
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span>Hiển thị điểm sau khi nộp</span>
                </label>

                <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-xs font-medium text-slate-700 cursor-pointer hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={quizForm.show_correct_answers || false}
                    onChange={(e) =>
                      setQuizForm({ ...quizForm, show_correct_answers: e.target.checked })
                    }
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span>Hiển thị đáp án đúng</span>
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t">
                <button
                  type="button"
                  onClick={() => setShowCreateQuizModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={formSubmitting}
                  className="flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2 text-xs font-bold text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {formSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
                  {editingQuiz ? 'Lưu thay đổi' : 'Tạo bài thi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Create Question Bank ── */}
      {showCreateBankModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-fadeIn">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-base font-bold text-slate-900">Thêm ngân hàng câu hỏi mới</h3>
            <form onSubmit={handleCreateBank} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Tên ngân hàng câu hỏi <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={bankName}
                  onChange={(e) => setBankName(e.target.value)}
                  placeholder="Ví dụ: Ngân hàng OOP Java & Design Patterns"
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateBankModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-emerald-600 px-5 py-2 text-xs font-bold text-white hover:bg-emerald-700"
                >
                  Lưu ngân hàng
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Sub-component Overlays ── */}
      {activeEditorQuiz && (
        <QuizQuestionEditor
          courseId={selectedCourseId}
          quiz={activeEditorQuiz}
          questions={activeEditorQuestions}
          onClose={() => setActiveEditorQuiz(null)}
          onRefresh={async () => {
            const res = await fetchQuestions(selectedCourseId, activeEditorQuiz.id)
            setActiveEditorQuestions(res.items || [])
          }}
        />
      )}

      {activeResultsQuiz && (
        <QuizResultsViewer
          courseId={selectedCourseId}
          quiz={activeResultsQuiz}
          onClose={() => setActiveResultsQuiz(null)}
        />
      )}

      {previewQuiz && (
        <StudentQuizPlayer
          quiz={previewQuiz}
          questions={previewQuestions}
          onClose={() => setPreviewQuiz(null)}
        />
      )}
    </div>
  )
}
