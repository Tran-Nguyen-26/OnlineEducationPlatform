import { useState } from 'react'
import {
  HelpCircle,
  Search,
  BookOpen,
  Lock,
  Unlock,
  Calendar,
  Layers,
  Award,
  Loader2,
  RefreshCw,
  AlertCircle,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type { AdminQuizSummary } from '@/api/quiz'
import { adminFetchQuizzes, fetchTeachingCourses } from '@/api/quiz'

export default function AdminQuizzesPage() {
  const [selectedCourseFilter, setSelectedCourseFilter] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [page, setPage] = useState(1)

  const { data: courses = [] } = useQuery({
    queryKey: ['teachingCourses'],
    queryFn: fetchTeachingCourses,
    staleTime: 60 * 1000,
  })

  const {
    data: quizData,
    isLoading: loading,
    error: queryError,
    refetch: loadAdminQuizzes,
  } = useQuery({
    queryKey: ['adminQuizzes', { selectedCourseFilter, page }],
    queryFn: () => adminFetchQuizzes(selectedCourseFilter || undefined, page, 20),
    staleTime: 15 * 1000,
  })

  const quizzes: AdminQuizSummary[] = quizData?.items || []
  const totalElements = quizData?.pagination?.total || 0
  const error = queryError instanceof Error ? queryError.message : ''

  const filteredQuizzes = quizzes.filter((q) => {
    const matchTitle = q.title.toLowerCase().includes(searchQuery.toLowerCase())
    const matchCourse = q.course_title?.toLowerCase().includes(searchQuery.toLowerCase())
    return matchTitle || matchCourse
  })

  // Compute stat highlights
  const totalAttempts = quizzes.reduce((sum, q) => sum + (Number(q.total_attempts) || 0), 0)
  const lockedCount = quizzes.filter((q) => q.is_locked).length
  const activeCount = quizzes.filter((q) => !q.is_locked).length

  return (
    <div className="space-y-6">
      {/* ── Page Header ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Quản trị Đề thi & Bài kiểm tra
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Giám sát, quản lý và thống kê toàn bộ các bài thi Quiz trong tất cả các khóa học.
          </p>
        </div>

        <button
          onClick={() => loadAdminQuizzes()}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Làm mới
        </button>
      </div>

      {/* ── Metric Cards ── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Tổng số bài thi
            </span>
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <HelpCircle className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-slate-900">{totalElements}</div>
          <div className="mt-1 text-xs text-slate-400">Trên toàn hệ thống</div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Đang mở làm bài
            </span>
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
              <Unlock className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-emerald-600">{activeCount}</div>
          <div className="mt-1 text-xs text-slate-400">Cho phép học viên nộp</div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Đã khóa bài thi
            </span>
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
              <Lock className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-slate-700">{lockedCount}</div>
          <div className="mt-1 text-xs text-slate-400">Đã đóng / hết hạn</div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Tổng lượt nộp bài
            </span>
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-purple-50 text-purple-600">
              <Award className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-purple-600">{totalAttempts}</div>
          <div className="mt-1 text-xs text-slate-400">Lượt hoàn thành</div>
        </div>
      </div>

      {/* ── Filter Bar ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 rounded-2xl bg-white p-4 border border-slate-200 shadow-xs">
        <div className="flex items-center gap-3">
          <label className="text-xs font-bold text-slate-600 uppercase tracking-wider">
            Lọc theo khóa học:
          </label>
          <select
            value={selectedCourseFilter}
            onChange={(e) => {
              setSelectedCourseFilter(e.target.value)
              setPage(1)
            }}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 shadow-xs focus:border-blue-500 focus:outline-none"
          >
            <option value="">-- Tất cả khóa học --</option>
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </div>

        <div className="relative w-full md:w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm theo tên bài thi..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-xl border border-slate-200 py-1.5 pl-9 pr-4 text-xs focus:border-blue-500 focus:outline-none"
          />
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm text-red-700 border border-red-200">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* ── Table List ── */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-6 py-4">Bài thi Quiz</th>
                <th className="px-6 py-4">Thuộc khóa học</th>
                <th className="px-6 py-4">Trạng thái</th>
                <th className="px-6 py-4">Tổng lượt làm</th>
                <th className="px-6 py-4">Ngày tạo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center">
                    <Loader2 className="mx-auto h-8 w-8 animate-spin text-blue-600" />
                  </td>
                </tr>
              ) : filteredQuizzes.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-slate-400">
                    <Layers className="mx-auto mb-2 h-10 w-10 text-slate-300" />
                    Không tìm thấy bài thi nào phù hợp.
                  </td>
                </tr>
              ) : (
                filteredQuizzes.map((quiz) => (
                  <tr key={quiz.id} className="hover:bg-slate-50/80 transition">
                    <td className="px-6 py-4">
                      <div className="font-bold text-slate-900">{quiz.title}</div>
                      <div className="text-[11px] font-mono text-slate-400">{quiz.id}</div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2 font-medium text-slate-800">
                        <BookOpen className="h-4 w-4 text-blue-500 shrink-0" />
                        <span>{quiz.course_title || 'N/A'}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {quiz.is_locked ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
                          <Lock className="h-3 w-3" /> Đã khóa
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200">
                          <Unlock className="h-3 w-3" /> Đang mở
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <span className="rounded-lg bg-purple-50 px-3 py-1 text-xs font-bold text-purple-700 border border-purple-200">
                        {quiz.total_attempts} lượt
                      </span>
                    </td>
                    <td className="px-6 py-4 text-xs text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5" />
                        {new Date(quiz.created_at).toLocaleDateString('vi-VN')}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
