import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  Users,
  UserCheck,
  BookOpen,
  Settings,
  LogOut,
  GraduationCap,
  Search,
  Bell,
  HelpCircle,
  ChevronDown,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { fetchInstructorApplicationStats } from '@/api/instructorApplications'

export function AdminLayout() {
  const navigate = useNavigate()
  const [profileMenuOpen, setProfileMenuOpen] = useState(false)

  const { data: appStats } = useQuery({
    queryKey: ['instructorApplicationStats'],
    queryFn: fetchInstructorApplicationStats,
    staleTime: 30 * 1000,
  })

  let adminName = 'Alex Mercer'
  let adminEmail = 'admin@eduplatform.vn'

  try {
    const rawUser = localStorage.getItem('user')
    if (rawUser) {
      const u = JSON.parse(rawUser)
      if (u.fullName) adminName = u.fullName
      if (u.email) adminEmail = u.email
    }
  } catch {
    // ignore parse error
  }

  const handleLogout = () => {
    localStorage.removeItem('access_token')
    localStorage.removeItem('user')
    navigate('/login')
  }

  return (
    <div className="flex min-h-screen bg-[#f3f4f6] text-slate-800 font-sans antialiased">
      {/* ── Dark Charcoal Sidebar ── */}
      <aside className="fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-[#16191d] text-slate-300">
        {/* Brand Header */}
        <div className="flex h-20 items-center gap-3 px-6">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#f5b823] text-slate-950 shadow-sm">
            <GraduationCap className="h-5 w-5" strokeWidth={2.5} />
          </div>
          <div className="flex flex-col">
            <span className="text-xs font-black tracking-widest text-white uppercase">
              EduPlatform
            </span>
            <span className="text-[11px] font-bold tracking-wider text-slate-400 uppercase">
              Admin
            </span>
          </div>
        </div>

        {/* Navigation Menu */}
        <nav className="flex-1 space-y-1.5 px-3 py-4 overflow-y-auto">
          {/* Users (Quản lý người dùng) */}
          <NavLink
            to="/admin/users"
            className={({ isActive }) =>
              `flex items-center gap-3.5 rounded-lg px-3.5 py-3 text-sm font-semibold transition-colors ${
                isActive
                  ? 'bg-[#f5b823] text-slate-950 shadow-sm'
                  : 'text-slate-300 hover:bg-[#22272e] hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Users className={`h-4 w-4 ${isActive ? 'text-slate-950' : 'text-slate-400'}`} />
                <span>Người dùng</span>
              </>
            )}
          </NavLink>

          {/* Courses (Khóa học) */}
          <NavLink
            to="/admin/courses"
            className={({ isActive }) =>
              `flex items-center gap-3.5 rounded-lg px-3.5 py-3 text-sm font-semibold transition-colors ${
                isActive
                  ? 'bg-[#f5b823] text-slate-950 shadow-sm'
                  : 'text-slate-300 hover:bg-[#22272e] hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <BookOpen className={`h-4 w-4 ${isActive ? 'text-slate-950' : 'text-slate-400'}`} />
                <span>Khóa học</span>
              </>
            )}
          </NavLink>

          {/* Instructor Applications (Duyệt đơn giảng viên) */}
          <NavLink
            to="/admin/instructor-applications"
            className={({ isActive }) =>
              `flex items-center justify-between rounded-lg px-3.5 py-3 text-sm font-semibold transition-colors ${
                isActive
                  ? 'bg-[#f5b823] text-slate-950 shadow-sm'
                  : 'text-slate-300 hover:bg-[#22272e] hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <div className="flex items-center gap-3.5">
                  <UserCheck className={`h-4 w-4 ${isActive ? 'text-slate-950' : 'text-slate-400'}`} />
                  <span>Duyệt giảng viên</span>
                </div>
                {appStats?.pending != null && appStats.pending > 0 && (
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                      isActive ? 'bg-slate-950 text-[#f5b823]' : 'bg-[#f5b823] text-slate-950'
                    }`}
                  >
                    {appStats.pending}
                  </span>
                )}
              </>
            )}
          </NavLink>

          {/* Analytics / Dashboard (Thống kê / Tổng quan) */}
          <NavLink
            to="/admin/dashboard"
            className={({ isActive }) =>
              `flex items-center gap-3.5 rounded-lg px-3.5 py-3 text-sm font-semibold transition-colors ${
                isActive
                  ? 'bg-[#f5b823] text-slate-950 shadow-sm'
                  : 'text-slate-300 hover:bg-[#22272e] hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <LayoutDashboard className={`h-4 w-4 ${isActive ? 'text-slate-950' : 'text-slate-400'}`} />
                <span>Thống kê & Tổng quan</span>
              </>
            )}
          </NavLink>

          {/* Settings (Cài đặt) */}
          <NavLink
            to="/admin/settings"
            className={({ isActive }) =>
              `flex items-center gap-3.5 rounded-lg px-3.5 py-3 text-sm font-semibold transition-colors ${
                isActive
                  ? 'bg-[#f5b823] text-slate-950 shadow-sm'
                  : 'text-slate-300 hover:bg-[#22272e] hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Settings className={`h-4 w-4 ${isActive ? 'text-slate-950' : 'text-slate-400'}`} />
                <span>Cài đặt hệ thống</span>
              </>
            )}
          </NavLink>
        </nav>

        {/* Bottom Profile Widget */}
        <div className="p-4 border-t border-slate-800/60 relative">
          <div
            onClick={() => setProfileMenuOpen(!profileMenuOpen)}
            className="flex items-center justify-between rounded-xl bg-[#22272e] p-2.5 cursor-pointer hover:bg-[#2a3038] transition-colors"
          >
            <div className="flex items-center gap-3 min-w-0">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#f5b823] text-slate-950 font-bold text-sm">
                <Users className="h-4 w-4" />
              </div>
              <div className="flex flex-col min-w-0">
                <span className="truncate text-xs font-bold text-white leading-tight">
                  {adminName}
                </span>
                <span className="truncate text-[11px] text-slate-400">
                  Super Admin
                </span>
              </div>
            </div>
            <ChevronDown className="h-4 w-4 text-slate-400 shrink-0 ml-1" />
          </div>

          {/* Profile Dropdown / Logout */}
          {profileMenuOpen && (
            <div className="absolute bottom-18 left-4 right-4 rounded-xl bg-[#22272e] border border-slate-700/80 p-2 shadow-xl z-50">
              <div className="px-3 py-2 border-b border-slate-700/60 text-xs text-slate-400">
                {adminEmail}
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-xs font-semibold text-rose-400 hover:bg-rose-500/10 transition-colors mt-1"
              >
                <LogOut className="h-4 w-4" />
                <span>Đăng xuất</span>
              </button>
            </div>
          )}
        </div>
      </aside>

      {/* ── Main Area ── */}
      <div className="flex flex-1 flex-col pl-64 min-w-0">
        {/* Top Header Bar */}
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white px-8">
          {/* Search bar */}
          <div className="relative w-full max-w-md">
            <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Tìm kiếm học viên, khóa học, nhật ký..."
              className="w-full rounded-md border border-slate-200 bg-white py-2 pl-10 pr-4 text-xs text-slate-700 placeholder-slate-400 focus:border-slate-400 focus:outline-none"
            />
          </div>

          {/* Right Header Controls */}
          <div className="flex items-center gap-5">
            {/* Bell icon with yellow dot */}
            <button
              type="button"
              className="relative rounded-full p-1.5 text-slate-600 hover:bg-slate-100 transition-colors"
              title="Thông báo"
            >
              <Bell className="h-4 w-4 text-slate-700" />
              <span className="absolute top-1 right-1 h-2 w-2 rounded-full bg-[#f5b823] ring-2 ring-white" />
            </button>

            {/* Help icon */}
            <button
              type="button"
              className="rounded-full p-1.5 text-slate-600 hover:bg-slate-100 transition-colors"
              title="Trợ giúp & Hướng dẫn"
            >
              <HelpCircle className="h-4 w-4 text-slate-700" />
            </button>

            {/* User Avatar Circle */}
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#3d3319] text-[#f5b823] font-bold text-xs">
              {adminName.charAt(0).toUpperCase()}
            </div>
          </div>
        </header>

        {/* Main Content Body */}
        <main className="flex-1 p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
