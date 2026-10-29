import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Clock, RefreshCw, ArrowLeft, AlertCircle } from 'lucide-react'
import { OtpInput } from './OtpInput'
import { useCountdownTimer } from '../hooks/useCountdownTimer'

export interface StepOtpProps {
  maskedEmail: string
  otpDigits: string[]
  setOtpDigits: (digits: string[]) => void
  onVerify: (otpCode: string) => Promise<void>
  onResendOtp: () => Promise<void>
  onBackToEmail: () => void
  loading: boolean
  error: string | null
  initialExpiresInSeconds?: number
  initialCooldownSeconds?: number
}

export function StepOtp({
  maskedEmail,
  otpDigits,
  setOtpDigits,
  onVerify,
  onResendOtp,
  onBackToEmail,
  loading,
  error,
  initialExpiresInSeconds = 300,
  initialCooldownSeconds = 60,
}: StepOtpProps) {
  const [resending, setResending] = useState(false)

  // Timer 1: OTP expiration (5 minutes)
  const expiryTimer = useCountdownTimer({
    initialSeconds: initialExpiresInSeconds,
    autoStart: true,
  })

  // Timer 2: Cooldown for resending OTP (60 seconds)
  const cooldownTimer = useCountdownTimer({
    initialSeconds: initialCooldownSeconds,
    autoStart: true,
  })

  const fullCode = otpDigits.join('')
  const isComplete = fullCode.length === 6

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault()
    if (!isComplete || loading || expiryTimer.isExpired) return
    await onVerify(fullCode)
  }

  const handleResendClick = async () => {
    if (cooldownTimer.secondsLeft > 0 || resending || loading) return
    setResending(true)
    try {
      await onResendOtp()
      // Reset both timers
      expiryTimer.reset(initialExpiresInSeconds)
      cooldownTimer.reset(initialCooldownSeconds)
      // Clear digits
      setOtpDigits(['', '', '', '', '', ''])
    } finally {
      setResending(false)
    }
  }

  return (
    <div className="fp-step-content">
      {/* Header */}
      <div className="fp-header">
        <h1 className="fp-title">Xác thực mã OTP</h1>
        <p className="fp-subtitle">
          Mã xác thực gồm 6 chữ số đã được gửi tới hộp thư{' '}
          <strong className="fp-email-highlight">{maskedEmail || 'email của bạn'}</strong>.
        </p>
        <button
          type="button"
          onClick={onBackToEmail}
          className="fp-link-btn"
          aria-label="Thay đổi địa chỉ email nhận mã"
        >
          Đổi địa chỉ email khác
        </button>
      </div>

      <hr className="fp-divider" />

      {/* Error alert */}
      {error && (
        <div className="fp-alert fp-alert--error" role="alert">
          <AlertCircle className="fp-alert-icon" />
          <div className="fp-alert-text">{error}</div>
        </div>
      )}

      {/* Expiry warning if timer finished */}
      {expiryTimer.isExpired && (
        <div className="fp-alert fp-alert--warning" role="alert">
          <AlertCircle className="fp-alert-icon" />
          <div className="fp-alert-text">
            Mã xác thực đã hết hạn hiệu lực. Vui lòng bấm &ldquo;Gửi lại mã xác thực&rdquo; để nhận
            mã mới.
          </div>
        </div>
      )}

      {/* OTP Form */}
      <form onSubmit={handleSubmit} className="fp-form" noValidate>
        <div className="fp-field">
          <label className="fp-label fp-label--center">
            Nhập mã bảo mật 6 số <span className="fp-required">*</span>
          </label>

          <OtpInput
            value={otpDigits}
            onChange={setOtpDigits}
            onComplete={(code) => {
              if (!expiryTimer.isExpired && !loading) {
                onVerify(code)
              }
            }}
            disabled={loading || expiryTimer.isExpired}
            hasError={Boolean(error)}
            autoFocus
          />

          {/* Expiry countdown */}
          <div className="fp-timer-row">
            <Clock className="fp-timer-icon" />
            <span className="fp-timer-text">
              {expiryTimer.isExpired ? (
                <span className="fp-expired-text">Mã đã hết hiệu lực</span>
              ) : (
                <>
                  Mã có hiệu lực trong:{' '}
                  <strong className="fp-timer-countdown">{expiryTimer.formattedTime}</strong>
                </>
              )}
            </span>
          </div>
        </div>

        {/* Submit button */}
        <button
          type="submit"
          id="fp-btn-verify-otp"
          disabled={!isComplete || loading || expiryTimer.isExpired}
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
                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
              </svg>
              Đang xác thực...
            </>
          ) : (
            'Xác thực mã OTP'
          )}
        </button>

        {/* Resend OTP Section */}
        <div className="fp-resend-section">
          <span className="fp-resend-label">Bạn chưa nhận được mã?</span>
          {cooldownTimer.secondsLeft > 0 ? (
            <button type="button" disabled className="fp-resend-btn fp-resend-btn--disabled">
              Gửi lại mã sau ({cooldownTimer.secondsLeft}s)
            </button>
          ) : (
            <button
              type="button"
              onClick={handleResendClick}
              disabled={resending || loading}
              className="fp-resend-btn fp-resend-btn--active"
            >
              <RefreshCw className={`fp-resend-icon ${resending ? 'fp-spin' : ''}`} />
              <span>{resending ? 'Đang gửi lại...' : 'Gửi lại mã xác thực'}</span>
            </button>
          )}
        </div>

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
