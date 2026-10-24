import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    // TODO: wire to apiClient when /api/auth/register is ready
    await new Promise((resolve) => setTimeout(resolve, 800))
    setLoading(false)
    navigate('/login')
  }

  return (
    <>
      <h1 className="mb-2 text-2xl font-bold text-gray-900">Tạo tài khoản</h1>
      <p className="mb-6 text-sm text-gray-500">Điền thông tin bên dưới để đăng ký.</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="firstName" className="mb-1.5 block text-sm font-medium text-gray-700">
              Họ
            </label>
            <input
              id="firstName"
              type="text"
              required
              placeholder="Nguyễn"
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm transition outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
            />
          </div>
          <div>
            <label htmlFor="lastName" className="mb-1.5 block text-sm font-medium text-gray-700">
              Tên
            </label>
            <input
              id="lastName"
              type="text"
              required
              placeholder="Văn A"
              className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm transition outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
            />
          </div>
        </div>
        <div>
          <label htmlFor="regEmail" className="mb-1.5 block text-sm font-medium text-gray-700">
            Email
          </label>
          <input
            id="regEmail"
            type="email"
            required
            placeholder="you@example.com"
            className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm transition outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
          />
        </div>
        <div>
          <label htmlFor="regPassword" className="mb-1.5 block text-sm font-medium text-gray-700">
            Mật khẩu
          </label>
          <input
            id="regPassword"
            type="password"
            required
            placeholder="Ít nhất 8 ký tự"
            className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm transition outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-blue-600 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:opacity-60"
        >
          {loading ? 'Đang tạo...' : 'Đăng ký'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-500">
        Đã có tài khoản?{' '}
        <Link to="/login" className="font-medium text-blue-600 hover:underline">
          Đăng nhập
        </Link>
      </p>
    </>
  )
}
