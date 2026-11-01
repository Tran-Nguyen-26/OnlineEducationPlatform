import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, CircleAlert, Send } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { courseApi } from './courseApi'

export default function PublishCoursePage() {
  const { courseId = '' } = useParams()
  const queryClient = useQueryClient()
  const readiness = useQuery({ queryKey: ['publish-readiness', courseId], queryFn: async () => {
    const page = await courseApi.list()
    const course = page.items.find((item) => item.id === courseId)
    if (!course) throw new Error('Không tìm thấy khóa học')
    const modules = await courseApi.modules(courseId)
    let lessonCount = 0
    let availableContentCount = 0
    for (const module of modules.items) {
      const lessons = await courseApi.lessons(courseId, module.id)
      lessonCount += lessons.items.length
      for (const lesson of lessons.items) {
        const contents = await courseApi.contents(courseId, module.id, lesson.id)
        availableContentCount += contents.items.filter((item) => item.upload_status === 'AVAILABLE').length
      }
    }
    return { course, lessonCount, availableContentCount }
  } })
  const publish = useMutation({
    mutationFn: () => courseApi.publish(courseId),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['publish-readiness', courseId] }); await queryClient.invalidateQueries({ queryKey: ['instructor-courses'] }) },
  })
  const canPublish = (readiness.data?.lessonCount ?? 0) > 0 && (readiness.data?.availableContentCount ?? 0) > 0

  if (readiness.isLoading) return <div aria-label="Đang kiểm tra điều kiện xuất bản" className="mx-auto h-80 max-w-4xl animate-pulse rounded-2xl bg-slate-100" />
  if (readiness.isError || !readiness.data) return <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">{readiness.error?.message ?? 'Không kiểm tra được khóa học'}</p>

  return <main className="mx-auto max-w-3xl space-y-6">
    <Link to={`/instructor/courses/${courseId}/builder`} className="inline-flex items-center gap-2 text-sm font-semibold text-indigo-700"><ArrowLeft size={16} />Quay lại course builder</Link>
    <header><p className="text-sm font-semibold text-indigo-600">Bước cuối</p><h1 className="mt-2 text-3xl font-bold text-slate-950">Xuất bản khóa học</h1><p className="mt-2 text-slate-600">Kiểm tra nội dung trước khi học viên có thể truy cập khóa học.</p></header>
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
      <div><h2 className="text-xl font-bold">{readiness.data.course.title}</h2><p className="mt-1 text-sm text-slate-500">Trạng thái hiện tại: {readiness.data.course.lifecycle_status}</p></div>
      <ChecklistItem complete={readiness.data.lessonCount > 0} label={`${readiness.data.lessonCount} bài học`} help="Khóa học cần ít nhất một bài học." />
      <ChecklistItem complete={readiness.data.availableContentCount > 0} label={`${readiness.data.availableContentCount} nội dung khả dụng`} help="Cần ít nhất một nội dung đã lưu hoặc upload và xác minh thành công." />
      {publish.isError && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{publish.error instanceof Error ? publish.error.message : 'Không xuất bản được khóa học'}</p>}
      {publish.isSuccess && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm font-medium text-emerald-800">Khóa học đã được xuất bản.</p>}
      <button onClick={() => publish.mutate()} disabled={!canPublish || publish.isPending || readiness.data.course.lifecycle_status === 'PUBLISHED'} className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-5 py-3 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40"><Send size={17} />{publish.isPending ? 'Đang xuất bản…' : 'Xuất bản khóa học'}</button>
    </section>
  </main>
}

function ChecklistItem({ complete, label, help }: { complete: boolean; label: string; help: string }) {
  const Icon = complete ? CheckCircle2 : CircleAlert
  return <div className={`flex gap-3 rounded-xl p-4 ${complete ? 'bg-emerald-50' : 'bg-amber-50'}`}><Icon className={complete ? 'shrink-0 text-emerald-600' : 'shrink-0 text-amber-600'} /><div><p className="font-semibold text-slate-900">{label}</p><p className="mt-1 text-sm text-slate-600">{help}</p></div></div>
}
