import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Plus, ArrowUpRight } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { courseApi, type Course } from './courseApi'

export default function InstructorCoursesPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [error, setError] = useState('')
  const courses = useQuery({ queryKey: ['instructor-courses'], queryFn: courseApi.list })
  const create = useMutation({
    mutationFn: () => courseApi.create({
      title: title.trim(), description: '', visibility: 'PUBLIC', learning_mode: 'FREE_NAVIGATION',
    }),
    onSuccess: async (course) => {
      await queryClient.invalidateQueries({ queryKey: ['instructor-courses'] })
      navigate(`/instructor/courses/${course.id}/builder`)
    },
    onError: (cause) => setError(cause instanceof Error ? cause.message : 'Không tạo được khóa học'),
  })

  return (
    <main className="mx-auto max-w-6xl space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">Không gian giảng viên</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">Khóa học của tôi</h1>
          <p className="mt-2 text-slate-600">Tạo nội dung, sắp xếp bài học và xuất bản khóa học.</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-3 font-semibold text-white shadow-sm hover:bg-indigo-700">
          <Plus size={18} /> Tạo khóa học
        </button>
      </header>

      {courses.isError && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">{courses.error.message}</p>}
      {courses.isLoading ? <div aria-label="Đang tải" className="h-40 animate-pulse rounded-2xl bg-slate-100" /> : (
        <section aria-label="Danh sách khóa học" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {(courses.data?.items ?? []).map((course: Course) => (
            <article key={course.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between gap-4">
                <div className="flex size-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600"><BookOpen size={21} /></div>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${course.lifecycle_status === 'PUBLISHED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                  {course.lifecycle_status === 'PUBLISHED' ? 'Đã xuất bản' : course.lifecycle_status}
                </span>
              </div>
              <h2 className="mt-5 line-clamp-2 text-lg font-bold text-slate-900">{course.title}</h2>
              <p className="mt-2 line-clamp-2 min-h-10 text-sm text-slate-500">{course.description || 'Chưa có mô tả'}</p>
              <Link to={`/instructor/courses/${course.id}/builder`} className="mt-5 inline-flex items-center gap-2 font-semibold text-indigo-700 hover:text-indigo-900">
                Mở course builder <ArrowUpRight size={16} />
              </Link>
            </article>
          ))}
          {courses.data?.items.length === 0 && <div className="col-span-full rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center"><BookOpen className="mx-auto text-slate-400" /><h2 className="mt-4 font-semibold text-slate-900">Chưa có khóa học</h2><p className="mt-1 text-sm text-slate-500">Tạo khóa học đầu tiên để bắt đầu xây dựng nội dung.</p></div>}
        </section>
      )}

      {showCreate && <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4" role="presentation">
        <form aria-label="Tạo khóa học" onSubmit={(event) => { event.preventDefault(); setError(''); create.mutate() }} className="w-full max-w-lg space-y-5 rounded-2xl bg-white p-6 shadow-2xl">
          <div><h2 className="text-xl font-bold">Tạo khóa học mới</h2><p className="mt-1 text-sm text-slate-500">Khóa học được tạo ở trạng thái nháp.</p></div>
          <label className="block text-sm font-medium text-slate-700">Tên khóa học<input autoFocus required maxLength={255} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5" /></label>
          {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
          <div className="flex justify-end gap-3"><button type="button" onClick={() => setShowCreate(false)} className="rounded-xl px-4 py-2.5 text-slate-600 hover:bg-slate-100">Hủy</button><button disabled={create.isPending || !title.trim()} className="rounded-xl bg-indigo-600 px-4 py-2.5 font-semibold text-white disabled:opacity-50">{create.isPending ? 'Đang tạo…' : 'Tạo nháp'}</button></div>
        </form>
      </div>}
    </main>
  )
}
