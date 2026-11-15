import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Pencil, Plus, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { LessonResponse, ModuleResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { LessonItem } from './LessonItem'

export function ModuleItem({ module }: { module: ModuleResponse }) {
  const [expanded, setExpanded] = useState(false)
  const queryClient = useQueryClient()

  const lessonsQuery = useQuery({
    queryKey: ['modules', module.id, 'lessons'],
    queryFn: () => apiClient.get<LessonResponse[]>(`/api/admin/modules/${module.id}/lessons`).then((r) => r.data),
    enabled: expanded,
  })

  const [editing, setEditing] = useState<LessonResponse | 'new' | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [required, setRequired] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setEditing('new')
    setTitle('')
    setDescription('')
    setRequired(true)
    setError(null)
  }

  function openEdit(lesson: LessonResponse) {
    setEditing(lesson)
    setTitle(lesson.title)
    setDescription(lesson.description ?? '')
    setRequired(lesson.required)
    setError(null)
  }

  const nextPosition = lessonsQuery.data?.length ?? 0

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/admin/modules/${module.id}/lessons`, {
        title,
        description: description || null,
        position: nextPosition,
        required,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules', module.id, 'lessons'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: (lesson: LessonResponse) =>
      apiClient.patch(`/api/admin/lessons/${lesson.id}`, {
        title,
        description: description || null,
        position: lesson.position,
        required,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modules', module.id, 'lessons'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (lessonId: string) => apiClient.delete(`/api/admin/lessons/${lessonId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['modules', module.id, 'lessons'] }),
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (editing === 'new') createMutation.mutate()
    else if (editing) updateMutation.mutate(editing)
  }

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <div className="border-t border-slate-100">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-600 hover:bg-slate-50"
      >
        {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        Bài học ({lessonsQuery.data?.length ?? '...'})
      </button>

      {expanded && (
        <div className="space-y-2 px-4 pb-3">
          {lessonsQuery.data?.map((lesson) => (
            <div key={lesson.id} className="rounded-md border border-slate-100 bg-slate-50">
              <div className="flex items-center justify-between px-3 py-2">
                <div className="flex-1">
                  <span className="text-sm font-medium text-slate-800">{lesson.title}</span>
                  {!lesson.required && <span className="ml-2 text-xs text-slate-400">(không bắt buộc)</span>}
                </div>
                <div className="flex items-center gap-1">
                  <button onClick={() => openEdit(lesson)} className="p-1 text-slate-400 hover:text-slate-700" aria-label="Sửa">
                    <Pencil size={14} />
                  </button>
                  <button
                    onClick={() => deleteMutation.mutate(lesson.id)}
                    className="p-1 text-slate-400 hover:text-red-600"
                    aria-label="Xóa"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
              <LessonItem lesson={lesson} />
            </div>
          ))}

          <Button size="sm" variant="secondary" onClick={openCreate}>
            <Plus size={14} /> Thêm bài học
          </Button>
        </div>
      )}

      <Modal title={editing === 'new' ? 'Thêm bài học' : 'Sửa bài học'} open={editing !== null} onClose={() => setEditing(null)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên bài học</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />
          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-4" />
          <label className="mb-4 flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={required} onChange={(e) => setRequired(e.target.checked)} />
            Bắt buộc hoàn thành
          </label>
          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
