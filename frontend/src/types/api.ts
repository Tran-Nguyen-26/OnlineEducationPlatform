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

export interface NotificationResponse {
  id: string
  eventType: string
  title: string
  message: string
  targetType: string | null
  targetId: string | null
  read: boolean
  createdAt: string
}

export interface AdminDashboardStatsResponse {
  totalSubjects: number
  totalCourses: number
  draftCourses: number
  publishedCourses: number
  archivedCourses: number
  disabledCourses: number
  totalTeachers: number
  totalStudents: number
  totalEnrollments: number
  activeEnrollments: number
  completedEnrollments: number
  withdrawnEnrollments: number
  removedEnrollments: number
  completionRatePercent: number
}

export interface MyProgressResponse {
  enrollmentId: string
  courseId: string
  courseTitle: string
  subjectName: string
  status: string
  totalLessons: number
  completedLessons: number
  percent: number
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

// ---- Student-facing ----

export interface StudentCourseResponse {
  id: string
  title: string
  description: string | null
  subjectName: string
  capacity: number | null
  learningMode: LearningMode
  enrolled: boolean
}

export type EnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'WITHDRAWN' | 'REMOVED'

export interface MyEnrollmentResponse {
  enrollmentId: string
  courseId: string
  courseTitle: string
  subjectName: string
  status: EnrollmentStatus
  enrolledAt: string
  completedAt: string | null
  withdrawnAt: string | null
  removedAt: string | null
}

export interface AdminEnrollmentResponse {
  enrollmentId: string
  studentId: string
  studentEmail: string
  studentFullName: string | null
  status: EnrollmentStatus
  enrolledAt: string
  completedAt: string | null
  withdrawnAt: string | null
  removedAt: string | null
}

export type LessonProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export interface OutlineContent {
  id: string
  contentType: ContentType
  title: string | null
  textContent: string | null
  externalUrl: string | null
  fileName: string | null
  mimeType: string | null
  durationSeconds: number | null
  uploadStatus: UploadStatus
}

export interface OutlineLesson {
  id: string
  title: string
  description: string | null
  position: number
  required: boolean
  progressStatus: LessonProgressStatus
  contents: OutlineContent[]
}

export interface OutlineModule {
  id: string
  title: string
  description: string | null
  position: number
  lessons: OutlineLesson[]
}

export interface CourseOutlineResponse {
  courseId: string
  title: string
  modules: OutlineModule[]
}

export interface ViewUrlResponse {
  viewUrl: string
  expiresInSeconds: number
}

export interface StudentQuizResponse {
  id: string
  courseId: string
  lessonId: string | null
  title: string
  description: string | null
  timeLimitSeconds: number | null
  deadline: string | null
  maxAttempts: number
  gradingPolicy: QuizGradingPolicy
  locked: boolean
  attemptsUsed: number
  canAttempt: boolean
  bestScore: number | null
}

export interface QuestionForAttempt {
  questionId: string
  questionType: QuestionType
  questionText: string
  position: number
  points: number
  options: { id: string; optionText: string; position: number }[]
}

export interface StartAttemptResponse {
  attemptId: string
  attemptNumber: number
  startedAt: string
  timeLimitSeconds: number | null
  questions: QuestionForAttempt[]
}

export type QuizAttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'TIMED_OUT' | 'GRADED'

export interface AttemptSummaryResponse {
  attemptId: string
  attemptNumber: number
  status: QuizAttemptStatus
  submittedAt: string | null
  score: number | null
}

export interface AttemptResultResponse {
  attemptId: string
  attemptNumber: number
  status: QuizAttemptStatus
  submittedAt: string
  score: number | null
  questionResults: { questionId: string; correct: boolean | null; awardedScore: number | null }[]
}

export interface StudentAssignmentResponse {
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
  submissionCount: number
  canSubmit: boolean
  graded: boolean
  score: number | null
}

export interface SubmissionFileResponse {
  id: string
  fileName: string
  fileType: string | null
  fileSize: number
  uploadStatus: UploadStatus
}

export type AssignmentSubmissionStatus = 'SUBMITTED' | 'PENDING_REGRADE' | 'GRADED'

export interface MySubmissionResponse {
  id: string
  textContent: string | null
  submissionCount: number
  status: AssignmentSubmissionStatus
  submittedAt: string
  late: boolean
  files: SubmissionFileResponse[]
  score: number | null
  feedback: string | null
}

// ---- Teacher-facing ----

export interface InstructorCourseResponse {
  id: string
  title: string
  description: string | null
  subjectName: string
  lifecycleStatus: CourseLifecycleStatus
}

export interface ContentItem {
  id: string
  contentType: ContentType
  title: string | null
  textContent: string | null
  externalUrl: string | null
  fileName: string | null
  uploadStatus: UploadStatus
}

export interface ContentLesson {
  id: string
  title: string
  description: string | null
  position: number
  required: boolean
  contents: ContentItem[]
}

export interface ContentModule {
  id: string
  title: string
  description: string | null
  position: number
  lessons: ContentLesson[]
}

export interface CourseContentResponse {
  courseId: string
  title: string
  modules: ContentModule[]
}

export interface TeacherAssignmentResponse {
  id: string
  title: string
  instructions: string | null
  deadline: string | null
  maxScore: number
  submissionCount: number
}

export interface SubmissionForGradingResponse {
  submissionId: string
  studentId: string
  studentName: string
  studentEmail: string
  textContent: string | null
  submissionCount: number
  status: AssignmentSubmissionStatus
  submittedAt: string
  late: boolean
  files: SubmissionFileResponse[]
  score: number | null
  feedback: string | null
}

// ---- Shared (Q&A) ----

export interface MessageResponse {
  id: string
  senderId: string
  senderName: string
  senderRole: UserRole
  content: string
  createdAt: string
}
