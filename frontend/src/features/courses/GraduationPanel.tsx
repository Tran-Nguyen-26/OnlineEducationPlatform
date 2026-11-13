import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from '@/api/quiz'
import type { FormEvent } from 'react'
import CourseBadge from './CourseBadge'

type Progress = {
  requiredLessons: number
  completedLessons: number
  finalPercent: number | null
  threshold: number
  postTest: number | null
  massExam: number | null
  assignment: number | null
  surveySubmitted: boolean
  eligible: boolean
}

function percent(value: number | null) {
  return value == null ? 'Chưa đủ điểm' : `${value.toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%`
}

export default function GraduationPanel({ courseId }: { courseId: string }) {
  const path = `/api/v1/courses/${courseId}/graduation`
  const key = ['graduation', courseId]
  const cache = useQueryClient()
  const progress = useQuery({ queryKey: key, queryFn: () => request<Progress>(path), retry: false })
  const survey = useMutation({
    mutationFn: (answers: { csat: number; nps: number }) => request<Progress>(path + '/survey', {
      method: 'POST', body: JSON.stringify(answers),
    }),
    onSuccess: (value) => { cache.setQueryData(key, value) },
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    survey.mutate({ csat: Number(form.get('csat')), nps: Number(form.get('nps')) })
  }
  if (progress.isPending) return <p role="status">Đang tải kết quả khóa học…</p>
  if (progress.isError) return (
    <>
      <p role="status" className="text-sm text-slate-600">{progress.error.message}</p>
      <CourseBadge courseId={courseId} eligible={false} />
    </>
  )
  const data = progress.data
  const scores = [
    ['Post-test', percent(data.postTest)], ['Thi đồng loạt', percent(data.massExam)],
    ['Tự luận', percent(data.assignment)], ['Tổng kết', percent(data.finalPercent)],
  ]
  return (
    <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="font-semibold">Kết quả khóa học</h2>
      <p className="text-sm text-slate-600">
        Đã hoàn thành {data.completedLessons}/{data.requiredLessons} bài bắt buộc. Ngưỡng tốt nghiệp {data.threshold}%.
      </p>
      <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {scores.map(([label, value]) => (
          <div key={label} className="rounded-lg bg-slate-50 p-3"><dt className="text-sm text-slate-600">{label}</dt><dd className="font-semibold">{value}</dd></div>
        ))}
      </dl>
      {data.surveySubmitted ? <p className="text-sm text-teal-800">Đã hoàn tất khảo sát.</p> : (
        <form onSubmit={submit} className="space-y-3 border-t pt-3">
          <h3 className="font-medium">Khảo sát khóa học</h3>
          <fieldset disabled={survey.isPending} className="grid gap-3">
            <label className="flex flex-wrap items-center gap-2 text-sm">Bạn hài lòng với khóa học ở mức nào? (1–5)
              <input type="number" name="csat" min={1} max={5} required className="w-20 rounded border px-2 py-1" />
            </label>
            <label className="flex flex-wrap items-center gap-2 text-sm">Bạn sẵn sàng giới thiệu khóa học ở mức nào? (0–10)
              <input type="number" name="nps" min={0} max={10} required className="w-20 rounded border px-2 py-1" />
            </label>
            <button className="w-fit rounded bg-teal-700 px-3 py-2 text-sm text-white">Gửi khảo sát</button>
          </fieldset>
          {survey.isError && <p role="alert" className="text-sm text-red-700">{survey.error.message}</p>}
        </form>
      )}
      <p role="status" className="text-sm font-medium">{data.eligible ? 'Đã đạt điều kiện tốt nghiệp.' : 'Chưa đủ điều kiện tốt nghiệp.'}</p>
      <CourseBadge courseId={courseId} eligible={data.eligible} />
    </section>
  )
}
