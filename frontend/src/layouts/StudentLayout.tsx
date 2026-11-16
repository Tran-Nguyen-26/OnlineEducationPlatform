import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Award, Bell, BookOpen, CalendarDays, ClipboardList, Compass, GraduationCap, Home, LogOut, Menu, Receipt, Star, User } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { apiClient } from '../lib/apiClient'
import type { NotificationResponse } from '../types/api'
import { NotificationBell } from '../components/NotificationBell'
import { Avatar } from '../components/Avatar'

const NAV = [
  { to: '/student', label: 'Trang chủ', icon: Home, end: true },
  { to: '/student/my-courses', label: 'Khóa học của tôi', icon: BookOpen },
  { to: '/student/explore', label: 'Khám phá khóa học', icon: Compass },
  { to: '/student/calendar', label: 'Lịch của tôi', icon: CalendarDays },
  { to: '/student/tasks', label: 'Bài tập & Quiz', icon: ClipboardList },
  { to: '/student/grades', label: 'Điểm số', icon: Star },
  { to: '/student/certificates', label: 'Chứng chỉ', icon: Award },
  { to: '/student/tuition', label: 'Học phí', icon: Receipt },
  { to: '/student/notifications', label: 'Thông báo', icon: Bell, badge: true },
  { to: '/student/profile', label: 'Hồ sơ cá nhân', icon: User },
]

export function StudentLayout() {
  const { user, logout } = useAuth()
  const { data: notifications } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiClient.get<NotificationResponse[]>('/api/notifications').then((r) => r.data),
    refetchInterval: 30_000,
  })
  const unread = notifications?.filter((n) => !n.read).length ?? 0

  const [menu, setMenu] = useState(false)

  const sidebar = (
    <>
      <div className="flex items-center justify-between px-5 py-5">
        <span className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-white">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/25"><GraduationCap size={19} /></span> OEP
        </span>
        <NotificationBell tone="dark" />
      </div>
      <nav className="scroll-thin flex-1 space-y-1 overflow-y-auto px-3">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={() => setMenu(false)}
            className={({ isActive }) =>
              cn(
                'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-indigo-100/80 transition hover:bg-white/10 hover:text-white',
                isActive && 'bg-white/15 text-white shadow-[inset_0_0_0_1px_rgb(255_255_255/0.18)] hover:bg-white/15',
              )
            }
          >
            <item.icon size={18} />
            <span className="flex-1">{item.label}</span>
            {item.badge && unread > 0 && (
              <span className="rounded-full bg-rose-500 px-1.5 text-[11px] font-semibold text-white">{unread > 9 ? '9+' : unread}</span>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded-2xl bg-white/10 p-3 ring-1 ring-white/10">
        <div className="flex items-center gap-2.5">
          <Avatar size={36} name={user?.fullName ?? user?.email} />
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-white">{user?.fullName ?? user?.email}</div>
            <div className="truncate text-xs text-indigo-200/80">{user?.email}</div>
          </div>
        </div>
        <button onClick={logout} className="mt-3 flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-indigo-100/90 transition hover:bg-white/10 hover:text-white">
          <LogOut size={16} />
          Đăng xuất
        </button>
      </div>
    </>
  )

  return (
    <div className="flex h-screen bg-canvas">
      <aside className="bg-mesh-dark hidden w-64 shrink-0 flex-col lg:flex">{sidebar}</aside>
      {menu && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick={() => setMenu(false)} />
          <aside className="bg-mesh-dark relative flex h-full w-72 max-w-[85%] animate-pop flex-col">{sidebar}</aside>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3 lg:hidden">
          <button onClick={() => setMenu(true)} className="rounded-lg p-1.5 text-slate-700 hover:bg-slate-100" aria-label="Mở menu"><Menu size={22} /></button>
          <span className="font-extrabold tracking-tight text-slate-900">OEP</span>
        </div>
        <main className="relative flex-1 overflow-y-auto p-5 sm:p-8">
          <div className="pointer-events-none absolute inset-x-0 top-0 -z-0 h-64 bg-gradient-to-b from-indigo-100/60 to-transparent" />
          <div className="relative"><Outlet /></div>
        </main>
      </div>
    </div>
  )
}
