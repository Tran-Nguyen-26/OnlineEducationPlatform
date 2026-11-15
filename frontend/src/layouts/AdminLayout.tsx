import { NavLink, Outlet } from 'react-router-dom'
import { BookOpen, GraduationCap, LayoutDashboard, LogOut, Users as UsersIcon } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { NotificationBell } from '../components/NotificationBell'

const NAV_ITEMS = [
  { to: '/admin', label: 'Tổng quan', icon: LayoutDashboard, end: true },
  { to: '/admin/subjects', label: 'Môn học', icon: BookOpen },
  { to: '/admin/courses', label: 'Khóa học', icon: GraduationCap },
  { to: '/admin/teachers', label: 'Giáo viên', icon: UsersIcon },
  { to: '/admin/users', label: 'Người dùng', icon: UsersIcon },
]

export function AdminLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="flex h-screen bg-slate-50">
      <aside className="flex w-60 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center justify-between px-5 py-5">
          <span className="text-lg font-bold text-slate-900">OEP Admin</span>
          <NotificationBell />
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {NAV_ITEMS.map((item) => (
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
              {item.label}
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
