import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck, Settings2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { request } from '@/api/quiz'

interface NotificationItem {
  id: string
  title: string
  message: string
  actionUrl: string | null
  isRead: boolean
  createdAt: string
}

interface NotificationPage {
  content: NotificationItem[]
  totalElements: number
}

interface NotificationPreferences {
  inAppEnabled: boolean
  emailEnabled: boolean
}

const inboxKey = ['notifications', 'inbox']
const unreadKey = ['notifications', 'unread']

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [preferencesOpen, setPreferencesOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const unread = useQuery({
    queryKey: unreadKey,
    queryFn: () => request<{ unreadCount: number }>('/api/notifications/unread-count'),
    refetchInterval: 30_000,
  })
  const inbox = useQuery({
    queryKey: inboxKey,
    queryFn: () => request<NotificationPage>('/api/notifications?page=0&size=8'),
    enabled: open,
    refetchInterval: open ? 30_000 : false,
  })
  const preferences = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: () => request<NotificationPreferences>('/api/notifications/preferences'),
    enabled: open && preferencesOpen,
  })

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: inboxKey }),
      queryClient.invalidateQueries({ queryKey: unreadKey }),
    ])
  }
  const markOne = useMutation({
    mutationFn: (id: string) => request(`/api/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' }),
    onSuccess: refresh,
  })
  const markAll = useMutation({
    mutationFn: () => request('/api/notifications/read-all', { method: 'PATCH' }),
    onSuccess: refresh,
  })
  const updatePreferences = useMutation({
    mutationFn: (value: NotificationPreferences) => request<NotificationPreferences>('/api/notifications/preferences', {
      method: 'PUT', body: JSON.stringify(value),
    }),
    onSuccess: (value) => queryClient.setQueryData(['notifications', 'preferences'], value),
  })

  async function openNotification(item: NotificationItem) {
    if (!item.isRead) await markOne.mutateAsync(item.id)
    setOpen(false)
    if (item.actionUrl?.startsWith('/') && !item.actionUrl.startsWith('//')) navigate(item.actionUrl)
  }

  return <div className="relative">
    <button type="button" aria-label={unread.data?.unreadCount ? `Thông báo, ${unread.data.unreadCount} chưa đọc` : 'Thông báo'} aria-expanded={open}
      onClick={() => setOpen((value) => !value)} className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-teal-700">
      <Bell className="h-5 w-5" aria-hidden="true" />
      {!!unread.data?.unreadCount && <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-rose-600 px-1 text-center text-[10px] font-bold leading-4 text-white">{unread.data.unreadCount > 99 ? '99+' : unread.data.unreadCount}</span>}
    </button>
    {open && <section aria-label="Hộp thư thông báo" className="absolute right-0 z-50 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
      <header className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
        <h2 className="font-semibold text-slate-900">Thông báo</h2>
        <div className="flex items-center gap-3">
        <button type="button" aria-label="Tùy chọn thông báo" aria-expanded={preferencesOpen} onClick={() => setPreferencesOpen((value) => !value)} className="rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-800">
          <Settings2 className="h-4 w-4" aria-hidden="true" />
        </button>
        <button type="button" onClick={() => markAll.mutate()} disabled={!unread.data?.unreadCount || markAll.isPending}
          className="inline-flex items-center gap-1 text-xs font-medium text-teal-800 hover:underline disabled:opacity-50">
          <CheckCheck className="h-4 w-4" aria-hidden="true" />Đánh dấu tất cả đã đọc
        </button>
        </div>
      </header>
      {inbox.isPending ? <p role="status" className="p-4 text-sm text-slate-500">Đang tải thông báo…</p>
        : inbox.isError ? <p role="alert" className="p-4 text-sm text-rose-700">Không tải được thông báo.</p>
          : inbox.data?.content.length ? <ul className="max-h-[min(70vh,28rem)] overflow-y-auto divide-y divide-slate-100">
            {inbox.data.content.map((item) => <li key={item.id}>
              <button type="button" onClick={() => void openNotification(item)} className={`w-full px-4 py-3 text-left hover:bg-slate-50 ${item.isRead ? '' : 'bg-teal-50/60'}`}>
                <span className="flex items-start gap-2">
                  {!item.isRead && <span aria-label="Chưa đọc" className="mt-1.5 h-2 w-2 flex-shrink-0 rounded-full bg-teal-700" />}
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-slate-900">{item.title}</span>
                    <span className="mt-0.5 block text-sm text-slate-600">{item.message}</span>
                    <time className="mt-1 block text-xs text-slate-400" dateTime={item.createdAt}>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.createdAt))}</time>
                  </span>
                </span>
              </button>
            </li>)}
          </ul> : <p className="p-5 text-sm text-slate-500">Bạn chưa có thông báo nào.</p>}
      {preferencesOpen && <section aria-label="Tùy chọn thông báo" className="space-y-3 border-t border-slate-100 bg-slate-50 p-4">
        <h3 className="text-sm font-semibold text-slate-800">Kênh nhận thông báo</h3>
        {preferences.isPending ? <p role="status" className="text-xs text-slate-500">Đang tải tùy chọn…</p> : preferences.isError ? <p role="alert" className="text-xs text-rose-700">Không tải được tùy chọn.</p> : preferences.data && <>
          <label className="flex items-center justify-between gap-3 text-sm text-slate-700"><span>Thông báo trong ứng dụng</span><input type="checkbox" checked={preferences.data.inAppEnabled} disabled={updatePreferences.isPending} onChange={(event) => updatePreferences.mutate({ ...preferences.data!, inAppEnabled: event.target.checked })} /></label>
          <label className="flex items-center justify-between gap-3 text-sm text-slate-700"><span>Email</span><input type="checkbox" checked={preferences.data.emailEnabled} disabled={updatePreferences.isPending} onChange={(event) => updatePreferences.mutate({ ...preferences.data!, emailEnabled: event.target.checked })} /></label>
          <p className="text-xs text-slate-500">Tùy chọn áp dụng cho các thông báo mới.</p>
          {updatePreferences.isError && <p role="alert" className="text-xs text-rose-700">{updatePreferences.error.message}</p>}
        </>}
      </section>}
    </section>}
  </div>
}
