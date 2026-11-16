import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { CourseResponse, SubjectResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Modal } from '../../components/ui/Modal'
import { Badge } from '../../components/Badge'

export function CoursesPage() {
  const queryClient = useQueryClient()
  const { data: courses, isLoading } = useQuery({
    queryKey: ['courses'],
    queryFn: () => apiClient.get<CourseResponse[]>('/api/admin/courses').then((r) => r.data),
  })
  const { data: subjects } = useQuery({
    queryKey: ['subjects'],
    queryFn: () => apiClient.get<SubjectResponse[]>('/api/admin/subjects').then((r) => r.data),
  })

  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [subjectId, setSubjectId] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [capacity, setCapacity] = useState('')
  const [feeAmount, setFeeAmount] = useState('')
  const [error, setError] = useState<string | null>(null)

  function openCreate() {
    setSubjectId(subjects?.[0]?.id ?? '')
    setTitle('')
    setDescription('')
    setCapacity('')
    setFeeAmount('')
    setError(null)
    setOpen(true)
  }

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post('/api/admin/courses', {
        subjectId,
        title,
        description: description || null,
        capacity: capacity ? Number(capacity) : null,
        feeAmount: feeAmount ? Number(feeAmount) : null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses'] })
      setOpen(false)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    createMutation.mutate()
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">Khóa học</h1>
        <Button onClick={openCreate} disabled={!subjects?.length}>
          <Plus size={16} /> Tạo khóa học
        </Button>
      </div>

      {!subjects?.length && (
        <p className="mb-4 text-sm text-amber-600">Hãy tạo ít nhất một môn học trước khi tạo khóa học.</p>
      )}

      <div className="mb-4 flex flex-wrap gap-3">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm theo tên khóa học..."
          className="h-9 w-72 rounded-md border border-slate-300 px-3 text-sm"
        />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="h-9 rounded-md border border-slate-300 px-3 text-sm">
          <option value="">Tất cả trạng thái</option>
          <option value="DRAFT">Lưu nháp</option>
          <option value="PUBLISHED">Công khai</option>
          <option value="ARCHIVED">Lưu trữ</option>
          <option value="DISABLED">Vô hiệu hóa</option>
        </select>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Tên khóa học</th>
              <th className="px-4 py-3 font-medium">Môn học</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Sĩ số</th>
              <th className="px-4 py-3 font-medium">Học phí</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Đang tải...
                </td>
              </tr>
            )}
            {courses
              ?.filter((c) => (!statusFilter || c.lifecycleStatus === statusFilter) && c.title.toLowerCase().includes(search.trim().toLowerCase()))
              .map((course) => (
              <tr key={course.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/admin/courses/${course.id}`} className="font-medium text-slate-900 hover:underline">
                    {course.title}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{course.subjectName}</td>
                <td className="px-4 py-3">
                  <Badge status={course.lifecycleStatus} />
                </td>
                <td className="px-4 py-3 text-slate-500">{course.capacity ?? '—'}</td>
                <td className="px-4 py-3 text-slate-500">{course.feeAmount ? `${course.feeAmount.toLocaleString('vi-VN')} đ` : 'Miễn phí'}</td>
              </tr>
            ))}
            {courses?.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Chưa có khóa học nào.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Modal title="Tạo khóa học" open={open} onClose={() => setOpen(false)}>
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Môn học</label>
          <select
            required
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            {subjects?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>

          <label className="mb-1 block text-sm font-medium text-slate-700">Tên khóa học</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả / giới thiệu</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Sĩ số tối đa (tùy chọn)</label>
          <Input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Học phí (VNĐ, tùy chọn)</label>
          <Input type="number" min={0} step={1000} value={feeAmount} onChange={(e) => setFeeAmount(e.target.value)} className="mb-4" />

          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Đang tạo...' : 'Tạo khóa học'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
