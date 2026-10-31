import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, AlertCircle, Info, ArrowRight } from 'lucide-react'

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
        <h1 className="fp-title">QUÊN MẬT KHẨU?</h1>
        <p className="fp-subtitle">
          Nhập địa chỉ email tài khoản đã đăng ký để nhận mã xác thực OTP gồm 6 chữ số khôi phục tài khoản.
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
            EMAIL TÀI KHOẢN / ĐƠN VỊ <span className="fp-required">*</span>
          </label>
          <div className="fp-input-wrap">
            <span className="fp-input-icon">
              <span className="fp-at-symbol">@</span>
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
              placeholder="scholar@university.edu"
              className={`fp-input ${displayedError ? 'fp-input--error' : ''}`}
            />
          </div>
          <p className="fp-hint">
            Hệ thống sẽ gửi mã bảo mật 6 số có hiệu lực trong 5 phút tới hòm thư này.
          </p>
        </div>

        {/* Submit button matching Login */}
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
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
                <path d="M12 2a10 10 0 0110 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              </svg>
              <span>ĐANG GỬI MÃ XÁC THỰC...</span>
            </>
          ) : (
            <>
              <span>GỬI MÃ XÁC THỰC</span>
              <ArrowRight className="fp-btn-arrow" />
            </>
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
