import { useQuery } from '@tanstack/react-query'

type Regrade = {
  submissionId: string
  courseTitle: string | null
  originalScore: number
  finalScore: number
  difference: number
  maxScore: number | null
  criterion: string | null
  reason: string | null
  reviewerEmail: string
  regradedAt: string
  exceedsAlertThreshold: boolean
}

function useRegrades() {
  return useQuery({
    queryKey: ['admin-regrade-audit'],
    queryFn: async () => {
      const token = localStorage.getItem('access_token')
      const response = await fetch('/api/admin/regrade-audit', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      const data = await response.json().catch(() => null) as (Regrade[] & { message?: string }) | null
      if (!response.ok) throw new Error(data?.message || 'Không tải được nhật ký phúc khảo.')
      return data as Regrade[]
    },
  })
}

export default function AdminRegradeAuditPage() {
  const audit = useRegrades()
  return <div className="space-y-5">
    <header>
      <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Quản trị · Chất lượng chấm</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-900">Nhật ký phúc khảo</h1>
      <p className="mt-2 text-sm text-slate-600">Điểm trước/sau, tiêu chí, lý do và giảng viên chấm độc lập. Chênh lệch trên 15% thang điểm được đánh dấu.</p>
    </header>
    {audit.isPending && <p role="status" className="rounded-xl bg-white p-5 text-sm text-slate-600">Đang tải nhật ký…</p>}
    {audit.isError && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{audit.error.message}</p>}
    {audit.data?.length === 0 && <p className="rounded-xl border border-dashed bg-white p-8 text-center text-sm text-slate-500">Chưa có lượt phúc khảo.</p>}
    {!!audit.data?.length && <div className="overflow-x-auto rounded-xl border bg-white">
      <table className="w-full min-w-[900px] text-left text-sm">
        <thead className="bg-slate-50 text-xs text-slate-500"><tr>{['Thời gian', 'Khóa học', 'Bài nộp', 'Điểm trước → sau', 'Độ lệch', 'Tiêu chí / lý do', 'Người chấm'].map((label) => <th key={label} className="px-4 py-3 font-medium">{label}</th>)}</tr></thead>
        <tbody>{audit.data.map((item) => <tr key={`${item.submissionId}-${item.regradedAt}`} className="border-t align-top">
          <td className="whitespace-nowrap px-4 py-3">{new Date(item.regradedAt).toLocaleString('vi-VN')}</td>
          <td className="px-4 py-3">{item.courseTitle ?? 'Khóa học đã xóa'}</td>
          <td className="px-4 py-3 font-mono text-xs">{item.submissionId.slice(0, 8)}</td>
          <td className="whitespace-nowrap px-4 py-3">{item.originalScore} → {item.finalScore} / {item.maxScore ?? '—'}</td>
          <td className={`px-4 py-3 font-semibold ${item.exceedsAlertThreshold ? 'text-rose-700' : 'text-slate-700'}`}>{item.difference}{item.exceedsAlertThreshold ? ' · Cảnh báo >15%' : ''}</td>
          <td className="max-w-sm px-4 py-3"><p>{item.criterion || '—'}</p><p className="mt-1 whitespace-pre-wrap text-xs text-slate-600">{item.reason || '—'}</p></td>
          <td className="px-4 py-3">{item.reviewerEmail}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </div>
}
