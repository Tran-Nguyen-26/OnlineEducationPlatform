import { useState } from 'react'
import { useNavigate, Link, useLocation } from 'react-router-dom'
import { loginUser } from '@/api/auth.ts'
import './login.css'

interface LocationState {
  registeredEmail?: string
  message?: string
}

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const navState = location.state as LocationState | null

  const [email, setEmail] = useState(
    navState?.registeredEmail ?? (localStorage.getItem('remembered_email') || '')
  )
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(
    Boolean(localStorage.getItem('remembered_email'))
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(navState?.message ?? '')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess('')

    const trimmedEmail = email.trim()
    if (!trimmedEmail) {
      setError('Vui lòng nhập địa chỉ email.')
      return
    }

    if (!password) {
      setError('Vui lòng nhập mật khẩu.')
      return
    }

    setLoading(true)
    try {
      const response = await loginUser({ email: trimmedEmail, password })

      localStorage.setItem('access_token', response.accessToken)
      if (response.refreshToken) {
        localStorage.setItem('refresh_token', response.refreshToken)
      }
      if (response.user) {
        localStorage.setItem('user', JSON.stringify(response.user))
      }

      if (rememberMe) {
        localStorage.setItem('remembered_email', trimmedEmail)
      } else {
        localStorage.removeItem('remembered_email')
      }

      // Chuyển hướng theo vai trò người dùng
      const userRole = response.user?.role?.toUpperCase()
      const isAdmin =
        userRole === 'ADMIN' ||
        trimmedEmail.toLowerCase() === 'admin@eduplatform.vn' ||
        trimmedEmail.toLowerCase() === 'admin@vnu.edu.vn'

      if (isAdmin) {
        navigate('/admin/users')
      } else {
        navigate('/dashboard')
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Sai email hoặc mật khẩu. Vui lòng thử lại.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="lp-split-page">
      {/* ════ LEFT COLUMN – Dark Institutional Banner ════ */}
      <aside className="lp-sidebar">
        {/* Top Logo and Branding */}
        <Link to="/" className="lp-brand-top">
          <div className="lp-logo-box">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="lp-cap-icon">
              <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
              <path d="M6 12v5c3 3 9 3 12 0v-5" />
            </svg>
          </div>
          <div className="lp-brand-text">
            <span className="lp-brand-title">EDUPLATFORM</span>
            <span className="lp-brand-sub">HỆ THỐNG ĐÀO TẠO CHUẨN MỰC</span>
          </div>
        </Link>

        {/* Center Hero Heading & Slogan */}
        <div className="lp-hero">
          <h1 className="lp-hero-line1">CHÀO MỪNG ĐẾN VỚI</h1>
          <h2 className="lp-hero-line2">EDUPLATFORM</h2>
          <p className="lp-hero-sub">Nền tảng trí tuệ học thuật thế hệ mới.</p>
        </div>

        {/* Bottom Tier Hub Indicator */}
        <div className="lp-sidebar-footer">
          <span className="lp-tier-dot" />
          <span className="lp-tier-text">TRUNG TÂM HỌC THUẬT TIÊU CHUẨN QUỐC TẾ</span>
        </div>
      </aside>

      {/* ════ RIGHT COLUMN – Sign In Form ════ */}
      <main className="lp-main">
        <div className="lp-form-wrapper">
          {/* Mobile Logo (Visible only on smaller screens) */}
          <Link to="/" className="lp-mobile-logo">
            <div className="lp-logo-box">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="lp-cap-icon">
                <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
                <path d="M6 12v5c3 3 9 3 12 0v-5" />
              </svg>
            </div>
            <span className="lp-mobile-logo-name">EDUPLATFORM</span>
          </Link>

          {/* Form Header */}
          <h2 className="lp-form-title">ĐĂNG NHẬP</h2>

          {/* Alerts */}
          {success && (
            <div className="lp-alert lp-alert--success" role="alert">
              <svg className="lp-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
              <span>{success}</span>
            </div>
          )}
          {error && (
            <div className="lp-alert lp-alert--error" role="alert">
              <svg className="lp-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          {/* Login Form */}
          <form onSubmit={handleSubmit} className="lp-form" noValidate>
            {/* Email Field */}
            <div className="lp-field">
              <label htmlFor="lp-email" className="lp-label">EMAIL TÀI KHOẢN / ĐƠN VỊ</label>
              <div className="lp-input-wrap">
                <span className="lp-input-icon">
                  <span className="lp-at-icon">@</span>
                </span>
                <input
                  id="lp-email"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="scholar@university.edu"
                  className="lp-input"
                />
              </div>
            </div>

            {/* Password Field */}
            <div className="lp-field">
              <label htmlFor="lp-password" className="lp-label">MẬT KHẨU</label>
              <div className="lp-input-wrap">
                <span className="lp-input-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0110 0v4" />
                  </svg>
                </span>
                <input
                  id="lp-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="lp-input lp-input--with-toggle"
                />
                <button
                  type="button"
                  className="lp-toggle-btn"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? (
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94" />
                      <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19" />
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  )}
                </button>
              </div>
            </div>

            {/* Remember Me & Forgot Password */}
            <div className="lp-options-row">
              <label className="lp-remember" htmlFor="lp-remember">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  id="lp-remember"
                  className="lp-checkbox"
                />
                <span>Ghi nhớ đăng nhập</span>
              </label>
              <Link to="/forgot-password" className="lp-forgot">QUÊN MẬT KHẨU?</Link>
            </div>

            {/* Yellow Primary Action Button */}
            <button
              id="lp-submit"
              type="submit"
              disabled={loading}
              className="lp-submit-btn"
            >
              {loading ? (
                <>
                  <svg className="lp-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
                    <path d="M12 2a10 10 0 0110 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                  </svg>
                  ĐANG ĐĂNG NHẬP...
                </>
              ) : (
                <>
                  <span>ĐĂNG NHẬP</span>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="lp-arrow-icon">
                    <line x1="5" y1="12" x2="19" y2="12" />
                    <polyline points="12 5 19 12 12 19" />
                  </svg>
                </>
              )}
            </button>

            {/* Google Sign-in Button */}
            <button type="button" className="lp-google-btn" id="lp-google">
              <svg className="lp-google-icon" viewBox="0 0 24 24">
                <path
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  fill="#4285F4"
                />
                <path
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  fill="#34A853"
                />
                <path
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                  fill="#FBBC05"
                />
                <path
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                  fill="#EA4335"
                />
              </svg>
              <span>Tiếp tục với Google</span>
            </button>

            {/* Sign up prompt with yellow underline accent */}
            <p className="lp-signup-text">
              Chưa có tài khoản đơn vị?{' '}
              <Link to="/choose-role" className="lp-signup-link">
                ĐĂNG KÝ
              </Link>
            </p>
          </form>

          {/* SSL Secured Footer */}
          <footer className="lp-ssl-footer">
            <span>BẢO MẬT SSL 256-BIT</span>
            <span className="lp-ssl-dot">·</span>
            <a href="#" className="lp-ssl-link">QUYỀN RIÊNG TƯ</a>
            <span className="lp-ssl-dot">·</span>
            <a href="#" className="lp-ssl-link">ĐIỀU KHOẢN</a>
          </footer>
        </div>
      </main>
    </div>
  )
}
