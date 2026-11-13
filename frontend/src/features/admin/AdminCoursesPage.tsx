import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import CurriculumSettings from './CurriculumSettings'

type Item = { id: string; title: string; description?: string; lifecycle_status?: string }
type Level = 'course' | 'module' | 'lesson'

function requestHeaders() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
}

async function listItems(path: string) {
  const { data } = await axios.get<{ items: Item[] }>(path, {
    headers: requestHeaders(),
    params: { page: 1, page_size: 100 },
  })
  return data.items
}

const labels = { course: 'Khóa học', module: 'Chương', lesson: 'Bài học' }
const courseStatuses: Record<string, string> = {
  DRAFT: 'Bản nháp', PUBLISHED: 'Đã xuất bản', ARCHIVED: 'Đã lưu trữ', DISABLED: 'Đã khóa',
}

function BuilderList({ level, items, selected, onSelect }: {
  level: Level
  items?: Item[]
  selected?: string
  onSelect: (item: Item) => void
}) {
  return (
    <section className="min-w-0 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">{labels[level]}</h2>
      {items?.length === 0 && <p className="text-sm text-slate-600">Chưa có mục nào.</p>}
      <ul className="space-y-1">
        {items?.map((item) => (
          <li key={item.id}>
            <button type="button" onClick={() => onSelect(item)} aria-pressed={selected === item.id}
              className="w-full rounded-lg p-2 text-left text-sm hover:bg-slate-50 aria-pressed:bg-teal-50 aria-pressed:text-teal-900">
              <span className="block break-words font-medium">{item.title}</span>
              {item.lifecycle_status && <span className="text-slate-600">{courseStatuses[item.lifecycle_status] ?? item.lifecycle_status}</span>}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default function AdminCoursesPage() {
  const cache = useQueryClient()
  const [courseId, setCourseId] = useState('')
  const [moduleId, setModuleId] = useState('')
  const [level, setLevel] = useState<Level>('course')
  const [editing, setEditing] = useState<Item>()
  const coursePath = '/api/v1/courses'
  const modulePath = `${coursePath}/${courseId}/modules`
  const lessonPath = `${modulePath}/${moduleId}/lessons`
  const courses = useQuery({ queryKey: ['course-builder', 'course'], queryFn: () => listItems('/api/v1/my/courses') })
  const modules = useQuery({ queryKey: ['course-builder', 'module', courseId], queryFn: () => listItems(modulePath), enabled: !!courseId })
  const lessons = useQuery({ queryKey: ['course-builder', 'lesson', moduleId], queryFn: () => listItems(lessonPath), enabled: !!moduleId })
  const selectedCourse = courses.data?.find((item) => item.id === courseId)
  const update = useMutation({
    mutationFn: async (request: { path: string; method: 'post' | 'patch'; data?: object }) => {
      await axios.request({ ...request, url: request.path, headers: requestHeaders() })
    },
    onSuccess: () => cache.invalidateQueries({ queryKey: ['course-builder'] }),
  })
  const errorMessage = axios.isAxiosError<{ message?: string }>(update.error)
    ? update.error.response?.data?.message : undefined

  function startEdit(nextLevel: Level, item?: Item) {
    update.reset()
    setLevel(nextLevel)
    setEditing(item)
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const fields = new FormData(form)
    const title = String(fields.get('title')).trim()
    if (!title) return
    const path = { course: coursePath, module: modulePath, lesson: lessonPath }[level]
    update.mutate({
      path: editing ? `${path}/${editing.id}` : path,
      method: editing ? 'patch' : 'post',
      data: { title, description: String(fields.get('description')).trim() },
    }, { onSuccess: () => { if (!editing) form.reset() } })
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý khóa học</h1>
        {selectedCourse?.lifecycle_status === 'DRAFT' && (
          <button type="button" disabled={update.isPending} onClick={() => update.mutate({
            path: `${coursePath}/${courseId}/publish`, method: 'post',
          })} className="rounded-lg border border-teal-700 px-4 py-2 text-sm text-teal-800 disabled:opacity-60">Xuất bản khóa học</button>
        )}
      </div>
      {(courses.isError || modules.isError || lessons.isError) && <p role="alert">Không tải được cấu trúc khóa học.</p>}
      <div className="grid gap-3 lg:grid-cols-3">
        <BuilderList level="course" items={courses.data} selected={courseId} onSelect={(item) => {
          setCourseId(item.id); setModuleId(''); startEdit('course', item)
        }} />
        <BuilderList level="module" items={modules.data} selected={moduleId} onSelect={(item) => {
          setModuleId(item.id); startEdit('module', item)
        }} />
        <BuilderList level="lesson" items={lessons.data} selected={level === 'lesson' ? editing?.id : undefined}
          onSelect={(item) => startEdit('lesson', item)} />
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={() => startEdit('course')} className="rounded-lg border px-3 py-2 text-sm">Thêm khóa học</button>
        <button type="button" disabled={!courseId} onClick={() => startEdit('module')} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Thêm chương</button>
        <button type="button" disabled={!moduleId} onClick={() => startEdit('lesson')} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-50">Thêm bài học</button>
      </div>
      <form key={`${level}/${editing?.id ?? 'new'}`} onSubmit={save} className="max-w-xl space-y-3 rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="font-semibold">{editing ? 'Cập nhật' : 'Tạo'} {labels[level].toLowerCase()}</h2>
        <label className="grid gap-1 text-sm">Tên
          <input name="title" required maxLength={255} defaultValue={editing?.title} className="rounded-lg border px-3 py-2" />
        </label>
        <label className="grid gap-1 text-sm">Mô tả
          <textarea name="description" defaultValue={editing?.description} rows={3} className="rounded-lg border px-3 py-2" />
        </label>
        <button disabled={update.isPending} className="rounded-lg bg-teal-700 px-4 py-2 text-sm text-white disabled:opacity-60">Lưu</button>
        {update.isSuccess && <p role="status" className="text-sm text-teal-800">Đã lưu thay đổi.</p>}
        {update.isError && <p role="alert" className="text-sm text-red-700">{errorMessage ?? 'Không lưu được thay đổi.'}</p>}
      </form>
      {selectedCourse && <CurriculumSettings key={courseId} courseId={courseId} editable={selectedCourse.lifecycle_status === 'DRAFT'} />}
    </section>
  )
}
