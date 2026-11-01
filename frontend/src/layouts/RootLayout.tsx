import { BookOpen, ClipboardCheck, GraduationCap, LayoutDashboard, LogOut } from 'lucide-react'
import courseStudioMark from '@/assets/figma/course-studio-mark.svg'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'

const instructorNavItems = [
  { to: '/dashboard', label: 'Tổng quan', icon: LayoutDashboard },
  { to: '/instructor/courses', label: 'Khóa học của tôi', icon: BookOpen },
]
const studentNavItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/courses', label: 'Khóa học', icon: BookOpen },
  { to: '/enrollment', label: 'Đăng ký học', icon: GraduationCap },
  { to: '/assessment', label: 'Kiểm tra', icon: ClipboardCheck },
]

export function RootLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  let storedUser: Record<string, unknown> | null = null
  try {
    storedUser = JSON.parse(localStorage.getItem('user') ?? 'null') as Record<string, unknown> | null
  } catch {
    storedUser = null
  }

  const role = typeof storedUser?.role === 'string' ? storedUser.role : undefined
  const isInstructorWorkspace = location.pathname.startsWith('/instructor/courses')
  const profileName = [storedUser?.firstName, storedUser?.lastName]
    .filter((part): part is string => typeof part === 'string' && part.trim().length > 0)
    .join(' ') || (typeof storedUser?.name === 'string' ? storedUser.name : '') || 'Giảng viên'

  const handleLogout = () => {
    localStorage.removeItem('access_token')
    navigate('/login')
  }

  if (isInstructorWorkspace) {
    return (
      <div className="min-h-screen bg-[#f5f6f7] text-[#191c1d]">
        <aside className="fixed inset-y-0 left-0 z-30 flex w-[68px] flex-col bg-[#222425] text-white md:w-[250px]">
          <Link to="/instructor/courses" className="flex h-[72px] items-center gap-3 border-b border-white/10 px-4 md:px-6">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-[#fed136] text-[#191c1d]"><img src={courseStudioMark} alt="" width="22" height="18" /></span>
            <span className="hidden min-w-0 md:block"><span className="block truncate text-[15px] font-extrabold tracking-[0.08em] text-[#fed136]">EDUPLATFORM</span><span className="mt-0.5 block text-[9px] font-medium tracking-[0.16em] text-white/55">INSTRUCTOR PORTAL</span></span>
          </Link>

          <nav aria-label="Điều hướng giảng viên" className="flex-1 space-y-2 px-2 py-6 md:px-4">
            {instructorNavItems.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/instructor/courses'}
                  title={item.label}
                  className={({ isActive }) => `flex h-11 items-center justify-center gap-3 rounded-md px-2 text-sm font-semibold transition-colors md:justify-start md:px-3 ${isActive ? 'bg-[#fed136] text-[#191c1d]' : 'text-white/80 hover:bg-white/10 hover:text-white'}`}
                >
                  <Icon size={19} className="shrink-0" />
                  <span className="hidden md:block">{item.label}</span>
                </NavLink>
              )
            })}
          </nav>

          <div className="border-t border-white/10 p-3 md:p-4">
            <div className="mb-3 hidden items-center gap-3 rounded-md border border-white/10 p-2.5 md:flex">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[#fed136] text-xs font-bold text-[#191c1d]">{profileName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('').toUpperCase()}</span>
              <span className="min-w-0"><span className="block truncate text-xs font-semibold">{profileName}</span><span className="mt-0.5 block text-[11px] text-white/55">Giảng viên</span></span>
            </div>
            <button onClick={handleLogout} title="Đăng xuất" className="flex h-10 w-full items-center justify-center gap-3 rounded-md px-2 text-sm font-medium text-white/70 transition hover:bg-white/10 hover:text-white md:justify-start md:px-3">
              <LogOut size={18} /><span className="hidden md:block">Đăng xuất</span>
            </button>
          </div>
        </aside>

        <div className="ml-[68px] flex min-h-screen min-w-0 flex-col md:ml-[250px]">
          <header className="flex h-[72px] shrink-0 items-center justify-between border-b border-slate-200 bg-white px-4 sm:px-6 xl:px-8">
            <div><p className="text-[11px] font-bold uppercase tracking-[0.14em] text-slate-500">Course Studio</p><p className="mt-1 text-xs text-slate-400">Không gian giảng viên</p></div>
            <div className="flex items-center gap-2 md:hidden"><span className="flex size-8 items-center justify-center rounded-full bg-[#fed136] text-[11px] font-bold">{profileName.slice(0, 1).toUpperCase()}</span></div>
            <div className="hidden items-center gap-3 md:flex"><span className="text-right"><span className="block text-sm font-semibold">{profileName}</span><span className="block text-xs text-slate-500">Giảng viên</span></span><span className="flex size-9 items-center justify-center rounded-full bg-[#fff2bf] text-sm font-bold text-[#735c00]">{profileName.slice(0, 1).toUpperCase()}</span></div>
          </header>
          <main className="min-w-0 flex-1 p-4 sm:p-6 xl:p-8"><Outlet /></main>
        </div>
      </div>
    )
  }

  const visibleItems = role === 'INSTRUCTOR'
    ? [...studentNavItems, { to: '/instructor/courses', label: 'Course Studio', icon: BookOpen }]
    : studentNavItems
  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="fixed inset-y-0 left-0 z-50 flex w-16 flex-col bg-white shadow-lg sm:w-64">
        <div className="flex h-16 items-center justify-center gap-3 border-b px-2 sm:justify-start sm:px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-sm font-bold text-white">OEP</div>
          <Link to="/dashboard" className="hidden text-lg font-semibold text-gray-900 sm:block">OEP Platform</Link>
        </div>
        <nav className="flex-1 space-y-1 p-4">
          {visibleItems.map((item) => {
            const Icon = item.icon
            return <NavLink key={item.to} to={item.to} className={({ isActive }) => `flex items-center justify-center gap-3 rounded-lg px-2 py-2.5 text-sm font-medium transition-colors sm:justify-start sm:px-3 ${isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'}`}><Icon size={18} /><span className="sr-only sm:not-sr-only">{item.label}</span></NavLink>
          })}
        </nav>
        <div className="border-t p-4"><button onClick={handleLogout} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-red-600 transition-colors hover:bg-red-50"><LogOut size={18} /><span>Đăng xuất</span></button></div>
      </aside>
      <main className="min-w-0 flex-1 pl-16 sm:pl-64"><div className="p-4 sm:p-8"><Outlet /></div></main>
    </div>
  )
}
