import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Paperclip } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { SubmissionForGradingResponse, TeacherAssignmentResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Badge } from '../../../components/Badge'

function GradeForm({
  assignmentId,
  submission,
  maxScore,
}: {
  assignmentId: string
  submission: SubmissionForGradingResponse
  maxScore: number
}) {
  const queryClient = useQueryClient()
  const [score, setScore] = useState(submission.score?.toString() ?? '')
  const [feedback, setFeedback] = useState(submission.feedback ?? '')
  const [error, setError] = useState<string | null>(null)

  const gradeMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/submissions/${submission.submissionId}/grade`, { score: Number(score), feedback: feedback || null }),
    onSuccess: () => {
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['teacher', 'submissions', assignmentId] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    gradeMutation.mutate()
  }

  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
      <div className="mb-2 flex items-center justify-between">
        <div>
          <span className="font-medium text-slate-900">{submission.studentName}</span>{' '}
          <span className="text-xs text-slate-400">({submission.studentEmail})</span>
        </div>
        <div className="flex items-center gap-2">
          <Badge status={submission.status} />
          {submission.late && <span className="text-xs text-amber-600">Nộp muộn</span>}
        </div>
      </div>
      {submission.textContent && <p className="mb-2 whitespace-pre-wrap text-sm text-slate-600">{submission.textContent}</p>}
      {submission.files.length > 0 && (
        <ul className="mb-2 space-y-1">
          {submission.files.map((f) => (
            <li key={f.id} className="flex items-center gap-1 text-xs text-slate-500">
              <Paperclip size={12} /> {f.fileName} <Badge status={f.uploadStatus} />
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={handleSubmit} className="flex items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Điểm (/{maxScore})</label>
          <Input type="number" min={0} max={maxScore} step={0.5} required value={score} onChange={(e) => setScore(e.target.value)} className="w-24" />
        </div>
        <div className="flex-1">
          <label className="mb-1 block text-xs font-medium text-slate-600">Nhận xét</label>
          <Input value={feedback} onChange={(e) => setFeedback(e.target.value)} placeholder="Nhận xét cho học sinh" />
        </div>
        <Button type="submit" size="sm" disabled={gradeMutation.isPending}>
          {gradeMutation.isPending ? 'Đang lưu...' : submission.score !== null ? 'Chấm lại' : 'Chấm điểm'}
        </Button>
      </form>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}

function AssignmentRow({ assignment }: { assignment: TeacherAssignmentResponse }) {
  const [open, setOpen] = useState(false)
  const submissionsQuery = useQuery({
    queryKey: ['teacher', 'submissions', assignment.id],
    queryFn: () => apiClient.get<SubmissionForGradingResponse[]>(`/api/assignments/${assignment.id}/submissions`).then((r) => r.data),
    enabled: open,
  })

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-medium text-slate-900">{assignment.title}</h3>
          <p className="text-xs text-slate-400">
            {assignment.submissionCount} bài đã nộp
            {assignment.deadline && ` · Hạn: ${new Date(assignment.deadline).toLocaleString('vi-VN')}`}
          </p>
        </div>
        <Button size="sm" variant="secondary" onClick={() => setOpen((v) => !v)}>
          {open ? 'Đóng' : 'Xem bài nộp'}
        </Button>
      </div>
      {open && (
        <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
          {submissionsQuery.isLoading && <p className="text-sm text-slate-400">Đang tải...</p>}
          {submissionsQuery.data?.length === 0 && <p className="text-sm text-slate-400">Chưa có bài nộp nào.</p>}
          {submissionsQuery.data?.map((s) => (
            <GradeForm key={s.submissionId} assignmentId={assignment.id} submission={s} maxScore={assignment.maxScore} />
          ))}
        </div>
      )}
    </div>
  )
}

export function GradingTab({ courseId }: { courseId: string }) {
  const assignmentsQuery = useQuery({
    queryKey: ['teacher', 'assignments', courseId],
    queryFn: () => apiClient.get<TeacherAssignmentResponse[]>(`/api/assignments/by-course/${courseId}`).then((r) => r.data),
  })

  return (
    <div className="space-y-3">
      {assignmentsQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {assignmentsQuery.data?.length === 0 && <p className="text-slate-400">Khóa học chưa có bài tập nào.</p>}
      {assignmentsQuery.data?.map((a) => <AssignmentRow key={a.id} assignment={a} />)}
    </div>
  )
}
