import { Check } from 'lucide-react'

export interface ForgotPasswordStepperProps {
  currentStep: number // 1, 2, 3, 4
}

interface StepItem {
  number: number
  label: string
}

const STEPS: StepItem[] = [
  { number: 1, label: 'Email' },
  { number: 2, label: 'Xác thực OTP' },
  { number: 3, label: 'Mật khẩu mới' },
]

export function ForgotPasswordStepper({ currentStep }: ForgotPasswordStepperProps) {
  return (
    <div className="fp-stepper" aria-label="Tiến trình khôi phục mật khẩu">
      {STEPS.map((step, index) => {
        const isCompleted = currentStep > step.number
        const isActive = currentStep === step.number

        let statusClass = 'fp-step--upcoming'
        if (isCompleted) statusClass = 'fp-step--completed'
        else if (isActive) statusClass = 'fp-step--active'

        return (
          <div key={step.number} className="fp-step-wrapper">
            {/* Step node */}
            <div className={`fp-step ${statusClass}`}>
              <div className="fp-step-circle">
                {isCompleted ? (
                  <Check className="fp-step-icon" strokeWidth={2.5} />
                ) : (
                  <span>{step.number}</span>
                )}
              </div>
              <span className="fp-step-label">{step.label}</span>
            </div>

            {/* Connecting line (not rendered after last step) */}
            {index < STEPS.length - 1 && (
              <div
                className={`fp-step-line ${
                  currentStep > step.number ? 'fp-step-line--completed' : ''
                }`}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}
