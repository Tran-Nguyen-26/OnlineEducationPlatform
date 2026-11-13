import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from '@/api/quiz'
import { useState, type FormEvent } from 'react'

type Assignment = { id: string; title: string; instructions: string; deadline: string; allowText: boolean;
  allowFiles: boolean; submissionId?: string; status?: string; submittedText?: string; canSubmit: boolean }
type Grade = { score: number; scores: Record<string, number>; feedback?: string; appealUntil?: string }

function AssignmentCard({ item, courseId }: { item: Assignment; courseId: string }) {
  const cache = useQueryClient()
  const [mode, setMode] = useState(item.allowText ? 'text' : 'file')
  const gradePath = `/api/v1/courses/${courseId}/grading/${item.submissionId}`
  const grade = useQuery({ queryKey: ['assignment-grade', item.submissionId],
    enabled: !!item.submissionId && ['GRADED', 'PENDING_REGRADE'].includes(item.status ?? ''),
    queryFn: () => request<Grade>(gradePath + '/result') })
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
    save.mutate({ form: new FormData(event.currentTarget), appeal })
  }
  return (
    <article className="space-y-3 rounded-lg border p-4">
      <h3 className="font-medium">{item.title}</h3>
      <p className="whitespace-pre-wrap text-sm">{item.instructions}</p>
      {item.deadline && <p className="text-sm text-slate-600">Hạn nộp {new Date(item.deadline).toLocaleString('vi-VN')}</p>}
      {item.status && <p role="status" className="text-sm">{({ SUBMITTED: 'Đã nộp', GRADED: 'Đã chấm', PENDING_REGRADE: 'Đang phúc khảo' } as Record<string,string>)[item.status] ?? item.status}</p>}
      {item.canSubmit && <form className="grid gap-2" onSubmit={submit}>
        {item.allowText && item.allowFiles && <label className="text-sm">Cách nộp <select className="rounded border p-1" value={mode}
          onChange={(event) => setMode(event.target.value)}><option value="text">Văn bản</option><option value="file">PDF / DOCX</option></select></label>}
        {mode === 'text' ? <label className="grid gap-1 text-sm">Nội dung<textarea name="text" required maxLength={100000}
          defaultValue={item.submittedText ?? ''} className="rounded border p-2" rows={5} /></label>
          : <label className="grid gap-1 text-sm">Tệp tối đa 20 MB<input type="file" name="file" required accept=".pdf,.docx" /></label>}
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
