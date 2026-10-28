import axios from 'axios'

export type UserRole = 'STUDENT' | 'INSTRUCTOR' | 'ADMIN'
export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'DISABLED'

export interface AdminUserItem {
  id: string
  stt?: number | string
  fullName: string
  email: string
  role: UserRole
  accountStatus: AccountStatus
  createdAt: string
  phone?: string | null
  organization?: string | null
  avatarObjectKey?: string | null
  bio?: string | null
  dateOfBirth?: string | null
}

export interface AdminUserStats {
  totalUsers: number
  activeUsers: number
  lockedUsers: number
}

export interface AdminUsersQuery {
  search?: string
  role?: string
  status?: string
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
 * Fetch paginated and filtered users directly from database via backend API.
 */
export async function fetchAdminUsers(query: AdminUsersQuery = {}): Promise<PageResponse<AdminUserItem>> {
  const page = query.page ?? 0
  const size = query.size ?? 8

  const params: Record<string, string | number> = { page, size }
  if (query.search?.trim()) params.search = query.search.trim()
  if (query.role && query.role !== 'ALL') params.role = query.role
  if (query.status && query.status !== 'ALL') params.status = query.status

  const res = await axios.get<PageResponse<AdminUserItem>>('/api/admin/users', {
    params,
    headers: getAuthHeader(),
  })

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
 * Fetch user counts / metrics directly from database via backend API.
 */
export async function fetchAdminUserStats(): Promise<AdminUserStats> {
  const res = await axios.get<AdminUserStats>('/api/admin/users/stats', {
    headers: getAuthHeader(),
  })
  return res.data
}

/**
 * Fetch full profile details of a single user by ID from database via backend API.
 */
export async function fetchAdminUserDetail(id: string): Promise<AdminUserItem> {
  const res = await axios.get<AdminUserItem>(`/api/admin/users/${id}`, {
    headers: getAuthHeader(),
  })
  return res.data
}

/**
 * Update user account status (LOCKED / ACTIVE) in database via backend API.
 */
export async function updateAdminUserStatus(
  id: string,
  newStatus: AccountStatus,
  reason?: string
): Promise<AdminUserItem> {
  const res = await axios.patch<{ success: boolean; message: string; data: AdminUserItem }>(
    `/api/admin/users/${id}/status`,
    { status: newStatus, reason },
    { headers: getAuthHeader() }
  )
  return res.data.data
}
