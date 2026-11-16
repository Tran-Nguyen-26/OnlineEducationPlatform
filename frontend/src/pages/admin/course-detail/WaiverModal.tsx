import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AdminEnrollmentResponse, WaiverItem } from '../../../types/api'
import { Modal } from '../../../components/ui/Modal'
import { Button } from '../../../components/ui/Button'

/** Exempt one student from a quiz/assignment (e.g. missed deadline) so it no longer blocks course completion or counts in their grade. */
export function WaiverModal({ enrollment, onClose }: { enrollment: AdminEnrollmentResponse; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const items = useQuery({
    queryKey: ['waivers', enrollment.enrollmentId],
    queryFn: () => apiClient.get<WaiverItem[]>(`/api/admin/enrollments/${enrollment.enrollmentId}/waivers`).then((r) => r.data),
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['waivers', enrollment.enrollmentId] })
    queryClient.invalidateQueries({ queryKey: ['gradebook'] })
  }
  const waive = useMutation({
    mutationFn: (it: WaiverItem) => apiClient.post(`/api/admin/enrollments/${enrollment.enrollmentId}/waivers`, { targetType: it.type, targetId: it.id, reason }),
    onSuccess: () => { setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const unwaive = useMutation({
    mutationFn: (it: WaiverItem) => apiClient.delete(`/api/admin/enrollments/${enrollment.enrollmentId}/waivers/${it.waiverId}`),
    onSuccess: refresh,
    onError: (e) => setError(getApiErrorMessage(e)),
  })

  return (
    <Modal title={`Miễn trừ — ${enrollment.studentFullName ?? enrollment.studentEmail}`} open onClose={onClose}>
      <p className="mb-3 text-sm text-slate-500">Mục được miễn trừ sẽ không bắt buộc phải làm và không tính vào điểm tổng kết của học sinh này.</p>
      <label className="mb-1 block text-xs font-medium text-slate-600">Lý do (ghi nhật ký)</label>
      <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="VD: nghỉ ốm, quá hạn có lý do" className="mb-3 h-9 w-full rounded-md border border-slate-300 px-3 text-sm" />
      {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
      <div className="max-h-72 space-y-2 overflow-y-auto">
        {items.isLoading && <p className="text-slate-400">Đang tải...</p>}
        {items.data?.map((it) => (
          <div key={it.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
            <div>
              <div className="text-sm text-slate-900">{it.type === 'QUIZ' ? 'Quiz' : 'Bài tập'}: {it.title}</div>
              <div className="text-xs text-slate-400">
                {it.done ? 'Đã làm' : 'Chưa làm'}{it.deadline ? ` · hạn ${new Date(it.deadline).toLocaleDateString('vi-VN')}` : ''}{it.waived && it.reason ? ` · miễn trừ: ${it.reason}` : ''}
              </div>
            </div>
            {it.waived
              ? <Button size="sm" variant="secondary" disabled={unwaive.isPending} onClick={() => unwaive.mutate(it)}>Bỏ miễn trừ</Button>
              : <Button size="sm" variant="secondary" disabled={waive.isPending} onClick={() => waive.mutate(it)}>Miễn trừ</Button>}
          </div>
        ))}
        {items.data?.length === 0 && <p className="text-sm text-slate-400">Khóa học chưa có quiz hay bài tập.</p>}
      </div>
      <div className="mt-4 flex justify-end"><Button onClick={onClose}>Đóng</Button></div>
    </Modal>
  )
}
