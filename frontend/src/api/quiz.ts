export interface PageInfo {
  page: number
  page_size: number
  total: number
  has_next: boolean
}

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER'
export type QuizGradingPolicy = 'HIGHEST' | 'LATEST' | 'AVERAGE'

export interface QuizItem {
  id: string
  course_id: string
  lesson_id?: string | null
  title: string
  description?: string | null
  question_source?: string | null
  time_limit_seconds?: number | null
  deadline?: string | null
  max_attempts: number
  grading_policy: QuizGradingPolicy
  randomize_questions?: boolean | null
  randomize_answers?: boolean | null
  show_score?: boolean | null
  show_correct_answers?: boolean | null
  is_locked: boolean
  created_by?: string | null
  created_at: string
  updated_at: string
}

export interface QuizPageResponse {
  items: QuizItem[]
  pagination: PageInfo
}

export interface CreateQuizPayload {
  title: string
  description?: string
  lesson_id?: string | null
  question_source?: string
  time_limit_seconds?: number | null
  deadline?: string | null
  max_attempts?: number
  grading_policy?: QuizGradingPolicy
  randomize_questions?: boolean
  randomize_answers?: boolean
  show_score?: boolean
  show_correct_answers?: boolean
}

export interface UpdateQuizPayload {
  title?: string
  description?: string
  lesson_id?: string | null
  question_source?: string
  time_limit_seconds?: number | null
  deadline?: string | null
  max_attempts?: number
  grading_policy?: QuizGradingPolicy
  randomize_questions?: boolean
  randomize_answers?: boolean
  show_score?: boolean
  show_correct_answers?: boolean
}

export interface QuestionOptionItem {
  id: string
  option_text: string
  is_correct: boolean
  position: number
}

export interface OptionPayload {
  option_text: string
  is_correct: boolean
  position?: number
}

export interface QuestionItem {
  id: string
  question_bank_id?: string | null
  question_type: QuestionType
  question_text: string
  keyword_answer?: string | null
  points: number
  position: number
  options: QuestionOptionItem[]
  created_by?: string | null
  created_at: string
  updated_at: string
}

export interface QuestionPageResponse {
  items: QuestionItem[]
  pagination: PageInfo
}

export interface CreateQuestionPayload {
  question_type: QuestionType
  question_text: string
  keyword_answer?: string | null
  question_bank_id?: string | null
  points: number
  position?: number
  options: OptionPayload[]
}

export interface UpdateQuestionPayload {
  question_type?: QuestionType
  question_text?: string
  keyword_answer?: string | null
  question_bank_id?: string | null
  points?: number
  position?: number
  options?: OptionPayload[]
}

export interface QuestionBankItem {
  id: string
  course_id: string
  name: string
  created_by?: string | null
  created_at: string
}

export interface QuestionBankPageResponse {
  items: QuestionBankItem[]
  pagination: PageInfo
}

export interface QuizResultItem {
  id: string
  attempt_id: string
  auto_score: number
  final_score: number
  adjusted_by?: string | null
  adjustment_reason?: string | null
  created_at: string
  updated_at: string
}

export interface QuizResultPageResponse {
  items: QuizResultItem[]
  pagination: PageInfo
}

export interface AdminQuizSummary {
  id: string
  course_id: string
  course_title: string
  title: string
  is_locked: boolean
  total_attempts: number
  created_at: string
}

export interface AdminQuizPageResponse {
  items: AdminQuizSummary[]
  pagination: PageInfo
}

export interface TeachingCourse {
  id: string
  title: string
  description?: string
  visibility: string
  lifecycle_status: string
}

function getAuthHeader(): Record<string, string> {
  const token = localStorage.getItem('access_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = {
    'Content-Type': 'application/json',
    ...getAuthHeader(),
    ...options.headers,
  }

  const response = await fetch(url, { ...options, headers })
  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!response.ok) {
    const errData = data as { message?: string; error?: string } | null
    const errorMsg =
      errData?.message ||
      errData?.error ||
      `Request failed with status ${response.status}: ${response.statusText}`
    throw new Error(errorMsg)
  }

  return data as T
}

// ── Course Selector Helper ──
export async function fetchTeachingCourses(): Promise<TeachingCourse[]> {
  try {
    const res = await request<{ items: TeachingCourse[] }>(
      '/api/v1/my/courses?page=1&page_size=100'
    )
    return res.items || []
  } catch {
    // Fallback: public courses list if not an instructor course link
    const fallback = await request<{ items: TeachingCourse[] }>(
      '/api/v1/courses?page=1&page_size=100'
    )
    return fallback.items || []
  }
}

