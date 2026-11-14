import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { decideCourseIssue, fetchCourseIssues, type CourseIssue } from '@/api/adminCourseIssues'

const statusLabel: Record<CourseIssue['status'], string> = { OPEN: 'Chờ xử lý', RESOLVED: 'Đã xử lý', REJECTED: 'Không tiếp nhận' }

export default function AdminCourseIssuesPage() {
  const cache = useQueryClient()
  const [status, setStatus] = useState('OPEN')
  const [resolutions, setResolutions] = useState<Record<string, string>>({})
  const issues = useQuery({ queryKey: ['admin-course-issues', status], queryFn: () => fetchCourseIssues(status) })
  const decide = useMutation({
    mutationFn: ({ issue, decision }: { issue: CourseIssue; decision: 'RESOLVED' | 'REJECTED' }) =>
      decideCourseIssue(issue.id, decision, (resolutions[issue.id] ?? '').trim()),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['admin-course-issues'] }),
  })

  return <div className="space-y-5">
    <header className="flex flex-wrap items-end justify-between gap-3">
      <div><p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Quản trị · Nội dung</p><h1 className="mt-1 text-2xl font-bold text-slate-900">Báo lỗi nội dung và Rubric</h1><p className="mt-2 text-sm text-slate-600">Xem báo cáo của Instructor, ghi nhận hướng xử lý và thông báo lại kết quả.</p></div>
      <label className="grid gap-1 text-xs text-slate-600">Trạng thái
        <select value={status} onChange={(event) => setStatus(event.target.value)} className="rounded-lg border bg-white px-3 py-2 text-sm text-slate-900">
          <option value="OPEN">Chờ xử lý</option><option value="RESOLVED">Đã xử lý</option><option value="REJECTED">Không tiếp nhận</option><option value="ALL">Tất cả</option>
        </select>
      </label>
    </header>
    {issues.isPending && <p role="status" className="rounded-lg bg-white p-4 text-sm text-slate-600">Đang tải báo cáo…</p>}
    {issues.isError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{issues.error.message}</p>}
    {issues.data?.length === 0 && <p className="rounded-xl border border-dashed bg-white p-8 text-center text-sm text-slate-500">Không có báo cáo ở trạng thái này.</p>}
    <div className="space-y-4">{issues.data?.map((issue) => <article key={issue.id} className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold text-slate-900">{issue.courseTitle}</h2><p className="mt-1 text-xs text-slate-500">{issue.category === 'CONTENT' ? 'Nội dung khóa học' : 'Rubric'} · {issue.instructorEmail} · {new Date(issue.createdAt).toLocaleString('vi-VN')}</p></div><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${issue.status === 'OPEN' ? 'bg-amber-50 text-amber-800' : issue.status === 'RESOLVED' ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-slate-700'}`}>{statusLabel[issue.status]}</span></div>
      <p className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-6 text-slate-800">{issue.detail}</p>
      {issue.status !== 'OPEN' ? <p className="text-sm text-slate-700"><strong>Ghi chú xử lý:</strong> {issue.resolution}</p> : <>
        <label className="grid gap-1 text-sm">Kết quả xử lý / lý do không tiếp nhận
          <textarea minLength={3} maxLength={2000} required value={resolutions[issue.id] ?? ''} onChange={(event) => setResolutions((current) => ({ ...current, [issue.id]: event.target.value }))} rows={3} className="rounded-lg border px-3 py-2" />
        </label>
        {decide.isError && <p role="alert" className="text-sm text-rose-700">{decide.error.message}</p>}
        <div className="flex justify-end gap-2"><button type="button" disabled={decide.isPending || !(resolutions[issue.id] ?? '').trim()} onClick={() => decide.mutate({ issue, decision: 'REJECTED' })} className="rounded-lg border px-3 py-2 text-sm text-slate-700 disabled:opacity-50">Không tiếp nhận</button><button type="button" disabled={decide.isPending || !(resolutions[issue.id] ?? '').trim()} onClick={() => decide.mutate({ issue, decision: 'RESOLVED' })} className="rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Đánh dấu đã xử lý</button></div>
      </>}
    </article>)}</div>
  </div>
}
