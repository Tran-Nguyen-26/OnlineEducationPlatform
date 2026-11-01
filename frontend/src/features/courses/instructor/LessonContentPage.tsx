import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, FileText, Link2, Save, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { courseApi, type Content } from './courseApi'

export default function LessonContentPage() {
  const { courseId = '', lessonId = '' } = useParams()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Content | null>(null)
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [url, setUrl] = useState('')
  const [kind, setKind] = useState<'TEXT' | 'LINK'>('TEXT')
  const [dirty, setDirty] = useState(false)
  const [message, setMessage] = useState('')
  const [draggedContentId, setDraggedContentId] = useState<string | null>(null)
  const route = useQuery({ queryKey: ['lesson-route', courseId, lessonId], queryFn: () => courseApi.modules(courseId).then(async (page) => {
    for (const module of page.items) {
      const lessons = await courseApi.lessons(courseId, module.id)
      const lesson = lessons.items.find((item) => item.id === lessonId)
      if (lesson) return { module, lesson }
    }
    throw new Error('Không tìm thấy bài học')
  }) })
  const moduleId = route.data?.module.id ?? ''
  const contentQuery = useQuery({ queryKey: ['lesson-contents', courseId, moduleId, lessonId], queryFn: () => courseApi.contents(courseId, moduleId, lessonId), enabled: Boolean(moduleId) })
  const contentKey = ['lesson-contents', courseId, moduleId, lessonId]
  const save = useMutation({
    mutationFn: () => selected
      ? courseApi.updateContent(courseId, moduleId, lessonId, selected.id, { title, ...(kind === 'TEXT' ? { text_content: text } : { external_url: url }) })
      : courseApi.createContent(courseId, moduleId, lessonId, { content_type: kind, title, ...(kind === 'TEXT' ? { text_content: text } : { external_url: url }) }),
    onSuccess: async (content) => { setSelected(content); setDirty(false); setMessage('Nội dung đã lưu'); await queryClient.invalidateQueries({ queryKey: contentKey }) },
    onError: (error) => setMessage(error instanceof Error ? error.message : 'Không lưu được nội dung'),
  })

  useEffect(() => {
    if (!dirty || !moduleId) return
    const timer = window.setTimeout(() => save.mutate(), 30_000)
    return () => window.clearTimeout(timer)
  }, [dirty, title, text, url, moduleId])

  const selectContent = (content: Content) => {
    setSelected(content)
    setKind(content.content_type === 'LINK' ? 'LINK' : 'TEXT')
    setTitle(content.title ?? '')
    setText(content.text_content ?? '')
    setUrl(content.external_url ?? '')
    setDirty(false)
  }
  const reorder = (index: number, delta: number) => {
    const items = [...(contentQuery.data?.items ?? [])]
    const target = index + delta
    ;[items[index], items[target]] = [items[target], items[index]]
    void courseApi.reorderContents(courseId, moduleId, lessonId, items.map((item) => item.id))
      .then(() => queryClient.invalidateQueries({ queryKey: contentKey }))
      .catch((error: unknown) => setMessage(error instanceof Error ? error.message : 'Không lưu được thứ tự'))
  }
  const dropContent = (targetId: string) => {
    if (!draggedContentId || draggedContentId === targetId) return
    const items = [...(contentQuery.data?.items ?? [])]
    const from = items.findIndex((item) => item.id === draggedContentId)
    const to = items.findIndex((item) => item.id === targetId)
    const [moved] = items.splice(from, 1)
    items.splice(to, 0, moved)
    setDraggedContentId(null)
    void courseApi.reorderContents(courseId, moduleId, lessonId, items.map((item) => item.id))
      .then(() => queryClient.invalidateQueries({ queryKey: contentKey }))
      .catch((error: unknown) => setMessage(error instanceof Error ? error.message : 'Không lưu được thứ tự'))
  }

  if (route.isLoading) return <div aria-label="Đang tải nội dung" className="mx-auto h-80 max-w-5xl animate-pulse rounded-2xl bg-slate-100" />
  if (!route.data) return <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">Không tải được bài học.</p>

  return <main className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[300px_1fr]">
    <aside className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4">
      <Link to={`/instructor/courses/${courseId}/builder`} className="text-sm font-semibold text-indigo-700">← Course builder</Link>
      <div><p className="mt-4 text-xs font-semibold uppercase tracking-wide text-slate-500">Đang soạn</p><h1 className="mt-1 text-lg font-bold">{route.data.lesson.title}</h1></div>
      <Link to={`/instructor/courses/${courseId}/lessons/${lessonId}/upload`} className="flex items-center gap-2 rounded-xl bg-indigo-50 px-3 py-2.5 font-semibold text-indigo-700"><Upload size={17} />Quản lý upload</Link>
      <div className="flex gap-2"><button onClick={() => { setSelected(null); setKind('TEXT'); setTitle(''); setText(''); setUrl(''); setDirty(true) }} className="flex-1 rounded-lg border border-slate-200 px-2 py-2 text-sm font-semibold">+ Văn bản</button><button onClick={() => { setSelected(null); setKind('LINK'); setTitle(''); setText(''); setUrl(''); setDirty(true) }} className="flex-1 rounded-lg border border-slate-200 px-2 py-2 text-sm font-semibold">+ Liên kết</button></div>
      <ul aria-label="Nội dung bài học" className="space-y-1">{(contentQuery.data?.items ?? []).map((content, index) => <li key={content.id} draggable onDragStart={() => setDraggedContentId(content.id)} onDragEnd={() => setDraggedContentId(null)} onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); dropContent(content.id) }} className="flex items-center gap-1"><button aria-label={`Đưa nội dung ${content.title ?? index + 1} lên`} disabled={index === 0} onClick={() => reorder(index, -1)} className="text-slate-400 disabled:opacity-30"><ArrowUp size={14} /></button><button aria-label={`Đưa nội dung ${content.title ?? index + 1} xuống`} disabled={index === (contentQuery.data?.items.length ?? 0) - 1} onClick={() => reorder(index, 1)} className="text-slate-400 disabled:opacity-30"><ArrowDown size={14} /></button><button onClick={() => selectContent(content)} className={`flex min-w-0 flex-1 items-center gap-2 rounded-lg p-2 text-left text-sm ${selected?.id === content.id ? 'bg-indigo-50 text-indigo-800' : 'hover:bg-slate-50'}`}>{content.content_type === 'LINK' ? <Link2 size={15} /> : <FileText size={15} />}<span className="truncate">{content.title || content.file_name || 'Chưa đặt tên'}</span></button></li>)}</ul>
    </aside>

    <section className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 md:p-7">
      <header className="flex items-start justify-between gap-3"><div><p className="text-sm font-semibold text-indigo-600">Soạn nội dung</p><h2 className="mt-1 text-2xl font-bold">{selected ? 'Chỉnh sửa nội dung' : 'Tạo nội dung mới'}</h2></div><button onClick={() => save.mutate()} disabled={save.isPending || !dirty} className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 font-semibold text-white disabled:opacity-50"><Save size={16} />Lưu</button></header>
      {message && <p role="status" className="rounded-xl bg-indigo-50 p-3 text-sm text-indigo-800">{message}</p>}
      <div className="grid gap-4"><label className="text-sm font-semibold text-slate-700">Tiêu đề<input value={title} onChange={(event) => { setTitle(event.target.value); setDirty(true) }} maxLength={255} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>
        <div className="flex gap-2" role="group" aria-label="Loại nội dung"><button onClick={() => { setKind('TEXT'); setDirty(true) }} aria-pressed={kind === 'TEXT'} className={`rounded-lg px-3 py-2 text-sm font-semibold ${kind === 'TEXT' ? 'bg-indigo-100 text-indigo-800' : 'bg-slate-100 text-slate-600'}`}>Văn bản</button><button onClick={() => { setKind('LINK'); setDirty(true) }} aria-pressed={kind === 'LINK'} className={`rounded-lg px-3 py-2 text-sm font-semibold ${kind === 'LINK' ? 'bg-indigo-100 text-indigo-800' : 'bg-slate-100 text-slate-600'}`}>Liên kết</button></div>
        {kind === 'TEXT' ? <label className="text-sm font-semibold text-slate-700">Nội dung<textarea value={text} onChange={(event) => { setText(event.target.value); setDirty(true) }} rows={14} className="mt-2 w-full rounded-xl border border-slate-300 p-3 font-normal" /></label> : <label className="text-sm font-semibold text-slate-700">Địa chỉ HTTPS<input type="url" required value={url} onChange={(event) => { setUrl(event.target.value); setDirty(true) }} placeholder="https://…" className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" /></label>}
      </div>
      <p className="text-xs text-slate-500">Bản nháp tự lưu sau 30 giây khi có thay đổi.</p>
    </section>
  </main>
}
