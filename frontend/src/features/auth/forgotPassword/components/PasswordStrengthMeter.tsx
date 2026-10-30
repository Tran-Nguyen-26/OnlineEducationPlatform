import { useEffect, useMemo } from 'react'
import { Check, Circle } from 'lucide-react'

export interface PasswordStrengthMeterProps {
  password: string
  onScoreChange?: (score: number, isValid: boolean) => void
}

interface Criterion {
  id: string
  label: string
  passed: boolean
}

export function PasswordStrengthMeter({ password, onScoreChange }: PasswordStrengthMeterProps) {
  const criteria: Criterion[] = useMemo(() => {
    return [
      {
        id: 'length',
        label: 'Ít nhất 8 ký tự',
        passed: password.length >= 8,
      },
      {
        id: 'case',
        label: 'Chữ hoa (A-Z) và chữ thường (a-z)',
        passed: /[a-z]/.test(password) && /[A-Z]/.test(password),
      },
      {
        id: 'number',
        label: 'Ít nhất một chữ số (0-9)',
        passed: /\d/.test(password),
      },
      {
        id: 'special',
        label: 'Ký tự đặc biệt (!@#$%^&*...)',
        passed: /[^A-Za-z0-9]/.test(password),
      },
    ]
  }, [password])

  const score = useMemo(() => {
    if (!password) return 0
    return criteria.filter((c) => c.passed).length
  }, [criteria, password])

  const isValid = score === 4

  useEffect(() => {
    onScoreChange?.(score, isValid)
  }, [score, isValid, onScoreChange])

  // Get strength label and color class
  const getStrengthMeta = () => {
    if (!password) return { label: '', colorClass: '' }
    switch (score) {
      case 1:
        return { label: 'Rất yếu', colorClass: 'fp-meter--weak' }
      case 2:
        return { label: 'Trung bình', colorClass: 'fp-meter--fair' }
      case 3:
        return { label: 'Khá', colorClass: 'fp-meter--good' }
      case 4:
        return { label: 'Rất mạnh', colorClass: 'fp-meter--strong' }
      default:
        return { label: 'Rất yếu', colorClass: 'fp-meter--weak' }
    }
  }

  const meta = getStrengthMeta()

  return (
    <div className="fp-strength-meter">
      {/* Label & Score */}
      {password && (
        <div className="fp-meter-header">
          <span className="fp-meter-title">Độ mạnh mật khẩu:</span>
          <span className={`fp-meter-badge ${meta.colorClass}`}>{meta.label}</span>
        </div>
      )}

      {/* 4 segments */}
      {password && (
        <div
          className="fp-meter-bars"
          role="progressbar"
          aria-valuenow={score}
          aria-valuemin={0}
          aria-valuemax={4}
        >
          {[1, 2, 3, 4].map((seg) => (
            <div
              key={seg}
              className={`fp-meter-bar ${seg <= score ? meta.colorClass : 'fp-meter-bar--empty'}`}
            />
          ))}
        </div>
      )}

      {/* Checklist */}
      <ul className="fp-criteria-list">
        {criteria.map((c) => (
          <li
            key={c.id}
            className={`fp-criterion ${c.passed ? 'fp-criterion--passed' : 'fp-criterion--pending'}`}
          >
            {c.passed ? (
              <Check className="fp-criterion-icon fp-criterion-icon--passed" />
            ) : (
              <Circle className="fp-criterion-icon fp-criterion-icon--pending" />
            )}
            <span>{c.label}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
