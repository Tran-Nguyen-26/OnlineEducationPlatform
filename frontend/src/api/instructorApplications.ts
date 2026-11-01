import axios from 'axios'

export type InstructorApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface InstructorApplicationItem {
  id: string
  stt?: number | string
  email: string
  fullName: string
  phone?: string | null
  organization?: string | null
  jobTitle?: string | null
  expertise?: string | null
  yearsOfExperience?: number | null
  professionalBio?: string | null
  motivation?: string | null
  status: InstructorApplicationStatus
  submittedAt: string
  reviewedBy?: string | null
  reviewedAt?: string | null
  rejectionReason?: string | null
}

export interface InstructorApplicationStats {
  total: number
  pending: number
  approved: number
  rejected: number
}

export interface InstructorApplicationQuery {
  status?: string
  search?: string
  page?: number
  size?: number
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

function getAuthHeader(): Record<string, string> {
  const token = localStorage.getItem('access_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * Fetch paginated and filtered instructor applications directly from backend API.
 */
export async function fetchInstructorApplications(
  query: InstructorApplicationQuery = {}
): Promise<PageResponse<InstructorApplicationItem>> {
  const page = query.page ?? 0
  const size = query.size ?? 8

  const params: Record<string, string | number> = { page, size }
  if (query.search?.trim()) params.search = query.search.trim()
  if (query.status && query.status !== 'ALL') params.status = query.status

  const res = await axios.get<PageResponse<InstructorApplicationItem>>(
    '/api/admin/instructor-applications',
    {
      params,
      headers: getAuthHeader(),
    }
  )

  const data = res.data
  const formattedContent = (data.content || []).map((item, idx) => ({
    ...item,
    stt: String(page * size + idx + 1).padStart(2, '0'),
  }))

  return {
    ...data,
    content: formattedContent,
  }
}

/**
 * Fetch summary statistics (total, pending, approved, rejected) for instructor applications.
 */
export async function fetchInstructorApplicationStats(): Promise<InstructorApplicationStats> {
  const res = await axios.get<InstructorApplicationStats>(
    '/api/admin/instructor-applications/stats',
    {
      headers: getAuthHeader(),
    }
  )
  return res.data
}

/**
 * Fetch single instructor application details by ID.
 */
export async function fetchInstructorApplicationDetail(
  id: string
): Promise<InstructorApplicationItem> {
  const res = await axios.get<InstructorApplicationItem>(
    `/api/admin/instructor-applications/${id}`,
    {
      headers: getAuthHeader(),
    }
  )
  return res.data
}

/**
 * Approve an instructor application (creates account, generates password, sends email).
 */
export async function approveInstructorApplication(id: string): Promise<{
  success: boolean
  message: string
  data?: unknown
}> {
  const res = await axios.post<{
    success: boolean
    message: string
    data?: unknown
  }>(`/api/admin/instructor-applications/${id}/approve`, {}, { headers: getAuthHeader() })
  return res.data
}

/**
 * Reject an instructor application with a required reason.
 */
export async function rejectInstructorApplication(
  id: string,
  rejectionReason: string
): Promise<{
  success: boolean
  message: string
  data?: unknown
}> {
  const res = await axios.post<{
    success: boolean
    message: string
    data?: unknown
  }>(
    `/api/admin/instructor-applications/${id}/reject`,
    { rejectionReason },
    { headers: getAuthHeader() }
  )
  return res.data
}