// ── Quiz CRUD ──
export async function fetchQuizzes(
  courseId: string,
  page = 1,
  pageSize = 20
): Promise<QuizPageResponse> {
  return request<QuizPageResponse>(
    `/api/v1/courses/${courseId}/quizzes?page=${page}&page_size=${pageSize}`
  )
}

export async function fetchQuiz(courseId: string, quizId: string): Promise<QuizItem> {
  return request<QuizItem>(`/api/v1/courses/${courseId}/quizzes/${quizId}`)
}

export async function createQuiz(courseId: string, payload: CreateQuizPayload): Promise<QuizItem> {
  return request<QuizItem>(`/api/v1/courses/${courseId}/quizzes`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function updateQuiz(
  courseId: string,
  quizId: string,
  payload: UpdateQuizPayload
): Promise<QuizItem> {
  return request<QuizItem>(`/api/v1/courses/${courseId}/quizzes/${quizId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export async function deleteQuiz(
  courseId: string,
  quizId: string
): Promise<{ success: boolean; deleted_id: string }> {
  return request<{ success: boolean; deleted_id: string }>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}`,
    {
      method: 'DELETE',
    }
  )
}

// ── Question Bank CRUD ──
export async function fetchQuestionBanks(
  courseId: string,
  page = 1,
  pageSize = 50
): Promise<QuestionBankPageResponse> {
  return request<QuestionBankPageResponse>(
    `/api/v1/courses/${courseId}/question-banks?page=${page}&page_size=${pageSize}`
  )
}

export async function createQuestionBank(
  courseId: string,
  name: string
): Promise<QuestionBankItem> {
  return request<QuestionBankItem>(`/api/v1/courses/${courseId}/question-banks`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export async function deleteQuestionBank(
  courseId: string,
  bankId: string
): Promise<{ success: boolean; deleted_id: string }> {
  return request<{ success: boolean; deleted_id: string }>(
    `/api/v1/courses/${courseId}/question-banks/${bankId}`,
    {
      method: 'DELETE',
    }
  )
}

// ── Question & Options CRUD ──
export async function fetchQuestions(
  courseId: string,
  quizId: string,
  page = 1,
  pageSize = 50
): Promise<QuestionPageResponse> {
  return request<QuestionPageResponse>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/questions?page=${page}&page_size=${pageSize}`
  )
}

export async function createQuestion(
  courseId: string,
  quizId: string,
  payload: CreateQuestionPayload
): Promise<QuestionItem> {
  return request<QuestionItem>(`/api/v1/courses/${courseId}/quizzes/${quizId}/questions`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function updateQuestion(
  courseId: string,
  quizId: string,
  questionId: string,
  payload: UpdateQuestionPayload
): Promise<QuestionItem> {
  return request<QuestionItem>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/questions/${questionId}`,
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }
  )
}

export async function deleteQuestion(
  courseId: string,
  quizId: string,
  questionId: string
): Promise<{ success: boolean; deleted_id: string }> {
  return request<{ success: boolean; deleted_id: string }>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/questions/${questionId}`,
    {
      method: 'DELETE',
    }
  )
}

export async function reorderQuestions(
  courseId: string,
  quizId: string,
  orderedIds: string[]
): Promise<{ success: boolean; reordered_count: number }> {
  return request<{ success: boolean; reordered_count: number }>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/questions/reorder`,
    {
      method: 'POST',
      body: JSON.stringify({ ordered_ids: orderedIds }),
    }
  )
}

// ── Results & Score Adjustment ──
export async function fetchQuizResults(
  courseId: string,
  quizId: string,
  page = 1,
  pageSize = 20
): Promise<QuizResultPageResponse> {
  return request<QuizResultPageResponse>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/results?page=${page}&page_size=${pageSize}`
  )
}

export async function adjustScore(
  courseId: string,
  quizId: string,
  resultId: string,
  finalScore: number,
  adjustmentReason: string
): Promise<QuizResultItem> {
  return request<QuizResultItem>(
    `/api/v1/courses/${courseId}/quizzes/${quizId}/results/${resultId}/adjust-score`,
    {
      method: 'POST',
      body: JSON.stringify({
        final_score: finalScore,
        adjustment_reason: adjustmentReason,
      }),
    }
  )
}

// ── Admin Overview ──
export async function adminFetchQuizzes(
  courseIdFilter?: string,
  page = 1,
  pageSize = 20
): Promise<AdminQuizPageResponse> {
  const query = new URLSearchParams()
  query.set('page', String(page))
  query.set('page_size', String(pageSize))
  if (courseIdFilter) {
    query.set('course_id', courseIdFilter)
  }
  return request<AdminQuizPageResponse>(`/api/v1/admin/quizzes?${query.toString()}`)
}
