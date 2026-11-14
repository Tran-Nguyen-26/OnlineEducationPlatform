export interface AiCitation {
  chunkId: string | null
  contentId: string
  videoId: string | null
  title: string
  excerpt: string
  timestampSeconds: number
}

export interface AiTutorReply {
  conversationId: string
  answer: string
  citations: AiCitation[]
  retrievalOnly: boolean
  refused: boolean
  remainingDailyQuestions: number
}

export async function askAiTutor(input: {
  conversationId?: string
  courseId: string
  lessonId: string
  timestampSeconds: number
  question: string
  locale?: 'vi-VN' | 'en-US'
}): Promise<AiTutorReply> {
  const token = localStorage.getItem('access_token')
  const response = await fetch('/api/v1/ai/tutor/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(input),
  })
  const data = await response.json().catch(() => null) as (AiTutorReply & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Chưa thể gửi câu hỏi cho trợ lý AI.')
  return data as AiTutorReply
}
