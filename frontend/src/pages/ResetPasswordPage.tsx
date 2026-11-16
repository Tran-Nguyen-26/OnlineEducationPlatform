import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') ?? ''
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (newPassword !== confirm) {
      setError('Mật khẩu xác nhận không khớp.')
      return
    }
    setSubmitting(true)
    try {
      await apiClient.post('/api/auth/reset-password', { token, newPassword })
      navigate('/login', { replace: true, state: { resetSuccess: true } })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Đặt lại mật khẩu</h1>
        <p className="mb-6 text-sm text-slate-500">Nhập mật khẩu mới cho tài khoản của bạn.</p>

        {!token ? (
          <p className="mb-4 text-sm text-red-600">Link không hợp lệ — thiếu token.</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-slate-700">Mật khẩu mới</label>
            <Input
              type="password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="mb-4"
            />

            <label className="mb-1 block text-sm font-medium text-slate-700">Xác nhận mật khẩu</label>
            <Input
              type="password"
              required
              minLength={8}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className="mb-4"
            />

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <Button type="submit" disabled={submitting} className="w-full">
              {submitting ? 'Đang xử lý...' : 'Đặt lại mật khẩu'}
            </Button>
          </form>
        )}

        <p className="mt-4 text-center text-sm text-slate-500">
          <Link to="/login" className="font-medium text-slate-900 hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </div>
    </div>
  )
}
