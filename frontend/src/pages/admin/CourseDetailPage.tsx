import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Plus, Trash2 } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type {
  AdminUserResponse,
  CourseInstructorResponse,
  CourseLifecycleStatus,
  CourseResponse,
  InviteCodeResponse,
} from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Badge } from '../../components/Badge'

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

export function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const queryClient = useQueryClient()

  const courseQuery = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => apiClient.get<CourseResponse>(`/api/admin/courses/${courseId}`).then((r) => r.data),
  })
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

  const [statusError, setStatusError] = useState<string | null>(null)
  const statusMutation = useMutation({
    mutationFn: (status: CourseLifecycleStatus) =>
      apiClient.patch(`/api/admin/courses/${courseId}/status`, { status }),
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
    mutationFn: () =>
      apiClient.post(`/api/admin/courses/${courseId}/instructors`, { instructorId: selectedInstructorId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'instructors'] })
      setAssignOpen(false)
    },
    onError: (err) => setAssignError(getApiErrorMessage(err)),
  })

  const unassignMutation = useMutation({
    mutationFn: (instructorId: string) =>
      apiClient.delete(`/api/admin/courses/${courseId}/instructors/${instructorId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'instructors'] }),
  })

  const generateCodeMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/courses/${courseId}/invite-codes`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'invite-codes'] }),
  })

  const deactivateCodeMutation = useMutation({
    mutationFn: (codeId: string) =>
      apiClient.patch(`/api/admin/courses/${courseId}/invite-codes/${codeId}/deactivate`),
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

  const course = courseQuery.data
  if (courseQuery.isLoading || !course) {
    return <p className="text-slate-400">Đang tải...</p>
  }

  return (
    <div>
      <Link to="/admin/courses" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách khóa học
      </Link>

      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{course.title}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {course.subjectName} · Tạo bởi {course.createdByEmail}
          </p>
          {course.description && <p className="mt-2 max-w-2xl text-sm text-slate-600">{course.description}</p>}
        </div>
        <Badge status={course.lifecycleStatus} />
      </div>

      <div className="mb-8 flex flex-wrap items-center gap-2">
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
    </div>
  )
}
