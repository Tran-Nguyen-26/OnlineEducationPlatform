import React, { useRef, useEffect } from 'react'

export interface OtpInputProps {
  length?: number
  value: string[]
  onChange: (digits: string[]) => void
  onComplete?: (code: string) => void
  disabled?: boolean
  hasError?: boolean
  autoFocus?: boolean
}

export function OtpInput({
  length = 6,
  value,
  onChange,
  onComplete,
  disabled = false,
  hasError = false,
  autoFocus = true,
}: OtpInputProps) {
  const inputsRef = useRef<(HTMLInputElement | null)[]>([])

  // Ensure digits array matches length
  const digits = Array.from({ length }, (_, i) => value[i] || '')

  useEffect(() => {
    if (autoFocus && !disabled) {
      inputsRef.current[0]?.focus()
    }
  }, [autoFocus, disabled])

  const handleInputChange = (index: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const rawVal = e.target.value
    // Take the last character typed if multiple characters were entered
    const char = rawVal.replace(/\D/g, '').slice(-1)

    const nextDigits = [...digits]
    nextDigits[index] = char
    onChange(nextDigits)

    if (char && index < length - 1) {
      inputsRef.current[index + 1]?.focus()
    }

    const fullCode = nextDigits.join('')
    if (fullCode.length === length) {
      onComplete?.(fullCode)
    }
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (!digits[index] && index > 0) {
        // Move to previous input and clear it
        const nextDigits = [...digits]
        nextDigits[index - 1] = ''
        onChange(nextDigits)
        inputsRef.current[index - 1]?.focus()
        e.preventDefault()
      } else if (digits[index]) {
        const nextDigits = [...digits]
        nextDigits[index] = ''
        onChange(nextDigits)
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      inputsRef.current[index - 1]?.focus()
      e.preventDefault()
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      inputsRef.current[index + 1]?.focus()
      e.preventDefault()
    }
  }

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault()
    if (disabled) return

    const pasteData = e.clipboardData.getData('text')
    const numbersOnly = pasteData.replace(/\D/g, '').slice(0, length)
    if (!numbersOnly) return

    const nextDigits = [...digits]
    for (let i = 0; i < length; i++) {
      nextDigits[i] = numbersOnly[i] || ''
    }
    onChange(nextDigits)

    // Focus the box after the last pasted digit, or the last box
    const focusIndex = Math.min(numbersOnly.length, length - 1)
    inputsRef.current[focusIndex]?.focus()

    if (numbersOnly.length === length) {
      onComplete?.(numbersOnly)
    }
  }

  return (
    <div className={`fp-otp-container ${hasError ? 'fp-otp-container--error' : ''}`}>
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => {
            inputsRef.current[i] = el
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={1}
          autoComplete="one-time-code"
          value={digits[i]}
          onChange={(e) => handleInputChange(i, e)}
          onKeyDown={(e) => handleKeyDown(i, e)}
          onPaste={handlePaste}
          disabled={disabled}
          className={`fp-otp-box ${digits[i] ? 'fp-otp-box--filled' : ''} ${
            hasError ? 'fp-otp-box--error' : ''
          }`}
          aria-label={`Chữ số xác thực thứ ${i + 1}`}
        />
      ))}
    </div>
  )
}
