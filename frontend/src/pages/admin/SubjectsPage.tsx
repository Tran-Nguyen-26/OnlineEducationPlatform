import { Pager, usePaged } from '../../components/Pager'
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { SubjectResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Modal } from '../../components/ui/Modal'
import { Badge } from '../../components/Badge'

export function SubjectsPage() {
  const queryClient = useQueryClient()
  const { data: subjects, isLoading } = useQuery({
    queryKey: ['subjects'],
    queryFn: () => apiClient.get<SubjectResponse[]>('/api/admin/subjects').then((r) => r.data),
  })
  const pgd = usePaged(subjects ?? [], 15)

  const [editing, setEditing] = useState<SubjectResponse | 'new' | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setEditing('new')
    setName('')
    setDescription('')
    setActive(true)
    setError(null)
  }

  function openEdit(subject: SubjectResponse) {
    setEditing(subject)
    setName(subject.name)
    setDescription(subject.description ?? '')
    setActive(subject.active)
    setError(null)
  }

  const createMutation = useMutation({
    mutationFn: () => apiClient.post('/api/admin/subjects', { name, description: description || null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subjects'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.patch(`/api/admin/subjects/${id}`, { name, description: description || null, active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subjects'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (editing === 'new') {
      createMutation.mutate()
    } else if (editing) {
      updateMutation.mutate(editing.id)
    }
  }

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Môn học</h1>
        <Button onClick={openCreate}>
          <Plus size={16} /> Thêm môn học
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Tên môn học</th>
              <th className="px-4 py-3 font-medium">Mô tả</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={4}>
                  Đang tải...
                </td>
              </tr>
            )}
            {pgd.slice.map((subject) => (
              <tr key={subject.id}>
                <td className="px-4 py-3 font-medium text-slate-900">{subject.name}</td>
                <td className="px-4 py-3 text-slate-500">{subject.description ?? '—'}</td>
                <td className="px-4 py-3">
                  <Badge status={subject.active ? 'ACTIVE' : 'DISABLED'} />
                </td>
                <td className="px-4 py-3 text-right">
                  <button onClick={() => openEdit(subject)} className="text-slate-400 hover:text-slate-700">
                    <Pencil size={16} />
                  </button>
                </td>
              </tr>
            ))}
            {subjects?.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={4}>
                  Chưa có môn học nào.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Modal title={editing === 'new' ? 'Thêm môn học' : 'Sửa môn học'} open={editing !== null} onClose={() => setEditing(null)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên môn học</label>
          <Input required value={name} onChange={(e) => setName(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-4" />

          {editing !== 'new' && (
            <label className="mb-4 flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Đang hoạt động
            </label>
          )}

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
      <Pager page={pgd.page} pages={pgd.pages} total={pgd.total} setPage={pgd.setPage} label="môn học" />
    </div>
  )
}
