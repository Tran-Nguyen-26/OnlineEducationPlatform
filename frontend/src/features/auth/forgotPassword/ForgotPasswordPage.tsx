import { useState } from 'react'
import { Link } from 'react-router-dom'
import { requestForgotPasswordOtp, verifyForgotPasswordOtp, submitResetPassword } from '@/api/auth'
import { ForgotPasswordStepper } from './components/ForgotPasswordStepper'
import { StepEmail } from './components/StepEmail'
import { StepOtp } from './components/StepOtp'
import { StepResetPassword } from './components/StepResetPassword'
import { StepSuccess } from './components/StepSuccess'
import './forgot-password.css'

export type ForgotPasswordStep = 1 | 2 | 3 | 4

const CURRENT_YEAR = new Date().getFullYear()

export default function ForgotPasswordPage() {
  const [currentStep, setCurrentStep] = useState<ForgotPasswordStep>(1)

  // Step 1 State
  const [email, setEmail] = useState('')
  const [infoNotice, setInfoNotice] = useState<string | null>(null)

  // Step 2 State
  const [maskedEmail, setMaskedEmail] = useState('')
  const [otpDigits, setOtpDigits] = useState<string[]>(['', '', '', '', '', ''])
  const [otpExpiresInSeconds, setOtpExpiresInSeconds] = useState(300)
  const [cooldownSeconds, setCooldownSeconds] = useState(60)

  // Step 3 State
  const [resetToken, setResetToken] = useState('')

  // Shared UI State
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // ── Handler: Step 1 Submit Email ──
  const handleEmailSubmit = async (submittedEmail: string) => {
    setLoading(true)
    setError(null)
    setInfoNotice(null)

    try {
      const response = await requestForgotPasswordOtp({ email: submittedEmail })

      // Check if backend returned instruction for Google-only account
      if (response.cooldownSeconds === 0 && response.expiresInSeconds === 0) {
        setInfoNotice(response.message)
        return
      }

      setEmail(submittedEmail)
      setMaskedEmail(response.emailMasked || submittedEmail)
      setCooldownSeconds(response.cooldownSeconds || 60)
      setOtpExpiresInSeconds(response.expiresInSeconds || 300)
      setOtpDigits(['', '', '', '', '', ''])
      setCurrentStep(2)
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Không thể gửi mã xác thực. Vui lòng thử lại sau.')
      }
    } finally {
      setLoading(false)
    }
  }

  // ── Handler: Resend OTP (Step 2) ──
  const handleResendOtp = async () => {
    setError(null)
    try {
      const response = await requestForgotPasswordOtp({ email })
      setCooldownSeconds(response.cooldownSeconds || 60)
      setOtpExpiresInSeconds(response.expiresInSeconds || 300)
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Không thể gửi lại mã xác thực. Vui lòng thử lại.')
      }
      throw err
    }
  }

  // ── Handler: Step 2 Verify OTP ──
  const handleOtpVerify = async (otpCode: string) => {
    setLoading(true)
    setError(null)

    try {
      const response = await verifyForgotPasswordOtp({ email, otpCode })
      setResetToken(response.resetToken)
      setCurrentStep(3)
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Mã xác thực không hợp lệ hoặc đã hết hạn.')
      }
    } finally {
      setLoading(false)
    }
  }

  // ── Handler: Step 3 Reset Password ──
  const handleResetPasswordSubmit = async (newPassword: string, confirmPassword: string) => {
    setLoading(true)
    setError(null)

    try {
      await submitResetPassword({
        resetToken,
        newPassword,
        confirmPassword,
      })
      // Clear sensitive reset token from memory
      setResetToken('')
      setCurrentStep(4)
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Không thể đặt lại mật khẩu. Vui lòng thử lại từ đầu.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fp-page">
      {/* ── Topbar Logo matching Login style ── */}
      <Link to="/" className="fp-topbar">
        <div className="fp-logo-box">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="fp-cap-icon">
            <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
            <path d="M6 12v5c3 3 9 3 12 0v-5" />
          </svg>
        </div>
        <div className="fp-brand-text">
          <span className="fp-brand-title">EDUPLATFORM</span>
          <span className="fp-brand-sub">HỆ THỐNG ĐÀO TẠO CHUẨN MỰC</span>
        </div>
      </Link>

      {/* ── Main Centered Card ── */}
      <div className="fp-card">
        {/* Render Stepper for Steps 1, 2, 3 */}
        {currentStep <= 3 && <ForgotPasswordStepper currentStep={currentStep} />}

        {/* Step 1: Input Email */}
        {currentStep === 1 && (
          <StepEmail
            email={email}
            setEmail={setEmail}
            onSubmit={handleEmailSubmit}
            loading={loading}
            error={error}
            infoNotice={infoNotice}
          />
        )}

        {/* Step 2: Verify OTP */}
        {currentStep === 2 && (
          <StepOtp
            maskedEmail={maskedEmail}
            otpDigits={otpDigits}
            setOtpDigits={setOtpDigits}
            onVerify={handleOtpVerify}
            onResendOtp={handleResendOtp}
            onBackToEmail={() => {
              setError(null)
              setCurrentStep(1)
            }}
            loading={loading}
            error={error}
            initialExpiresInSeconds={otpExpiresInSeconds}
            initialCooldownSeconds={cooldownSeconds}
          />
        )}

        {/* Step 3: Set New Password */}
        {currentStep === 3 && (
          <StepResetPassword onSubmit={handleResetPasswordSubmit} loading={loading} error={error} />
        )}

        {/* Step 4: Success & Redirect */}
        {currentStep === 4 && <StepSuccess email={email} />}
      </div>

      {/* ── Footer matching Login ── */}
      <footer className="fp-footer">
        <div className="fp-ssl-footer">
          <span>BẢO MẬT SSL 256-BIT</span>
          <span className="fp-ssl-dot">·</span>
          <a href="#" className="fp-ssl-link">QUYỀN RIÊNG TƯ</a>
          <span className="fp-ssl-dot">·</span>
          <a href="#" className="fp-ssl-link">ĐIỀU KHOẢN</a>
        </div>
        <p className="fp-footer-copyright">&copy; {CURRENT_YEAR} EduPlatform Inc. Bảo lưu mọi quyền.</p>
      </footer>
    </div>
  )
}
