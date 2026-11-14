import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { request } from '@/api/quiz'

type Outcome = {
  code: string
  description: string
  bloom: string
  percent: number | null
  quizItems: number
  rubricCriteria: number
}
type Drill = { questionId: string; question: string; learningOutcome: string; dueAt: string; options: { id: string; text: string }[] }
type RemediationPlan = { outcomes: { code: string; description: string; percent: number; videos: { id: string; lessonId: string; lessonTitle: string; title: string; durationSeconds: number }[]; drills: Drill[] }[] }
type ReviewResult = { questionId: string; correct: boolean; correctOptionIds: string[]; appliedRating: string; nextDueAt: string; stabilityDays: number; difficulty: number }

const bloomLabels: Record<string, string> = { REMEMBER: 'Nhớ', UNDERSTAND: 'Hiểu', APPLY: 'Vận dụng' }

export default function OutcomeMasteryPanel({ courseId, activeAccess = true }: { courseId: string; activeAccess?: boolean }) {
  const cache = useQueryClient()
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [ratings, setRatings] = useState<Record<string, string>>({})
  const [results, setResults] = useState<Record<string, ReviewResult>>({})
  const mastery = useQuery({
    queryKey: ['outcome-mastery', courseId],
    queryFn: () => request<Outcome[]>(`/api/v1/courses/${courseId}/graduation/outcomes`),
    retry: false,
  })
  const remediation = useQuery({ queryKey: ['adaptive-remediation', courseId], enabled: activeAccess,
    queryFn: () => request<RemediationPlan>(`/api/v1/courses/${courseId}/graduation/remediation`) })
  const answerDrill = useMutation({
    mutationFn: ({ questionId, selectedOptionId, rating }: { questionId: string; selectedOptionId: string; rating: string }) =>
      request<ReviewResult>(`/api/v1/courses/${courseId}/graduation/remediation/${questionId}/answer`, {
        method: 'POST', body: JSON.stringify({ selectedOptionId, rating }),
      }),
    onSuccess: (result) => {
      setResults((current) => ({ ...current, [result.questionId]: result }))
    },
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
      {activeAccess && <section className="space-y-3 border-t pt-4">
        <div className="flex flex-wrap items-start justify-between gap-2"><div><h3 className="font-semibold">Lộ trình học bù thích ứng</h3><p className="text-sm text-slate-600">Các LO dưới 70% được ghép với video đã mở và câu hỏi ôn tập đến hạn. Lịch ôn dùng độ ổn định, độ khó và khả năng nhớ theo mô hình FSRS.</p></div>
          <button type="button" onClick={() => void cache.invalidateQueries({ queryKey: ['adaptive-remediation', courseId] })} className="rounded border bg-white px-2 py-1 text-xs">Cập nhật lịch ôn</button></div>
        {remediation.isPending && <p role="status" className="text-sm text-slate-600">Đang tạo lộ trình…</p>}
        {remediation.isError && <p role="alert" className="text-sm text-red-700">{remediation.error.message}</p>}
        {remediation.data?.outcomes.length === 0 && <p className="text-sm text-emerald-800">Chưa có LO dưới ngưỡng 70% cần ôn tập.</p>}
        {remediation.data?.outcomes.map((item) => <article key={item.code} className="space-y-3 rounded-lg border border-amber-200 bg-amber-50/50 p-3">
          <div><h4 className="font-semibold">{item.code} · {item.percent.toLocaleString('vi-VN')}%</h4><p className="text-sm">{item.description}</p></div>
          <div className="space-y-1 text-sm"><p className="font-medium">Video nên xem lại</p>
            {!item.videos.length && <p className="text-slate-600">Chưa có video được gắn với LO trong các bài đã mở.</p>}
            {item.videos.map((video) => <p key={video.id}><a className="text-teal-800 underline" href={`/courses/${courseId}?video=${video.id}&time=0`}>{video.lessonTitle} · {video.title}</a></p>)}
          </div>
          {!!item.drills.length && <div className="space-y-3"><p className="font-medium text-sm">Micro-drill đến hạn · tối đa 5 câu</p>
            {item.drills.map((drill) => <div key={drill.questionId} className="space-y-2 rounded border bg-white p-3 text-sm">
              <p className="font-medium">{drill.question}</p>
              {drill.options.map((option) => <label key={option.id} className="flex items-start gap-2">
                <input type="radio" name={`drill-${drill.questionId}`} checked={selected[drill.questionId] === option.id}
                  disabled={!!results[drill.questionId]}
                  onChange={() => setSelected((value) => ({ ...value, [drill.questionId]: option.id }))} />{option.text}
              </label>)}
              <label className="flex items-center gap-2 text-xs">Mức độ nhớ
                <select className="rounded border p-1" value={ratings[drill.questionId] ?? 'GOOD'} onChange={(event) => setRatings((value) => ({ ...value, [drill.questionId]: event.target.value }))}>
                  <option value="AGAIN">Quên</option><option value="HARD">Khó</option><option value="GOOD">Nhớ được</option><option value="EASY">Dễ</option>
                </select>
              </label>
              <button type="button" disabled={answerDrill.isPending || !selected[drill.questionId] || !!results[drill.questionId]} onClick={() => answerDrill.mutate({
                questionId: drill.questionId, selectedOptionId: selected[drill.questionId], rating: ratings[drill.questionId] ?? 'GOOD',
              })} className="rounded bg-indigo-700 px-3 py-1.5 text-white disabled:opacity-50">Trả lời</button>
              {results[drill.questionId] && <p role="status" className={results[drill.questionId].correct ? 'text-emerald-800' : 'text-rose-800'}>
                {results[drill.questionId].correct ? 'Chính xác.' : `Chưa chính xác. Đáp án đúng: ${drill.options.filter((option) => results[drill.questionId].correctOptionIds.includes(option.id)).map((option) => option.text).join(', ')}.`}
                {' '}Lịch ôn tiếp theo: {new Date(results[drill.questionId].nextDueAt).toLocaleString('vi-VN')}.
              </p>}
            </div>)}
          </div>}
        </article>)}
        {answerDrill.isError && <p role="alert" className="text-sm text-red-700">{answerDrill.error.message}</p>}
      </section>}
    </section>
  )
}
