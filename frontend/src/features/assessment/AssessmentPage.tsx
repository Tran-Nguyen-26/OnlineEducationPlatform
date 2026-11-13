import { useState, useEffect } from 'react'
import {
  FileQuestion,
  Layers,
  Clock,
  CheckCircle2,
  Calendar,
  Play,
  Award,
  Sparkles,
  BookOpen,
} from 'lucide-react'
import { InstructorQuizManager } from './components/InstructorQuizManager'
import { StudentQuizPlayer } from './components/StudentQuizPlayer'
import type { QuizItem, LearnerAttempt, TeachingCourse } from '@/api/quiz'
import { fetchLearnerQuizzes, startLearnerQuiz } from '@/api/quiz'
import { fetchEnrollments } from '@/api/accessCodes'
import { useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'

export default function AssessmentPage() {
  const cache = useQueryClient()
  const [searchParams] = useSearchParams()
  const requestedCourse = searchParams.get('course')
  // Detect current user role
  let userRole = 'STUDENT'
  let userName = 'Học viên'
  try {
    const rawUser = localStorage.getItem('user')
    if (rawUser) {
      const u = JSON.parse(rawUser)
      if (u.role) userRole = u.role.toUpperCase()
      if (u.fullName) userName = u.fullName
    }
  } catch {
    // fallback
  }

  const isInstructorOrAdmin = userRole === 'INSTRUCTOR' || userRole === 'ADMIN'

  // Main Tab: 'student' or 'instructor'
  const [activeMainTab, setActiveMainTab] = useState<'student' | 'instructor'>(
    isInstructorOrAdmin ? 'instructor' : 'student'
  )

  // Student assessments state
  const [courses, setCourses] = useState<Pick<TeachingCourse, 'id' | 'title' | 'description'>[]>([])
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [studentQuizzes, setStudentQuizzes] = useState<QuizItem[]>([])
  const [studentStatusFilter, setStudentStatusFilter] = useState<'all' | 'open' | 'locked'>('all')
  const [loadingStudent, setLoadingStudent] = useState(false)

  // Student player
  const [takingQuiz, setTakingQuiz] = useState<QuizItem | null>(null)
  const [attempt, setAttempt] = useState<LearnerAttempt | null>(null)
  const [completedScores, setCompletedScores] = useState<Record<string, number>>({})

  useEffect(() => {
    async function loadStudentCourses() {
      try {
        if (isInstructorOrAdmin) return
        const cList = (await fetchEnrollments()).filter((item) => item.accessState === 'ACTIVE')
          .map((item) => ({ id: item.courseId, title: item.courseTitle }))
        setCourses(cList)
        if (cList.length > 0) {
          setSelectedCourseId(cList.find((course) => course.id === requestedCourse)?.id ?? cList[0].id)
        }
      } catch {
        // ignore
      }
    }
    loadStudentCourses()
  }, [isInstructorOrAdmin, requestedCourse])

  useEffect(() => {
    async function loadQuizzesForStudent() {
      if (!selectedCourseId) return
      setLoadingStudent(true)
      try {
        setStudentQuizzes(await fetchLearnerQuizzes(selectedCourseId))
      } catch {
        setStudentQuizzes([])
      } finally {
        setLoadingStudent(false)
      }
    }
    loadQuizzesForStudent()
  }, [selectedCourseId])

  const handleStartExam = async (quiz: QuizItem) => {
    try {
      const started = await startLearnerQuiz(selectedCourseId, quiz.id)
      setTakingQuiz(quiz)
      setAttempt(started)
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Không thể tải đề thi')
    }
  }

  const filteredStudentQuizzes = studentQuizzes.filter((q) => {
    if (studentStatusFilter === 'open') return !q.is_locked
    if (studentStatusFilter === 'locked') return q.is_locked
    return true
  })

  return (
    <div className="space-y-6">
      {/* ── Page Header with Welcome & Role Badges ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 rounded-3xl bg-linear-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl border border-slate-800">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-indigo-400 uppercase tracking-widest">
            <Sparkles className="h-4 w-4" />
            <span>Hệ thống Kiểm tra & Đánh giá năng lực</span>
          </div>
          <h1 className="mt-2 text-2xl md:text-3xl font-extrabold tracking-tight">
            Kiểm tra & Bài tập (Quiz Engine)
          </h1>
          <p className="mt-1 text-sm text-slate-300">
            Xin chào, <strong className="text-white">{userName}</strong>!{' '}
            {isInstructorOrAdmin
              ? 'Bạn có quyền quản lý và soạn thảo các bộ đề thi trắc nghiệm.'
              : 'Theo dõi và hoàn thành các bài kiểm tra được giao để nâng cao điểm số.'}
          </p>
        </div>

        {userRole === 'INSTRUCTOR' && (
          <div className="flex flex-wrap gap-3">
            <a className="underline" href="/assessment/grading">Hàng đợi chấm ẩn danh</a>
            <a className="underline" href="/dashboard/analytics">Tiến độ khóa học</a>
          </div>
        )}

        {/* Role Switcher Tabs */}
        {isInstructorOrAdmin && (
          <div className="flex rounded-2xl bg-white/10 p-1.5 backdrop-blur-md border border-white/15">
            <button
              onClick={() => setActiveMainTab('instructor')}
              className={`flex items-center gap-2 rounded-xl px-5 py-2.5 text-xs font-bold transition ${
                activeMainTab === 'instructor'
                  ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/40'
                  : 'text-slate-300 hover:text-white'
              }`}
            >
              <Layers className="h-4 w-4" />
              Quản lý đề thi (Giảng viên)
            </button>
            <button
              onClick={() => setActiveMainTab('student')}
              className={`flex items-center gap-2 rounded-xl px-5 py-2.5 text-xs font-bold transition ${
                activeMainTab === 'student'
                  ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/40'
                  : 'text-slate-300 hover:text-white'
              }`}
            >
              <Award className="h-4 w-4" />
              Làm bài kiểm tra (Học viên)
            </button>
          </div>
        )}
      </div>

      {/* ── Tab Content ── */}
      {activeMainTab === 'instructor' && isInstructorOrAdmin ? (
        <InstructorQuizManager />
      ) : (
        /* ── Student Assessment View ── */
        <div className="space-y-6">
          {/* Filter Bar */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 rounded-2xl bg-white p-5 border border-slate-200 shadow-xs">
            <div className="flex items-center gap-3">
              <BookOpen className="h-5 w-5 text-blue-600" />
              <label className="text-xs font-bold text-slate-600 uppercase tracking-wider">
                Khóa học:
              </label>
              <select
                value={selectedCourseId}
                onChange={(e) => setSelectedCourseId(e.target.value)}
                className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold text-slate-800 shadow-xs focus:border-blue-500 focus:outline-none"
              >
                {courses.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex rounded-xl bg-slate-100 p-1">
              <button
                onClick={() => setStudentStatusFilter('all')}
                className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                  studentStatusFilter === 'all'
                    ? 'bg-white text-slate-900 shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Tất cả ({studentQuizzes.length})
              </button>
              <button
                onClick={() => setStudentStatusFilter('open')}
                className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                  studentStatusFilter === 'open'
                    ? 'bg-white text-slate-900 shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Đang mở
              </button>
              <button
                onClick={() => setStudentStatusFilter('locked')}
                className={`rounded-lg px-4 py-1.5 text-xs font-semibold transition ${
                  studentStatusFilter === 'locked'
                    ? 'bg-white text-slate-900 shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Đã đóng
              </button>
            </div>
          </div>

          {/* Student Quizzes Cards */}
          {loadingStudent ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-48 rounded-2xl bg-slate-200 animate-pulse" />
              ))}
            </div>
          ) : filteredStudentQuizzes.length === 0 ? (
            <div className="rounded-3xl border border-dashed border-slate-300 bg-white py-16 text-center text-slate-400">
              <FileQuestion className="mx-auto mb-3 h-12 w-12 text-slate-300" />
              <p className="text-base font-bold text-slate-700">Không có bài kiểm tra nào</p>
              <p className="text-xs mt-1">
                Hiện tại khóa học này chưa có bài kiểm tra nào được phát hành.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {filteredStudentQuizzes.map((quiz) => {
                const score = quiz.show_score === false ? undefined : completedScores[quiz.id]
                return (
                  <div
                    key={quiz.id}
                    className="flex flex-col justify-between rounded-3xl border border-slate-200 bg-white p-6 shadow-xs hover:border-slate-300 hover:shadow-lg transition group"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-3">
                        <span className="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-700 border border-blue-200">
                          {quiz.assessment_kind === 'POST_TEST' ? 'Post-test' : quiz.assessment_kind === 'PRE_TEST' ? 'Pre-test' : 'Mini-quiz'}
                        </span>
                        {quiz.is_locked ? (
                          <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[10px] font-bold text-slate-600">
                            {quiz.attempt_state === 'REMEDIATION_REQUIRED' ? 'Cần học bù' : quiz.attempt_state === 'EXHAUSTED' ? 'Hết lượt' : 'Đã đóng'}
                          </span>
                        ) : (
                          <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-[10px] font-bold text-emerald-700 border border-emerald-200">
                            {quiz.attempt_state === 'REMEDIATION_AVAILABLE' ? 'Đã mở lượt học bù' : 'Đang mở làm bài'}
                          </span>
                        )}
                      </div>

                      <h3 className="mt-3 text-base font-bold text-slate-900 group-hover:text-blue-600 transition">
                        {quiz.title}
                      </h3>
                      <p className="mt-1 text-xs text-slate-500 line-clamp-2">
                        {quiz.description || 'Bài kiểm tra trắc nghiệm kiến thức'}
                      </p>
                      {quiz.attempt_state === 'REMEDIATION_REQUIRED' && (
                        <p className="mt-2 text-xs text-amber-800">
                          Xem lại toàn bộ video của chương để mở một lượt thi bổ sung.
                          {quiz.retry_after && ` Lượt học bù mở sớm nhất lúc ${new Date(quiz.retry_after).toLocaleString('vi-VN')}.`}
                        </p>
                      )}
                      {quiz.effective_percent != null && (
                        <p className="mt-2 text-xs font-semibold text-slate-700">
                          Điểm theo chính sách của bài thi {Number(quiz.effective_percent).toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%
                        </p>
                      )}

                      <div className="mt-5 space-y-2 text-xs text-slate-600">
                        <div className="flex items-center gap-2">
                          <Clock className="h-4 w-4 text-blue-500" />
                          <span>
                            Thời lượng:{' '}
                            <strong className="text-slate-800">
                              {quiz.time_limit_seconds
                                ? `${Math.round(quiz.time_limit_seconds / 60)} phút`
                                : 'Không giới hạn'}
                            </strong>
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                          <span>
                            Lượt làm bài:{' '}
                            <strong className="text-slate-800">{quiz.max_attempts === 0 ? 'Không giới hạn' : `${quiz.max_attempts} lần`}</strong>
                          </span>
                        </div>
                        {quiz.deadline && (
                          <div className="flex items-center gap-2 text-amber-600">
                            <Calendar className="h-4 w-4" />
                            <span>
                              Hạn chót: {new Date(quiz.deadline).toLocaleDateString('vi-VN')}
                            </span>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between">
                      {score !== undefined ? (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-400">Điểm:</span>
                          <span className="text-base font-bold text-emerald-600">{score.toFixed(1)}</span>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">Chưa nộp bài</span>
                      )}

                      <button
                        onClick={() => handleStartExam(quiz)}
                        className="flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white shadow-md shadow-blue-600/20 hover:bg-blue-700 transition"
                      >
                        <Play className="h-3.5 w-3.5 fill-current" />
                        {score !== undefined ? 'Làm lại' : 'Làm bài ngay'}
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* ── Active Student Quiz Player Modal ── */}
      {takingQuiz && attempt && (
        <StudentQuizPlayer
          quiz={takingQuiz}
          attempt={attempt}
          onClose={() => { setTakingQuiz(null); setAttempt(null) }}
          onCompleted={(achieved) => {
            setCompletedScores((prev) => ({
              ...prev,
              [takingQuiz.id]: achieved,
            }))
            void cache.invalidateQueries({ queryKey: ['course-mastery', takingQuiz.course_id] })
            void cache.invalidateQueries({ queryKey: ['course-playback', takingQuiz.course_id] })
            void fetchLearnerQuizzes(takingQuiz.course_id).then(setStudentQuizzes).catch(() => {})
          }}
        />
      )}
    </div>
  )
}
