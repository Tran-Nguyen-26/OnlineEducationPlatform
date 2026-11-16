import { useQuery } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { downloadCsv } from '../lib/csv'
import type { QuizAnalyticsResponse } from '../types/api'
import { Modal } from './ui/Modal'
import { Button } from './ui/Button'

const STATUS: Record<string, string> = { IN_PROGRESS: 'Đang làm', SUBMITTED: 'Đã nộp', EXPIRED: 'Hết giờ', GRADED: 'Đã chấm' }

function fmtDuration(s: number | null) {
  if (s == null) return '—'
  return `${Math.floor(s / 60)}p ${s % 60}s`
}

/** Teacher/admin drill-down for a quiz: all attempts + per-question correctness (item analysis). */
export function QuizAnalyticsModal({ courseId, quizId, onClose }: { courseId: string; quizId: string | null; onClose: () => void }) {
  const query = useQuery({
    enabled: quizId !== null,
    queryKey: ['quiz-analytics', courseId, quizId],
    queryFn: () => apiClient.get<QuizAnalyticsResponse>(`/api/gradebook/${courseId}/quizzes/${quizId}`).then((r) => r.data),
  })
  const d = query.data

  function exportCsv() {
    if (!d) return
    downloadCsv(`quiz-${d.quizTitle}.csv`, [
      ['Học sinh', 'Email', 'Lần', 'Trạng thái', 'Bắt đầu', 'Nộp', 'Thời gian (s)', 'Điểm tự động', 'Điểm cuối'],
      ...d.attempts.map((a) => [a.studentName, a.studentEmail, a.attemptNumber, a.status, a.startedAt, a.submittedAt ?? '', a.durationSeconds ?? '', a.autoScore ?? '', a.finalScore ?? '']),
    ])
  }

  return (
    <Modal title={d ? `Quiz: ${d.quizTitle}` : 'Chi tiết quiz'} open={quizId !== null} onClose={onClose}>
      {query.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {query.isError && <p className="text-red-600">{getApiErrorMessage(query.error)}</p>}
      {d && (
        <div className="max-h-[70vh] space-y-5 overflow-y-auto">
          <div className="grid grid-cols-3 gap-3 text-sm">
            <div className="rounded-md bg-slate-50 p-3"><div className="text-slate-500">Lượt làm</div><div className="text-lg font-semibold">{d.attemptCount}</div></div>
            <div className="rounded-md bg-slate-50 p-3"><div className="text-slate-500">Học sinh</div><div className="text-lg font-semibold">{d.studentCount}</div></div>
            <div className="rounded-md bg-slate-50 p-3"><div className="text-slate-500">Điểm TB</div><div className="text-lg font-semibold">{d.averageScore ?? '—'} / {d.maxScore}</div></div>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold text-slate-700">Phân tích từng câu hỏi</h3>
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500"><tr><th className="py-1">#</th><th>Câu hỏi</th><th>Điểm</th><th>Đúng</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {d.questions.map((q) => {
                  const pct = q.answeredCount ? Math.round((q.correctCount / q.answeredCount) * 100) : null
                  return (
                    <tr key={q.questionId}>
                      <td className="py-1.5 pr-2">{q.position + 1}</td>
                      <td className="pr-2">{q.text}</td>
                      <td className="pr-2">{q.points}</td>
                      <td className={pct !== null && pct < 40 ? 'font-medium text-red-600' : ''}>{pct === null ? '—' : `${q.correctCount}/${q.answeredCount} (${pct}%)`}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <p className="mt-1 text-xs text-slate-400">Tỷ lệ đúng dưới 40% được tô đỏ — câu quá khó hoặc có thể sai đáp án.</p>
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-700">Các lượt làm</h3>
              <Button size="sm" variant="secondary" onClick={exportCsv} disabled={!d.attempts.length}>Xuất CSV</Button>
            </div>
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500"><tr><th className="py-1">Học sinh</th><th>Lần</th><th>Trạng thái</th><th>Thời gian</th><th>Điểm</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {d.attempts.map((a) => (
                  <tr key={a.attemptId}>
                    <td className="py-1.5 pr-2"><div className="font-medium">{a.studentName}</div><div className="text-xs text-slate-400">{new Date(a.startedAt).toLocaleString('vi-VN')}</div></td>
                    <td>{a.attemptNumber}/{d.maxAttempts}</td>
                    <td>{STATUS[a.status] ?? a.status}</td>
                    <td>{fmtDuration(a.durationSeconds)}</td>
                    <td>{a.finalScore ?? '—'}{a.adjustmentReason ? <span title={a.adjustmentReason}> *</span> : null}</td>
                  </tr>
                ))}
                {d.attempts.length === 0 && <tr><td colSpan={5} className="py-3 text-slate-400">Chưa có lượt làm nào.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Modal>
  )
}
