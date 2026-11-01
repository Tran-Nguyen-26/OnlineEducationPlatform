import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, ArrowLeft, AlertCircle, Info } from 'lucide-react'

export interface StepEmailProps {
  email: string
  setEmail: (email: string) => void
  onSubmit: (email: string) => Promise<void>
  loading: boolean
  error: string | null
  infoNotice?: string | null
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function StepEmail({
  email,
  setEmail,
  onSubmit,
  loading,
  error,
  infoNotice,
}: StepEmailProps) {
  const [localError, setLocalError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLocalError(null)

    const trimmed = email.trim()
    if (!trimmed) {
      setLocalError('Vui lòng nhập địa chỉ email.')
      return
    }

    if (!EMAIL_REGEX.test(trimmed)) {
      setLocalError('Địa chỉ email không đúng định dạng.')
      return
    }

    await onSubmit(trimmed)
  }

  const displayedError = localError || error

  return (
    <div className="fp-step-content">
      {/* Header */}
      <div className="fp-header">
        <h1 className="fp-title">Quên mật khẩu?</h1>
        <p className="fp-subtitle">
          Nhập địa chỉ email đã đăng ký để nhận mã xác thực OTP gồm 6 chữ số khôi phục tài khoản.
        </p>
      </div>

      <hr className="fp-divider" />

      {/* Info notice (e.g. Google account instruction) */}
      {infoNotice && (
        <div className="fp-alert fp-alert--warning" role="alert">
          <Info className="fp-alert-icon" />
          <div className="fp-alert-text">{infoNotice}</div>
        </div>
      )}

      {/* Error alert */}
      {displayedError && (
        <div className="fp-alert fp-alert--error" role="alert">
          <AlertCircle className="fp-alert-icon" />
          <div className="fp-alert-text">{displayedError}</div>
        </div>
      )}

      {/* Form */}
      <form onSubmit={handleSubmit} className="fp-form" noValidate>
        <div className="fp-field">
          <label htmlFor="fp-email-input" className="fp-label">
            Địa chỉ Email <span className="fp-required">*</span>
          </label>
          <div className="fp-input-wrap">
            <span className="fp-input-icon">
              <Mail />
            </span>
            <input
              id="fp-email-input"
              type="email"
              autoComplete="email"
              required
              disabled={loading}
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                if (localError) setLocalError(null)
              }}
              placeholder="name@example.com"
              className={`fp-input ${displayedError ? 'fp-input--error' : ''}`}
            />
          </div>
          <p className="fp-hint">
            Hệ thống sẽ gửi mã bảo mật 6 số có hiệu lực trong 5 phút tới hòm thư này.
          </p>
        </div>

        {/* Submit button */}
        <button type="submit" id="fp-btn-send-email" disabled={loading} className="fp-submit-btn">
          {loading ? (
            <>
              <svg
                className="fp-spinner"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
              >
                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
              </svg>
              Đang gửi mã xác thực...
            </>
          ) : (
            'Gửi mã xác thực'
          )}
        </button>

        {/* Back to login */}
        <div className="fp-footer-nav">
          <Link to="/login" className="fp-back-link">
            <ArrowLeft className="fp-back-icon" />
            <span>Quay lại trang Đăng nhập</span>
          </Link>
        </div>
      </form>
    </div>
  )
}
