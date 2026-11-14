import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchFailedEmailDeliveries, replayFailedEmailDelivery } from '@/api/adminEmailDeliveries'

export default function AdminEmailDeliveriesPage() {
  const cache = useQueryClient()
  const [page, setPage] = useState(1)
  const deliveries = useQuery({
    queryKey: ['admin-failed-email-deliveries', page],
    queryFn: () => fetchFailedEmailDeliveries(page),
  })
  const replay = useMutation({
    mutationFn: replayFailedEmailDelivery,
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['admin-failed-email-deliveries'] })
    },
  })

  return <section className="space-y-5">
    <header>
      <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Quản trị · Thông báo</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-900">Email gửi thất bại</h1>
      <p className="mt-2 text-sm text-slate-600">Xem lỗi gửi email và đưa event thất bại trở lại hàng đợi xử lý.</p>
    </header>
    {replay.isSuccess && <p role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">Email đã được đưa lại vào hàng đợi.</p>}
    {replay.isError && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{replay.error.message}</p>}
    {deliveries.isPending && <p role="status" className="rounded-xl bg-white p-5 text-sm text-slate-600">Đang tải email lỗi…</p>}
    {deliveries.isError && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-800">{deliveries.error.message}</p>}
    {deliveries.data?.items.length === 0 && <p className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">Không có email nào đang thất bại.</p>}
    <div className="space-y-3">
      {deliveries.data?.items.map((delivery) => <article key={delivery.id} className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="break-words font-semibold text-slate-900">{delivery.subject || delivery.eventType || 'Email thông báo'}</h2>
            <p className="mt-1 break-all text-sm text-slate-600">{delivery.recipientEmail || 'Không có địa chỉ nhận'}</p>
            <p className="mt-1 text-xs text-slate-500">{delivery.eventType || 'Không rõ loại event'} · {delivery.retryCount} lần thử · {delivery.scheduledAt ? new Date(delivery.scheduledAt).toLocaleString('vi-VN') : 'Chưa có lịch gửi'}</p>
          </div>
          <span className="rounded-full bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-800">Thất bại</span>
        </div>
        {delivery.lastError && <p className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-3 text-sm text-slate-700">{delivery.lastError}</p>}
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
          <code className="max-w-full break-all text-xs text-slate-500">{delivery.dedupeKey || delivery.id}</code>
          <button type="button" disabled={replay.isPending} onClick={() => replay.mutate(delivery.id)}
            className="rounded-lg bg-teal-800 px-3 py-2 text-sm font-semibold text-white hover:bg-teal-900 disabled:opacity-50">
            {replay.isPending ? 'Đang xếp lại…' : 'Chạy lại email'}
          </button>
        </div>
      </article>)}
    </div>
    {deliveries.data && deliveries.data.total > 0 && <nav aria-label="Phân trang email lỗi" className="flex items-center justify-center gap-4">
      <button type="button" disabled={page <= 1} onClick={() => setPage((value) => value - 1)} className="rounded-lg border px-4 py-2 text-sm disabled:opacity-50">Trang trước</button>
      <span className="text-sm text-slate-600">Trang {page} · {deliveries.data.total} email</span>
      <button type="button" disabled={!deliveries.data.hasNext} onClick={() => setPage((value) => value + 1)} className="rounded-lg border px-4 py-2 text-sm disabled:opacity-50">Trang sau</button>
    </nav>}
  </section>
}
