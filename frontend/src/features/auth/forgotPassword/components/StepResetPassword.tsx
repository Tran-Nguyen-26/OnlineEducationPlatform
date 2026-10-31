import React, { useState } from 'react'
import { Lock, Eye, EyeOff, AlertCircle, CheckCircle2, ArrowRight } from 'lucide-react'
import { PasswordStrengthMeter } from './PasswordStrengthMeter'

export interface StepResetPasswordProps {
  onSubmit: (newPassword: string, confirmPassword: string) => Promise<void>
  loading: boolean
  error: string | null
}

export function StepResetPassword({ onSubmit, loading, error }: StepResetPasswordProps) {
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [isPasswordStrong, setIsPasswordStrong] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)

  const isMatching = confirmPassword.length > 0 && newPassword === confirmPassword
  const isMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLocalError(null)

    if (!newPassword) {
      setLocalError('Vui lòng nhập mật khẩu mới.')
      return
    }

    if (!isPasswordStrong) {
      setLocalError('Mật khẩu chưa đáp ứng đủ 4 tiêu chí bảo mật.')
      return
    }

    if (newPassword !== confirmPassword) {
      setLocalError('Mật khẩu xác nhận không trùng khớp.')
      return
    }

    await onSubmit(newPassword, confirmPassword)
  }

  const displayedError = localError || error

  return (
    <div className="fp-step-content">
      {/* Header */}
      <div className="fp-header">
        <h1 className="fp-title">ĐẶT LẠI MẬT KHẨU</h1>
        <p className="fp-subtitle">
          Tạo mật khẩu mới an toàn cho tài khoản của bạn. Mọi phiên đăng nhập cũ trên thiết bị khác
          sẽ tự động bị thu hồi.
        </p>
      </div>

      <hr className="fp-divider" />

      {/* Error alert */}
      {displayedError && (
        <div className="fp-alert fp-alert--error" role="alert">
          <AlertCircle className="fp-alert-icon" />
          <div className="fp-alert-text">{displayedError}</div>
        </div>
      )}

      {/* Form */}
      <form onSubmit={handleSubmit} className="fp-form" noValidate>
        {/* New password */}
        <div className="fp-field">
          <label htmlFor="fp-new-password" className="fp-label">
            MẬT KHẨU MỚI <span className="fp-required">*</span>
          </label>
          <div className="fp-input-wrap">
            <span className="fp-input-icon">
              <Lock />
            </span>
            <input
              id="fp-new-password"
              type={showNewPassword ? 'text' : 'password'}
              autoComplete="new-password"
              required
              disabled={loading}
              value={newPassword}
              onChange={(e) => {
                setNewPassword(e.target.value)
                if (localError) setLocalError(null)
              }}
              placeholder="Nhập mật khẩu mới"
              className="fp-input fp-input--with-toggle"
            />
            <button
              type="button"
              className="fp-toggle-btn"
              onClick={() => setShowNewPassword((v) => !v)}
              aria-label={showNewPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showNewPassword ? <EyeOff /> : <Eye />}
            </button>
          </div>

          {/* Strength Meter */}
          <PasswordStrengthMeter
            password={newPassword}
            onScoreChange={(_score, isValid) => setIsPasswordStrong(isValid)}
          />
        </div>

        {/* Confirm password */}
        <div className="fp-field">
          <label htmlFor="fp-confirm-password" className="fp-label">
            XÁC NHẬN MẬT KHẨU <span className="fp-required">*</span>
          </label>
          <div className="fp-input-wrap">
            <span className="fp-input-icon">
              <Lock />
            </span>
            <input
              id="fp-confirm-password"
              type={showConfirmPassword ? 'text' : 'password'}
              autoComplete="new-password"
              required
              disabled={loading}
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value)
                if (localError) setLocalError(null)
              }}
              placeholder="Nhập lại mật khẩu mới"
              className={`fp-input fp-input--with-toggle ${
                isMismatch ? 'fp-input--error' : isMatching ? 'fp-input--success' : ''
              }`}
            />
            <button
              type="button"
              className="fp-toggle-btn"
              onClick={() => setShowConfirmPassword((v) => !v)}
              aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showConfirmPassword ? <EyeOff /> : <Eye />}
            </button>
          </div>

          {/* Feedback message for confirm password */}
          {isMismatch && (
            <div className="fp-field-feedback fp-field-feedback--error">
              <AlertCircle className="fp-feedback-icon" />
              <span>Mật khẩu xác nhận không trùng khớp.</span>
            </div>
          )}
          {isMatching && (
            <div className="fp-field-feedback fp-field-feedback--success">
              <CheckCircle2 className="fp-feedback-icon" />
              <span>Mật khẩu xác nhận trùng khớp.</span>
            </div>
          )}
        </div>

        {/* Submit button */}
        <button
          type="submit"
          id="fp-btn-save-password"
          disabled={!isPasswordStrong || !isMatching || loading}
          className="fp-submit-btn"
        >
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
              <span>ĐANG CẬP NHẬT...</span>
            </>
          ) : (
            <>
              <span>CẬP NHẬT MẬT KHẨU</span>
              <ArrowRight className="fp-btn-arrow" />
            </>
          )}
        </button>
      </form>
    </div>
  )
}
