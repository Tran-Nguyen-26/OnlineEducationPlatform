import { AuthShell } from '../components/AuthShell'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getApiErrorMessage } from '../lib/apiClient'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/student'} replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(email, password, fullName)
      navigate('/student', { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-depth ring-1 ring-slate-200/70">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Đăng ký học sinh</h1>
        <p className="mb-6 text-sm text-slate-500">Tạo tài khoản để xem và học các khóa học công khai</p>

        <label className="mb-1 block text-sm font-medium text-slate-700">Họ tên</label>
        <Input required value={fullName} onChange={(e) => setFullName(e.target.value)} className="mb-4" />

        <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
        <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mb-4" />

        <label className="mb-1 block text-sm font-medium text-slate-700">Mật khẩu (tối thiểu 8 ký tự)</label>
        <Input
          type="password"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-4"
        />

        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
        </Button>

        <p className="mt-4 text-center text-sm text-slate-500">
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-medium text-slate-900 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </form>
    </AuthShell>
  )
}
