import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  Users,
  BookOpen,
  Settings,
  LogOut,
  GraduationCap,
  Shield,
} from 'lucide-react'

export function AdminLayout() {
  const navigate = useNavigate()

  let adminName = 'Quản trị viên'
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
    <div className="flex min-h-screen bg-[#f8fafc] text-slate-800">
      {/* ── Sidebar ── */}
      <aside className="fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-slate-200 bg-white">
        {/* Logo / Brand Header */}
        <div className="flex h-20 items-center gap-3 border-b border-slate-100 px-6">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#00695c] text-white shadow-sm">
            <GraduationCap className="h-6 w-6" />
          </div>
          <div>
            <div className="text-base font-bold tracking-tight text-slate-900">EduPlatform</div>
            <div className="text-xs font-medium text-slate-500">Admin Portal</div>
          </div>
        </div>

        {/* Navigation Sections */}
        <div className="flex-1 overflow-y-auto px-4 py-6">
          {/* Main Admin Section */}
          <div className="mb-8">
            <div className="px-3 pb-2 text-[11px] font-bold tracking-wider text-slate-400 uppercase">
              Quản trị chính
            </div>
            <nav className="space-y-1">
              <NavLink
                to="/admin/dashboard"
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-emerald-50 text-[#00695c]'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`
                }
              >
                <LayoutDashboard className="h-4 w-4" />
                <span>Tổng quan</span>
              </NavLink>

              <NavLink
                to="/admin/users"
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-[#e6f4ea] text-[#00695c] font-semibold'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`
                }
              >
                <Users className="h-4 w-4 text-[#00695c]" />
                <span>Quản lý người dùng</span>
              </NavLink>

              <NavLink
                to="/admin/courses"
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-emerald-50 text-[#00695c]'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`
                }
              >
                <BookOpen className="h-4 w-4" />
                <span>Quản lý khóa học</span>
              </NavLink>
            </nav>
          </div>

          {/* System Section */}
          <div>
            <div className="px-3 pb-2 text-[11px] font-bold tracking-wider text-slate-400 uppercase">
              Hệ thống
            </div>
            <nav className="space-y-1">
              <NavLink
                to="/admin/settings"
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-emerald-50 text-[#00695c]'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                  }`
                }
              >
                <Settings className="h-4 w-4" />
                <span>Cài đặt</span>
              </NavLink>

              <button
                type="button"
                onClick={handleLogout}
                className="flex w-full items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm font-medium text-rose-600 transition-colors hover:bg-rose-50"
              >
                <LogOut className="h-4 w-4" />
                <span>Đăng xuất</span>
              </button>
            </nav>
          </div>
        </div>
      </aside>

      {/* ── Main Area ── */}
      <div className="flex flex-1 flex-col pl-64">
        {/* Top Header Bar */}
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-8 backdrop-blur-sm">
          {/* Left Title with Status Dot */}
          <div className="flex items-center gap-2.5">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500"></span>
            </span>
            <span className="text-sm font-medium text-slate-700">
              Hệ thống Quản trị Giáo dục Trực tuyến
            </span>
          </div>

          {/* Right Admin Profile */}
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#00695c] text-white">
                <Shield className="h-5 w-5" />
              </div>
              <div className="flex flex-col text-right">
                <span className="text-sm font-semibold text-slate-900 leading-tight">
                  {adminName}
                </span>
                <span className="text-xs text-slate-500">{adminEmail}</span>
              </div>
              <span className="ml-1 rounded-full bg-indigo-50 px-2.5 py-0.5 text-[11px] font-semibold text-indigo-700">
                ADMIN
              </span>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              title="Đăng xuất"
              className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
            >
              <LogOut className="h-4 w-4" />
            </button>
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
