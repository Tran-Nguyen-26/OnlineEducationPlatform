export interface AdminAiMetrics {
  from: string
  to: string
  questions: number
  promptTokens: number
  completionTokens: number
  averageLatencyMs: number
  refusalRate: number
  mistakeCacheHits: number
  mistakeCacheMisses: number
  mistakeCacheHitRate: number
  estimatedCostUsd: number
  costConfigured: boolean
  daily: Array<{
    date: string
    questions: number
    promptTokens: number
    completionTokens: number
    averageLatencyMs: number
    refusalRate: number
    estimatedCostUsd: number
  }>
  courses: Array<{
    courseId: string
    courseTitle: string
    questions: number
    promptTokens: number
    completionTokens: number
    averageLatencyMs: number
    refusalRate: number
    estimatedCostUsd: number
  }>
}

export async function fetchAdminAiMetrics(from: string, to: string): Promise<AdminAiMetrics> {
  const token = localStorage.getItem('access_token')
  const query = new URLSearchParams({ from, to })
  const response = await fetch(`/api/v1/admin/ai/metrics?${query}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  const data = await response.json().catch(() => null) as (AdminAiMetrics & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Không tải được thống kê AI.')
  return data as AdminAiMetrics
}
