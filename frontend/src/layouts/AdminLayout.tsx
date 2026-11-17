import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Bell, BookOpen, GraduationCap, LayoutDashboard, LogOut, Menu, Activity, BarChart3, ClipboardCheck, Megaphone, Receipt, ScrollText, User, Users as UsersIcon } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { NotificationBell } from '../components/NotificationBell'
import { Avatar } from '../components/Avatar'

const NAV_ITEMS = [
  { to: '/admin', label: 'Tổng quan', icon: LayoutDashboard, end: true },
  { to: '/admin/subjects', label: 'Môn học', icon: BookOpen },
  { to: '/admin/courses', label: 'Khóa học', icon: GraduationCap },
  { to: '/admin/teachers', label: 'Giáo viên', icon: UsersIcon },
  { to: '/admin/users', label: 'Người dùng', icon: UsersIcon },
  { to: '/admin/reports', label: 'Báo cáo & thống kê', icon: BarChart3 },
  { to: '/admin/grading', label: 'Chấm bài & phúc khảo', icon: ClipboardCheck },
  { to: '/admin/finance', label: 'Học phí & công nợ', icon: Receipt },
  { to: '/admin/broadcast', label: 'Thông báo hệ thống', icon: Megaphone },
  { to: '/admin/ops', label: 'Vận hành', icon: Activity },
  { to: '/admin/audit-logs', label: 'Nhật ký', icon: ScrollText },
  { to: '/admin/notifications', label: 'Thông báo của tôi', icon: Bell },
  { to: '/admin/profile', label: 'Hồ sơ cá nhân', icon: User },
]

export function AdminLayout() {
  const [menu, setMenu] = useState(false)
  const { user, logout } = useAuth()

  const sidebar = (
    <>
        <div className="flex items-center justify-between px-5 py-5">
          <span className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-slate-900"><span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><GraduationCap size={19} /></span>OEP <span className="font-semibold text-indigo-600">Admin</span></span>
          <NotificationBell />
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {NAV_ITEMS.map((item) => (
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
              {item.label}
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
