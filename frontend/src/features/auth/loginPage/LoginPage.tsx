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

      navigate('/dashboard')
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
    <div className="lp-page">
      {/* ── Logo trên cùng ── */}
      <Link to="/" className="lp-topbar">
        <div className="lp-topbar-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 14l9-5-9-5-9 5 9 5z" />
            <path d="M12 14l6.16-3.422A12.083 12.083 0 0112 21.93a12.083 12.083 0 01-6.16-11.352L12 14z" />
          </svg>
        </div>
        <span className="lp-topbar-name">EduPlatform</span>
      </Link>

      {/* ── Card chính ── */}
      <div className="lp-card">
        {/* Welcome heading */}
        <div className="lp-welcome">
          <h1 className="lp-welcome-title">Chào mừng trở lại</h1>
          <p className="lp-welcome-sub">Đăng nhập để tham gia học tập ngay</p>
        </div>

        <hr className="lp-divider-top" />

        {/* Form */}
        <form onSubmit={handleSubmit} className="lp-form" noValidate>
          {/* Alerts */}
          {success && (
            <div className="lp-alert lp-alert--success">
              <svg className="lp-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 11.08V12a10 10 0 11-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
              {success}
            </div>
          )}
          {error && (
            <div className="lp-alert lp-alert--error">
              <svg className="lp-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              {error}
            </div>
          )}

          {/* Email */}
          <div className="lp-field">
            <label htmlFor="lp-email" className="lp-label">Email</label>
            <div className="lp-input-wrap">
              <span className="lp-input-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="2" y="4" width="20" height="16" rx="2" />
                  <path d="M2 7l10 7 10-7" />
                </svg>
              </span>
              <input
                id="lp-email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Nhập email của bạn"
                className="lp-input"
              />
            </div>
          </div>

          {/* Password */}
          <div className="lp-field">
            <label htmlFor="lp-password" className="lp-label">Mật khẩu</label>
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
                placeholder="Nhập mật khẩu"
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

          {/* Remember me + Forgot password */}
          <div className="lp-row-options">
            <label className="lp-remember">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                id="lp-remember"
              />
              Ghi nhớ đăng nhập
            </label>
            <a href="#" className="lp-forgot">Quên mật khẩu?</a>
          </div>

          {/* Submit */}
          <button
            id="lp-submit"
            type="submit"
            disabled={loading}
            className="lp-submit-btn"
          >
            {loading ? (
              <>
                <svg className="lp-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
                </svg>
                Đang đăng nhập...
              </>
            ) : (
              'Đăng nhập'
            )}
          </button>

          {/* OR */}
          <div className="lp-or">
            <div className="lp-or-line" />
            <span className="lp-or-text">HOẶC</span>
            <div className="lp-or-line" />
          </div>

          {/* Google */}
          <button type="button" className="lp-google-btn" id="lp-google">
            {/* Google G icon SVG */}
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
            Tiếp tục với Google
          </button>

          {/* Signup link */}
          <p className="lp-signup-link">
            Chưa có tài khoản?{' '}
            <Link to="/choose-role">Tạo tài khoản</Link>
          </p>
        </form>
      </div>
    </div>
  )
}
