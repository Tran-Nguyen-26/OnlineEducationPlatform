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

export interface ModuleResponse {
  id: string
  courseId: string
  title: string
  description: string | null
  position: number
  isDefault: boolean
}

export interface LessonResponse {
  id: string
  moduleId: string
  title: string
  description: string | null
  position: number
  required: boolean
}

export type ContentType = 'TEXT' | 'LINK' | 'VIDEO' | 'PDF' | 'SLIDE' | 'DOCUMENT' | 'FILE'
export type UploadStatus = 'PENDING' | 'UPLOADED' | 'AVAILABLE' | 'FAILED'

export interface LessonContentResponse {
  id: string
  lessonId: string
  contentType: ContentType
  title: string | null
  textContent: string | null
  externalUrl: string | null
  fileName: string | null
  mimeType: string | null
  fileSize: number | null
  durationSeconds: number | null
  uploadStatus: UploadStatus
  position: number
}

export interface UploadUrlResponse {
  uploadUrl: string
  objectKey: string
  expiresInSeconds: number
}

export type QuizGradingPolicy = 'HIGHEST' | 'LATEST' | 'AVERAGE'

export interface QuizResponse {
  id: string
  courseId: string
  lessonId: string | null
  title: string
  description: string | null
  timeLimitSeconds: number | null
  deadline: string | null
  maxAttempts: number
  gradingPolicy: QuizGradingPolicy
  randomizeQuestions: boolean
  randomizeAnswers: boolean
  showScore: boolean
  showCorrectAnswers: boolean
  locked: boolean
  questionCount: number
}

export type QuestionType = 'TRUE_FALSE' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'KEYWORD_FILL'

export interface OptionResponse {
  id: string
  optionText: string
  correct: boolean
  position: number
}

export interface QuizQuestionResponse {
  questionId: string
  questionType: QuestionType
  questionText: string
  keywordAnswer: string | null
  position: number
  points: number
  options: OptionResponse[]
}

export interface AssignmentResponse {
  id: string
  courseId: string
  lessonId: string | null
  title: string
  instructions: string | null
  deadline: string | null
  maxSubmissions: number
  allowLateSubmission: boolean
  allowText: boolean
  allowFiles: boolean
  maxScore: number
  createdByEmail: string | null
  createdAt: string
}
