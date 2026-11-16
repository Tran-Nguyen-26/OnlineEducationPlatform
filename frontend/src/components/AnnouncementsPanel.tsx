import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import type { AnnouncementResponse } from '../types/api'
import { Button } from './ui/Button'
import { Input } from './ui/Input'

/** Course announcements board; canPost is true for admin / assigned instructors (the API enforces it too). */
export function AnnouncementsPanel({ courseId, canPost }: { courseId: string; canPost: boolean }) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  const query = useQuery({
    queryKey: ['announcements', courseId],
    queryFn: () => apiClient.get<AnnouncementResponse[]>(`/api/courses/${courseId}/announcements`).then((r) => r.data),
  })
  const post = useMutation({
    mutationFn: () => apiClient.post(`/api/courses/${courseId}/announcements`, { title, content }),
    onSuccess: () => {
      setTitle('')
      setContent('')
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['announcements', courseId] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    post.mutate()
  }

  return (
    <div className="space-y-4">
      {canPost && (
        <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-2 text-sm text-slate-500">Thông báo sẽ hiện trong khóa học và gửi thông báo tới mọi học sinh đang học.</p>
          <Input required placeholder="Tiêu đề" value={title} onChange={(e) => setTitle(e.target.value)} className="mb-2" />
          <textarea
            required
            rows={3}
            placeholder="Nội dung thông báo"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="mb-2 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
          <Button type="submit" size="sm" disabled={post.isPending}>{post.isPending ? 'Đang gửi...' : 'Đăng thông báo'}</Button>
        </form>
      )}
      {query.data?.map((a) => (
        <div key={a.id} className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="font-semibold text-slate-900">{a.title}</div>
          <div className="mb-2 text-xs text-slate-400">
            {a.authorName} ({a.authorRole === 'ADMIN' ? 'Admin' : 'Giáo viên'}) · {new Date(a.createdAt).toLocaleString('vi-VN')}
          </div>
          <p className="whitespace-pre-wrap text-sm text-slate-700">{a.content}</p>
        </div>
      ))}
      {query.data?.length === 0 && <p className="text-slate-400">Chưa có thông báo nào.</p>}
    </div>
  )
}
