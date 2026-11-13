import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from '@/api/quiz'

export default function GraduationThresholdForm({ courseId }: { courseId: string }) {
  const path = `/api/v1/courses/${courseId}/graduation/threshold`
  const key = ['graduation-threshold', courseId]
  const cache = useQueryClient()
  const current = useQuery({ queryKey: key, queryFn: () => request<{ percent: number }>(path) })
  const save = useMutation({
    mutationFn: (percent: number) => request<{ percent: number }>(path, { method: 'PUT', body: JSON.stringify({ percent }) }),
    onSuccess: (value) => { cache.setQueryData(key, value) },
  })
  return (
    <form className="space-y-2 rounded-xl border border-slate-200 bg-white p-5" onSubmit={(event) => {
      event.preventDefault()
      save.mutate(Number(new FormData(event.currentTarget).get('percent')))
    }}>
      <h2 className="font-semibold">Điều kiện tốt nghiệp</h2>
      {current.data && (
        <label className="flex flex-wrap items-center gap-2 text-sm">
          Ngưỡng điểm (%)
          <input key={current.data.percent} name="percent" type="number" min={0} max={100} required
            defaultValue={current.data.percent} className="w-24 rounded border px-2 py-1" />
        </label>
      )}
      <button disabled={!current.data || save.isPending} className="rounded bg-teal-700 px-3 py-2 text-sm text-white disabled:opacity-50">Lưu ngưỡng</button>
      {current.isError && <p role="alert" className="text-sm text-red-700">{current.error.message}</p>}
      {save.isError && <p role="alert" className="text-sm text-red-700">{save.error.message}</p>}
      {save.isSuccess && <p role="status" className="text-sm text-teal-800">Đã lưu ngưỡng.</p>}
    </form>
  )
}
