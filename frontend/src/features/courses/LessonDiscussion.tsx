import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from '@/api/quiz'

type Question = { id: string; videoId: string | null; timestampSec: number; body: string }
type Reply = { id: string; body: string; verified: boolean }
type VerifiedSuggestion = { id: string; questionId: string; question: string; answer: string; videoId: string | null; timestampSec: number; similarity: number }
type Thread = { questions: Question[]; canAsk: boolean; canReply: boolean; canVerify: boolean; hasMore: boolean }
type Context = { videoId: string; timestampSec: number }

function timestamp(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function MessageForm({ label, onSend, pending }: { label: string; onSend: (body: string, form: HTMLFormElement) => void; pending: boolean }) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = String(new FormData(event.currentTarget).get('body')).trim()
    if (body) onSend(body, event.currentTarget)
  }
  return (
    <form onSubmit={submit} className="space-y-2">
      <label className="grid gap-1 text-sm">{label}<textarea name="body" required maxLength={4000} rows={2} className="rounded border p-2" /></label>
      <button disabled={pending} className="rounded bg-teal-700 px-3 py-2 text-sm text-white disabled:opacity-50">Gửi</button>
    </form>
  )
}

function AskQuestionForm({ base, label, pending, onSend }: {
  base: string; label: string; pending: boolean; onSend: (body: string, form: HTMLFormElement, clear: () => void) => void
}) {
  const [body, setBody] = useState('')
  const suggestions = useQuery({
    queryKey: ['verified-qa-suggestions', base, body.trim()],
    enabled: body.trim().length >= 4,
    queryFn: () => request<VerifiedSuggestion[]>(`${base}/suggestions?query=${encodeURIComponent(body.trim())}`),
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = body.trim()
    if (text) onSend(text, event.currentTarget, () => setBody(''))
  }
  return <form onSubmit={submit} className="space-y-2">
    <label className="grid gap-1 text-sm">{label}<textarea name="body" required maxLength={4000} rows={2} value={body}
      onChange={(event) => setBody(event.target.value)} className="rounded border p-2" /></label>
    {!!suggestions.data?.length && <aside aria-live="polite" className="space-y-2 rounded-md border border-amber-200 bg-amber-50 p-3">
      <p className="text-sm font-medium">Có câu hỏi đã được giảng viên xác nhận gần giống:</p>
      {suggestions.data.map((item) => <article key={item.id} className="rounded bg-white p-2 text-sm">
        <p className="font-medium">{item.question}</p><p className="mt-1 whitespace-pre-wrap">{item.answer}</p>
        <span className="text-xs text-slate-500">Câu trả lời đã được giảng viên xác nhận</span>
      </article>)}
      <p className="text-xs text-slate-600">Bạn vẫn có thể gửi câu hỏi mới nếu chưa giải quyết được thắc mắc.</p>
    </aside>}
    <button disabled={pending} className="rounded bg-teal-700 px-3 py-2 text-sm text-white disabled:opacity-50">Gửi câu hỏi</button>
  </form>
}

export default function LessonDiscussion({ courseId, lessonId, context, onSeek }: {
  courseId: string; lessonId: string; context?: Context; onSeek?: (videoId: string, seconds: number) => void
}) {
  const base = `/api/v1/courses/${courseId}/lessons/${lessonId}/discussion`
  const cache = useQueryClient()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string>()
  const [replyPage, setReplyPage] = useState(0)
  const key = ['lesson-discussion', courseId, lessonId]
  const thread = useQuery({ queryKey: [...key, page], queryFn: () => request<Thread>(base + `?page=${page}`) })
  const replyPath = `${base}/${selected}/replies`
  const replies = useQuery({
    queryKey: [...key, selected, 'replies', replyPage], enabled: !!selected,
    queryFn: () => request<Reply[]>(replyPath + `?page=${replyPage}`),
  })
  const save = useMutation({
    mutationFn: ({ path, method, data }: { path: string; method: 'POST' | 'PUT'; data?: object }) =>
      request(path, { method, body: data ? JSON.stringify(data) : undefined }),
    onSuccess: () => cache.invalidateQueries({ queryKey: key }),
  })
  return (
    <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
      <h3 className="font-semibold">Thảo luận bài học</h3>
      {thread.isPending && <p role="status">Đang tải thảo luận…</p>}
      {thread.isError && <p role="alert" className="text-sm text-red-700">{thread.error.message}</p>}
      {thread.data?.canAsk && context && (
        <AskQuestionForm base={base} label={`Hỏi tại ${timestamp(context.timestampSec)}`} pending={save.isPending} onSend={(body, form, clear) =>
          save.mutate({ path: base, method: 'POST', data: { ...context, body } }, { onSuccess: () => { form.reset(); clear(); setPage(0) } })} />
      )}
      {thread.data && !thread.data.canReply && <p className="text-sm text-slate-600">Thảo luận lưu trữ · Chỉ đọc</p>}
      {thread.data?.questions.length === 0 && <p className="text-sm text-slate-600">Chưa có câu hỏi.</p>}
      <ul className="space-y-3">
        {thread.data?.questions.map((question) => (
          <li key={question.id} className="space-y-2 rounded border p-3">
            {question.videoId && onSeek ? (
              <button type="button" className="font-mono text-sm text-teal-800 underline"
                onClick={() => onSeek(question.videoId!, question.timestampSec)}>{timestamp(question.timestampSec)}</button>
            ) : <span className="font-mono text-sm text-slate-600">{timestamp(question.timestampSec)}</span>}
            <p className="whitespace-pre-wrap break-words text-sm">{question.body}</p>
            <button type="button" aria-expanded={selected === question.id} className="text-sm text-teal-800 underline"
              onClick={() => { setSelected(selected === question.id ? undefined : question.id); setReplyPage(0) }}>Câu trả lời</button>
            {selected === question.id && (
              <div className="space-y-2 border-t pt-2">
                {replies.isPending && <p role="status">Đang tải câu trả lời…</p>}
                {replies.isError && <p role="alert" className="text-sm text-red-700">{replies.error.message}</p>}
                {replies.data?.map((reply) => (
                  <div key={reply.id} className="space-y-1 rounded bg-slate-50 p-2 text-sm">
                    {reply.verified && <p className="font-medium text-teal-800">Đã được giảng viên xác nhận</p>}
                    <p className="whitespace-pre-wrap break-words">{reply.body}</p>
                    {thread.data?.canVerify && !reply.verified && (
                      <button type="button" disabled={save.isPending} className="text-teal-800 underline"
                        onClick={() => save.mutate({ path: `${replyPath}/${reply.id}/verify`, method: 'PUT' })}>Xác nhận câu trả lời</button>
                    )}
                  </div>
                ))}
                {replyPage > 0 && <button type="button" className="mr-3 text-sm underline" onClick={() => setReplyPage(replyPage - 1)}>Trả lời trước</button>}
                {replies.data?.length === 30 && <button type="button" className="text-sm underline" onClick={() => setReplyPage(replyPage + 1)}>Trả lời sau</button>}
                {thread.data?.canReply && <MessageForm label="Trả lời" pending={save.isPending}
                  onSend={(body, form) => save.mutate({ path: replyPath, method: 'POST', data: { body } }, { onSuccess: () => form.reset() })} />}
              </div>
            )}
          </li>
        ))}
      </ul>
      {page > 0 && <button type="button" className="mr-3 text-sm underline" onClick={() => { setSelected(undefined); setPage(page - 1) }}>Trang trước</button>}
      {thread.data?.hasMore && <button type="button" className="text-sm underline" onClick={() => { setSelected(undefined); setPage(page + 1) }}>Trang sau</button>}
      {save.isError && <p role="alert" className="text-sm text-red-700">{save.error.message}</p>}
    </section>
  )
}
