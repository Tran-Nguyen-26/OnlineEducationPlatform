import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { ModuleResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { ModuleItem } from './ModuleItem'

export function ContentTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const modulesQuery = useQuery({
    queryKey: ['courses', courseId, 'modules'],
    queryFn: () => apiClient.get<ModuleResponse[]>(`/api/admin/courses/${courseId}/modules`).then((r) => r.data),
  })

  const [editing, setEditing] = useState<ModuleResponse | 'new' | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setEditing('new')
    setTitle('')
    setDescription('')
    setError(null)
  }

  function openEdit(module: ModuleResponse) {
    setEditing(module)
    setTitle(module.title)
    setDescription(module.description ?? '')
    setError(null)
  }

  const nextPosition = modulesQuery.data?.length ?? 0

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/admin/courses/${courseId}/modules`, { title, description: description || null, position: nextPosition }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'modules'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: (module: ModuleResponse) =>
      apiClient.patch(`/api/admin/modules/${module.id}`, { title, description: description || null, position: module.position }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'modules'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (moduleId: string) => apiClient.delete(`/api/admin/modules/${moduleId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'modules'] }),
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (editing === 'new') createMutation.mutate()
    else if (editing) updateMutation.mutate(editing)
  }

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">Cấu trúc: Chương (module) → Bài học (lesson) → Nội dung (video/tài liệu/văn bản).</p>
        <Button size="sm" onClick={openCreate}>
          <Plus size={16} /> Thêm chương
        </Button>
      </div>

      {modulesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {modulesQuery.data?.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-400">
          Chưa có chương nào. Bấm "Thêm chương" để bắt đầu soạn nội dung.
        </p>
      )}

      <div className="space-y-3">
        {modulesQuery.data?.map((module) => (
          <div key={module.id} className="rounded-lg border border-slate-200 bg-white">
            <div className="flex items-center justify-between px-4 py-3">
              <div className="flex-1">
                <div className="font-medium text-slate-900">{module.title}</div>
                {module.description && <div className="text-sm text-slate-500">{module.description}</div>}
              </div>
              <div className="flex items-center gap-1">
                <button onClick={() => openEdit(module)} className="p-1 text-slate-400 hover:text-slate-700" aria-label="Sửa">
                  <Pencil size={16} />
                </button>
                <button
                  onClick={() => deleteMutation.mutate(module.id)}
                  className="p-1 text-slate-400 hover:text-red-600"
                  aria-label="Xóa"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
            <ModuleItem module={module} />
          </div>
        ))}
      </div>

      <Modal title={editing === 'new' ? 'Thêm chương' : 'Sửa chương'} open={editing !== null} onClose={() => setEditing(null)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên chương</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />
          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-4" />
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
