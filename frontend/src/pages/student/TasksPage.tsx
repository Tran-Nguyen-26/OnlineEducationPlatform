import { useState } from 'react'
import { Link } from 'react-router-dom'
import { STATUS_LABEL, useCourseTasks, type TaskStatus } from './useCourseTasks'

const FILTERS: Array<{ key: 'ALL' | TaskStatus; label: string }> = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'TODO', label: 'Chưa làm' },
  { key: 'DONE', label: 'Đã nộp' },
  { key: 'GRADED', label: 'Đã chấm' },
  { key: 'OVERDUE', label: 'Quá hạn' },
]

export function TasksPage() {
  const { tasks, loading } = useCourseTasks()
  const [filter, setFilter] = useState<'ALL' | TaskStatus>('ALL')
  const rows = tasks
    .filter((t) => filter === 'ALL' || t.status === filter)
    .sort((a, b) => (a.deadline ?? '9999').localeCompare(b.deadline ?? '9999'))

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Bài tập & Quiz</h1>
      <p className="mb-4 text-sm text-slate-500">Tất cả bài tập và quiz của các khóa bạn đang học, sắp theo hạn nộp.</p>
      <div className="mb-4 flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={`rounded-full border px-3 py-1 text-sm ${filter === f.key ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 text-slate-600'}`}
          >
            {f.label}
          </button>
        ))}
      </div>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Loại</th>
              <th className="px-4 py-3 font-medium">Tên</th>
              <th className="px-4 py-3 font-medium">Khóa học</th>
              <th className="px-4 py-3 font-medium">Hạn</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Điểm</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={6}>Đang tải...</td>
              </tr>
            )}
            {rows.map((t) => (
              <tr key={t.key}>
                <td className="px-4 py-3 text-slate-500">{t.kind === 'QUIZ' ? 'Quiz' : 'Bài tập'}</td>
                <td className="px-4 py-3 font-medium text-slate-900">
                  <Link to={`/student/courses/${t.courseId}`} className="hover:underline">{t.title}</Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{t.courseTitle}</td>
                <td className="px-4 py-3 text-slate-500">{t.deadline ? new Date(t.deadline).toLocaleString('vi-VN') : 'Không có hạn'}</td>
                <td className="px-4 py-3">{STATUS_LABEL[t.status]}</td>
                <td className="px-4 py-3 text-slate-500">{t.score != null ? (t.maxScore ? `${t.score}/${t.maxScore}` : t.score) : '—'}</td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={6}>Không có mục nào.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
