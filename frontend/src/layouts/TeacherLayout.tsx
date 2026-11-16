import { NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, ClipboardCheck, GraduationCap, Home, LogOut, User } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { apiClient } from '../lib/apiClient'
import type { NotificationResponse, PendingGradingResponse } from '../types/api'
import { NotificationBell } from '../components/NotificationBell'

const NAV = [
  { to: '/teacher', label: 'Tổng quan', icon: Home, end: true },
  { to: '/teacher/courses', label: 'Khóa học của tôi', icon: GraduationCap },
  { to: '/teacher/grading', label: 'Cần chấm bài', icon: ClipboardCheck, badge: 'grading' as const },
  { to: '/teacher/notifications', label: 'Thông báo', icon: Bell, badge: 'notifications' as const },
  { to: '/teacher/profile', label: 'Hồ sơ cá nhân', icon: User },
]

export function TeacherLayout() {
  const { user, logout } = useAuth()
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
    refetchInterval: 30_000,
  })
  const pending = useQuery({
    queryKey: ['teacher', 'pending-grading'],
    queryFn: () => apiClient.get<PendingGradingResponse[]>('/api/gradebook/pending-grading').then((r) => r.data),
    refetchInterval: 60_000,
  })
  const counts = {
    grading: pending.data?.length ?? 0,
    notifications: notifications.data?.filter((n) => !n.read).length ?? 0,
  }

  return (
    <div className="flex h-screen bg-slate-50">
      <aside className="flex w-64 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center justify-between px-5 py-5">
          <span className="text-lg font-bold text-slate-900">OEP Giáo viên</span>
          <NotificationBell />
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100',
                  isActive && 'bg-slate-900 text-white hover:bg-slate-900',
                )
              }
            >
              <item.icon size={18} />
              <span className="flex-1">{item.label}</span>
              {item.badge && counts[item.badge] > 0 && (
                <span className="rounded-full bg-red-600 px-1.5 text-[11px] text-white">{counts[item.badge] > 9 ? '9+' : counts[item.badge]}</span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-200 p-3">
          <div className="truncate px-2 text-sm font-medium text-slate-900">{user?.fullName ?? user?.email}</div>
          <div className="truncate px-2 text-xs text-slate-500">{user?.email}</div>
          <button
            onClick={logout}
            className="mt-2 flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-600 hover:bg-slate-100"
          >
            <LogOut size={16} />
            Đăng xuất
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
