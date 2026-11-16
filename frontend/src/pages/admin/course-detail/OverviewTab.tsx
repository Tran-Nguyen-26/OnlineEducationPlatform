import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type {
  AdminUserResponse,
  CourseInstructorResponse,
  CourseLifecycleStatus,
  CoursePrerequisiteResponse,
  CourseResponse,
  InviteCodeResponse,
  LearningMode,
  SubjectResponse,
} from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { Badge } from '../../../components/Badge'

function toDateTimeLocal(iso: string | null): string {
  return iso ? iso.slice(0, 16) : ''
}

const NEXT_STATUSES: Record<CourseLifecycleStatus, CourseLifecycleStatus[]> = {
  DRAFT: ['PUBLISHED', 'DISABLED'],
  PUBLISHED: ['ARCHIVED', 'DISABLED'],
  ARCHIVED: ['PUBLISHED', 'DISABLED'],
  DISABLED: ['PUBLISHED'],
}

const STATUS_LABEL: Record<CourseLifecycleStatus, string> = {
  DRAFT: 'Lưu nháp',
  PUBLISHED: 'Công khai',
  ARCHIVED: 'Lưu trữ',
  DISABLED: 'Vô hiệu hóa',
}

export function OverviewTab({ course }: { course: CourseResponse }) {
  const courseId = course.id
  const queryClient = useQueryClient()

  const instructorsQuery = useQuery({
    queryKey: ['courses', courseId, 'instructors'],
    queryFn: () =>
      apiClient.get<CourseInstructorResponse[]>(`/api/admin/courses/${courseId}/instructors`).then((r) => r.data),
  })
  const inviteCodesQuery = useQuery({
    queryKey: ['courses', courseId, 'invite-codes'],
    queryFn: () => apiClient.get<InviteCodeResponse[]>(`/api/admin/courses/${courseId}/invite-codes`).then((r) => r.data),
  })
  const instructorUsersQuery = useQuery({
    queryKey: ['users', 'INSTRUCTOR'],
    queryFn: () => apiClient.get<AdminUserResponse[]>('/api/admin/users?role=INSTRUCTOR').then((r) => r.data),
  })
  const subjectsQuery = useQuery({
    queryKey: ['subjects'],
    queryFn: () => apiClient.get<SubjectResponse[]>('/api/admin/subjects').then((r) => r.data),
  })
  const allCoursesQuery = useQuery({
    queryKey: ['courses'],
    queryFn: () => apiClient.get<CourseResponse[]>('/api/admin/courses').then((r) => r.data),
  })
  const prerequisitesQuery = useQuery({
    queryKey: ['courses', courseId, 'prerequisites'],
    queryFn: () => apiClient.get<CoursePrerequisiteResponse[]>(`/api/admin/courses/${courseId}/prerequisites`).then((r) => r.data),
  })

  const [editOpen, setEditOpen] = useState(false)
  const [editSubjectId, setEditSubjectId] = useState('')
  const [editTitle, setEditTitle] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editCapacity, setEditCapacity] = useState('')
  const [editLearningMode, setEditLearningMode] = useState<LearningMode>('FREE_NAVIGATION')
  const [editStartAt, setEditStartAt] = useState('')
  const [editEndAt, setEditEndAt] = useState('')
  const [editFeeAmount, setEditFeeAmount] = useState('')
  const [editError, setEditError] = useState<string | null>(null)

  function openEdit() {
    setEditSubjectId(course.subjectId)
    setEditTitle(course.title)
    setEditDescription(course.description ?? '')
    setEditCapacity(course.capacity != null ? String(course.capacity) : '')
    setEditLearningMode(course.learningMode)
    setEditStartAt(toDateTimeLocal(course.startAt))
    setEditEndAt(toDateTimeLocal(course.endAt))
    setEditFeeAmount(course.feeAmount != null ? String(course.feeAmount) : '')
    setEditError(null)
    setEditOpen(true)
  }

  const editMutation = useMutation({
    mutationFn: () =>
      apiClient.patch(`/api/admin/courses/${courseId}`, {
        subjectId: editSubjectId,
        title: editTitle,
        description: editDescription || null,
        capacity: editCapacity ? Number(editCapacity) : null,
        startAt: editStartAt ? new Date(editStartAt).toISOString() : null,
        endAt: editEndAt ? new Date(editEndAt).toISOString() : null,
        learningMode: editLearningMode,
        feeAmount: editFeeAmount ? Number(editFeeAmount) : null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId] })
      queryClient.invalidateQueries({ queryKey: ['courses'] })
      setEditOpen(false)
    },
    onError: (err) => setEditError(getApiErrorMessage(err)),
  })

  function handleEditSubmit(e: FormEvent) {
    e.preventDefault()
    editMutation.mutate()
  }

  const [statusError, setStatusError] = useState<string | null>(null)
  const statusMutation = useMutation({
    mutationFn: (status: CourseLifecycleStatus) => apiClient.patch(`/api/admin/courses/${courseId}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId] })
      queryClient.invalidateQueries({ queryKey: ['courses'] })
      setStatusError(null)
    },
    onError: (err) => setStatusError(getApiErrorMessage(err)),
  })

  const [assignOpen, setAssignOpen] = useState(false)
  const [selectedInstructorId, setSelectedInstructorId] = useState('')
  const [assignError, setAssignError] = useState<string | null>(null)

  const unassignedInstructors = useMemo(() => {
    const assignedIds = new Set(instructorsQuery.data?.map((i) => i.instructorId))
    return instructorUsersQuery.data?.filter((u) => !assignedIds.has(u.id)) ?? []
  }, [instructorUsersQuery.data, instructorsQuery.data])

  const assignMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/courses/${courseId}/instructors`, { instructorId: selectedInstructorId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'instructors'] })
      setAssignOpen(false)
    },
    onError: (err) => setAssignError(getApiErrorMessage(err)),
  })

  const unassignMutation = useMutation({
    mutationFn: (instructorId: string) => apiClient.delete(`/api/admin/courses/${courseId}/instructors/${instructorId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'instructors'] }),
  })

  const [selectedPrereqId, setSelectedPrereqId] = useState('')
  const [prereqError, setPrereqError] = useState<string | null>(null)

  const availablePrereqCourses = useMemo(() => {
    const already = new Set(prerequisitesQuery.data?.map((p) => p.prerequisiteCourseId))
    return (allCoursesQuery.data ?? []).filter((c) => c.id !== courseId && !already.has(c.id))
  }, [allCoursesQuery.data, prerequisitesQuery.data, courseId])

  const addPrereqMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/courses/${courseId}/prerequisites`, { prerequisiteCourseId: selectedPrereqId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'prerequisites'] })
      setSelectedPrereqId('')
      setPrereqError(null)
    },
    onError: (err) => setPrereqError(getApiErrorMessage(err)),
  })

  const removePrereqMutation = useMutation({
    mutationFn: (prerequisiteCourseId: string) =>
      apiClient.delete(`/api/admin/courses/${courseId}/prerequisites/${prerequisiteCourseId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'prerequisites'] }),
  })

  const generateCodeMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/courses/${courseId}/invite-codes`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'invite-codes'] }),
  })

  const deactivateCodeMutation = useMutation({
    mutationFn: (codeId: string) => apiClient.patch(`/api/admin/courses/${courseId}/invite-codes/${codeId}/deactivate`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'invite-codes'] }),
  })

  function openAssign() {
    setSelectedInstructorId(unassignedInstructors[0]?.id ?? '')
    setAssignError(null)
    setAssignOpen(true)
  }

  function handleAssignSubmit(e: FormEvent) {
    e.preventDefault()
    if (selectedInstructorId) assignMutation.mutate()
  }

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={openEdit}>
          <Pencil size={16} /> Sửa thông tin
        </Button>
        {NEXT_STATUSES[course.lifecycleStatus].map((status) => (
          <Button
            key={status}
            variant="secondary"
            size="sm"
            disabled={statusMutation.isPending}
            onClick={() => statusMutation.mutate(status)}
          >
            Chuyển sang: {STATUS_LABEL[status]}
          </Button>
        ))}
        {statusError && <p className="text-sm text-red-600">{statusError}</p>}
      </div>

      <section className="mb-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Giáo viên được gán</h2>
          <Button size="sm" onClick={openAssign}>
            <Plus size={16} /> Gán giáo viên
          </Button>
        </div>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-4 py-3 font-medium">Giáo viên</th>
                <th className="px-4 py-3 font-medium">Email</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {instructorsQuery.data?.map((instructor) => (
                <tr key={instructor.instructorId}>
                  <td className="px-4 py-3 font-medium text-slate-900">{instructor.fullName ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-500">{instructor.email}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => unassignMutation.mutate(instructor.instructorId)}
                      className="text-slate-400 hover:text-red-600"
                      aria-label="Bỏ gán"
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
              {instructorsQuery.data?.length === 0 && (
                <tr>
                  <td className="px-4 py-6 text-slate-400" colSpan={3}>
                    Chưa gán giáo viên nào.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Mã kích hoạt khóa học</h2>
          <Button size="sm" disabled={generateCodeMutation.isPending} onClick={() => generateCodeMutation.mutate()}>
            <Plus size={16} /> Sinh mã mới
          </Button>
        </div>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-4 py-3 font-medium">Mã</th>
                <th className="px-4 py-3 font-medium">Trạng thái</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {inviteCodesQuery.data?.map((code) => (
                <tr key={code.id}>
                  <td className="px-4 py-3 font-mono font-medium text-slate-900">{code.code}</td>
                  <td className="px-4 py-3">
                    <Badge status={code.active ? 'ACTIVE' : 'DISABLED'} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    {code.active && (
                      <button
                        onClick={() => deactivateCodeMutation.mutate(code.id)}
                        className="text-sm text-slate-500 hover:text-red-600"
                      >
                        Vô hiệu hóa
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {inviteCodesQuery.data?.length === 0 && (
                <tr>
                  <td className="px-4 py-6 text-slate-400" colSpan={3}>
                    Chưa có mã nào — sinh mã để gửi cho học sinh.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Điều kiện tiên quyết</h2>
        </div>
        <p className="mb-3 text-sm text-slate-500">
          Học sinh phải hoàn thành các khóa học dưới đây trước khi có thể ghi danh khóa này.
        </p>
        <div className="mb-3 flex flex-wrap gap-2">
          {prerequisitesQuery.data?.map((p) => (
            <span
              key={p.prerequisiteCourseId}
              className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-700"
            >
              {p.prerequisiteCourseTitle}
              <button
                onClick={() => removePrereqMutation.mutate(p.prerequisiteCourseId)}
                className="text-slate-400 hover:text-red-600"
                aria-label="Xóa điều kiện"
              >
                <Trash2 size={13} />
              </button>
            </span>
          ))}
          {prerequisitesQuery.data?.length === 0 && (
            <span className="text-sm text-slate-400">Không yêu cầu điều kiện tiên quyết nào.</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedPrereqId}
            onChange={(e) => setSelectedPrereqId(e.target.value)}
            className="h-9 w-72 rounded-md border border-slate-300 px-3 text-sm"
          >
            <option value="">Chọn khóa học...</option>
            {availablePrereqCourses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
          <Button
            size="sm"
            variant="secondary"
            disabled={!selectedPrereqId || addPrereqMutation.isPending}
            onClick={() => addPrereqMutation.mutate()}
          >
            <Plus size={14} /> Thêm điều kiện
          </Button>
        </div>
        {prereqError && <p className="mt-2 text-sm text-red-600">{prereqError}</p>}
      </section>

      <Modal title="Gán giáo viên" open={assignOpen} onClose={() => setAssignOpen(false)}>
        <form onSubmit={handleAssignSubmit}>
          {unassignedInstructors.length === 0 ? (
            <p className="mb-4 text-sm text-slate-500">Không còn giáo viên nào để gán (tất cả đã được gán hoặc chưa có giáo viên).</p>
          ) : (
            <select
              required
              value={selectedInstructorId}
              onChange={(e) => setSelectedInstructorId(e.target.value)}
              className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
            >
              {unassignedInstructors.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName ?? u.email} ({u.email})
                </option>
              ))}
            </select>
          )}
          {assignError && <p className="mb-4 text-sm text-red-600">{assignError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setAssignOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={assignMutation.isPending || unassignedInstructors.length === 0}>
              {assignMutation.isPending ? 'Đang gán...' : 'Gán'}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal title="Sửa thông tin khóa học" open={editOpen} onClose={() => setEditOpen(false)}>
        <form onSubmit={handleEditSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Môn học</label>
          <select
            required
            value={editSubjectId}
            onChange={(e) => setEditSubjectId(e.target.value)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            {subjectsQuery.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>

          <label className="mb-1 block text-sm font-medium text-slate-700">Tên khóa học</label>
          <Input required value={editTitle} onChange={(e) => setEditTitle(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả / giới thiệu</label>
          <Input value={editDescription} onChange={(e) => setEditDescription(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Sĩ số tối đa (tùy chọn)</label>
          <Input type="number" min={1} value={editCapacity} onChange={(e) => setEditCapacity(e.target.value)} className="mb-4" />

          <label className="mb-1 block text-sm font-medium text-slate-700">Chế độ học</label>
          <select
            value={editLearningMode}
            onChange={(e) => setEditLearningMode(e.target.value as LearningMode)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            <option value="FREE_NAVIGATION">Tự do (học bài bất kỳ thứ tự)</option>
            <option value="SEQUENTIAL">Tuần tự (phải học theo thứ tự)</option>
          </select>

          <div className="mb-4 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Bắt đầu (tùy chọn)</label>
              <Input type="datetime-local" value={editStartAt} onChange={(e) => setEditStartAt(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Kết thúc (tùy chọn)</label>
              <Input type="datetime-local" value={editEndAt} onChange={(e) => setEditEndAt(e.target.value)} />
            </div>
          </div>

          <label className="mb-1 block text-sm font-medium text-slate-700">Học phí (VNĐ, tùy chọn)</label>
          <Input
            type="number"
            min={0}
            step={1000}
            value={editFeeAmount}
            onChange={(e) => setEditFeeAmount(e.target.value)}
            className="mb-4"
          />

          {editError && <p className="mb-4 text-sm text-red-600">{editError}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setEditOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={editMutation.isPending}>
              {editMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
