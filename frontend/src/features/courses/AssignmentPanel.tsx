import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from '@/api/quiz'
import { useState, type FormEvent } from 'react'

type Assignment = { id: string; title: string; instructions: string; deadline: string; allowText: boolean;
  allowFiles: boolean; submissionId?: string; status?: string; submittedText?: string; canSubmit: boolean;
  vivaQuestions?: string[]; vivaCompleted?: boolean }
type Grade = { score: number; scores: Record<string, number>; feedback?: string; appealUntil?: string }
type VivaResult = { submissionId: string; questions: string[]; completed: boolean; consistencyScore?: number | null; consistencyNote?: string | null }

function AssignmentCard({ item, courseId }: { item: Assignment; courseId: string }) {
  const cache = useQueryClient()
  const [mode, setMode] = useState(item.allowText ? 'text' : 'file')
  const [draftText, setDraftText] = useState(item.submittedText ?? '')
  const [validationError, setValidationError] = useState('')
  const wordCount = (draftText.match(/\p{L}[\p{L}\p{M}]*/gu) ?? []).length
  const letterCount = (draftText.match(/\p{L}/gu) ?? []).length
  const textMeetsMinimum = wordCount >= 50 || letterCount >= 300
  const gradePath = `/api/v1/courses/${courseId}/grading/${item.submissionId}`
  const grade = useQuery({ queryKey: ['assignment-grade', item.submissionId],
    enabled: !!item.submissionId && ['GRADED', 'PENDING_REGRADE'].includes(item.status ?? ''),
    queryFn: () => request<Grade>(gradePath + '/result') })
  const viva = useQuery({ queryKey: ['assignment-viva', item.id, item.submissionId], enabled: !!item.submissionId,
    queryFn: () => request<VivaResult>(`/api/v1/courses/${courseId}/learner-assignments/${item.id}/viva`) })
  const [vivaAnswers, setVivaAnswers] = useState(['', ''])
  const answerViva = useMutation({ mutationFn: () => request<VivaResult>(
    `/api/v1/courses/${courseId}/learner-assignments/${item.id}/viva/answers`,
    { method: 'POST', body: JSON.stringify({ answers: vivaAnswers }) }),
    onSuccess: () => void cache.invalidateQueries({ queryKey: ['assignment-viva', item.id, item.submissionId] }) })
  const save = useMutation({
    mutationFn: async ({ form, appeal }: { form: FormData; appeal?: boolean }) => {
      if (appeal) return request(gradePath + '/appeal', { method: 'POST',
        body: JSON.stringify({ criterion: form.get('criterion'), reason: form.get('reason') }) })
      const path = `/api/v1/courses/${courseId}/learner-assignments/${item.id}/submission`
      if (mode === 'text') return request(path, { method: 'PUT', body: JSON.stringify({ text: form.get('text') }) })
      const response = await fetch(path, { method: 'PUT', body: form,
        headers: { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` } })
      if (!response.ok) {
        const error = await response.json().catch(() => ({}))
        throw new Error(error.message ?? 'Không thể nộp tệp.')
      }
    },
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ['learner-assignments', courseId] })
      void cache.invalidateQueries({ queryKey: ['assignment-grade', item.submissionId] })
    },
  })
  const submit = (event: FormEvent<HTMLFormElement>, appeal = false) => {
    event.preventDefault()
    setValidationError('')
    if (!appeal) {
      const form = new FormData(event.currentTarget)
      if (mode === 'text' && !textMeetsMinimum) {
        setValidationError('Bài viết cần có ít nhất 50 từ hoặc 300 ký tự chữ cái.')
        return
      }
      if (mode === 'file') {
        const file = form.get('file')
        if (!(file instanceof File) || file.size === 0) {
          setValidationError('Hãy chọn tệp PDF hoặc DOCX cần nộp.')
          return
        }
        if (file.size > 20 * 1024 * 1024) {
          setValidationError('Tệp nộp không được vượt quá 20 MB.')
          return
        }
      }
    }
    save.mutate({ form: new FormData(event.currentTarget), appeal })
  }
  return (
    <article className="space-y-3 rounded-lg border p-4">
      <h3 className="font-medium">{item.title}</h3>
      <p className="whitespace-pre-wrap text-sm">{item.instructions}</p>
      {item.deadline && <p className="text-sm text-slate-600">Hạn nộp {new Date(item.deadline).toLocaleString('vi-VN')}</p>}
      {item.status && <p role="status" className="text-sm">{({ SUBMITTED: 'Đã nộp', GRADED: 'Đã chấm', PENDING_REGRADE: 'Đang phúc khảo' } as Record<string,string>)[item.status] ?? item.status}</p>}
      {viva.data?.questions.length === 2 && !viva.data.completed && <section className="space-y-2 rounded-md border border-teal-200 bg-teal-50 p-3">
        <h4 className="font-medium">Vấn đáp xác thực bài làm (2 câu)</h4>
        {viva.data.questions.map((question, index) => <label key={index} className="grid gap-1 text-sm">{question}
          <textarea required maxLength={5000} value={vivaAnswers[index]} onChange={(event) => setVivaAnswers((rows) => rows.map((value, row) => row === index ? event.target.value : value))} className="rounded border p-2" rows={2} />
        </label>)}
        <p className="text-xs text-slate-600">Chỉ số nhất quán là gợi ý cho giảng viên, không phải kết luận về quyền tác giả.</p>
        <button type="button" disabled={answerViva.isPending || vivaAnswers.some((answer) => !answer.trim())} onClick={() => answerViva.mutate()} className="rounded bg-teal-700 px-3 py-2 text-sm text-white">{answerViva.isPending ? 'Đang gửi…' : 'Gửi câu trả lời'}</button>
      </section>}
      {viva.data?.completed && <section className="rounded-md border p-3 text-sm"><h4 className="font-medium">Đã hoàn thành phần vấn đáp</h4>
        {viva.data.consistencyScore != null && <p>Chỉ số nhất quán tham khảo: {viva.data.consistencyScore}/100</p>}
        {viva.data.consistencyNote && <p className="text-slate-600">{viva.data.consistencyNote}</p>}
      </section>}
      {answerViva.isError && <p role="alert" className="text-sm text-red-700">{answerViva.error.message}</p>}
      {item.canSubmit && <form className="grid gap-2" onSubmit={submit}>
        {item.allowText && item.allowFiles && <label className="text-sm">Cách nộp <select className="rounded border p-1" value={mode}
          onChange={(event) => setMode(event.target.value)}><option value="text">Văn bản</option><option value="file">PDF / DOCX</option></select></label>}
        {mode === 'text' ? <label className="grid gap-1 text-sm">Nội dung<textarea name="text" required maxLength={100000}
          value={draftText} onChange={(event) => setDraftText(event.target.value)} className="rounded border p-2" rows={5} />
          <span className="text-xs text-slate-500">{wordCount} từ · cần tối thiểu 50 từ hoặc 300 ký tự chữ cái</span></label>
          : <label className="grid gap-1 text-sm">Tệp PDF hoặc DOCX, tối đa 20 MB<input type="file" name="file" required accept=".pdf,.docx" /></label>}
        <button disabled={save.isPending} className="w-fit rounded bg-teal-700 px-3 py-2 text-sm text-white">{item.submissionId ? 'Nộp lại' : 'Nộp bài'}</button>
      </form>}
      {grade.data && <div className="space-y-2 border-t pt-2 text-sm">
        <p className="font-semibold">Điểm {grade.data.score}</p>
        {Object.entries(grade.data.scores ?? {}).map(([key, value]) => <p key={key}>{key} · {value}</p>)}
        {grade.data.feedback && <p className="whitespace-pre-wrap">{grade.data.feedback}</p>}
        {item.status === 'GRADED' && grade.data.appealUntil && Date.now() < Date.parse(grade.data.appealUntil) && (
          <form className="grid gap-2" onSubmit={(event) => submit(event, true)}>
            <label>Tiêu chí phúc khảo <select name="criterion" required className="rounded border p-1">
              {Object.keys(grade.data.scores ?? {}).map((key) => <option key={key}>{key}</option>)}
            </select></label>
            <label className="grid gap-1">Lý do<textarea name="reason" required maxLength={10000} className="rounded border p-2" /></label>
            <button disabled={save.isPending} className="w-fit rounded border px-3 py-1">Gửi phúc khảo</button>
          </form>
        )}
      </div>}
      {save.isError && <p role="alert" className="text-sm text-red-700">{save.error.message}</p>}
      {validationError && <p role="alert" className="text-sm text-red-700">{validationError}</p>}
    </article>
  )
}

export default function AssignmentPanel({ courseId }: { courseId: string }) {
  const assignments = useQuery({ queryKey: ['learner-assignments', courseId], retry: false,
    queryFn: () => request<Assignment[]>(`/api/v1/courses/${courseId}/learner-assignments`) })
  if (!assignments.data?.length) return null
  return <section className="space-y-3 rounded-xl border bg-white p-5">
    <h2 className="font-semibold">Bài tự luận</h2>
    {assignments.data.map((item) => <AssignmentCard key={item.id} item={item} courseId={courseId} />)}
  </section>
}
