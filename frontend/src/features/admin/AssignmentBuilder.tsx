import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { request } from '@/api/quiz'

type Criterion = { id: number; code: string; outcome: string; description: string; points: number }
const input = 'rounded border px-2 py-1'

export default function AssignmentBuilder({ courseId, outcomes }: { courseId: string; outcomes: { code: string }[] }) {
  const [rows, setRows] = useState<Criterion[]>([{ id: 1, code: '', outcome: '', description: '', points: 10 }])
  const [next, setNext] = useState(2)
  const create = useMutation({
    mutationFn: (data: object) => request(`/api/v1/courses/${courseId}/admin-assignments`, { method: 'POST', body: JSON.stringify(data) }),
  })
  function update(id: number, change: Partial<Criterion>) {
    setRows((current) => current.map((row) => row.id === id ? { ...row, ...change } : row))
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    create.mutate({ title: form.get('title'), instructions: form.get('instructions'),
      deadline: new Date(String(form.get('deadline'))).toISOString(), attempts: Number(form.get('attempts')),
      rubric: rows.map(({ code, outcome, description, points }) => ({ code, outcome, description, points })) })
  }
  return (
    <form onSubmit={submit} className="grid gap-3 rounded-lg border p-4">
      <h3 className="font-semibold">Tạo bài tự luận và rubric</h3>
      <label className="grid gap-1 text-sm">Tên bài<input name="title" required maxLength={255} className={input} /></label>
      <label className="grid gap-1 text-sm">Đề bài<textarea name="instructions" required maxLength={10000} rows={3} className={input} /></label>
      <div className="flex flex-wrap gap-2">
        <label className="grid gap-1 text-sm">Hạn nộp<input name="deadline" type="datetime-local" required className={input} /></label>
        <label className="grid gap-1 text-sm">Số lượt nộp<input name="attempts" type="number" min={1} max={20} defaultValue={2} required className={input} /></label>
      </div>
      {rows.map((row) => <fieldset key={row.id} className="grid gap-2 rounded border p-3 sm:grid-cols-2">
        <label className="grid gap-1 text-sm">Mã tiêu chí<input required maxLength={40} className={input} value={row.code}
          onChange={(event) => update(row.id, { code: event.target.value })} /></label>
        <label className="grid gap-1 text-sm">Chuẩn đầu ra<select required className={input} value={row.outcome}
          onChange={(event) => update(row.id, { outcome: event.target.value })}>
          <option value="">Chọn LO</option>{outcomes.map(({ code }) => <option key={code}>{code}</option>)}
        </select></label>
        <label className="grid gap-1 text-sm">Mô tả<input required maxLength={500} className={input} value={row.description}
          onChange={(event) => update(row.id, { description: event.target.value })} /></label>
        <label className="grid gap-1 text-sm">Điểm tối đa<input required min={0.01} max={999.99} step={0.01} type="number"
          className={input} value={row.points || ''} onChange={(event) => update(row.id, { points: Number(event.target.value) })} /></label>
        {rows.length > 1 && <button type="button" className="w-fit text-sm text-red-700" onClick={() => setRows(rows.filter((item) => item.id !== row.id))}>Xóa tiêu chí</button>}
      </fieldset>)}
      <div className="flex gap-3">
        <button type="button" disabled={rows.length >= 30} className="rounded border px-3 py-1 text-sm"
          onClick={() => { setRows([...rows, { id: next, code: '', outcome: '', description: '', points: 10 }]); setNext(next + 1) }}>Thêm tiêu chí</button>
        <button disabled={create.isPending} className="rounded bg-teal-700 px-3 py-1 text-sm text-white">Tạo bài</button>
      </div>
      {create.isSuccess && <p role="status" className="text-sm text-teal-800">Đã tạo bài tự luận.</p>}
      {create.isError && <p role="alert" className="text-sm text-red-700">{create.error.message}</p>}
    </form>
  )
}
