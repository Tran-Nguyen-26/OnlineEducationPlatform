import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

type Outcome = { id: string; code: string; description: string; bloom: string }
type Weights = { postTest: number | null; massExam: number | null; assignment: number | null }
const weightLabels = { postTest: 'Post-test', massExam: 'Kỳ thi đồng loạt', assignment: 'Bài tự luận' }
const inputClass = 'rounded-lg border px-3 py-2 text-sm'
const buttonClass = 'rounded-lg bg-teal-700 px-4 py-2 text-sm text-white disabled:opacity-50'

export default function CurriculumSettings({ courseId, editable }: { courseId: string; editable: boolean }) {
  const cache = useQueryClient()
  const [editing, setEditing] = useState<Outcome>()
  const [target, setTarget] = useState('material')
  const path = `/api/v1/courses/${courseId}/curriculum`
  const headers = { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
  const key = ['curriculum-settings', courseId]
  const { data, isError, isPending } = useQuery({
    queryKey: key,
    queryFn: async () => (await axios.get<{ weights: Weights; outcomes: Outcome[] }>(path, { headers })).data,
  })
  const save = useMutation({
    mutationFn: ({ suffix, method, data }: { suffix: string; method: 'post' | 'put'; data: object }) =>
      axios.request({ url: path + suffix, method, data, headers }),
    onSuccess: () => cache.invalidateQueries({ queryKey: key }),
  })
  const error = axios.isAxiosError<{ message?: string }>(save.error) ? save.error.response?.data?.message : undefined

  function submitWeights(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    save.mutate({ suffix: '/weights', method: 'put', data: {
      postTest: Number(form.get('postTest')), massExam: Number(form.get('massExam')), assignment: Number(form.get('assignment')),
    } })
  }

  function submitOutcome(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    save.mutate({
      suffix: editing ? `/outcomes/${editing.id}` : '/outcomes',
      method: editing ? 'put' : 'post',
      data: { code: String(form.get('code')).trim(), description: String(form.get('description')).trim(), bloom: form.get('bloom') },
    }, { onSuccess: () => setEditing(undefined) })
  }

  function submitAlignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const id = encodeURIComponent(String(form.get('id')).trim())
    const quizId = encodeURIComponent(String(form.get('quizId') ?? '').trim())
    const outcomeId = form.get('outcomeId')
    if (target === 'quiz') {
      save.mutate({ suffix: `/quizzes/${id}`, method: 'put', data: { kind: form.get('kind') } })
    } else {
      const suffix = target === 'material' ? `/materials/${id}` : `/quizzes/${quizId}/questions/${id}`
      save.mutate({ suffix, method: 'put', data: { outcomeId } })
    }
  }

  if (isPending) return <p role="status">Đang tải chuẩn đầu ra…</p>
  if (isError || !data) return <p role="alert">Không tải được chuẩn đầu ra.</p>
  const disabled = !editable || save.isPending

  return (
    <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-semibold">Chuẩn đầu ra và trọng số</h2>
      {!editable && <p className="text-sm text-slate-600">Chuyển khóa học về bản nháp để chỉnh cấu hình.</p>}
      <form onSubmit={submitWeights}>
        <fieldset disabled={disabled} className="flex flex-wrap items-end gap-2">
          {(Object.keys(weightLabels) as (keyof Weights)[]).map((name) => (
            <label key={name} className="grid gap-1 text-sm">{weightLabels[name]} (%)
              <input className={inputClass + ' w-28'} name={name} type="number" min="0" max="100" required defaultValue={data.weights[name] ?? ''} />
            </label>
          ))}
          <button className={buttonClass}>Lưu trọng số</button>
        </fieldset>
      </form>
      <ul className="space-y-1">
        {data.outcomes.map((outcome) => (
          <li key={outcome.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm">
            <span><strong>{outcome.code}</strong> · {outcome.bloom} · {outcome.description}</span>
            {editable && <button type="button" className="text-teal-800" onClick={() => setEditing(outcome)}>Sửa</button>}
          </li>
        ))}
      </ul>
      {editable && (
        <>
          <form key={editing?.id ?? 'new-outcome'} onSubmit={submitOutcome}>
            <fieldset disabled={disabled} className="flex flex-wrap items-end gap-2">
              <label className="grid gap-1 text-sm">Mã LO
                <input className={inputClass + ' w-28'} name="code" required pattern="[A-Za-z0-9][A-Za-z0-9_\-]{0,39}" defaultValue={editing?.code} />
              </label>
              <label className="grid flex-1 gap-1 text-sm">Mô tả
                <input className={inputClass} name="description" required maxLength={500} defaultValue={editing?.description} />
              </label>
              <label className="grid gap-1 text-sm">Bloom
                <select className={inputClass} name="bloom" defaultValue={editing?.bloom ?? 'REMEMBER'}>
                  <option value="REMEMBER">Nhớ</option><option value="UNDERSTAND">Hiểu</option><option value="APPLY">Vận dụng</option>
                </select>
              </label>
              <button className={buttonClass}>{editing ? 'Cập nhật LO' : 'Thêm LO'}</button>
              {editing && <button type="button" onClick={() => setEditing(undefined)} className="px-2 py-2 text-sm">Hủy</button>}
            </fieldset>
          </form>
          <form onSubmit={submitAlignment} className="border-t pt-4">
            <fieldset disabled={disabled} className="flex flex-wrap items-end gap-2">
              <label className="grid gap-1 text-sm">Liên kết
                <select className={inputClass} value={target} onChange={(event) => setTarget(event.target.value)}>
                  <option value="material">Học liệu</option><option value="quiz">Loại bài kiểm tra</option><option value="question">Duyệt và gắn câu hỏi thủ công</option>
                </select>
              </label>
              <label className="grid gap-1 text-sm">{target === 'material' ? 'ID học liệu' : target === 'quiz' ? 'ID bài kiểm tra' : 'ID câu hỏi'}
                <input key={target} className={inputClass} name="id" required />
              </label>
              {target === 'question' && <label className="grid gap-1 text-sm">ID bài kiểm tra<input className={inputClass} name="quizId" required /></label>}
              {target === 'quiz'
                ? <label className="grid gap-1 text-sm">Loại bài
                    <select className={inputClass} name="kind">
                      <option value="PRE_TEST">Pre-test</option><option value="MINI_QUIZ">Mini-quiz</option>
                      <option value="POST_TEST">Post-test</option><option value="MASS_EXAM">Kỳ thi đồng loạt</option>
                    </select>
                  </label>
                : <label className="grid gap-1 text-sm">Chuẩn đầu ra
                    <select className={inputClass} name="outcomeId" required defaultValue="">
                      <option value="" disabled>Chọn LO</option>
                      {data.outcomes.map((outcome) => <option key={outcome.id} value={outcome.id}>{outcome.code}</option>)}
                    </select>
                  </label>}
              <button className={buttonClass} disabled={target !== 'quiz' && !data.outcomes.length}>Lưu liên kết</button>
            </fieldset>
          </form>
        </>
      )}
      {save.isSuccess && <p role="status" className="text-sm text-teal-800">Đã lưu cấu hình.</p>}
      {save.isError && <p role="alert" className="text-sm text-red-700">{error ?? 'Không thể lưu cấu hình.'}</p>}
    </section>
  )
}
