import { useCallback, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../lib/apiClient'

/**
 * Reports the highest watched percentage of a lesson's video to the server (throttled: only when it grew by >= 5 points
 * or reached 100). The server keeps the max, so seeking backwards never loses progress. No-op for read-only review.
 */
export function useVideoProgress(courseId: string, lessonId: string, enabled: boolean) {
  const queryClient = useQueryClient()
  const best = useRef(0)
  const sent = useRef(0)

  return useCallback((percent: number) => {
    if (!enabled || !Number.isFinite(percent)) return
    const p = Math.max(0, Math.min(100, Math.floor(percent)))
    if (p > best.current) best.current = p
    if (best.current >= sent.current + 5 || (best.current >= 98 && sent.current < 100)) {
      sent.current = best.current >= 98 ? 100 : best.current
      apiClient.patch(`/api/lessons/${lessonId}/video-progress`, { percent: sent.current })
        .then(() => queryClient.invalidateQueries({ queryKey: ['student', 'courses', courseId, 'outline'] }))
        .catch(() => { sent.current = Math.max(0, sent.current - 5) })
    }
  }, [courseId, lessonId, enabled, queryClient])
}
