import { AuthShell } from '../components/AuthShell'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await apiClient.post('/api/auth/forgot-password', { email })
      setSent(true)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-depth ring-1 ring-slate-200/70">
        <h1 className="mb-1 text-xl font-bold text-slate-900">Quên mật khẩu</h1>
        <p className="mb-6 text-sm text-slate-500">Nhập email để nhận link đặt lại mật khẩu.</p>

        {sent ? (
          <p className="mb-4 rounded-md bg-green-50 p-3 text-sm text-green-700">
            Nếu email này tồn tại trong hệ thống, một link đặt lại mật khẩu đã được tạo và sẽ được gửi tới bạn. Liên hệ
            Admin nếu bạn không nhận được link sau ít phút.
          </p>
        ) : (
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
            <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mb-4" />

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <Button type="submit" disabled={submitting} className="w-full">
              {submitting ? 'Đang gửi...' : 'Gửi link đặt lại mật khẩu'}
            </Button>
          </form>
        )}

        <p className="mt-4 text-center text-sm text-slate-500">
          <Link to="/login" className="font-medium text-slate-900 hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </div>
    </AuthShell>
  )
}
