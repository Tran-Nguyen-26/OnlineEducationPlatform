import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Award, CheckCircle2, Circle } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { CourseCompletionResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'

/** Course-level completion checklist; when everything is satisfied the student can finish the course and receive the certificate. */
export function CompletionPanel({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const status = useQuery({
    queryKey: ['student', 'completion', courseId],
    queryFn: () => apiClient.get<CourseCompletionResponse>(`/api/courses/${courseId}/completion`).then((r) => r.data),
  })
  const complete = useMutation({
    mutationFn: () => apiClient.post<CourseCompletionResponse>(`/api/courses/${courseId}/complete`).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student'] })
    },
  })
  const d = status.data
  if (status.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (!d) return null

  const rows = [
    { ok: d.completedRequiredLessons === d.requiredLessons && d.requiredLessons > 0, text: `Hoàn thành bài học bắt buộc (${d.completedRequiredLessons}/${d.requiredLessons})` },
    { ok: d.attemptedQuizzes === d.quizzes, text: `Làm tất cả quiz (${d.attemptedQuizzes}/${d.quizzes})` },
    { ok: d.submittedAssignments === d.assignments, text: `Nộp tất cả bài tập (${d.submittedAssignments}/${d.assignments})` },
  ]

  return (
    <div className="max-w-xl">
      {d.completed ? (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-5 text-emerald-900">
          <div className="mb-1 flex items-center gap-2 font-semibold"><Award size={20} /> Bạn đã hoàn thành khóa học!</div>
          <p className="mb-3 text-sm">Chứng chỉ đã được cấp. Bạn vẫn có thể xem lại toàn bộ nội dung khóa học.</p>
          <Link to="/student/certificates" className="text-sm font-medium text-emerald-800 underline">Xem chứng chỉ</Link>
        </div>
      ) : (
        <>
          <p className="mb-4 text-slate-600">Hoàn thành các yêu cầu sau để kết thúc khóa học và nhận chứng chỉ:</p>
          <ul className="mb-6 space-y-2">
            {rows.map((r) => (
              <li key={r.text} className={`flex items-center gap-2 text-sm ${r.ok ? 'text-green-700' : 'text-slate-700'}`}>
                {r.ok ? <CheckCircle2 size={16} /> : <Circle size={16} className="text-slate-300" />} {r.text}
              </li>
            ))}
          </ul>
          <Button disabled={!d.eligible || complete.isPending} onClick={() => complete.mutate()}>
            <Award size={16} /> {complete.isPending ? 'Đang xử lý...' : 'Hoàn thành khóa học'}
          </Button>
          {!d.eligible && <p className="mt-2 text-xs text-slate-400">Nút sẽ sáng lên khi bạn đáp ứng đủ các yêu cầu.</p>}
          {complete.isError && <p className="mt-3 text-sm text-red-600">{getApiErrorMessage(complete.error)}</p>}
        </>
      )}
    </div>
  )
}
