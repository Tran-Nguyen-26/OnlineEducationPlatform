export type UserRole = 'STUDENT' | 'INSTRUCTOR' | 'ADMIN'
export type AccountStatus = 'ACTIVE' | 'DISABLED'
export type CourseLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'DISABLED'
export type LearningMode = 'FREE_NAVIGATION' | 'SEQUENTIAL'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  role: UserRole
  mustChangePassword: boolean
}

export interface MeResponse {
  id: string
  email: string
  role: UserRole
  fullName: string | null
  mustChangePassword: boolean
  accountStatus: AccountStatus
}

export interface SubjectResponse {
  id: string
  name: string
  description: string | null
  active: boolean
}

export interface CourseResponse {
  id: string
  subjectId: string
  subjectName: string
  title: string
  description: string | null
  lifecycleStatus: CourseLifecycleStatus
  capacity: number | null
  startAt: string | null
  endAt: string | null
  learningMode: LearningMode
  createdByEmail: string
  createdAt: string
  updatedAt: string
}

export interface CourseInstructorResponse {
  instructorId: string
  email: string
  fullName: string | null
  addedAt: string
}

export interface InviteCodeResponse {
  id: string
  code: string
  expiresAt: string | null
  active: boolean
  createdAt: string
}

export interface AdminUserResponse {
  id: string
  email: string
  role: UserRole
  accountStatus: AccountStatus
  fullName: string | null
  createdAt: string
}

export interface CreateTeacherResponse {
  userId: string
  email: string
  fullName: string
  temporaryPassword: string
}

export interface ApiErrorBody {
  code: string
  message: string
}
