import { useEffect, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchTeachingCourses, fetchQuestionBanks, type TeachingCourse, type QuestionBankItem } from '@/api/quiz'
import {
  approveAiDraft, editApproveAiDraft, generateAiDrafts, listAiDrafts, rejectAiDraft,
  type AiDraftQuestion,
} from '@/api/aiQuizDrafts'

type CurriculumOutcome = { id: string; code: string; description: string; bloom: string }
type SelectItem = { id: string; title: string }

function headers() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
}

async function listItems(path: string): Promise<SelectItem[]> {
  const response = await fetch(`${path}?page=1&page_size=100`, { headers: headers() })
  if (!response.ok) throw new Error('Không tải được cấu trúc khóa học.')
  const data = await response.json() as { items?: SelectItem[] }
  return data.items ?? []
}

export default function AdminAiQuizPage() {
  const courses = useQuery({ queryKey: ['ai-draft-courses'], queryFn: fetchTeachingCourses })
  const [courseId, setCourseId] = useState('')
  const [moduleId, setModuleId] = useState('')
  const [lessonId, setLessonId] = useState('')
  const [outcomeId, setOutcomeId] = useState('')
  const [bankId, setBankId] = useState('')
  const [bloomLevel, setBloomLevel] = useState('UNDERSTAND')
  const [count, setCount] = useState(3)
  const [drafts, setDrafts] = useState<AiDraftQuestion[]>([])
  const [edits, setEdits] = useState<Record<string, AiDraftQuestion>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const modules = useQuery({ queryKey: ['ai-draft-modules', courseId], queryFn: () => listItems(`/api/v1/courses/${courseId}/modules`), enabled: !!courseId })
  const lessons = useQuery({ queryKey: ['ai-draft-lessons', moduleId], queryFn: () => listItems(`/api/v1/courses/${courseId}/modules/${moduleId}/lessons`), enabled: !!courseId && !!moduleId })
  const outcomes = useQuery({
    queryKey: ['ai-draft-outcomes', courseId], enabled: !!courseId,
    queryFn: async () => {
      const response = await fetch(`/api/v1/courses/${courseId}/curriculum`, { headers: headers() })
      if (!response.ok) throw new Error('Không tải được chuẩn đầu ra.')
      return (await response.json() as { outcomes: CurriculumOutcome[] }).outcomes
    },
  })
  const banks = useQuery({ queryKey: ['ai-draft-banks', courseId], queryFn: () => fetchQuestionBanks(courseId, 1, 100), enabled: !!courseId })

  useEffect(() => {
    if (!courseId) { setDrafts([]); return }
    void listAiDrafts(courseId).then(setDrafts).catch((cause) => setError(cause instanceof Error ? cause.message : 'Không tải được câu hỏi nháp.'))
  }, [courseId])

  async function refreshDrafts() {
    if (courseId) setDrafts(await listAiDrafts(courseId))
  }

  async function generate(event: FormEvent) {
    event.preventDefault()
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await generateAiDrafts({ courseId, lessonId, learningOutcomeId: outcomeId,
        questionBankId: bankId || undefined, bloomLevel, count })
      setDrafts((current) => [...result.drafts, ...current])
      setNotice(`Đã tạo ${result.createdCount} câu hỏi nháp trong ngân hàng đã chọn.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không sinh được câu hỏi.')
    } finally { setBusy(false) }
  }

  function editDraft(id: string, update: (draft: AiDraftQuestion) => AiDraftQuestion) {
    const current = edits[id] ?? drafts.find((draft) => draft.id === id)
    if (current) setEdits((previous) => ({ ...previous, [id]: update(current) }))
  }

  async function review(id: string, action: 'approve' | 'reject' | 'edit') {
    const draft = edits[id] ?? drafts.find((item) => item.id === id)
    if (!draft) return
    setBusy(true); setError(''); setNotice('')
    try {
      if (action === 'approve') await approveAiDraft(id)
      else if (action === 'reject') await rejectAiDraft(id)
      else await editApproveAiDraft(id, { question: draft.question, options: draft.options.map((option) => ({
        text: option.text, correct: option.correct, explanation: option.explanation,
      })) })
      setDrafts((current) => current.filter((item) => item.id !== id))
      setEdits((current) => { const next = { ...current }; delete next[id]; return next })
      setNotice(action === 'reject' ? 'Đã loại câu hỏi nháp.' : 'Câu hỏi đã được duyệt và chuyển vào ngân hàng chính thức.')
      await refreshDrafts()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không lưu được quyết định duyệt.')
    } finally { setBusy(false) }
  }

  const selectedCourse = (courses.data ?? []).find((course: TeachingCourse) => course.id === courseId)
  const bankItems: QuestionBankItem[] = banks.data?.items ?? []

  return <main className="space-y-6">
    <header>
      <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Admin · AI Quiz</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-900">Sinh và duyệt câu hỏi AI</h1>
      <p className="mt-2 text-sm text-slate-600">Câu hỏi sinh ra luôn ở trạng thái nháp. Chỉ Admin có thể duyệt, chỉnh sửa và duyệt, hoặc loại bỏ.</p>
    </header>

    <form onSubmit={generate} className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5 md:grid-cols-2 xl:grid-cols-3">
      <label className="grid gap-1 text-sm">Khóa học
        <select required value={courseId} onChange={(event) => { setCourseId(event.target.value); setModuleId(''); setLessonId(''); setOutcomeId(''); setBankId('') }} className="rounded-lg border px-3 py-2">
          <option value="">Chọn khóa học</option>{(courses.data ?? []).map((course) => <option key={course.id} value={course.id}>{course.title}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm">Chương
        <select required value={moduleId} onChange={(event) => { setModuleId(event.target.value); setLessonId('') }} className="rounded-lg border px-3 py-2" disabled={!courseId}>
          <option value="">Chọn chương</option>{(modules.data ?? []).map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm">Bài học nguồn
        <select required value={lessonId} onChange={(event) => setLessonId(event.target.value)} className="rounded-lg border px-3 py-2" disabled={!moduleId}>
          <option value="">Chọn bài học</option>{(lessons.data ?? []).map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm">Chuẩn đầu ra
        <select required value={outcomeId} onChange={(event) => setOutcomeId(event.target.value)} className="rounded-lg border px-3 py-2" disabled={!courseId}>
          <option value="">Chọn LO</option>{(outcomes.data ?? []).map((item) => <option key={item.id} value={item.id}>{item.code} · {item.description}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm">Mức Bloom
        <select value={bloomLevel} onChange={(event) => setBloomLevel(event.target.value)} className="rounded-lg border px-3 py-2">
          <option value="REMEMBER">Remember</option><option value="UNDERSTAND">Understand</option><option value="APPLY">Apply</option>
        </select>
      </label>
      <label className="grid gap-1 text-sm">Ngân hàng câu hỏi
        <select value={bankId} onChange={(event) => setBankId(event.target.value)} className="rounded-lg border px-3 py-2">
          <option value="">Tạo/chọn ngân hàng nháp AI</option>{bankItems.map((bank) => <option key={bank.id} value={bank.id}>{bank.name}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-sm">Số câu hỏi (1–10)
        <input type="number" min={1} max={10} value={count} onChange={(event) => setCount(Number(event.target.value))} className="rounded-lg border px-3 py-2" />
      </label>
      <div className="flex items-end"><button disabled={busy || !courseId || !lessonId || !outcomeId} className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{busy ? 'Đang xử lý…' : 'Sinh câu hỏi nháp'}</button></div>
    </form>

    {selectedCourse && <h2 className="text-lg font-semibold text-slate-900">Câu hỏi đang chờ duyệt · {selectedCourse.title}</h2>}
    {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
    {drafts.length === 0 ? <p className="rounded-xl border border-dashed p-8 text-center text-sm text-slate-500">Không có câu hỏi AI nào đang chờ duyệt cho khóa học này.</p> :
      <div className="space-y-4">{drafts.map((baseDraft) => {
        const draft = edits[baseDraft.id] ?? baseDraft
        return <article key={draft.id} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
          <div className="flex flex-wrap justify-between gap-2 text-xs text-slate-500"><span>{draft.learningOutcomeCode} · Bloom {draft.bloomLevel}</span><span>Nháp AI · Chưa được học viên sử dụng</span></div>
          <label className="grid gap-1 text-sm font-semibold">Câu hỏi
            <textarea value={draft.question} onChange={(event) => editDraft(draft.id, (item) => ({ ...item, question: event.target.value }))} rows={2} className="rounded-lg border px-3 py-2 font-normal" />
          </label>
          <div className="grid gap-3 md:grid-cols-2">{draft.options.map((option, index) => <div key={option.id} className="space-y-2 rounded-lg border p-3">
            <label className="grid gap-1 text-sm">Phương án {index + 1}
              <textarea value={option.text} onChange={(event) => editDraft(draft.id, (item) => ({ ...item, options: item.options.map((entry, i) => i === index ? { ...entry, text: event.target.value } : entry) }))} rows={2} className="rounded border px-2 py-1.5 font-normal" />
            </label>
            <label className="flex items-center gap-2 text-sm"><input type="radio" name={`correct-${draft.id}`} checked={option.correct} onChange={() => editDraft(draft.id, (item) => ({ ...item, options: item.options.map((entry, i) => ({ ...entry, correct: i === index })) }))} />Đáp án đúng</label>
            {!option.correct && <label className="grid gap-1 text-xs text-slate-600">Giải thích phương án nhiễu
              <textarea value={option.explanation} onChange={(event) => editDraft(draft.id, (item) => ({ ...item, options: item.options.map((entry, i) => i === index ? { ...entry, explanation: event.target.value } : entry) }))} rows={2} className="rounded border px-2 py-1.5 text-sm" />
            </label>}
          </div>)}</div>
          <div className="flex flex-wrap justify-end gap-2">
            <button type="button" disabled={busy} onClick={() => void review(draft.id, 'reject')} className="rounded-lg border border-rose-300 px-3 py-2 text-sm text-rose-700 disabled:opacity-50">Loại bỏ</button>
            <button type="button" disabled={busy} onClick={() => void review(draft.id, 'approve')} className="rounded-lg border border-teal-700 px-3 py-2 text-sm text-teal-800 disabled:opacity-50">Duyệt</button>
            <button type="button" disabled={busy} onClick={() => void review(draft.id, 'edit')} className="rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Chỉnh sửa và duyệt</button>
          </div>
        </article>
      })}</div>}
    <p className="text-xs text-slate-500">{courses.isError || modules.isError || lessons.isError || outcomes.isError || banks.isError ? 'Một số dữ liệu quản trị chưa tải được.' : ''}</p>
  </main>
}
