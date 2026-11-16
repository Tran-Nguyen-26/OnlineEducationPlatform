import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { apiClient } from '../lib/apiClient'
import { useAuth } from '../auth/AuthContext'
import type { NotificationResponse } from '../types/api'
import { cn } from '../lib/cn'

function courseLinkFor(role: string | undefined, courseId: string): string {
  if (role === 'ADMIN') return `/admin/courses/${courseId}`
  if (role === 'INSTRUCTOR') return `/teacher/courses/${courseId}`
  return `/student/courses/${courseId}`
}

function timeAgo(iso: string): string {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return 'vừa xong'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} phút trước`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} giờ trước`
  return `${Math.floor(hours / 24)} ngày trước`
}

export function NotificationBell({ tone = 'light' }: { tone?: 'light' | 'dark' } = {}) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const { data: notifications } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
    refetchInterval: 30_000,
  })

  const unreadCount = notifications?.filter((n) => !n.read).length ?? 0

  const markReadMutation = useMutation({
    mutationFn: (id: string) => apiClient.patch(`/api/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const markAllReadMutation = useMutation({
    mutationFn: () => apiClient.patch('/api/notifications/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  function handleClickNotification(n: NotificationResponse) {
    if (!n.read) markReadMutation.mutate(n.id)
    setOpen(false)
    if (n.targetType === 'COURSE' && n.targetId) {
      navigate(courseLinkFor(user?.role, n.targetId))
    }
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className={tone === 'dark' ? 'relative rounded-lg p-2 text-indigo-100 transition hover:bg-white/10 hover:text-white' : 'relative rounded-lg p-2 text-slate-500 transition hover:bg-indigo-50 hover:text-indigo-700'}
        aria-label="Thông báo"
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-medium text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full z-20 mt-2 w-80 rounded-2xl border border-slate-200 bg-white shadow-depth">
            <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2">
              <span className="text-sm font-semibold text-slate-900">Thông báo</span>
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllReadMutation.mutate()}
                  className="text-xs text-slate-500 hover:underline"
                >
                  Đánh dấu tất cả đã đọc
                </button>
              )}
            </div>
            <div className="max-h-96 overflow-y-auto">
              {!notifications?.length && (
                <p className="px-4 py-6 text-center text-sm text-slate-400">Chưa có thông báo nào.</p>
              )}
              {notifications?.map((n) => (
                <button
                  key={n.id}
                  onClick={() => handleClickNotification(n)}
                  className={cn(
                    'block w-full border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50',
                    !n.read && 'bg-slate-50/80',
                  )}
                >
                  <div className="flex items-start gap-2">
                    {!n.read && <span className="mt-1.5 h-2 w-2 flex-shrink-0 rounded-full bg-blue-600" />}
                    <div className={cn('flex-1', n.read && 'pl-4')}>
                      <p className="text-sm font-medium text-slate-900">{n.title}</p>
                      <p className="mt-0.5 line-clamp-2 text-xs text-slate-500">{n.message}</p>
                      <p className="mt-1 text-[11px] text-slate-400">{timeAgo(n.createdAt)}</p>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
