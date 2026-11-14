export interface AiDraftOption {
  id: string
  text: string
  correct: boolean
  explanation: string
}

export interface AiDraftQuestion {
  id: string
  courseId: string
  lessonId: string
  learningOutcomeId: string
  learningOutcomeCode: string
  bloomLevel: string
  question: string
  options: AiDraftOption[]
}

const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}`,
})

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(path, { method, headers: headers(), ...(body ? { body: JSON.stringify(body) } : {}) })
  if (response.status === 204) return undefined as T
  const data = await response.json().catch(() => null) as (T & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Không thể xử lý câu hỏi AI.')
  return data as T
}

const base = '/api/v1/admin/ai-quiz/drafts'

export const listAiDrafts = (courseId?: string) => request<AiDraftQuestion[]>(`${base}${courseId ? `?courseId=${courseId}` : ''}`)
export const generateAiDrafts = (input: {
  courseId: string; lessonId: string; learningOutcomeId: string; questionBankId?: string
  bloomLevel: string; count: number
}) => request<{ questionBankId: string; createdCount: number; drafts: AiDraftQuestion[] }>(`${base}/generate`, 'POST', input)
export const approveAiDraft = (id: string) => request<AiDraftQuestion>(`${base}/${id}/approve`, 'POST')
export const rejectAiDraft = (id: string) => request<void>(`${base}/${id}/reject`, 'POST')
export const editApproveAiDraft = (id: string, input: {
  question: string; options: { text: string; correct: boolean; explanation?: string }[]
}) => request<AiDraftQuestion>(`${base}/${id}/edit-approve`, 'PUT', input)
