import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AssignmentResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'

function toDatetimeLocal(iso: string | null): string {
  if (!iso) return ''
  return new Date(iso).toISOString().slice(0, 16)
}

export function AssignmentsTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const assignmentsQuery = useQuery({
    queryKey: ['courses', courseId, 'assignments'],
    queryFn: () => apiClient.get<AssignmentResponse[]>(`/api/admin/courses/${courseId}/assignments`).then((r) => r.data),
  })

  const [editing, setEditing] = useState<AssignmentResponse | 'new' | null>(null)
  const [title, setTitle] = useState('')
  const [instructions, setInstructions] = useState('')
  const [deadline, setDeadline] = useState('')
  const [maxScore, setMaxScore] = useState('100')
  const [maxSubmissions, setMaxSubmissions] = useState('1')
  const [allowLateSubmission, setAllowLateSubmission] = useState(false)
  const [allowText, setAllowText] = useState(true)
  const [allowFiles, setAllowFiles] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setEditing('new')
    setTitle('')
    setInstructions('')
    setDeadline('')
    setMaxScore('100')
    setMaxSubmissions('1')
    setAllowLateSubmission(false)
    setAllowText(true)
    setAllowFiles(true)
    setError(null)
  }

  function openEdit(assignment: AssignmentResponse) {
    setEditing(assignment)
    setTitle(assignment.title)
    setInstructions(assignment.instructions ?? '')
    setDeadline(toDatetimeLocal(assignment.deadline))
    setMaxScore(String(assignment.maxScore))
    setMaxSubmissions(String(assignment.maxSubmissions))
    setAllowLateSubmission(assignment.allowLateSubmission)
    setAllowText(assignment.allowText)
    setAllowFiles(assignment.allowFiles)
    setError(null)
  }

  const payload = () => ({
    title,
    instructions: instructions || null,
    deadline: deadline ? new Date(deadline).toISOString() : null,
    maxSubmissions: Number(maxSubmissions) || 1,
    allowLateSubmission,
    allowText,
    allowFiles,
    maxScore: Number(maxScore) || 100,
  })

  const createMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/courses/${courseId}/assignments`, payload()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'assignments'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: (id: string) => apiClient.patch(`/api/admin/assignments/${id}`, payload()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'assignments'] })
      setEditing(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/admin/assignments/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'assignments'] }),
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (editing === 'new') createMutation.mutate()
    else if (editing) updateMutation.mutate(editing.id)
  }

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">Bài tập học sinh nộp (văn bản và/hoặc file), giáo viên chấm điểm.</p>
        <Button size="sm" onClick={openCreate}>
          <Plus size={16} /> Tạo bài tập
        </Button>
      </div>

      {assignmentsQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}
      {assignmentsQuery.data?.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-400">
          Chưa có bài tập nào.
        </p>
      )}

      <div className="space-y-2">
        {assignmentsQuery.data?.map((assignment) => (
          <div key={assignment.id} className="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium text-slate-900">{assignment.title}</div>
                {assignment.instructions && <p className="mt-1 text-sm text-slate-500">{assignment.instructions}</p>}
                <p className="mt-1 text-xs text-slate-400">
                  Điểm tối đa: {assignment.maxScore} · Nộp tối đa {assignment.maxSubmissions} lần
                  {assignment.deadline && ` · Hạn: ${new Date(assignment.deadline).toLocaleString('vi-VN')}`}
                </p>
              </div>
              <div className="flex items-center gap-1">
                <button onClick={() => openEdit(assignment)} className="p-1 text-slate-400 hover:text-slate-700" aria-label="Sửa">
                  <Pencil size={16} />
                </button>
                <button
                  onClick={() => deleteMutation.mutate(assignment.id)}
                  className="p-1 text-slate-400 hover:text-red-600"
                  aria-label="Xóa"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal title={editing === 'new' ? 'Tạo bài tập' : 'Sửa bài tập'} open={editing !== null} onClose={() => setEditing(null)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên bài tập</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Hướng dẫn</label>
          <textarea
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
            rows={3}
            className="mb-4 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />

          <div className="mb-4 grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Hạn nộp</label>
              <Input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Điểm tối đa</label>
              <Input type="number" min={1} value={maxScore} onChange={(e) => setMaxScore(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Số lần nộp</label>
              <Input type="number" min={1} value={maxSubmissions} onChange={(e) => setMaxSubmissions(e.target.value)} />
            </div>
          </div>

          <div className="mb-4 space-y-2">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={allowText} onChange={(e) => setAllowText(e.target.checked)} />
              Cho phép nộp bằng văn bản
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={allowFiles} onChange={(e) => setAllowFiles(e.target.checked)} />
              Cho phép nộp file
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={allowLateSubmission} onChange={(e) => setAllowLateSubmission(e.target.checked)} />
              Cho phép nộp muộn
            </label>
          </div>

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
