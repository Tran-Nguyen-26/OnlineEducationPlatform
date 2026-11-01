import React, { useState } from 'react'
import {
  X,
  Award,
  Edit3,
  Calendar,
  AlertCircle,
  Loader2,
  CheckCircle2,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type { QuizItem, QuizResultItem } from '@/api/quiz'
import { fetchQuizResults, adjustScore } from '@/api/quiz'

interface QuizResultsViewerProps {
  courseId: string
  quiz: QuizItem
  onClose: () => void
}

export function QuizResultsViewer({ courseId, quiz, onClose }: QuizResultsViewerProps) {
  // Adjust score modal state
  const [adjustingResult, setAdjustingResult] = useState<QuizResultItem | null>(null)
  const [newScore, setNewScore] = useState<number>(0)
  const [reason, setReason] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')

  const {
    data: resultData,
    isLoading: loading,
    error: queryError,
    refetch: loadResults,
  } = useQuery({
    queryKey: ['quizResults', { courseId, quizId: quiz.id }],
    queryFn: () => fetchQuizResults(courseId, quiz.id),
    staleTime: 10 * 1000,
  })

  const results: QuizResultItem[] = resultData?.items || []
  const error = queryError instanceof Error ? queryError.message : ''

  const openAdjustModal = (item: QuizResultItem) => {
    setAdjustingResult(item)
    setNewScore(Number(item.final_score) || 0)
    setReason(item.adjustment_reason || '')
    setActionError('')
  }

  const handleSaveAdjustment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!adjustingResult) return
    if (!reason.trim()) {
      setActionError('Vui lòng nhập lý do điều chỉnh điểm.')
      return
    }

    setActionLoading(true)
    setActionError('')
    try {
      await adjustScore(courseId, quiz.id, adjustingResult.id, newScore, reason.trim())
      setAdjustingResult(null)
      loadResults()
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : 'Lỗi cập nhật điểm')
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-fadeIn">
      <div className="flex h-[85vh] w-full max-w-4xl flex-col rounded-3xl bg-white shadow-2xl overflow-hidden">
        {/* Header */}
        <header className="flex h-16 items-center justify-between border-b px-6 bg-slate-50">
          <div>
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <span>Bảng điểm bài thi:</span>
              <span className="text-blue-600 font-semibold">{quiz.title}</span>
            </h2>
            <p className="text-xs text-gray-500">
              Tổng số lượt nộp bài: <span className="font-semibold text-gray-800">{results.length}</span>
            </p>
          </div>

          <button
            onClick={onClose}
            className="rounded-lg p-2 text-gray-400 hover:bg-gray-200 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6">
          {error && (
            <div className="mb-4 flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm text-red-700 border border-red-200">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {loading ? (
            <div className="flex h-48 items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
            </div>
          ) : results.length === 0 ? (
            <div className="py-20 text-center text-gray-400">
              <Award className="mx-auto mb-3 h-12 w-12 text-gray-300" />
              <p className="text-base font-semibold text-gray-700">Chưa có lượt nộp bài nào</p>
              <p className="text-xs mt-1">Khi học viên hoàn thành bài kiểm tra, kết quả sẽ hiển thị tại đây.</p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-gray-200 shadow-xs">
              <table className="w-full text-left text-sm text-gray-600">
                <thead className="bg-gray-50 text-xs font-bold uppercase tracking-wider text-gray-500 border-b border-gray-200">
                  <tr>
                    <th className="px-5 py-3.5">Mã Attempt</th>
                    <th className="px-5 py-3.5">Điểm tự động</th>
                    <th className="px-5 py-3.5">Điểm tổng kết</th>
                    <th className="px-5 py-3.5">Lý do điều chỉnh</th>
                    <th className="px-5 py-3.5">Ngày nộp</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {results.map((r) => (
                    <tr key={r.id} className="hover:bg-gray-50/70 transition">
                      <td className="px-5 py-4 font-mono text-xs text-gray-800">
                        {r.attempt_id.slice(0, 8)}...
                      </td>
                      <td className="px-5 py-4 font-semibold text-gray-700">
                        {r.auto_score}
                      </td>
                      <td className="px-5 py-4 font-bold text-emerald-600">
                        {r.final_score}
                      </td>
                      <td className="px-5 py-4 text-xs text-gray-500 max-w-xs truncate">
                        {r.adjustment_reason || <span className="text-gray-300">—</span>}
                      </td>
                      <td className="px-5 py-4 text-xs text-gray-400">
                        <span className="flex items-center gap-1.5">
                          <Calendar className="h-3.5 w-3.5" />
                          {new Date(r.created_at).toLocaleString('vi-VN')}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-right">
                        <button
                          onClick={() => openAdjustModal(r)}
                          className="inline-flex items-center gap-1 rounded-lg bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-700 hover:bg-blue-100 transition"
                        >
                          <Edit3 className="h-3.5 w-3.5" />
                          Chỉnh điểm
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Adjust Score Modal */}
      {adjustingResult && (
        <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-fadeIn">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-base font-bold text-gray-900">Điều chỉnh điểm bài thi</h3>
            <p className="mt-1 text-xs text-gray-500">
              Điểm chấm máy ban đầu: <span className="font-semibold text-gray-700">{adjustingResult.auto_score}</span>
            </p>

            {actionError && (
              <div className="mt-3 flex items-center gap-2 rounded-xl bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <form onSubmit={handleSaveAdjustment} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Điểm mới
                </label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  value={newScore}
                  onChange={(e) => setNewScore(parseFloat(e.target.value) || 0)}
                  className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Lý do điều chỉnh điểm <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Ví dụ: Cộng 1 điểm câu hỏi 3 do đáp án có thể hiểu theo 2 nghĩa..."
                  className="w-full rounded-xl border border-gray-300 p-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setAdjustingResult(null)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-gray-600 hover:bg-gray-100"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {actionLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                  Lưu điểm mới
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
