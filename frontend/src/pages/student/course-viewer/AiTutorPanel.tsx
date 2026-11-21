import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Loader2, MessageSquarePlus, Send, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'

type Citation = {
  chunk_id: string
  content_id: string
  lesson_id: string
  source_title: string
  excerpt: string
  version_number: number
  page_number: number | null
  heading: string | null
  json_path: string | null
}

type ChatMessage = {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  answer_status?: 'ANSWERED' | 'ABSTAINED'
  citations: Citation[]
}

type Conversation = { id: string; title: string }
type Page<T> = { items: T[] }
type TutorStatus = {
  status: 'NOT_READY' | 'INDEXING' | 'READY' | 'DEGRADED'
  ready_sources: number
  indexing_sources: number
  failed_sources: number
}

const statusLabel: Record<TutorStatus['status'], string> = {
  NOT_READY: 'Chưa có tài liệu được lập chỉ mục',
  INDEXING: 'Đang xử lý tài liệu',
  READY: 'Sẵn sàng trả lời',
  DEGRADED: 'Có thể hỏi đáp, một số tài liệu đang xử lý',
}

export function AiTutorPanel({ courseId, onOpenCitation }: {
  courseId: string
  onOpenCitation: (citation: Citation) => void
}) {
  const queryClient = useQueryClient()
  const root = `/api/courses/${courseId}/ai`
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [retry, setRetry] = useState<{ text: string; key: string; conversationId: string } | null>(null)

  const status = useQuery({
    queryKey: ['ai-tutor', courseId, 'status'],
    queryFn: () => apiClient.get<TutorStatus>(`${root}/status`).then((response) => response.data),
    refetchInterval: 10000,
    retry: false,
  })
  const conversations = useQuery({
    queryKey: ['ai-tutor', courseId, 'conversations'],
    queryFn: () => apiClient.get<Page<Conversation>>(`${root}/conversations?page=1&page_size=100`).then((response) => response.data),
    retry: false,
  })
  const messages = useQuery({
    queryKey: ['ai-tutor', courseId, 'messages', conversationId],
    enabled: !!conversationId,
    queryFn: () => apiClient.get<Page<ChatMessage>>(`${root}/conversations/${conversationId}/messages?page=1&page_size=100`).then((response) => response.data),
    retry: false,
  })

  async function send() {
    const text = draft.trim()
    if (!text || sending || !status.data?.ready_sources) return
    setSending(true)
    setError(null)
    try {
      let target = conversationId
      if (!target) {
        const { data } = await apiClient.post<Conversation>(`${root}/conversations`, { title: text.slice(0, 80) })
        target = data.id
        setConversationId(target)
        await queryClient.invalidateQueries({ queryKey: ['ai-tutor', courseId, 'conversations'] })
      }
      const key = retry?.text === text && retry.conversationId === target ? retry.key : crypto.randomUUID()
      setRetry({ text, key, conversationId: target })
      await apiClient.post(`${root}/conversations/${target}/messages`, { message: text }, {
        headers: { 'Idempotency-Key': key },
      })
      setDraft('')
      setRetry(null)
      await queryClient.invalidateQueries({ queryKey: ['ai-tutor', courseId, 'messages', target] })
      await queryClient.invalidateQueries({ queryKey: ['ai-tutor', courseId, 'conversations'] })
      await queryClient.invalidateQueries({ queryKey: ['ai-tutor', courseId, 'status'] })
    } catch (cause) {
      setError(getApiErrorMessage(cause))
    } finally {
      setSending(false)
    }
  }

  async function removeConversation() {
    if (!conversationId || deleting) return
    setDeleting(true)
    setError(null)
    try {
      await apiClient.delete(`${root}/conversations/${conversationId}`)
      setConversationId(null)
      setRetry(null)
      await queryClient.invalidateQueries({ queryKey: ['ai-tutor', courseId, 'conversations'] })
    } catch (cause) {
      setError(getApiErrorMessage(cause))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-indigo-100 bg-white shadow-sm" aria-label="AI Tutor">
      <div className="border-b border-indigo-100 bg-gradient-to-r from-indigo-50 to-white p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">AI Tutor</h2>
            <p className="mt-1 text-sm text-slate-600">Hỏi đáp dựa trên tài liệu của khóa học, kèm nguồn trích dẫn.</p>
          </div>
          <button type="button" onClick={() => { setConversationId(null); setDraft(''); setRetry(null); setError(null) }}
            className="inline-flex items-center gap-1 rounded-lg border border-indigo-200 bg-white px-3 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-50">
            <MessageSquarePlus size={16} /> Cuộc trò chuyện mới
          </button>
        </div>
        {status.isLoading && <p className="mt-3 text-sm text-slate-500">Đang kiểm tra tài liệu...</p>}
        {status.isError && <p className="mt-3 text-sm text-rose-700">AI Tutor chưa khả dụng: {getApiErrorMessage(status.error)}</p>}
        {status.data && <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
          <span className="rounded-full bg-indigo-100 px-3 py-1 font-medium text-indigo-800">{statusLabel[status.data.status]}</span>
          <span className="text-slate-500">{status.data.ready_sources} tài liệu sẵn sàng</span>
          {status.data.indexing_sources > 0 && <span className="text-amber-700">{status.data.indexing_sources} đang xử lý</span>}
          {status.data.failed_sources > 0 && <span className="text-rose-700">{status.data.failed_sources} cần kiểm tra</span>}
        </div>}
      </div>

      <div className="grid min-h-[480px] md:grid-cols-[190px_minmax(0,1fr)]">
        <aside className="border-b border-slate-100 bg-slate-50 p-3 md:border-r md:border-b-0">
          <p className="mb-2 px-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Lịch sử</p>
          {conversations.isLoading && <p className="px-2 text-xs text-slate-500">Đang tải...</p>}
          {conversations.isError && <p className="px-2 text-xs text-rose-700">Không tải được lịch sử.</p>}
          {conversations.data?.items.length === 0 && <p className="px-2 text-xs text-slate-500">Chưa có cuộc trò chuyện.</p>}
          <div className="max-h-44 space-y-1 overflow-y-auto md:max-h-[520px]">
            {conversations.data?.items.map((item) => (
              <button key={item.id} type="button" onClick={() => { setConversationId(item.id); setRetry(null); setError(null) }}
                className={`w-full truncate rounded-lg px-2 py-2 text-left text-sm ${item.id === conversationId ? 'bg-indigo-100 font-medium text-indigo-800' : 'text-slate-600 hover:bg-slate-100'}`}>
                {item.title}
              </button>
            ))}
          </div>
        </aside>

        <div className="flex min-w-0 flex-col">
          <div className="flex min-h-[340px] flex-1 flex-col gap-4 p-5" aria-live="polite">
            {conversationId && <div className="flex justify-end">
              <button type="button" disabled={deleting} onClick={removeConversation}
                className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-rose-700 disabled:opacity-50">
                <Trash2 size={13} /> Xóa cuộc trò chuyện
              </button>
            </div>}
            {messages.isLoading && conversationId && <p className="text-sm text-slate-500">Đang tải tin nhắn...</p>}
            {messages.isError && <p className="text-sm text-rose-700">Không tải được tin nhắn.</p>}
            {(!conversationId || messages.data?.items.length === 0) && <div className="my-auto text-center text-slate-500">
              <BookOpen size={30} className="mx-auto mb-3 text-indigo-400" />
              <p className="text-sm">Đặt câu hỏi về nội dung trong tài liệu của khóa học.</p>
            </div>}
            {messages.data?.items.map((message) => (
              <article key={message.id} className={`max-w-[92%] rounded-xl px-4 py-3 text-sm ${message.role === 'USER' ? 'ml-auto bg-indigo-600 text-white' : 'border border-slate-200 bg-slate-50 text-slate-800'}`}>
                <p className="whitespace-pre-wrap break-words">{message.content}</p>
                {message.role === 'ASSISTANT' && message.answer_status === 'ABSTAINED' &&
                  <p className="mt-2 text-xs text-slate-500">Chưa đủ bằng chứng trong tài liệu.</p>}
                {message.citations.length > 0 && <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
                  <p className="text-xs font-semibold text-slate-600">Nguồn tham khảo</p>
                  {message.citations.map((citation) => (
                    <button key={citation.chunk_id} type="button" onClick={() => onOpenCitation(citation)}
                      className="block w-full rounded-lg border border-indigo-100 bg-white p-2 text-left text-xs text-slate-700 hover:border-indigo-300 hover:bg-indigo-50">
                      <span className="font-semibold text-indigo-700">{citation.source_title}</span>
                      <span className="ml-1 text-slate-500">{citation.page_number ? `· Trang ${citation.page_number}` : citation.heading ? `· ${citation.heading}` : citation.json_path ? `· ${citation.json_path}` : ''}</span>
                      <span className="mt-1 block line-clamp-2">{citation.excerpt}</span>
                    </button>
                  ))}
                </div>}
              </article>
            ))}
          </div>
          <div className="border-t border-slate-100 p-4">
            {error && <p className="mb-2 text-sm text-rose-700" role="alert">{error}</p>}
            <form onSubmit={(event) => { event.preventDefault(); void send() }} className="flex gap-2">
              <label className="sr-only" htmlFor="ai-tutor-question">Câu hỏi cho AI Tutor</label>
              <input id="ai-tutor-question" value={draft} maxLength={4000} onChange={(event) => setDraft(event.target.value)}
                placeholder={status.data?.ready_sources ? 'Hỏi về tài liệu của khóa học...' : 'Đang chờ tài liệu sẵn sàng'}
                disabled={!status.data?.ready_sources || sending}
                className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 disabled:bg-slate-50" />
              <button type="submit" disabled={!draft.trim() || sending || !status.data?.ready_sources}
                className="inline-flex items-center gap-1 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50">
                {sending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />} Gửi
              </button>
            </form>
            <p className="mt-2 text-xs text-slate-500">Câu trả lời có thể thiếu sót; hãy mở trích dẫn để đối chiếu tài liệu.</p>
          </div>
        </div>
      </div>
    </section>
  )
}
