import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import type { MessageResponse } from '../types/api'
import { Button } from './ui/Button'

export function CourseQaTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  const messagesQuery = useQuery({
    queryKey: ['course-messages', courseId],
    queryFn: () => apiClient.get<MessageResponse[]>(`/api/courses/${courseId}/messages`).then((r) => r.data),
  })

  const sendMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/courses/${courseId}/messages`, { content }),
    onSuccess: () => {
      setContent('')
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['course-messages', courseId] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (content.trim()) sendMutation.mutate()
  }

  return (
    <div className="flex h-[65vh] flex-col rounded-lg border border-slate-200 bg-white">
      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        {messagesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
        {messagesQuery.data?.length === 0 && <p className="text-sm text-slate-400">Chưa có câu hỏi nào — hãy là người đầu tiên hỏi!</p>}
        {messagesQuery.data?.map((m) => (
          <div key={m.id} className="rounded-md bg-slate-50 p-3">
            <div className="mb-1 flex items-center gap-2 text-xs">
              <span className="font-medium text-slate-800">{m.senderName}</span>
              <span
                className={
                  m.senderRole === 'INSTRUCTOR'
                    ? 'rounded-full bg-blue-100 px-2 py-0.5 text-blue-700'
                    : m.senderRole === 'ADMIN'
                      ? 'rounded-full bg-purple-100 px-2 py-0.5 text-purple-700'
                      : 'rounded-full bg-slate-200 px-2 py-0.5 text-slate-600'
                }
              >
                {m.senderRole === 'INSTRUCTOR' ? 'Giáo viên' : m.senderRole === 'ADMIN' ? 'Admin' : 'Học sinh'}
              </span>
              <span className="text-slate-400">{new Date(m.createdAt).toLocaleString('vi-VN')}</span>
            </div>
            <p className="whitespace-pre-wrap text-sm text-slate-700">{m.content}</p>
          </div>
        ))}
      </div>
      <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t border-slate-200 p-3">
        <input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Nhập câu hỏi hoặc câu trả lời..."
          className="h-10 flex-1 rounded-md border border-slate-300 px-3 text-sm outline-none focus:border-slate-500"
        />
        <Button type="submit" size="sm" disabled={sendMutation.isPending || !content.trim()}>
          <Send size={14} />
        </Button>
      </form>
      {error && <p className="px-3 pb-2 text-xs text-red-600">{error}</p>}
    </div>
  )
}
