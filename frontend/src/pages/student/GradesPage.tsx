import { useCourseTasks } from './useCourseTasks'

export function GradesPage() {
  const { tasks, progress, loading } = useCourseTasks()

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Điểm số</h1>
      <p className="mb-4 text-sm text-slate-500">Điểm quiz (theo chính sách tính điểm của từng quiz) và điểm bài tập đã được chấm, theo từng khóa.</p>
      {loading && <p className="text-slate-400">Đang tải...</p>}
      <div className="space-y-4">
        {progress.filter((p) => p.status === 'ACTIVE').map((course) => {
          const rows = tasks.filter((t) => t.courseId === course.courseId && t.score != null)
          return (
            <div key={course.enrollmentId} className="rounded-lg border border-slate-200 bg-white p-4">
              <h2 className="mb-2 font-semibold text-slate-900">{course.courseTitle}</h2>
              {rows.length === 0 ? (
                <p className="text-sm text-slate-400">Chưa có điểm nào.</p>
              ) : (
                <table className="w-full text-left text-sm">
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((t) => (
                      <tr key={t.key}>
                        <td className="py-2 text-slate-500">{t.kind === 'QUIZ' ? 'Quiz' : 'Bài tập'}</td>
                        <td className="py-2 text-slate-900">{t.title}</td>
                        <td className="py-2 text-right font-medium">{t.maxScore ? `${t.score}/${t.maxScore}` : t.score}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
