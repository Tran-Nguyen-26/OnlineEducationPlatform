import { useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, ShieldCheck } from 'lucide-react'
import { useCountdownTimer } from '../hooks/useCountdownTimer'

export interface StepSuccessProps {
  email: string
}

export function StepSuccess({ email }: StepSuccessProps) {
  const navigate = useNavigate()

  const handleGoToLogin = useCallback(() => {
    navigate('/login', {
      replace: true,
      state: {
        registeredEmail: email,
        message: 'Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.',
      },
    })
  }, [email, navigate])

  // 3-second auto-redirect timer
  const { secondsLeft } = useCountdownTimer({
    initialSeconds: 3,
    onExpire: handleGoToLogin,
    autoStart: true,
  })

  useEffect(() => {
    // Backup redirect after 3.5s just in case
    const timer = setTimeout(() => {
      handleGoToLogin()
    }, 3500)
    return () => clearTimeout(timer)
  }, [handleGoToLogin])

  return (
    <div className="fp-step-content fp-step-content--success">
      {/* Pop-in check icon */}
      <div className="fp-success-icon-wrap">
        <div className="fp-success-icon-circle">
          <Check className="fp-success-check-icon" strokeWidth={3} />
        </div>
      </div>

      {/* Heading */}
      <div className="fp-header fp-header--center">
        <h1 className="fp-title fp-title--success">Đặt lại mật khẩu thành công!</h1>
        <p className="fp-subtitle">
          Mật khẩu tài khoản của bạn đã được cập nhật an toàn. Mọi phiên đăng nhập cũ trên các thiết bị khác đã được thu hồi.
        </p>
      </div>

      {/* Security alert callout */}
      <div className="fp-alert fp-alert--success">
        <ShieldCheck className="fp-alert-icon" />
        <div className="fp-alert-text">
          Tài khoản đã được bảo vệ. Vui lòng ghi nhớ mật khẩu mới và không chia sẻ cho người khác.
        </div>
      </div>

      <hr className="fp-divider" />

      {/* Redirect info & Action button */}
      <div className="fp-redirect-box">
        <p className="fp-redirect-text">
          Hệ thống sẽ tự động chuyển hướng về trang Đăng nhập sau{' '}
          <strong className="fp-redirect-countdown">{secondsLeft}</strong> giây...
        </p>

        <button
          type="button"
          onClick={handleGoToLogin}
          id="fp-btn-login-now"
          className="fp-submit-btn"
        >
          Đăng nhập ngay
        </button>
      </div>
    </div>
  )
}
