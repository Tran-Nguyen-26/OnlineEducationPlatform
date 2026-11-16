import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getApiErrorMessage } from '../lib/apiClient'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

function homeFor(role: string) {
  if (role === 'ADMIN') return '/admin'
  if (role === 'INSTRUCTOR') return '/teacher'
  return '/student'
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    const redirectTo = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? homeFor(user.role)
    return <Navigate to={redirectTo} replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const me = await login(email, password)
      navigate(me.mustChangePassword ? '/change-password' : homeFor(me.role), { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-xl font-bold text-slate-900">OEP</h1>
        <p className="mb-6 text-sm text-slate-500">Đăng nhập để tiếp tục</p>

        <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
        <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mb-4" />

        <div className="mb-1 flex items-center justify-between">
          <label className="block text-sm font-medium text-slate-700">Mật khẩu</label>
          <Link to="/forgot-password" className="text-xs text-slate-500 hover:underline">
            Quên mật khẩu?
          </Link>
        </div>
        <Input
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-4"
        />

        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </Button>

        <p className="mt-4 text-center text-sm text-slate-500">
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-medium text-slate-900 hover:underline">
            Đăng ký làm học sinh
          </Link>
        </p>
      </form>
    </div>
  )
}
