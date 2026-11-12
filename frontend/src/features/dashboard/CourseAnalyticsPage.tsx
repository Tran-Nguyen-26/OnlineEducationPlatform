import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { fetchTeachingCourses, request } from '@/api/quiz'

interface Analytics {
  learners: number
  completed: number
  lessons: { id: string; title: string; started: number; completed: number }[]
}

const percent = new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 1 })

function rate(completed: number, learners: number) {
  return learners ? percent.format(completed / learners) : '—'
}

export default function CourseAnalyticsPage() {
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
      <h1 className="text-2xl font-semibold">Tiến độ khóa học</h1>
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
      {metrics.data && (
        <section className="space-y-4 rounded-xl bg-white p-5 shadow-sm">
          <p>
            Hoàn thành khóa học{' '}
            <strong>
              {metrics.data.completed}/{metrics.data.learners} (
              {rate(metrics.data.completed, metrics.data.learners)})
            </strong>
          </p>
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
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {metrics.data && (
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
