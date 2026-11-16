import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, ClipboardCheck, GraduationCap, Home, LogOut, Menu, User } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { apiClient } from '../lib/apiClient'
import { useGradingQueues } from '../lib/useGradingCounts'
import type { NotificationResponse } from '../types/api'
import { NotificationBell } from '../components/NotificationBell'
import { Avatar } from '../components/Avatar'

const NAV = [
  { to: '/teacher', label: 'Tổng quan', icon: Home, end: true },
  { to: '/teacher/courses', label: 'Khóa học của tôi', icon: GraduationCap },
  { to: '/teacher/grading', label: 'Cần chấm bài', icon: ClipboardCheck, badge: 'grading' as const },
  { to: '/teacher/notifications', label: 'Thông báo', icon: Bell, badge: 'notifications' as const },
  { to: '/teacher/profile', label: 'Hồ sơ cá nhân', icon: User },
]

export function TeacherLayout() {
  const [menu, setMenu] = useState(false)
  const { user, logout } = useAuth()
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
    refetchInterval: 30_000,
  })
  const queues = useGradingQueues()
  const counts = {
    grading: queues.total,
    notifications: notifications.data?.filter((n) => !n.read).length ?? 0,
  }

  const sidebar = (
    <>
        <div className="flex items-center justify-between px-5 py-5">
          <span className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-slate-900"><span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><GraduationCap size={19} /></span>OEP <span className="font-semibold text-indigo-600">Giáo viên</span></span>
          <NotificationBell />
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              onClick={() => setMenu(false)}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium text-slate-600 transition hover:bg-indigo-50 hover:text-indigo-700',
                  isActive && 'bg-brand-gradient text-white shadow-md shadow-indigo-500/25 hover:bg-brand-gradient hover:text-white',
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
          <div className="flex items-center gap-2 px-2">
            <Avatar size={32} name={user?.fullName ?? user?.email} />
            <div className="min-w-0 truncate text-sm font-medium text-slate-900">{user?.fullName ?? user?.email}</div>
          </div>
          <div className="truncate px-2 text-xs text-slate-500">{user?.email}</div>
          <button
            onClick={logout}
            className="mt-2 flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-600 hover:bg-slate-100"
          >
            <LogOut size={16} />
            Đăng xuất
          </button>
        </div>
    </>
  )

  return (
    <div className="flex h-screen bg-canvas">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white lg:flex">{sidebar}</aside>
      {menu && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick={() => setMenu(false)} />
          <aside className="relative flex h-full w-72 max-w-[85%] animate-pop flex-col bg-white">{sidebar}</aside>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3 lg:hidden">
          <button onClick={() => setMenu(true)} className="rounded-lg p-1.5 text-slate-700 hover:bg-slate-100" aria-label="Mở menu"><Menu size={22} /></button>
          <span className="font-extrabold tracking-tight text-slate-900">OEP</span>
        </div>
        <main className="flex-1 overflow-y-auto p-5 sm:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
