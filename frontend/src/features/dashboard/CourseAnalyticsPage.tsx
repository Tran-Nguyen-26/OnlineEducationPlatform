import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { fetchTeachingCourses, request } from '@/api/quiz'

interface Analytics {
  learners: number
  completed: number
  lessons: { id: string; title: string; started: number; completed: number }[]
  assessments: {
    pairedLearners: number
    learningGain: number | null
    postTestScores: { from: number; to: number; learners: number }[]
  }
}
interface RewindBucket { videoId: string; videoTitle: string; lessonId: string; lessonTitle: string; bucketStartSec: number; rewinds: number }
interface Misconception { questionId: string; question: string; learningOutcome: string | null; distractor: string; wrongSelections: number }

const percent = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 1 })

function rate(completed: number, learners: number) {
  return learners ? percent.format(completed / learners) : '—'
}

export default function CourseAnalyticsPage({ admin = false }: { admin?: boolean }) {
  const [selection, setSelection] = useState('')
  const [category, setCategory] = useState('CONTENT')
  const [detail, setDetail] = useState('')
  const courses = useQuery({
    queryKey: ['analytics-teaching-courses'],
    queryFn: fetchTeachingCourses,
  })
  const course = selection || courses.data?.[0]?.id || ''
  const url = '/api/v1/courses/' + course + '/analytics'
  const metrics = useQuery({
    queryKey: ['course-analytics', course],
    queryFn: ({ signal }) => request<Analytics>(url, { signal }),
    enabled: Boolean(course),
  })
  const rewindHeatmap = useQuery({ queryKey: ['course-rewind-heatmap', course],
    queryFn: ({ signal }) => request<RewindBucket[]>(url + '/rewind-heatmap', { signal }), enabled: Boolean(course) })
  const misconceptions = useQuery({ queryKey: ['course-misconception-radar', course],
    queryFn: ({ signal }) => request<Misconception[]>(url + '/misconception-radar', { signal }), enabled: Boolean(course) })

  const exportReport = useMutation({
    mutationFn: async () => {
      const csv = await request<string>(url + '/report', { headers: { Accept: 'text/csv' } })
      const objectUrl = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = 'course-analytics.csv'
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
    },
  })
  const flag = useMutation({
    mutationFn: () =>
      request<void>(url + '/issues', {
        method: 'POST',
        body: JSON.stringify({ category, detail }),
      }),
    onSuccess: () => setDetail(''),
  })

  function reportIssue(event: FormEvent) {
    event.preventDefault()
    flag.mutate()
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold">{admin ? 'Phân tích học tập' : 'Tiến độ khóa học'}</h1>
      {courses.isPending && <p role="status">Đang tải khóa học…</p>}
      {courses.error && <p role="alert">{courses.error.message}</p>}
      {courses.data?.length === 0 && <p>Bạn chưa có khóa học phụ trách.</p>}
      {course && (
        <div className="flex flex-wrap items-center gap-3">
          <label>
            Khóa học
            <select
              className="ml-2 rounded border bg-white p-2"
              value={course}
              onChange={(event) => setSelection(event.target.value)}
            >
              {courses.data?.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.title}
                </option>
              ))}
            </select>
          </label>
          <button
            className="rounded border bg-white px-3 py-2"
            disabled={!metrics.data || exportReport.isPending}
            onClick={() => exportReport.mutate()}
          >
            Xuất CSV
          </button>
        </div>
      )}
      {course && metrics.isPending && <p role="status">Đang tải tiến độ…</p>}
      {(metrics.error || exportReport.error) && (
        <p role="alert">{(metrics.error || exportReport.error)?.message}</p>
      )}
      {rewindHeatmap.error && <p role="alert">{rewindHeatmap.error.message}</p>}
      {rewindHeatmap.data && <section className="space-y-3 rounded-xl bg-white p-5 shadow-sm">
        <div><h2 className="font-semibold">Heatmap tua lại video</h2><p className="text-sm text-slate-600">Các mốc 30 giây được tua lại nhiều nhất; chỉ ghi nhận tổng hợp, không gắn với học viên.</p></div>
        {!rewindHeatmap.data.length ? <p className="text-sm text-slate-600">Chưa có dữ liệu tua lại.</p> : <ol className="space-y-2">
          {rewindHeatmap.data.slice(0, 20).map((bucket) => {
            const maximum = Math.max(...rewindHeatmap.data.map((row) => row.rewinds), 1)
            const marker = `${Math.floor(bucket.bucketStartSec / 60)}:${String(bucket.bucketStartSec % 60).padStart(2, '0')}`
            return <li key={`${bucket.videoId}-${bucket.bucketStartSec}`} className="grid gap-1 text-sm sm:grid-cols-[1fr_auto]">
              <span>{bucket.lessonTitle} · {bucket.videoTitle} · {marker}–{Math.floor((bucket.bucketStartSec + 30) / 60)}:{String((bucket.bucketStartSec + 30) % 60).padStart(2, '0')}</span>
              <strong>{bucket.rewinds} lượt</strong>
              <span aria-hidden="true" className="h-2 rounded bg-slate-100 sm:col-span-2"><span className="block h-2 rounded bg-amber-500" style={{ width: `${100 * bucket.rewinds / maximum}%` }} /></span>
            </li>
          })}
        </ol>}
      </section>}
      {misconceptions.error && <p role="alert">{misconceptions.error.message}</p>}
      {misconceptions.data && <section className="space-y-3 rounded-xl bg-white p-5 shadow-sm">
        <div><h2 className="font-semibold">Radar phương án nhiễu</h2><p className="text-sm text-slate-600">Phương án sai được chọn nhiều nhất trên các bài đã chấm; dùng để tìm LO hoặc câu hỏi cần giảng lại.</p></div>
        {!misconceptions.data.length ? <p className="text-sm text-slate-600">Chưa có dữ liệu câu trả lời sai.</p> : <ol className="space-y-2">
          {misconceptions.data.slice(0, 20).map((row, index) => <li key={`${row.questionId}-${row.distractor}`} className="rounded border p-3 text-sm">
            <p className="font-medium">{index + 1}. {row.question}</p>
            {row.learningOutcome && <p className="text-xs text-slate-500">LO {row.learningOutcome}</p>}
            <p className="mt-1">Phương án nhiễu: {row.distractor}</p>
            <p className="text-xs text-rose-700">Được chọn sai {row.wrongSelections} lượt</p>
          </li>)}
        </ol>}
      </section>}
      {metrics.data && (
        <section className="space-y-4 rounded-xl bg-white p-5 shadow-sm">
          <p>
            Hoàn thành khóa học{' '}
            <strong>
              {metrics.data.completed}/{metrics.data.learners} (
              {rate(metrics.data.completed, metrics.data.learners)})
            </strong>
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg bg-slate-50 p-3">
              <h2 className="font-semibold">Mức tăng điểm Pre-test → Post-test</h2>
              <p className="mt-1 text-sm">
                {metrics.data.assessments.learningGain == null ? 'Chưa có cặp kết quả'
                  : `${metrics.data.assessments.learningGain.toLocaleString('vi-VN')} điểm phần trăm`}
                {' · '}{metrics.data.assessments.pairedLearners} học viên có cả hai kết quả
              </p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3">
              <h2 className="font-semibold">Phân bố điểm Post-test</h2>
              <dl className="mt-1 grid grid-cols-2 gap-1 text-sm">
                {metrics.data.assessments.postTestScores.map((band) => (
                  <div key={band.from} className="flex justify-between gap-2">
                    <dt>{band.from}–{band.to === 100 ? '100' : `dưới ${band.to}`}%</dt><dd>{band.learners}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr>
                  <th className="p-2" scope="col">
                    Bài học
                  </th>
                  <th className="p-2" scope="col">
                    Đã bắt đầu
                  </th>
                  <th className="p-2" scope="col">
                    Đã hoàn thành
                  </th>
                  <th className="p-2" scope="col">
                    Tỷ lệ hoàn thành
                  </th>
                  <th className="p-2" scope="col">Đã bắt đầu, chưa hoàn thành</th>
                </tr>
              </thead>
              <tbody>
                {metrics.data.lessons.map((lesson) => (
                  <tr className="border-t" key={lesson.id}>
                    <th scope="row" className="p-2 font-normal">
                      {lesson.title}
                    </th>
                    <td className="p-2">{lesson.started}</td>
                    <td className="p-2">{lesson.completed}</td>
                    <td className="p-2">{rate(lesson.completed, metrics.data.learners)}</td>
                    <td className="p-2">{rate(lesson.started - lesson.completed, lesson.started)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {metrics.data && !admin && (
        <form className="space-y-3 rounded-xl bg-white p-5 shadow-sm" onSubmit={reportIssue}>
          <h2 className="font-semibold">Báo lỗi nội dung hoặc rubric</h2>
          <label>
            Loại lỗi
            <select
              className="ml-2 rounded border p-2"
              value={category}
              onChange={(event) => setCategory(event.target.value)}
            >
              <option value="CONTENT">Nội dung</option>
              <option value="RUBRIC">Rubric</option>
            </select>
          </label>
          <label className="block">
            Mô tả
            <textarea
              className="mt-1 block w-full rounded border p-2"
              value={detail}
              required
              maxLength={1000}
              onChange={(event) => setDetail(event.target.value)}
            />
          </label>
          <button
            type="submit"
            disabled={flag.isPending || !detail.trim()}
            className="rounded bg-indigo-700 px-4 py-2 text-white disabled:opacity-50"
          >
            Gửi báo lỗi
          </button>
          {flag.isSuccess && <p role="status">Đã tiếp nhận báo lỗi.</p>}
          {flag.error && <p role="alert">{flag.error.message}</p>}
        </form>
      )}
    </div>
  )
}
