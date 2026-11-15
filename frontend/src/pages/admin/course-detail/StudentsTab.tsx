import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AdminEnrollmentResponse } from '../../../types/api'
import { Badge } from '../../../components/Badge'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'

export function StudentsTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const enrollmentsQuery = useQuery({
    queryKey: ['courses', courseId, 'enrollments'],
    queryFn: () => apiClient.get<AdminEnrollmentResponse[]>(`/api/admin/courses/${courseId}/enrollments`).then((r) => r.data),
  })

  const [removing, setRemoving] = useState<AdminEnrollmentResponse | null>(null)
  const [removeError, setRemoveError] = useState<string | null>(null)

  const statusMutation = useMutation({
    mutationFn: ({ enrollmentId, status }: { enrollmentId: string; status: 'COMPLETED' | 'REMOVED' }) =>
      apiClient.patch(`/api/admin/enrollments/${enrollmentId}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'enrollments'] })
      setRemoving(null)
    },
    onError: (err) => setRemoveError(getApiErrorMessage(err)),
  })

  const rows = enrollmentsQuery.data ?? []

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Học sinh ghi danh</h2>
        <span className="text-sm text-slate-500">{rows.length} học sinh</span>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Học sinh</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Ngày ghi danh</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={row.enrollmentId}>
                <td className="px-4 py-3 font-medium text-slate-900">{row.studentFullName ?? '—'}</td>
                <td className="px-4 py-3 text-slate-500">{row.studentEmail}</td>
                <td className="px-4 py-3">
                  <Badge status={row.status} />
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(row.enrolledAt).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3 text-right">
                  {row.status === 'ACTIVE' && (
                    <div className="flex justify-end gap-3">
                      <button
                        className="text-sm text-slate-500 hover:text-green-700"
                        disabled={statusMutation.isPending}
                        onClick={() => statusMutation.mutate({ enrollmentId: row.enrollmentId, status: 'COMPLETED' })}
                      >
                        Đánh dấu hoàn thành
                      </button>
                      <button
                        className="text-sm text-slate-500 hover:text-red-600"
                        onClick={() => {
                          setRemoving(row)
                          setRemoveError(null)
                        }}
                      >
                        Loại khỏi khóa
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {!enrollmentsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Chưa có học sinh ghi danh khóa này.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Modal title="Loại học sinh khỏi khóa học" open={removing !== null} onClose={() => setRemoving(null)}>
        <p className="mb-4 text-sm text-slate-600">
          Loại <strong>{removing?.studentFullName ?? removing?.studentEmail}</strong> khỏi khóa học? Học sinh sẽ mất
          quyền truy cập nội dung; để vào lại cần một mã mời còn hiệu lực.
        </p>
        {removeError && <p className="mb-4 text-sm text-red-600">{removeError}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => setRemoving(null)}>
            Hủy
          </Button>
          <Button
            variant="danger"
            disabled={statusMutation.isPending}
            onClick={() => removing && statusMutation.mutate({ enrollmentId: removing.enrollmentId, status: 'REMOVED' })}
          >
            {statusMutation.isPending ? 'Đang xử lý...' : 'Xác nhận loại khỏi khóa'}
          </Button>
        </div>
      </Modal>
    </div>
  )
}
