import { tokenStore } from '../lib/tokenStore'
import { AuthShell } from '../components/AuthShell'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

export function ChangePasswordPage() {
  const { user, refreshMe, logout } = useAuth()
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const { data } = await apiClient.post<{ accessToken: string; refreshToken: string }>('/api/auth/change-password', { currentPassword, newPassword })
      tokenStore.setTokens(data.accessToken, data.refreshToken) // tokens issued after the change no longer carry must_change_password
      const me = await refreshMe()
      const home = me.role === 'ADMIN' ? '/admin' : me.role === 'INSTRUCTOR' ? '/teacher' : '/student'
      navigate(home, { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-depth ring-1 ring-slate-200/70">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Đổi mật khẩu</h1>
        <p className="mb-6 text-sm text-slate-500">
          {user?.mustChangePassword
            ? 'Tài khoản của bạn dùng mật khẩu tạm — hãy đổi mật khẩu trước khi tiếp tục.'
            : 'Cập nhật mật khẩu đăng nhập.'}
        </p>

        <label className="mb-1 block text-sm font-medium text-slate-700">Mật khẩu hiện tại</label>
        <Input
          type="password"
          required
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          className="mb-4"
        />

        <label className="mb-1 block text-sm font-medium text-slate-700">Mật khẩu mới (tối thiểu 8 ký tự)</label>
        <Input
          type="password"
          required
          minLength={8}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          className="mb-4"
        />

        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        <Button type="submit" disabled={submitting} className="w-full">
          {submitting ? 'Đang lưu...' : 'Đổi mật khẩu'}
        </Button>
        <button type="button" onClick={logout} className="mt-3 w-full text-center text-sm text-slate-500 hover:underline">
          Đăng xuất
        </button>
      </form>
    </AuthShell>
  )
}
