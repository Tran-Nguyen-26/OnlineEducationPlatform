import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerUser } from '@/api/auth'
import './register.css'

function getPasswordStrength(password: string): { level: number; label: string } {
  if (!password) return { level: 0, label: '' }
  let score = 0
  if (password.length >= 6) score++
  if (password.length >= 10) score++
  if (/[A-Z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++
  if (score <= 1) return { level: 1, label: 'Yếu' }
  if (score <= 2) return { level: 2, label: 'Trung bình' }
  if (score <= 3) return { level: 3, label: 'Khá' }
  return { level: 4, label: 'Mạnh' }
}

const strengthColors = ['', '#ef4444', '#f97316', '#eab308', '#10b981']
const strengthColorNames = ['', 'reg-strength-weak', 'reg-strength-fair', 'reg-strength-good', 'reg-strength-strong']

export default function RegisterPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [dob, setDob] = useState('')
  const [school, setSchool] = useState('')
  const [about, setAbout] = useState('')

  const strength = getPasswordStrength(password)
  const passwordsMatch = confirmPassword.length > 0 && password === confirmPassword

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không trùng khớp.')
      return
    }

    if (password.length < 6) {
      setError('Mật khẩu phải có độ dài từ 6 ký tự trở lên.')
      return
    }

    if (!fullName.trim()) {
      setError('Vui lòng nhập họ và tên của bạn.')
      return
    }

    setLoading(true)
    try {
      await registerUser({
        email,
        password,
        fullName,
        phone,
        dateOfBirth: dob,
        organization: school,
        bio: about,
      })

      navigate('/login', {
        state: {
          registeredEmail: email,
          message: 'Đăng ký tài khoản học viên thành công! Vui lòng đăng nhập.',
        },
      })
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Đã xảy ra lỗi trong quá trình đăng ký. Vui lòng thử lại.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      {/* Page header */}
      <div className="reg-form-header">
        <h2 className="reg-form-title">Tạo Tài Khoản Học Viên</h2>
        <p className="reg-form-subtitle">Tham gia EduPlatform và bắt đầu hành trình học tập của bạn ngay hôm nay.</p>
      </div>

      {error && (
        <div className="reg-alert-error" role="alert">
          <svg className="reg-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="reg-form-body">
        {/* ── Section: Account Information ── */}
        <div className="reg-section-header">
          <span className="reg-section-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="11" width="18" height="11" rx="2" />
              <path d="M7 11V7a5 5 0 0110 0v4" />
            </svg>
          </span>
          <span className="reg-section-title">THÔNG TIN TÀI KHOẢN</span>
        </div>

        {/* Email */}
        <div className="reg-field">
          <label htmlFor="regEmail" className="reg-label">
            Địa chỉ Email <span className="reg-required">*</span>
          </label>
          <div className="reg-input-wrap">
            <span className="reg-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                <polyline points="22,6 12,13 2,6" />
              </svg>
            </span>
            <input
              id="regEmail"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="hocvien@example.com"
              className="reg-input reg-input-with-icon"
            />
          </div>
        </div>

        {/* Password */}
        <div className="reg-field">
          <div className="reg-label-row">
            <label htmlFor="regPassword" className="reg-label">
              Mật khẩu <span className="reg-required">*</span>
            </label>
            <span className="reg-label-hint">6–100 ký tự</span>
          </div>
          <div className="reg-input-wrap">
            <span className="reg-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0110 0v4" />
              </svg>
            </span>
            <input
              id="regPassword"
              type={showPassword ? 'text' : 'password'}
              required
              minLength={6}
              maxLength={100}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••••"
              className="reg-input reg-input-with-icon reg-input-with-toggle"
            />
            <button
              type="button"
              className="reg-toggle-btn"
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
          {/* Strength bar */}
          {password && (
            <div className="reg-strength-row">
              <span className="reg-strength-label">Độ mạnh:</span>
              <div className="reg-strength-bars">
                {[1, 2, 3, 4].map((i) => (
                  <div
                    key={i}
                    className="reg-strength-bar"
                    style={{ backgroundColor: i <= strength.level ? strengthColors[strength.level] : '#e5e7eb' }}
                  />
                ))}
              </div>
              <span
                className={`reg-strength-text ${strengthColorNames[strength.level]}`}
              >
                {strength.label}
              </span>
            </div>
          )}
        </div>

        {/* Confirm password */}
        <div className="reg-field">
          <div className="reg-label-row">
            <label htmlFor="regConfirmPassword" className="reg-label">
              Xác nhận mật khẩu <span className="reg-required">*</span>
            </label>
            {passwordsMatch && (
              <span className="reg-match-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                Mật khẩu trùng khớp
              </span>
            )}
          </div>
          <div className="reg-input-wrap">
            <span className="reg-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4" />
              </svg>
            </span>
            <input
              id="regConfirmPassword"
              type={showConfirm ? 'text' : 'password'}
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="••••••••••••••"
              className={`reg-input reg-input-with-icon reg-input-with-toggle${
                confirmPassword && !passwordsMatch ? ' reg-input-error' : ''
              }`}
            />
            <button
              type="button"
              className="reg-toggle-btn"
              onClick={() => setShowConfirm((v) => !v)}
              aria-label={showConfirm ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showConfirm ? (
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

        {/* ── Section: Personal Profile ── */}
        <div className="reg-section-header" style={{ marginTop: '1.5rem' }}>
          <span className="reg-section-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </span>
          <span className="reg-section-title">HỒ SƠ CÁ NHÂN</span>
        </div>
        <p className="reg-section-desc">Cho chúng tôi biết thêm về bạn. Bạn có thể cập nhật hồ sơ sau.</p>

        {/* Full Name */}
        <div className="reg-field">
          <div className="reg-label-row">
            <label htmlFor="regFullName" className="reg-label">
              Họ và tên <span className="reg-required">*</span>
            </label>
            <span className="reg-label-hint">Tối đa 255 ký tự</span>
          </div>
          <div className="reg-input-wrap">
            <span className="reg-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </span>
            <input
              id="regFullName"
              type="text"
              required
              maxLength={255}
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Nguyễn Văn An"
              className="reg-input reg-input-with-icon"
            />
          </div>
        </div>

        {/* Phone + Date of Birth */}
        <div className="reg-two-col">
          <div className="reg-field">
            <div className="reg-label-row">
              <label htmlFor="regPhone" className="reg-label">Số điện thoại</label>
              <span className="reg-optional">Tùy chọn</span>
            </div>
            <div className="reg-input-wrap">
              <span className="reg-input-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 9.81a19.79 19.79 0 01-3.07-8.63A2 2 0 012 .18h3a2 2 0 012 1.72 12.84 12.84 0 00.7 2.81 2 2 0 01-.45 2.11L6.09 7.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 14z" />
                </svg>
              </span>
              <input
                id="regPhone"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="0901234567"
                className="reg-input reg-input-with-icon"
              />
            </div>
          </div>
          <div className="reg-field">
            <div className="reg-label-row">
              <label htmlFor="regDob" className="reg-label">Ngày sinh</label>
              <span className="reg-optional">Tùy chọn</span>
            </div>
            <input
              id="regDob"
              type="date"
              value={dob}
              onChange={(e) => setDob(e.target.value)}
              className="reg-input"
            />
          </div>
        </div>

        {/* School / Organization */}
        <div className="reg-field">
          <div className="reg-label-row">
            <label htmlFor="regSchool" className="reg-label">Trường / Tổ chức</label>
            <span className="reg-optional">Tùy chọn</span>
          </div>
          <div className="reg-input-wrap">
            <span className="reg-input-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="7" width="20" height="14" rx="2" />
                <path d="M16 7V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v2" />
                <line x1="12" y1="12" x2="12" y2="16" />
                <line x1="10" y1="14" x2="14" y2="14" />
              </svg>
            </span>
            <input
              id="regSchool"
              type="text"
              value={school}
              onChange={(e) => setSchool(e.target.value)}
              placeholder="Đại học Quốc gia Việt Nam"
              className="reg-input reg-input-with-icon"
            />
          </div>
        </div>

        {/* About You */}
        <div className="reg-field">
          <div className="reg-label-row">
            <label htmlFor="regAbout" className="reg-label">Giới thiệu bản thân</label>
            <span className="reg-optional">Tùy chọn</span>
          </div>
          <textarea
            id="regAbout"
            rows={3}
            value={about}
            onChange={(e) => setAbout(e.target.value)}
            placeholder="Tôi quan tâm đến phát triển web full-stack và kỹ thuật phần mềm đám mây."
            className="reg-input reg-textarea"
          />
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={loading}
          className="reg-submit-btn"
        >
          {loading ? (
            <>
              <svg className="reg-spinner" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
                <path d="M12 2a10 10 0 0110 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              </svg>
              Đang tạo tài khoản…
            </>
          ) : (
            'Tạo Tài Khoản'
          )}
        </button>

        <p className="reg-signin-link">
          Đã có tài khoản?{' '}
          <Link to="/login" className="reg-signin-link-a">Đăng nhập</Link>
        </p>

        <p className="reg-privacy-note">
          Thông tin của bạn sẽ được dùng để tạo và quản lý tài khoản học viên.
        </p>
      </form>
    </>
  )
}
