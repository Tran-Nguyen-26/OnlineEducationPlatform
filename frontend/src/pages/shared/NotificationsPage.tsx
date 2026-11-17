import { Pager, usePaged } from '../../components/Pager'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import type { NotificationResponse } from '../../types/api'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'

export function NotificationsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const base = user?.role === 'ADMIN' ? '/admin/courses' : user?.role === 'INSTRUCTOR' ? '/teacher/courses' : '/student/courses'
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const markRead = useMutation({ mutationFn: (id: string) => apiClient.patch(`/api/notifications/${id}/read`), onSuccess: refresh })
  const markAll = useMutation({ mutationFn: () => apiClient.patch('/api/notifications/read-all'), onSuccess: refresh })
  const allRows = query.data ?? []
  const pg = usePaged(allRows, 20)
  const rows = pg.slice

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Thông báo</h1>
        <Button size="sm" variant="secondary" onClick={() => markAll.mutate()} disabled={!allRows.some((n) => !n.read)}>
          Đánh dấu tất cả đã đọc
        </Button>
      </div>
      <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
        {rows.map((n) => (
          <button
            key={n.id}
            className={`block w-full px-4 py-3 text-left hover:bg-slate-50 ${n.read ? '' : 'bg-blue-50/50'}`}
            onClick={() => {
              if (!n.read) markRead.mutate(n.id)
              if (n.targetType === 'COURSE' && n.targetId) navigate(`${base}/${n.targetId}`)
            }}
          >
            <div className="text-sm font-medium text-slate-900">{n.title}</div>
            <div className="text-sm text-slate-600">{n.message}</div>
            <div className="mt-1 text-xs text-slate-400">{new Date(n.createdAt).toLocaleString('vi-VN')}</div>
          </button>
        ))}
        {!query.isLoading && rows.length === 0 && <p className="px-4 py-6 text-slate-400">Chưa có thông báo nào.</p>}
      </div>
      <Pager page={pg.page} pages={pg.pages} total={pg.total} setPage={pg.setPage} label="thông báo" />
    </div>
  )
}
