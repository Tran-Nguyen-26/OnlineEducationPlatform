import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Paperclip, Upload } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { MySubmissionResponse, StudentAssignmentResponse, UploadUrlResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Badge } from '../../../components/Badge'
import { AppealModal } from '../../../components/AppealModal'

export function AssignmentRow({ courseId, assignment }: { courseId: string; assignment: StudentAssignmentResponse }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [appealing, setAppealing] = useState(false)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['student', 'assignments', courseId] })
    queryClient.invalidateQueries({ queryKey: ['student', 'my-submission', assignment.id] })
    queryClient.invalidateQueries({ queryKey: ['student', 'completion', courseId] })
  }

  const mySubmissionQuery = useQuery({
    queryKey: ['student', 'my-submission', assignment.id],
    queryFn: () =>
      apiClient
        .get<MySubmissionResponse>(`/api/assignments/${assignment.id}/my-submission`)
        .then((r) => (r.status === 204 ? null : r.data)),
    enabled: open,
  })

  const submitMutation = useMutation({
    mutationFn: () => apiClient.post<MySubmissionResponse>(`/api/assignments/${assignment.id}/submissions`, { textContent: text }),
    onSuccess: () => {
      invalidate()
      setText('')
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  async function handleFileUpload(file: File) {
    const submissionId = mySubmissionQuery.data?.id;
    if (!submissionId) {
      setError('Hãy nộp nội dung văn bản trước (có thể để trống) để tạo bài nộp, rồi đính file.')
      return
    }
    setUploading(true)
    setError(null)
    try {
      const mimeType = file.type || 'application/octet-stream'
      const { data } = await apiClient.post<UploadUrlResponse>(`/api/submissions/${submissionId}/files/upload-url`, {
        fileName: file.name,
        mimeType,
      })
      const putResponse = await fetch(data.uploadUrl, { method: 'PUT', headers: { 'Content-Type': mimeType }, body: file })
      if (!putResponse.ok) throw new Error(`Tải file lên thất bại (HTTP ${putResponse.status})`)
      const files = (await apiClient.get<MySubmissionResponse>(`/api/assignments/${assignment.id}/my-submission`)).data.files
      const fileId = files.find((f) => f.fileName === file.name && f.uploadStatus === 'PENDING')?.id
      if (fileId) {
        await apiClient.post(`/api/submission-files/${fileId}/complete-upload`, { fileSize: file.size })
      }
      invalidate()
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-medium text-slate-900">{assignment.title}</h3>
          {assignment.instructions && <p className="text-sm text-slate-500">{assignment.instructions}</p>}
          <p className="mt-1 text-xs text-slate-400">
            Điểm tối đa: {assignment.maxScore} · Đã nộp {assignment.submissionCount}/{assignment.maxSubmissions} lần
            {assignment.deadline && ` · Hạn: ${new Date(assignment.deadline).toLocaleString('vi-VN')}`}
          </p>
          {assignment.graded && <p className="mt-1 text-sm font-medium text-green-700">Điểm: {assignment.score}</p>}
        </div>
        <Button size="sm" variant="secondary" onClick={() => setOpen((v) => !v)}>
          {open ? 'Đóng' : 'Xem / Nộp bài'}
        </Button>
      </div>

      {open && (
        <div className="mt-4 space-y-3 border-t border-slate-100 pt-3">
          {mySubmissionQuery.data && (
            <div className="rounded-md bg-slate-50 p-3 text-sm">
              <p className="font-medium text-slate-700">Bài đã nộp (lần {mySubmissionQuery.data.submissionCount}):</p>
              {mySubmissionQuery.data.textContent && <p className="mt-1 whitespace-pre-wrap text-slate-600">{mySubmissionQuery.data.textContent}</p>}
              <ul className="mt-2 space-y-1">
                {mySubmissionQuery.data.files.map((f) => (
                  <li key={f.id} className="flex items-center gap-1 text-xs text-slate-500">
                    <Paperclip size={12} /> {f.fileName} <Badge status={f.uploadStatus} />
                  </li>
                ))}
              </ul>
              {mySubmissionQuery.data.feedback && (
                <p className="mt-2 text-xs text-slate-500">Nhận xét: {mySubmissionQuery.data.feedback}</p>
              )}
              {assignment.graded && (
                <button className="mt-2 text-xs text-blue-600 hover:underline" onClick={() => setAppealing(true)}>Yêu cầu phúc khảo điểm</button>
              )}
            </div>
          )}

          {assignment.canSubmit ? (
            <>
              {assignment.allowText && (
                <textarea
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  rows={4}
                  placeholder="Nhập nội dung bài làm..."
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              )}
              {assignment.allowFiles && mySubmissionQuery.data && (
                <label className="inline-flex cursor-pointer items-center gap-1 rounded border border-dashed border-slate-300 px-2 py-1 text-xs text-slate-500 hover:border-slate-400">
                  <Upload size={12} />
                  {uploading ? 'Đang tải lên...' : 'Đính kèm file'}
                  <input
                    type="file"
                    className="hidden"
                    disabled={uploading}
                    onChange={(e) => {
                      const f = e.target.files?.[0]
                      if (f) handleFileUpload(f)
                    }}
                  />
                </label>
              )}
              {error && <p className="text-sm text-red-600">{error}</p>}
              <div>
                <Button size="sm" disabled={submitMutation.isPending} onClick={() => submitMutation.mutate()}>
                  {submitMutation.isPending ? 'Đang nộp...' : mySubmissionQuery.data ? 'Nộp lại' : 'Nộp bài'}
                </Button>
              </div>
            </>
          ) : (
            <p className="text-sm text-slate-400">Bạn đã hết lượt nộp hoặc đã quá hạn.</p>
          )}
        </div>
      )}
      {appealing && mySubmissionQuery.data && <AppealModal targetType="ASSIGNMENT_SUBMISSION" targetId={mySubmissionQuery.data.id} title={assignment.title} onClose={() => setAppealing(false)} />}
    </div>
  )
}

export function AssignmentsTab({ courseId }: { courseId: string }) {
  const assignmentsQuery = useQuery({
    queryKey: ['student', 'assignments', courseId],
    queryFn: () => apiClient.get<StudentAssignmentResponse[]>(`/api/assignments?courseId=${courseId}`).then((r) => r.data),
  })

  return (
    <div className="space-y-3">
      {assignmentsQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {assignmentsQuery.data?.length === 0 && <p className="text-slate-400">Chưa có bài tập nào trong khóa học này.</p>}
      {assignmentsQuery.data?.map((assignment) => (
        <AssignmentRow key={assignment.id} courseId={courseId} assignment={assignment} />
      ))}
    </div>
  )
}
