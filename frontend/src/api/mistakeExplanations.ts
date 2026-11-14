export interface MistakeExplanation {
  questionId: string
  optionId: string
  selectedOptionCorrect: boolean
  explanation: string
  cached: boolean
}

export async function fetchMistakeExplanation(courseId: string, attemptId: string, questionId: string, optionId: string) {
  const response = await fetch(`/api/v1/courses/${courseId}/learner-assessments/attempts/${attemptId}/questions/${questionId}/options/${optionId}/mistake-explanation`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}`,
    },
  })
  const data = await response.json().catch(() => null) as (MistakeExplanation & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Không tải được giải thích lỗi sai.')
  return data as MistakeExplanation
}
