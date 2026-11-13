import axios from 'axios'

export type CodeKind = 'ACTIVE' | 'EXTENSION'
export type CodeStatus = 'AVAILABLE' | 'USED' | 'REVOKED' | 'EXPIRED'

export interface CourseEnrollment {
  id: string
  courseId: string
  courseTitle: string
  enrolledAt: string
  accessExpiresAt: string | null
  accessState: 'ACTIVE' | 'ARCHIVED_STATIC' | 'UNAVAILABLE'
}

interface BatchRequest {
  courseId: string
  kind: CodeKind
  count: number
  accessDays: number
  expiresAt: string
}

export interface CodeBatch extends Omit<BatchRequest, 'count'> {
  batchId: string
  codes: { id: string; code: string }[]
}

export interface CodeStatusItem {
  id: string
  status: CodeStatus
  expiresAt: string
}

function authHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
}

export async function fetchEnrollments() {
  const { data } = await axios.get<{ content: CourseEnrollment[] }>('/api/enrollments', {
    headers: authHeaders(),
    params: { size: 100 },
  })
  return data.content
}

export async function redeemAccessCode(code: string) {
  const { data } = await axios.post<CourseEnrollment>('/api/enrollments/redeem', { code }, {
    headers: authHeaders(),
  })
  return data
}

export async function fetchAccessibleCourse(id: string) {
  const { data } = await axios.get<{ id: string; title: string; description?: string }>(
    `/api/v1/courses/${encodeURIComponent(id)}`, { headers: authHeaders() }
  )
  return data
}

export async function issueCodeBatch(input: BatchRequest) {
  const { data } = await axios.post<CodeBatch>('/api/admin/access-codes/batches', input, {
    headers: authHeaders(),
  })
  return data
}

export async function fetchCodeBatch(batchId: string) {
  const { data } = await axios.get<CodeStatusItem[]>(`/api/admin/access-codes/batches/${encodeURIComponent(batchId)}`, {
    headers: authHeaders(),
  })
  return data
}

export async function revokeAccessCode(id: string) {
  await axios.post(`/api/admin/access-codes/${encodeURIComponent(id)}/revoke`, null, {
    headers: authHeaders(),
  })
}

export function accessCodeError(error: unknown, fallback: string) {
  return axios.isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message || fallback
    : fallback
}
