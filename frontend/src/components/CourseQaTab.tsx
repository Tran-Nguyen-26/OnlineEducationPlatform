import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Paperclip, Send, X } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import type { MessageResponse } from '../types/api'
import { Button } from './ui/Button'

export function CourseQaTab({
  courseId,
  lessonId,
  compact,
}: {
  courseId: string
  lessonId?: string
  compact?: boolean
}) {
  const queryClient = useQueryClient()
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [files, setFiles] = useState<File[]>([])
  const queryKey = ['course-messages', courseId, lessonId ?? 'general']

  const messagesQuery = useQuery({
    queryKey,
    queryFn: () =>
      apiClient
        .get<MessageResponse[]>(`/api/courses/${courseId}/messages`, { params: lessonId ? { lessonId } : undefined })
        .then((r) => r.data),
  })

  const sendMutation = useMutation({
    mutationFn: async () => {
      const { data: msg } = await apiClient.post<MessageResponse>(`/api/courses/${courseId}/messages`, { content, lessonId: lessonId ?? null })
      for (const f of files) {
        const { data: up } = await apiClient.post<{ uploadUrl: string; objectKey: string }>(`/api/courses/${courseId}/messages/${msg.id}/attachments/upload-url`, {
          fileName: f.name, mimeType: f.type || 'application/octet-stream', fileSize: f.size,
        })
        const put = await fetch(up.uploadUrl, { method: 'PUT', headers: { 'Content-Type': f.type || 'application/octet-stream' }, body: f })
        if (!put.ok) throw new Error('Tải file lên thất bại')
        await apiClient.post(`/api/courses/${courseId}/messages/${msg.id}/attachments`, { objectKey: up.objectKey, fileName: f.name, mimeType: f.type || null, fileSize: f.size })
      }
    },
    onSuccess: () => {
      setFiles([])
      setContent('')
      setError(null)
      queryClient.invalidateQueries({ queryKey })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (content.trim()) sendMutation.mutate()
  }

  return (
    <div className={`flex flex-col rounded-lg border border-slate-200 bg-white ${compact ? 'h-72' : 'h-[65vh]'}`}>
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
            {m.attachments?.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {m.attachments.map((a) => (
                  <a key={a.id} href={a.url} target="_blank" rel="noreferrer noopener" className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-blue-600 hover:bg-slate-50">
                    <Paperclip size={12} /> {a.fileName}{a.fileSize ? ` (${Math.max(1, Math.round(a.fileSize / 1024))} KB)` : ''}
                  </a>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
      {files.length > 0 && (
        <div className="flex flex-wrap gap-2 border-t border-slate-100 px-3 pt-2">
          {files.map((f, i) => (
            <span key={i} className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
              {f.name}<button type="button" onClick={() => setFiles((fs) => fs.filter((_, j) => j !== i))}><X size={12} /></button>
            </span>
          ))}
        </div>
      )}
      <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t border-slate-200 p-3">
        <label className="cursor-pointer rounded-md p-2 text-slate-500 hover:bg-slate-100" title="Đính kèm file (tối đa 3 file, 10MB mỗi file)">
          <Paperclip size={16} />
          <input type="file" multiple className="hidden" onChange={(e) => { const picked = Array.from(e.target.files ?? []); setFiles((fs) => [...fs, ...picked].slice(0, 3)); e.target.value = '' }} />
        </label>
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
