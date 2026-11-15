import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { UserRole } from '../types/api'

interface ProtectedRouteProps {
  allowedRoles?: UserRole[]
}

/** Guards a route subtree: requires login, forces the first-login password change, then checks role. */
export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-slate-500">Đang tải...</div>
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}
