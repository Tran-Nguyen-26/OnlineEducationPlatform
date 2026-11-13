import { useQuery } from '@tanstack/react-query'
import { request } from '@/api/quiz'

type Outcome = {
  code: string
  description: string
  bloom: string
  percent: number | null
  quizItems: number
  rubricCriteria: number
}

const bloomLabels: Record<string, string> = { REMEMBER: 'Nhớ', UNDERSTAND: 'Hiểu', APPLY: 'Vận dụng' }

export default function OutcomeMasteryPanel({ courseId }: { courseId: string }) {
  const mastery = useQuery({
    queryKey: ['outcome-mastery', courseId],
    queryFn: () => request<Outcome[]>(`/api/v1/courses/${courseId}/graduation/outcomes`),
    retry: false,
  })
  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="font-semibold">Bản đồ thành thạo chuẩn đầu ra</h2>
      {mastery.isPending && <p role="status" className="text-sm text-slate-600">Đang tải kết quả…</p>}
      {mastery.isError && <p role="alert" className="text-sm text-slate-600">{mastery.error.message}</p>}
      {mastery.data?.length === 0 && <p className="text-sm text-slate-600">Khóa học chưa có chuẩn đầu ra.</p>}
      <ul className="grid gap-3 sm:grid-cols-2">
        {mastery.data?.map((outcome) => (
          <li key={outcome.code} className="space-y-2 rounded-lg border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">{outcome.code} · {bloomLabels[outcome.bloom] ?? outcome.bloom}</h3>
              <span className="text-sm font-medium text-teal-800">{outcome.percent == null ? 'Chưa có điểm' : `${outcome.percent.toLocaleString('vi-VN')}%`}</span>
            </div>
            <p className="break-words text-sm text-slate-700">{outcome.description}</p>
            {outcome.percent != null && (
              <>
                <meter value={outcome.percent} min={0} max={100} className="block w-full" aria-label={`Mức thành thạo ${outcome.code}`} />
                <p className="text-xs text-slate-600">{outcome.quizItems} câu hỏi · {outcome.rubricCriteria} tiêu chí tự luận</p>
              </>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
