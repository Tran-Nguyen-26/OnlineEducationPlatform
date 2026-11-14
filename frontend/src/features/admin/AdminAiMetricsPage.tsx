import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchAdminAiMetrics } from '@/api/adminAiMetrics'

function localDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const number = new Intl.NumberFormat('vi-VN')
const duration = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 4 })

export default function AdminAiMetricsPage() {
  const today = new Date()
  const initialFrom = new Date(today)
  initialFrom.setDate(initialFrom.getDate() - 29)
  const [from, setFrom] = useState(localDate(initialFrom))
  const [to, setTo] = useState(localDate(today))
  const metrics = useQuery({
    queryKey: ['admin-ai-metrics', from, to],
    queryFn: () => fetchAdminAiMetrics(from, to),
    enabled: Boolean(from && to && from <= to),
  })

  return <div className="space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Quản trị · AI</p>
        <h1 className="mt-1 text-2xl font-bold text-slate-900">Chi phí và độ trễ AI</h1>
        <p className="mt-2 text-sm text-slate-600">Tổng hợp lượt gọi AI, token, độ trễ, từ chối, chi phí ước tính và tỷ lệ hit cache giải thích lỗi sai theo thời gian, khóa học.</p>
      </div>
      <div className="flex flex-wrap items-center gap-2 rounded-xl border bg-white p-3">
        <label className="grid gap-1 text-xs text-slate-600">Từ ngày<input aria-label="Từ ngày" type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} className="rounded border px-2 py-1.5 text-sm text-slate-900" /></label>
        <label className="grid gap-1 text-xs text-slate-600">Đến ngày<input aria-label="Đến ngày" type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} className="rounded border px-2 py-1.5 text-sm text-slate-900" /></label>
      </div>
    </header>

    {metrics.isPending && <p role="status" className="rounded-lg bg-white p-4 text-sm text-slate-600">Đang tải thống kê AI…</p>}
    {metrics.error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{metrics.error.message}</p>}
    {metrics.data && <>
      <section aria-label="Tổng hợp AI" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
        {[
          ['Lượt gọi AI', number.format(metrics.data.questions)],
          ['Prompt tokens*', number.format(metrics.data.promptTokens)],
          ['Completion tokens*', number.format(metrics.data.completionTokens)],
          ['Độ trễ trung bình', `${duration.format(metrics.data.averageLatencyMs)} ms`],
          ['Tỷ lệ từ chối', `${metrics.data.refusalRate.toFixed(2)}%`],
          ['Mistake cache hit', `${metrics.data.mistakeCacheHitRate.toFixed(2)}%`],
          ['Chi phí ước tính', metrics.data.costConfigured ? usd.format(metrics.data.estimatedCostUsd) : 'Chưa cấu hình'],
        ].map(([label, value]) => <article key={label} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="text-xs font-medium text-slate-500">{label}</h2><p className="mt-2 text-2xl font-bold text-slate-900">{value}</p>
          {label === 'Mistake cache hit' && <p className="mt-1 text-xs text-slate-500">{number.format(metrics.data.mistakeCacheHits)} hit · {number.format(metrics.data.mistakeCacheMisses)} miss</p>}
        </article>)}
      </section>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div className="border-b p-4"><h2 className="font-semibold text-slate-900">Theo ngày</h2></div>
        {metrics.data.daily.length === 0 ? <p className="p-6 text-sm text-slate-500">Chưa có dữ liệu trong khoảng ngày đã chọn.</p> :
          <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500"><tr>{['Ngày', 'Lượt gọi AI', 'Prompt tokens*', 'Completion tokens*', 'Độ trễ TB', 'Từ chối', 'Chi phí USD'].map((heading) => <th key={heading} className="px-4 py-3 font-medium">{heading}</th>)}</tr></thead>
            <tbody>{metrics.data.daily.map((row) => <tr key={row.date} className="border-t">
              <td className="px-4 py-3 font-medium text-slate-800">{row.date}</td><td className="px-4 py-3">{number.format(row.questions)}</td>
              <td className="px-4 py-3">{number.format(row.promptTokens)}</td><td className="px-4 py-3">{number.format(row.completionTokens)}</td>
              <td className="px-4 py-3">{duration.format(row.averageLatencyMs)} ms</td><td className="px-4 py-3">{row.refusalRate.toFixed(2)}%</td><td className="px-4 py-3">{metrics.data.costConfigured ? usd.format(row.estimatedCostUsd) : '—'}</td>
            </tr>)}</tbody>
          </table></div>}
      </section>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div className="border-b p-4"><h2 className="font-semibold text-slate-900">Theo khóa học</h2></div>
        {metrics.data.courses.length === 0 ? <p className="p-6 text-sm text-slate-500">Chưa có dữ liệu theo khóa học.</p> :
          <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500"><tr>{['Khóa học', 'Lượt gọi AI', 'Prompt tokens*', 'Completion tokens*', 'Độ trễ TB', 'Từ chối', 'Chi phí USD'].map((heading) => <th key={heading} className="px-4 py-3 font-medium">{heading}</th>)}</tr></thead>
            <tbody>{metrics.data.courses.map((row) => <tr key={row.courseId} className="border-t">
              <td className="px-4 py-3 font-medium text-slate-800">{row.courseTitle}</td><td className="px-4 py-3">{number.format(row.questions)}</td>
              <td className="px-4 py-3">{number.format(row.promptTokens)}</td><td className="px-4 py-3">{number.format(row.completionTokens)}</td>
              <td className="px-4 py-3">{duration.format(row.averageLatencyMs)} ms</td><td className="px-4 py-3">{row.refusalRate.toFixed(2)}%</td><td className="px-4 py-3">{metrics.data.costConfigured ? usd.format(row.estimatedCostUsd) : '—'}</td>
            </tr>)}</tbody>
          </table></div>}
      </section>
      <p className="text-xs text-slate-500">* Token được ước tính từ độ dài văn bản cho đến khi nhà cung cấp AI trả usage thực tế, hiện gồm AI Tutor, giải thích lỗi sai mới và sinh câu hỏi nháp. Chi phí là ước tính theo đơn giá USD mỗi triệu token đầu vào/đầu ra cấu hình bằng `OEP_AI_COST_INPUT_USD_PER_MILLION` và `OEP_AI_COST_OUTPUT_USD_PER_MILLION`.</p>
    </>}
  </div>
}
