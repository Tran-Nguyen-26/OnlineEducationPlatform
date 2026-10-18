import { Link, Outlet } from 'react-router-dom'

export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 p-4">
      <div className="mb-8 flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-base font-bold text-white">
          OEP
        </div>
        <Link to="/" className="text-2xl font-bold text-gray-900">
          Online Education Platform
        </Link>
      </div>
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl">
        <Outlet />
      </div>
    </div>
  )
}
