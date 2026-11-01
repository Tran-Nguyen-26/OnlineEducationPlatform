import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowDown,
  ArrowUp,
  BookOpen,
  Check,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  GripVertical,
  Pencil,
  Plus,
  Save,
  Send,
} from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { courseApi, type Course, type Lesson, type Module } from './courseApi'

export default function CourseBuilderPage() {
  const { courseId = '' } = useParams()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dirty, setDirty] = useState(false)
  const [showDetails, setShowDetails] = useState(false)
  const [notice, setNotice] = useState<{ text: string; error?: boolean } | null>(null)
  const [draggedModuleId, setDraggedModuleId] = useState<string | null>(null)

  const courseQuery = useQuery({
    queryKey: ['builder-course', courseId],
    queryFn: () => courseApi.list().then((page) => page.items.find((course) => course.id === courseId) as Course),
  })
  const modulesQuery = useQuery({ queryKey: ['builder-modules', courseId], queryFn: () => courseApi.modules(courseId) })
  const modules = modulesQuery.data?.items ?? []
  const lessonQueries = useQueries({
    queries: modules.map((module) => ({
      queryKey: ['builder-lessons', courseId, module.id],
      queryFn: () => courseApi.lessons(courseId, module.id),
    })),
  })
  const lessonCount = lessonQueries.reduce((count, query) => count + (query.data?.pagination.total ?? query.data?.items.length ?? 0), 0)

  useEffect(() => {
    if (!courseQuery.data) return
    setTitle(courseQuery.data.title)
    setDescription(courseQuery.data.description ?? '')
    setDirty(false)
  }, [courseQuery.data])

  const saveDraft = useMutation({
    mutationFn: () => courseApi.update(courseId, {
      title: title.trim(),
      description,
      visibility: courseQuery.data?.visibility ?? 'PUBLIC',
    }),
    onSuccess: async (course) => {
      setTitle(course.title)
      setDescription(course.description ?? '')
      setDirty(false)
      setNotice({ text: 'Bản nháp đã được lưu.' })
      queryClient.setQueryData(['builder-course', courseId], course)
      await queryClient.invalidateQueries({ queryKey: ['instructor-courses'] })
    },
    onError: (error) => setNotice({ text: error instanceof Error ? error.message : 'Không lưu được bản nháp.', error: true }),
  })

  useEffect(() => {
    if (!dirty) return
    const timer = window.setTimeout(() => saveDraft.mutate(), 30_000)
    return () => window.clearTimeout(timer)
  }, [dirty, title, description])

  const addModule = useMutation({
    mutationFn: () => courseApi.createModule(courseId, `Chương ${modules.length + 1}`),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['builder-modules', courseId] })
      setNotice({ text: 'Đã thêm chương.' })
    },
    onError: (error) => setNotice({ text: error instanceof Error ? error.message : 'Không tạo được chương.', error: true }),
  })

  const reorderModules = useMutation({
    mutationFn: (ordered: Module[]) => courseApi.reorderModules(courseId, ordered.map((module) => module.id)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['builder-modules', courseId] }),
    onError: async (error) => {
      setNotice({ text: error instanceof Error ? error.message : 'Không lưu được thứ tự chương.', error: true })
      await queryClient.invalidateQueries({ queryKey: ['builder-modules', courseId] })
    },
  })

  if (courseQuery.isLoading || modulesQuery.isLoading) {
    return <div aria-label="Đang tải course builder" className="mx-auto h-96 max-w-6xl animate-pulse rounded-xl bg-white" />
  }
  if (!courseQuery.data) {
    return <p role="alert" className="rounded-lg bg-rose-50 p-4 text-rose-700">Không tìm thấy khóa học.</p>
  }

  const course = courseQuery.data
  const statusLabel: Record<Course['lifecycle_status'], string> = {
    DRAFT: 'Bản nháp',
    PUBLISHED: 'Đã xuất bản',
    ARCHIVED: 'Đã lưu trữ',
    DISABLED: 'Đã tạm khóa',
  }
  const statusTone = course.lifecycle_status === 'PUBLISHED'
    ? 'bg-emerald-50 text-emerald-700'
    : course.lifecycle_status === 'DISABLED'
      ? 'bg-rose-50 text-rose-700'
      : 'bg-slate-100 text-slate-700'

  return (
    <main className="mx-auto max-w-6xl space-y-5 pb-28">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-[0.14em] text-slate-500">Course Studio <span className="px-1 text-[#8a6c00]">•</span> Course builder</p>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-extrabold uppercase tracking-wide text-[#191c1d] sm:text-[28px]">{title.trim() || 'Tạo khóa học'}</h1>
            <span className={`rounded px-2.5 py-1 text-xs font-semibold ${statusTone}`}>{statusLabel[course.lifecycle_status]}</span>
          </div>
        </div>
        <button
          onClick={() => setShowDetails((current) => !current)}
          aria-expanded={showDetails}
          className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
        >
          <Pencil size={15} />Thông tin khóa học
        </button>
      </header>

      <nav aria-label="Các bước tạo khóa học" className="rounded-lg border border-slate-200 bg-white p-3 sm:px-5">
        <ol className="grid grid-cols-3 gap-2">
          <li>
            <button onClick={() => setShowDetails(true)} className={`flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-xs font-bold sm:px-3 sm:text-sm ${showDetails ? 'bg-[#fff8dc] text-[#735c00]' : 'text-slate-600 hover:bg-slate-50'}`}>
              <span className="flex size-7 shrink-0 items-center justify-center rounded bg-slate-100"><Check size={14} /></span>
              <span><span className="hidden sm:inline">1. </span>Thông tin cơ bản</span>
            </button>
          </li>
          <li aria-current="step">
            <div className="flex h-full items-center gap-2 rounded-md border-b-[3px] border-[#fed136] bg-[#fffaf0] px-2 py-2 text-xs font-bold text-[#191c1d] sm:px-3 sm:text-sm">
              <span className="flex size-7 shrink-0 items-center justify-center rounded bg-[#fed136]">2</span>
              <span>Nội dung khóa học</span>
            </div>
          </li>
          <li>
            <Link to={`/instructor/courses/${courseId}/publish`} className="flex h-full items-center gap-2 rounded-md px-2 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50 sm:px-3 sm:text-sm">
              <span className="flex size-7 shrink-0 items-center justify-center rounded bg-slate-100">3</span>
              <span>Kiểm tra &amp; xuất bản</span>
            </Link>
          </li>
        </ol>
      </nav>

      {showDetails && (
        <section aria-label="Thông tin khóa học" className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 sm:p-5">
          <label className="block text-sm font-semibold text-slate-700">
            Tên khóa học
            <input value={title} maxLength={255} onChange={(event) => { setTitle(event.target.value); setDirty(true) }} className="mt-2 w-full rounded-md border border-slate-300 px-3 py-2.5 text-base font-normal outline-none transition focus:border-[#b89400] focus:ring-2 focus:ring-[#fed136]/30" />
          </label>
          <label className="block text-sm font-semibold text-slate-700">
            Mô tả
            <textarea value={description} onChange={(event) => { setDescription(event.target.value); setDirty(true) }} rows={2} className="mt-2 w-full resize-y rounded-md border border-slate-300 px-3 py-2.5 font-normal outline-none transition focus:border-[#b89400] focus:ring-2 focus:ring-[#fed136]/30" />
          </label>
        </section>
      )}

      {notice && <p role={notice.error ? 'alert' : 'status'} className={`rounded-md px-3 py-2 text-sm ${notice.error ? 'bg-rose-50 text-rose-700' : 'bg-white text-slate-600'}`}>{notice.text}</p>}

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_280px]">
        <section aria-label="Cấu trúc khóa học" className="space-y-3">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-[#191c1d]">Chương trình học</h2>
              <p className="mt-1 text-sm text-slate-500">Kéo chương hoặc bài học để sắp xếp lại.</p>
            </div>
            <button onClick={() => addModule.mutate()} disabled={addModule.isPending} className="inline-flex items-center gap-2 rounded-md bg-[#fed136] px-3.5 py-2.5 text-sm font-bold text-[#191c1d] transition hover:bg-[#f3c51e] disabled:opacity-50">
              <Plus size={16} />{addModule.isPending ? 'Đang thêm…' : 'Thêm chương'}
            </button>
          </div>

          {modules.map((module, moduleIndex) => (
            <ModuleCard
              key={module.id}
              courseId={courseId}
              module={module}
              moduleIndex={moduleIndex}
              moduleCount={modules.length}
              lessons={lessonQueries[moduleIndex]?.data?.items ?? []}
              defaultExpanded={moduleIndex === 0}
              onMove={(delta) => {
                const next = [...modules]
                const target = moduleIndex + delta
                ;[next[moduleIndex], next[target]] = [next[target], next[moduleIndex]]
                reorderModules.mutate(next)
              }}
              onDragStart={() => setDraggedModuleId(module.id)}
              onDragEnd={() => setDraggedModuleId(null)}
              onDrop={() => {
                if (!draggedModuleId || draggedModuleId === module.id) return
                const from = modules.findIndex((item) => item.id === draggedModuleId)
                const next = [...modules]
                const [moved] = next.splice(from, 1)
                next.splice(moduleIndex, 0, moved)
                setDraggedModuleId(null)
                reorderModules.mutate(next)
              }}
              onNotice={setNotice}
            />
          ))}

          {modules.length === 0 && (
            <div className="rounded-lg border border-dashed border-slate-300 bg-white px-5 py-12 text-center">
              <BookOpen className="mx-auto text-slate-400" />
              <p className="mt-3 font-semibold text-slate-800">Chưa có chương nào</p>
              <p className="mt-1 text-sm text-slate-500">Thêm chương đầu tiên để bắt đầu tạo bài học.</p>
              <button onClick={() => addModule.mutate()} className="mt-4 rounded-md bg-[#fed136] px-4 py-2.5 text-sm font-bold text-[#191c1d]">Thêm chương đầu tiên</button>
            </div>
          )}
        </section>

        <aside className="space-y-3 xl:sticky xl:top-24">
          <section className="rounded-lg border border-slate-200 bg-white p-4 sm:p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-bold uppercase tracking-wide text-[#191c1d]">Tổng quan</h2>
              <span className="rounded bg-[#fff2bf] px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-[#735c00]">{statusLabel[course.lifecycle_status]}</span>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-3">
              <div className="rounded-md bg-[#f5f6f7] p-3"><dt className="text-xs text-slate-500">Chương</dt><dd className="mt-1 text-xl font-bold tabular-nums">{modules.length}</dd></div>
              <div className="rounded-md bg-[#f5f6f7] p-3"><dt className="text-xs text-slate-500">Bài học</dt><dd className="mt-1 text-xl font-bold tabular-nums">{lessonCount}</dd></div>
            </dl>
            <p className="mt-4 text-sm leading-6 text-slate-600">Để xuất bản, khóa học cần có ít nhất một bài học và một nội dung đã sẵn sàng.</p>
            <Link to={`/instructor/courses/${courseId}/publish`} className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-[#735c00] hover:underline">Xem điều kiện <ChevronRight size={15} /></Link>
          </section>

          <section className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-800"><CircleHelp size={17} className="text-[#8a6c00]" />Mẹo sắp xếp</div>
            <p className="mt-2 text-sm leading-5 text-slate-600">Mỗi bài học có thể chứa văn bản, liên kết hoặc tài liệu tải lên.</p>
          </section>
        </aside>
      </div>

      <footer className="fixed bottom-0 left-[68px] right-0 z-20 flex min-h-[68px] items-center justify-between gap-3 border-t border-slate-200 bg-white px-3 shadow-[0_-4px_12px_rgba(15,23,42,0.04)] sm:px-5 md:left-[250px] xl:px-8">
        <div className="flex min-w-0 items-center gap-3">
          <button onClick={() => saveDraft.mutate()} disabled={saveDraft.isPending || !dirty} className="inline-flex h-10 shrink-0 items-center gap-2 rounded-md bg-[#f3f4f5] px-3 text-xs font-bold uppercase tracking-wide text-[#191c1d] transition hover:bg-[#e7e8e9] disabled:opacity-50 sm:px-5 sm:text-sm"><Save size={15} /><span className="hidden sm:inline">Lưu nháp</span></button>
          <p className="hidden truncate text-xs text-slate-500 sm:block"><span className={`mr-2 inline-block size-2 rounded-full ${dirty || saveDraft.isError ? 'bg-amber-500' : 'bg-emerald-500'}`} />{saveDraft.isPending ? 'Đang lưu…' : dirty ? 'Có thay đổi chưa lưu' : 'Bản nháp đã đồng bộ'}</p>
        </div>
        <Link to={`/instructor/courses/${courseId}/publish`} className="inline-flex h-10 shrink-0 items-center gap-2 rounded-md bg-[#fed136] px-3 text-xs font-bold uppercase tracking-wide text-[#231a00] shadow-sm transition hover:bg-[#f3c51e] sm:px-6 sm:text-sm">Kiểm tra &amp; xuất bản <Send size={15} /></Link>
      </footer>
    </main>
  )
}

function ModuleCard({ courseId, module, moduleIndex, moduleCount, lessons, defaultExpanded, onMove, onDragStart, onDragEnd, onDrop, onNotice }: {
  courseId: string
  module: Module
  moduleIndex: number
  moduleCount: number
  lessons: Lesson[]
  defaultExpanded: boolean
  onMove: (delta: number) => void
  onDragStart: () => void
  onDragEnd: () => void
  onDrop: () => void
  onNotice: (notice: { text: string; error?: boolean } | null) => void
}) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(defaultExpanded)
  const [editingModule, setEditingModule] = useState(false)
  const [moduleTitle, setModuleTitle] = useState(module.title)
  const [editingLessonId, setEditingLessonId] = useState<string | null>(null)
  const [lessonTitle, setLessonTitle] = useState('')
  const [draggedLessonId, setDraggedLessonId] = useState<string | null>(null)

  const addLesson = useMutation({
    mutationFn: () => courseApi.createLesson(courseId, module.id, `Bài ${lessons.length + 1}`),
    onSuccess: async () => {
      setExpanded(true)
      await queryClient.invalidateQueries({ queryKey: ['builder-lessons', courseId, module.id] })
      onNotice({ text: 'Đã thêm bài học.' })
    },
    onError: (error) => onNotice({ text: error instanceof Error ? error.message : 'Không tạo được bài học.', error: true }),
  })
  const updateModule = useMutation({
    mutationFn: () => courseApi.updateModule(courseId, module.id, moduleTitle.trim()),
    onSuccess: async () => {
      setEditingModule(false)
      await queryClient.invalidateQueries({ queryKey: ['builder-modules', courseId] })
      onNotice({ text: 'Đã cập nhật tên chương.' })
    },
    onError: (error) => onNotice({ text: error instanceof Error ? error.message : 'Không cập nhật được chương.', error: true }),
  })
  const updateLesson = useMutation({
    mutationFn: ({ lessonId, title }: { lessonId: string; title: string }) => courseApi.updateLesson(courseId, module.id, lessonId, title),
    onSuccess: async () => {
      setEditingLessonId(null)
      await queryClient.invalidateQueries({ queryKey: ['builder-lessons', courseId, module.id] })
      onNotice({ text: 'Đã cập nhật tên bài học.' })
    },
    onError: (error) => onNotice({ text: error instanceof Error ? error.message : 'Không cập nhật được bài học.', error: true }),
  })
  const reorderLessons = useMutation({
    mutationFn: (orderedIds: string[]) => courseApi.reorderLessons(courseId, module.id, orderedIds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['builder-lessons', courseId, module.id] }),
    onError: async (error) => {
      onNotice({ text: error instanceof Error ? error.message : 'Không lưu được thứ tự bài học.', error: true })
      await queryClient.invalidateQueries({ queryKey: ['builder-lessons', courseId, module.id] })
    },
  })

  useEffect(() => setModuleTitle(module.title), [module.title])

  const moveLesson = (index: number, delta: number) => {
    const next = [...lessons]
    const target = index + delta
    ;[next[index], next[target]] = [next[target], next[index]]
    reorderLessons.mutate(next.map((lesson) => lesson.id))
  }
  const submitModuleTitle = (event: FormEvent) => {
    event.preventDefault()
    if (moduleTitle.trim() && moduleTitle.trim() !== module.title) updateModule.mutate()
    else setEditingModule(false)
  }

  return (
    <article
      draggable
      onDragStart={onDragStart}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => { event.preventDefault(); onDrop() }}
      onDragEnd={onDragEnd}
      className="group overflow-hidden rounded-lg border border-slate-200 bg-white"
    >
      <header className={`flex min-h-[68px] items-center gap-2 px-3 py-3 sm:gap-3 sm:px-5 ${expanded ? 'bg-[#f1f2f3]' : 'bg-white'}`}>
        <GripVertical size={17} aria-hidden="true" className="shrink-0 cursor-grab text-slate-400" />
        <span className="flex size-8 shrink-0 items-center justify-center rounded bg-white text-sm font-bold text-[#735c00]">{moduleIndex + 1}</span>
        {editingModule ? (
          <form onSubmit={submitModuleTitle} className="flex min-w-0 flex-1 gap-2">
            <input autoFocus aria-label="Tên chương" value={moduleTitle} onChange={(event) => setModuleTitle(event.target.value)} className="min-w-0 flex-1 rounded border border-slate-300 bg-white px-2 py-1.5 text-sm font-semibold outline-none focus:border-[#b89400]" />
            <button disabled={updateModule.isPending || !moduleTitle.trim()} className="rounded bg-[#fed136] px-2.5 text-xs font-bold disabled:opacity-50">Lưu</button>
          </form>
        ) : (
          <button aria-expanded={expanded} onClick={() => setExpanded((value) => !value)} className="flex min-w-0 flex-1 items-center gap-2 text-left">
            <span className="truncate text-sm font-bold uppercase tracking-wide text-[#191c1d] sm:text-base">{/^Chương\s+\d+$/i.test(module.title) ? module.title : `Chương ${moduleIndex + 1}: ${module.title}`}</span>
            <span className="hidden shrink-0 text-xs font-semibold text-slate-500 sm:inline">{lessons.length} bài</span>
            <ChevronDown size={16} className={`shrink-0 text-slate-500 transition-transform ${expanded ? '' : '-rotate-90'}`} />
          </button>
        )}
        <div className="flex shrink-0 items-center gap-1">
          <div className="flex flex-col opacity-60 transition group-hover:opacity-100 group-focus-within:opacity-100">
            <button aria-label={`Đưa chương ${module.title} lên`} disabled={moduleIndex === 0} onClick={() => onMove(-1)} className="text-slate-500 disabled:opacity-30"><ArrowUp size={13} /></button>
            <button aria-label={`Đưa chương ${module.title} xuống`} disabled={moduleIndex === moduleCount - 1} onClick={() => onMove(1)} className="text-slate-500 disabled:opacity-30"><ArrowDown size={13} /></button>
          </div>
          <button aria-label={`Sửa tên chương ${module.title}`} onClick={() => { setModuleTitle(module.title); setEditingModule(true); setExpanded(true) }} className="rounded p-1.5 text-slate-500 hover:bg-white hover:text-[#735c00]"><Pencil size={15} /></button>
          <button onClick={() => addLesson.mutate()} disabled={addLesson.isPending} className="inline-flex items-center gap-1 rounded-md bg-white px-2.5 py-2 text-xs font-bold text-[#735c00] transition hover:bg-[#fff8dc] disabled:opacity-50 sm:text-sm"><Plus size={15} /><span className="hidden sm:inline">Thêm bài</span></button>
        </div>
      </header>

      {expanded && (
        <ol aria-label={`Bài học trong ${module.title}`} className="space-y-2 border-t border-slate-100 p-3 sm:p-4">
          {lessons.map((lesson, index) => (
            <li
              key={lesson.id}
              draggable
              onDragStart={(event) => { event.stopPropagation(); setDraggedLessonId(lesson.id) }}
              onDragEnd={() => setDraggedLessonId(null)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault()
                event.stopPropagation()
                if (!draggedLessonId || draggedLessonId === lesson.id) return
                const from = lessons.findIndex((item) => item.id === draggedLessonId)
                const next = [...lessons]
                const [moved] = next.splice(from, 1)
                next.splice(index, 0, moved)
                setDraggedLessonId(null)
                reorderLessons.mutate(next.map((item) => item.id))
              }}
              className="flex min-h-[58px] items-center gap-2 rounded-md border border-slate-100 bg-white px-2 sm:gap-3 sm:px-3"
            >
              <GripVertical size={16} className="shrink-0 cursor-grab text-slate-400" aria-hidden="true" />
              <span className="flex size-8 shrink-0 items-center justify-center rounded bg-[#f1f2f3] text-slate-600"><BookOpen size={16} /></span>
              <span className="w-8 shrink-0 text-xs tabular-nums text-slate-500">{moduleIndex + 1}.{index + 1}</span>
              {editingLessonId === lesson.id ? (
                <form onSubmit={(event) => { event.preventDefault(); if (lessonTitle.trim() && lessonTitle.trim() !== lesson.title) updateLesson.mutate({ lessonId: lesson.id, title: lessonTitle.trim() }); else setEditingLessonId(null) }} className="flex min-w-0 flex-1 gap-2">
                  <input autoFocus aria-label="Tên bài học" value={lessonTitle} onChange={(event) => setLessonTitle(event.target.value)} className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1.5 text-sm outline-none focus:border-[#b89400]" />
                  <button disabled={updateLesson.isPending || !lessonTitle.trim()} className="rounded bg-[#fed136] px-2.5 text-xs font-bold disabled:opacity-50">Lưu</button>
                </form>
              ) : (
                <span className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-800">{lesson.title}</span>
              )}
              {editingLessonId !== lesson.id && <button aria-label={`Sửa tên bài ${lesson.title}`} onClick={() => { setLessonTitle(lesson.title); setEditingLessonId(lesson.id) }} className="rounded p-1.5 text-slate-400 opacity-60 hover:bg-slate-100 hover:text-[#735c00] group-hover:opacity-100 focus-visible:opacity-100"><Pencil size={14} /></button>}
              <div className="flex flex-col opacity-60 transition group-hover:opacity-100 group-focus-within:opacity-100">
                <button aria-label={`Đưa bài ${lesson.title} lên`} disabled={index === 0} onClick={() => moveLesson(index, -1)} className="text-slate-400 disabled:opacity-30"><ArrowUp size={12} /></button>
                <button aria-label={`Đưa bài ${lesson.title} xuống`} disabled={index === lessons.length - 1} onClick={() => moveLesson(index, 1)} className="text-slate-400 disabled:opacity-30"><ArrowDown size={12} /></button>
              </div>
              <Link aria-label={`Soạn nội dung ${lesson.title}`} to={`/instructor/courses/${courseId}/lessons/${lesson.id}/content`} className="inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-2 text-xs font-semibold text-[#735c00] transition hover:bg-[#fff8dc] sm:text-sm"><span className="hidden sm:inline">Nội dung</span><ChevronRight size={15} /></Link>
            </li>
          ))}
          {lessons.length === 0 && <li className="rounded-md border border-dashed border-slate-200 py-7 text-center text-sm text-slate-500">Chương chưa có bài học.</li>}
          <li><button onClick={() => addLesson.mutate()} disabled={addLesson.isPending} className="flex min-h-12 w-full items-center justify-center gap-2 rounded-md border border-dashed border-slate-300 bg-[#f8f8f8] px-3 py-2 text-sm font-semibold text-slate-700 transition hover:border-[#d4ae17] hover:bg-[#fffaf0] disabled:opacity-50"><Plus size={16} />{addLesson.isPending ? 'Đang thêm bài…' : 'Thêm bài học'}</button></li>
        </ol>
      )}
    </article>
  )
}
