import { useState } from 'react'
import { askAiTutor, type AiTutorReply } from '@/api/aiTutor'

export default function AiTutorPanel({ courseId, lessonId, timestampSeconds, onSeek }: {
  courseId: string
  lessonId: string
  timestampSeconds: number
  onSeek: (videoId: string | null, seconds: number) => void
}) {
  const [question, setQuestion] = useState('')
  const [reply, setReply] = useState<AiTutorReply | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [locale, setLocale] = useState<'vi-VN' | 'en-US'>(() =>
    localStorage.getItem('ai_tutor_locale') === 'en-US' ? 'en-US' : 'vi-VN'
  )
  const english = locale === 'en-US'

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      setReply(await askAiTutor({
        conversationId: reply?.conversationId,
        courseId,
        lessonId,
        timestampSeconds,
        question: question.trim(),
        locale,
      }))
      setQuestion('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không gửi được câu hỏi.')
    } finally {
      setBusy(false)
    }
  }

  const labels = english ? {
    region: 'AI learning tutor', title: 'Ask AI at', guidance: 'Socratic guidance based on this lesson; it will not do your work.', remaining: 'questions left today',
    question: 'Question for the AI tutor', placeholder: 'What would you like to understand in this section?', count: 'characters · Up to 20 questions/day', loading: 'Searching…', ask: 'Ask AI',
    fallback: 'AI is temporarily unavailable. These lesson excerpts may help.', jump: 'Jump to', genericError: 'Could not send the question.',
  } : {
    region: 'Trợ lý học tập AI', title: 'Hỏi AI theo mốc', guidance: 'Gợi mở theo học liệu của bài học, không làm bài thay bạn.', remaining: 'lượt hôm nay',
    question: 'Câu hỏi cho AI Tutor', placeholder: 'Bạn muốn hiểu rõ ý nào trong đoạn này?', count: 'ký tự · Tối đa 20 lượt/ngày', loading: 'Đang tìm…', ask: 'Hỏi AI',
    fallback: 'AI đang tạm bận. Đây là các đoạn học liệu liên quan.', jump: 'Nhảy tới', genericError: 'Không gửi được câu hỏi.',
  }

  return <section className="space-y-3 rounded-xl border border-teal-200 bg-teal-50/50 p-4" aria-label={labels.region}>
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 className="font-semibold text-slate-900">{labels.title} {Math.floor(timestampSeconds / 60)}:{String(timestampSeconds % 60).padStart(2, '0')}</h3>
        <p className="text-xs text-slate-600">{labels.guidance}</p>
      </div>
      <div className="flex items-center gap-2">
        <div className="inline-flex rounded-lg border border-teal-200 bg-white p-0.5" aria-label="Language">
          <button type="button" onClick={() => { setLocale('vi-VN'); localStorage.setItem('ai_tutor_locale', 'vi-VN') }} aria-pressed={!english} className={`rounded-md px-2 py-1 text-xs ${!english ? 'bg-teal-700 text-white' : 'text-slate-600'}`}>VI</button>
          <button type="button" onClick={() => { setLocale('en-US'); localStorage.setItem('ai_tutor_locale', 'en-US') }} aria-pressed={english} className={`rounded-md px-2 py-1 text-xs ${english ? 'bg-teal-700 text-white' : 'text-slate-600'}`}>EN</button>
        </div>
        {reply && <span className="text-xs text-slate-600">{reply.remainingDailyQuestions} {labels.remaining}</span>}
      </div>
    </div>
    <form onSubmit={submit} className="space-y-2">
      <label className="sr-only" htmlFor="ai-tutor-question">{labels.question}</label>
      <textarea id="ai-tutor-question" required maxLength={500} rows={3} value={question}
        onChange={(event) => setQuestion(event.target.value)}
        className="w-full rounded-lg border border-slate-300 bg-white p-3 text-sm focus:border-teal-700 focus:outline-none focus:ring-2 focus:ring-teal-100"
        placeholder={labels.placeholder} />
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-slate-500">{question.length}/500 {labels.count}</span>
        <button disabled={busy || !question.trim()} className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-60">
          {busy ? labels.loading : labels.ask}
        </button>
      </div>
    </form>
    {error && <p role="alert" className="rounded bg-rose-50 p-3 text-sm text-rose-800">{error === 'Không gửi được câu hỏi.' || error === 'Could not send the question.' ? labels.genericError : error}</p>}
    {reply && <div aria-live="polite" className="space-y-3 rounded-lg bg-white p-4">
      {reply.retrievalOnly && <p className="rounded bg-amber-50 p-2 text-sm text-amber-900">{labels.fallback}</p>}
      <p className="whitespace-pre-wrap text-sm leading-6 text-slate-800">{reply.answer}</p>
      {reply.citations.map((citation, index) => <article key={`${citation.chunkId || citation.contentId}-${index}`} className="border-l-2 border-teal-600 pl-3 text-sm">
        <p className="font-semibold text-slate-800">{citation.title}</p>
        <p className="mt-1 text-slate-600">{citation.excerpt}</p>
        {citation.timestampSeconds > 0 && <button type="button" className="mt-1 font-medium text-teal-800 underline"
          onClick={() => onSeek(citation.videoId, citation.timestampSeconds)}>{labels.jump} {Math.floor(citation.timestampSeconds / 60)}:{String(citation.timestampSeconds % 60).padStart(2, '0')}</button>}
      </article>)}
    </div>}
  </section>
}
