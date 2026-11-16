import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { downloadCsv } from '../lib/csv'
import type { GradebookCell, GradebookResponse } from '../types/api'
import { Button } from './ui/Button'
import { QuizAnalyticsModal } from './QuizAnalyticsModal'

function cellText(cell: GradebookCell | undefined): string {
  if (!cell || cell.status === 'NONE') return '—'
  if (cell.status === 'SUBMITTED') return 'Chờ chấm'
  return `${cell.score}${cell.attempts > 1 ? ` (${cell.attempts} lần)` : ''}`
}

/** Course gradebook: one row per ACTIVE/COMPLETED student, one column per quiz/assignment; shared by teacher and admin. */
export function GradebookView({ courseId }: { courseId: string }) {
  const query = useQuery({
    queryKey: ['gradebook', courseId],
    queryFn: () => apiClient.get<GradebookResponse>(`/api/gradebook/${courseId}`).then((r) => r.data),
  })
  const data = query.data
  const [quizId, setQuizId] = useState<string | null>(null)

  function exportCsv() {
    if (!data) return
    downloadCsv(`bang-diem-${courseId}.csv`, [
      ['Họ tên', 'Email', 'Trạng thái', ...data.columns.map((c) => `${c.kind === 'QUIZ' ? 'Quiz' : 'Bài tập'}: ${c.title} (/${c.maxScore ?? ''})`), 'Điểm TB (%)'],
      ...data.rows.map((r) => [
        r.studentName,
        r.studentEmail,
        r.enrollmentStatus,
        ...data.columns.map((c) => {
          const cell = r.cells[c.id]
          return cell?.status === 'GRADED' ? cell.score : cell?.status === 'SUBMITTED' ? 'Chờ chấm' : ''
        }),
        r.averagePercent,
      ]),
    ])
  }

  if (query.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (query.isError) return <p className="text-red-600">{getApiErrorMessage(query.error)}</p>
  if (!data) return null

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm text-slate-500">
          Điểm quiz theo chính sách tính điểm của từng quiz; bài tập là điểm đã chấm. Điểm TB (%) tính trên các mục đã có điểm.
        </p>
        <Button size="sm" variant="secondary" onClick={exportCsv} disabled={data.rows.length === 0}>
          Xuất CSV
        </Button>
      </div>
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Học sinh</th>
              {data.columns.map((c) => (
                <th key={c.id} className="px-4 py-3 font-medium">
                  {c.kind === 'QUIZ' ? (
                    <button className="text-left text-blue-600 hover:underline" onClick={() => setQuizId(c.id)}>{c.title}</button>
                  ) : (
                    <div>{c.title}</div>
                  )}
                  <div className="text-xs font-normal text-slate-400">
                    {c.kind === 'QUIZ' ? 'Quiz' : 'Bài tập'} · /{c.maxScore}
                  </div>
                </th>
              ))}
              <th className="px-4 py-3 font-medium">TB (%)</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.rows.map((r) => (
              <tr key={r.enrollmentId}>
                <td className="px-4 py-3">
                  <div className="font-medium text-slate-900">{r.studentName}</div>
                  <div className="text-xs text-slate-400">{r.studentEmail}{r.enrollmentStatus === 'COMPLETED' ? ' · đã hoàn thành' : ''}</div>
                </td>
                {data.columns.map((c) => (
                  <td key={c.id} className="px-4 py-3 text-slate-700">{cellText(r.cells[c.id])}</td>
                ))}
                <td className="px-4 py-3 font-medium text-slate-900">{r.averagePercent ?? '—'}</td>
              </tr>
            ))}
            {data.rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={data.columns.length + 2}>Chưa có học sinh nào trong khóa.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <QuizAnalyticsModal courseId={courseId} quizId={quizId} onClose={() => setQuizId(null)} />
    </div>
  )
}
