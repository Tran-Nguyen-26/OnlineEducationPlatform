import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: '📊' },
  { to: '/courses', label: 'Khóa học', icon: '📚' },
  { to: '/enrollment', label: 'Đăng ký học', icon: '✏️' },
  { to: '/assessment', label: 'Kiểm tra', icon: '📝' },
]

export function RootLayout() {
  const navigate = useNavigate()

  let isAdmin = false
  try {
    const rawUser = localStorage.getItem('user')
    if (rawUser) {
      const u = JSON.parse(rawUser)
      if (u.role === 'ADMIN' || u.email?.includes('admin')) isAdmin = true
    }
  } catch {
    // ignore
  }

  const handleLogout = () => {
    localStorage.removeItem('access_token')
    localStorage.removeItem('user')
    navigate('/login')
  }

  return (
    <div className="flex min-h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-white shadow-lg">
        <div className="flex h-16 items-center gap-3 border-b px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-sm font-bold text-white">
            OEP
          </div>
          <Link to="/dashboard" className="text-lg font-semibold text-gray-900">
            OEP Platform
          </Link>
        </div>

        <nav className="flex-1 space-y-1 p-4">
          {isAdmin && (
            <Link
              to="/admin/users"
              className="mb-2 flex items-center gap-3 rounded-lg bg-emerald-50 px-3 py-2.5 text-sm font-semibold text-[#00695c] transition-colors hover:bg-emerald-100"
            >
              <span>🛡️</span>
              Admin Portal
            </Link>
          )}
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`
              }
            >
              <span>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t p-4">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-red-600 transition-colors hover:bg-red-50"
          >
            <span>🚪</span>
            Đăng xuất
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 pl-64">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
