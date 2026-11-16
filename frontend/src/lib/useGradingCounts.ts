import { useQuery } from '@tanstack/react-query'
import { apiClient } from './apiClient'
import type { AppealResponse, PendingEssay, PendingGradingResponse } from '../types/api'

/** The three work queues of a grader (assignments, essay answers, open appeals) — shared by the page and the sidebar badge. */
export function useGradingQueues() {
  const assignments = useQuery({
    queryKey: ['teacher', 'pending-grading'],
    queryFn: () => apiClient.get<PendingGradingResponse[]>('/api/gradebook/pending-grading').then((r) => r.data),
  })
  const essays = useQuery({
    queryKey: ['teacher', 'pending-essays'],
    queryFn: () => apiClient.get<PendingEssay[]>('/api/attempts/pending-essays').then((r) => r.data),
  })
  const appeals = useQuery({
    queryKey: ['teacher', 'appeals'],
    queryFn: () => apiClient.get<AppealResponse[]>('/api/appeals').then((r) => r.data),
  })
  const openAppeals = (appeals.data ?? []).filter((a) => a.status === 'OPEN')
  return {
    assignments, essays, appeals, openAppeals,
    total: (assignments.data?.length ?? 0) + (essays.data?.length ?? 0) + openAppeals.length,
  }
}
