import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toDataURL } from 'qrcode'
import {
  accessCodeError, fetchCodeBatch, issueCodeBatch, revokeAccessCode, type CodeKind,
} from '@/api/accessCodes'
import { request, type TeachingCourse } from '@/api/quiz'

const statusLabels = { AVAILABLE: 'Khả dụng', USED: 'Đã dùng', REVOKED: 'Đã thu hồi', EXPIRED: 'Hết hạn' }
const inputClass = 'rounded-lg border border-slate-300 bg-white px-3 py-2'
const buttonClass = 'rounded-lg bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60'

export default function AccessCodesPage() {
  const queryClient = useQueryClient()
  const [batchId, setBatchId] = useState('')
  const [lookup, setLookup] = useState('')
  const [qrError, setQrError] = useState('')
  const courses = useQuery({
    queryKey: ['admin-access-code-courses'],
    queryFn: () => request<{ items: TeachingCourse[] }>('/api/v1/my/courses?page=1&page_size=100'),
  })
  const issue = useMutation({
    mutationFn: issueCodeBatch,
    onSuccess: (batch) => {
      setBatchId(batch.batchId)
      setLookup(batch.batchId)
    },
  })
  const batch = useQuery({
    queryKey: ['access-code-batch', batchId],
    queryFn: () => fetchCodeBatch(batchId),
    enabled: !!batchId,
  })
  const revoke = useMutation({
    mutationFn: revokeAccessCode,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['access-code-batch', batchId] }),
  })

  function handleIssue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    issue.mutate({
      courseId: String(fields.get('courseId')).trim(),
      kind: fields.get('kind') as CodeKind,
      count: Number(fields.get('count')),
      accessDays: Number(fields.get('accessDays')),
      expiresAt: new Date(String(fields.get('expiresAt'))).toISOString(),
    })
  }

  function exportCsv() {
    const issued = issue.data
    if (!issued) return
    const csv = ['batch_id,course_id,kind,access_days,expires_at,id,code',
      ...issued.codes.map(({ id, code }) => [issued.batchId, issued.courseId, issued.kind, issued.accessDays, issued.expiresAt, id, code].join(',')),
    ].join('\r\n')
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `access-codes-${issued.batchId}.csv`
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  async function downloadQr(id: string, code: string) {
    setQrError('')
    try {
      const link = document.createElement('a')
      link.href = await toDataURL(code, { width: 320 })
      link.download = `access-code-${id}.png`
      link.click()
    } catch {
      setQrError('Không thể tạo mã QR.')
    }
  }

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Mã truy cập khóa học</h1>
      <form onSubmit={handleIssue} className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="font-semibold">Phát hành lô mã</h2>
        <div className="grid gap-3 md:grid-cols-2">
          <label className="grid gap-1 text-sm">Khóa học
            <select name="courseId" className={inputClass} required defaultValue="" disabled={courses.isPending || !courses.data?.items.length}>
              <option value="" disabled>{courses.isPending ? 'Đang tải khóa học…' : 'Chọn khóa học'}</option>
              {courses.data?.items.map((course) => <option key={course.id} value={course.id}>{course.title} · {course.lifecycle_status}</option>)}
            </select>
          </label>
          <label className="grid gap-1 text-sm">Loại mã
            <select name="kind" className={inputClass}>
              <option value="ACTIVE">Kích hoạt</option>
              <option value="EXTENSION">Gia hạn</option>
            </select>
          </label>
          <label className="grid gap-1 text-sm">Số lượng
            <input name="count" type="number" min="1" max="500" defaultValue="1" className={inputClass} required />
          </label>
          <label className="grid gap-1 text-sm">Thời hạn học
            <select name="accessDays" className={inputClass}>
              <option value="90">90 ngày</option>
              <option value="180">180 ngày</option>
            </select>
          </label>
          <label className="grid gap-1 text-sm">Hạn dùng mã
            <input name="expiresAt" type="datetime-local" className={inputClass} required />
          </label>
        </div>
        {courses.isError && <p role="alert" className="text-sm text-red-700">Không tải được danh sách khóa học để phát hành mã.</p>}
        {courses.data?.items.length === 0 && <p className="text-sm text-slate-600">Chưa có khóa học để phát hành mã.</p>}
        <button className={buttonClass} disabled={issue.isPending || courses.isPending || !courses.data?.items.length}>Phát hành mã</button>
        {issue.isError && <p role="alert" className="text-sm text-red-700">{accessCodeError(issue.error, 'Không thể phát hành mã.')}</p>}
        {issue.data && (
          <div className="flex flex-wrap items-center gap-3">
            <p role="status" className="text-sm text-slate-600">Tải CSV hoặc QR trước khi rời trang. Hệ thống chỉ hiển thị mã gốc ngay sau khi phát hành.</p>
            <button type="button" onClick={exportCsv} className={buttonClass}>Tải CSV</button>
          </div>
        )}
        {issue.data && (
          <details>
            <summary className="cursor-pointer text-sm font-medium text-teal-800">Tải QR theo từng mã</summary>
            <ul className="mt-2 max-h-64 overflow-auto divide-y">
              {issue.data.codes.map(({ id, code }, index) => (
                <li key={id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="break-all">{id}</span>
                  <button type="button" onClick={() => void downloadQr(id, code)}
                    className="rounded-lg border px-3 py-1.5">Tải QR {index + 1}</button>
                </li>
              ))}
            </ul>
          </details>
        )}
        {qrError && <p role="alert" className="text-sm text-red-700">{qrError}</p>}
      </form>
      <form className="space-y-3 rounded-xl border border-slate-200 bg-white p-5"
        onSubmit={(event) => { event.preventDefault(); setBatchId(lookup.trim()) }}>
        <label className="grid gap-1 text-sm">Tra cứu theo ID lô
          <input className={inputClass} value={lookup} onChange={(event) => setLookup(event.target.value)} required />
        </label>
        <button className={buttonClass}>Tra cứu</button>
        {batch.isFetching && <p role="status">Đang tải lô mã…</p>}
        {batch.isError && <p role="alert" className="text-sm text-red-700">{accessCodeError(batch.error, 'Không tải được lô mã.')}</p>}
        {revoke.isError && <p role="alert" className="text-sm text-red-700">{accessCodeError(revoke.error, 'Không thu hồi được mã.')}</p>}
        <ul className="divide-y">
          {batch.data?.map((item) => (
            <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
              <span className="break-all">{item.id}</span>
              <span>{statusLabels[item.status]}</span>
              {item.status === 'AVAILABLE' && (
                <button type="button" disabled={revoke.isPending} onClick={() => revoke.mutate(item.id)}
                  className="rounded-lg border border-red-200 px-3 py-1.5 text-red-700 disabled:opacity-60">Thu hồi</button>
              )}
            </li>
          ))}
        </ul>
      </form>
    </section>
  )
}
