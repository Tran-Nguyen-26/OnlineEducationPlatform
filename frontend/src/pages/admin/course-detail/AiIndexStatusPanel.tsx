import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bot, ChevronDown, ChevronUp, RefreshCw } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'

type IndexItem = {
  content_id: string
  version_number: number
  source_title: string | null
  state: string
  error_code: string | null
  updated_at: string | null
}

type IndexStatus = {
  course_id: string
  embedding_space_key: string
  items: IndexItem[]
}

const labels: Record<string, string> = {
  QUEUED: 'Đang chờ', PROCESSING: 'Đang xử lý', PENDING: 'Đang chờ',
  READY: 'Sẵn sàng', FAILED: 'Lỗi xử lý', UNSUPPORTED: 'Không hỗ trợ',
  INACTIVE: 'Đã ngừng', SUPERSEDED: 'Đã thay thế',
}

export function AiIndexStatusPanel({ courseId }: { courseId: string }) {
  const [open, setOpen] = useState(false)
  const query = useQuery({
    queryKey: ['admin', 'courses', courseId, 'ai-index-status'],
    enabled: open,
    queryFn: () => apiClient.get<IndexStatus>(`/api/admin/courses/${courseId}/ai/index-status`).then((response) => response.data),
    refetchInterval: open ? 10000 : false,
    retry: false,
  })
  return <section className="mb-5 rounded-xl border border-indigo-100 bg-indigo-50/40 p-4">
    <button type="button" onClick={() => setOpen((value) => !value)}
      className="flex w-full items-center gap-2 text-left text-sm font-semibold text-indigo-800">
      <Bot size={18} /> <span className="flex-1">Tiến độ tài liệu AI Tutor</span>
      {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
    </button>
    {open && <div className="mt-3 border-t border-indigo-100 pt-3">
      <div className="mb-3 flex items-center justify-between gap-3">
        <p className="text-xs text-slate-500">Trạng thái lập chỉ mục được cập nhật tự động mỗi 10 giây.</p>
        <button type="button" onClick={() => void query.refetch()} disabled={query.isFetching}
          className="inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900 disabled:opacity-50">
          <RefreshCw size={13} className={query.isFetching ? 'animate-spin' : ''} /> Làm mới
        </button>
      </div>
      {query.isLoading && <p className="text-sm text-slate-500">Đang tải trạng thái...</p>}
      {query.isError && <p className="text-sm text-rose-700">Không tải được tiến độ AI Tutor: {getApiErrorMessage(query.error)}</p>}
      {query.data?.items.length === 0 && <p className="text-sm text-slate-500">Chưa có tài liệu nào được gửi để lập chỉ mục.</p>}
      {query.data && query.data.items.length > 0 && <ul className="space-y-2">
        {query.data.items.map((item) => <li key={item.content_id} className="flex flex-wrap items-center gap-2 rounded-lg bg-white px-3 py-2 text-sm shadow-sm">
          <span className="min-w-0 flex-1 truncate font-medium text-slate-700">{item.source_title || `Nội dung ${item.content_id.slice(0, 8)}`}</span>
          <span className="text-xs text-slate-500">v{item.version_number}</span>
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${item.state === 'READY' ? 'bg-emerald-100 text-emerald-800' : item.state === 'FAILED' || item.state === 'UNSUPPORTED' ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'}`}>
            {labels[item.state] || item.state}
          </span>
          {item.error_code && <span className="w-full text-xs text-rose-700">Mã lỗi: {item.error_code}</span>}
        </li>) }
      </ul>}
    </div>}
  </section>
}
