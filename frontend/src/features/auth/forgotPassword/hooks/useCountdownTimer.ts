import { useState, useEffect, useRef, useCallback } from 'react'

export interface UseCountdownTimerOptions {
  initialSeconds: number
  onExpire?: () => void
  autoStart?: boolean
}

export interface UseCountdownTimerReturn {
  secondsLeft: number
  isExpired: boolean
  isRunning: boolean
  start: () => void
  pause: () => void
  reset: (newSeconds?: number) => void
  formattedTime: string
}

export function useCountdownTimer({
  initialSeconds,
  onExpire,
  autoStart = true,
}: UseCountdownTimerOptions): UseCountdownTimerReturn {
  const [secondsLeft, setSecondsLeft] = useState(initialSeconds)
  const [isRunning, setIsRunning] = useState(autoStart)
  const onExpireRef = useRef(onExpire)

  useEffect(() => {
    onExpireRef.current = onExpire
  }, [onExpire])

  useEffect(() => {
    if (!isRunning || secondsLeft <= 0) return

    const interval = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(interval)
          setIsRunning(false)
          onExpireRef.current?.()
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [isRunning, secondsLeft])

  const start = useCallback(() => {
    if (secondsLeft > 0) {
      setIsRunning(true)
    }
  }, [secondsLeft])

  const pause = useCallback(() => {
    setIsRunning(false)
  }, [])

  const reset = useCallback(
    (newSeconds?: number) => {
      const next = typeof newSeconds === 'number' ? newSeconds : initialSeconds
      setSecondsLeft(next)
      setIsRunning(next > 0)
    },
    [initialSeconds]
  )

  // Format MM:SS (e.g. 04:59)
  const minutes = Math.floor(secondsLeft / 60)
  const remainingSeconds = secondsLeft % 60
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`

  return {
    secondsLeft,
    isExpired: secondsLeft === 0,
    isRunning,
    start,
    pause,
    reset,
    formattedTime,
  }
}
