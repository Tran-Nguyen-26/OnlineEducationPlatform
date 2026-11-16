import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { Modal } from './ui/Modal'
import { Button } from './ui/Button'

/** Student asks for a grade review (phúc khảo) of a graded assignment or a submitted quiz attempt. */
export function AppealModal({ targetType, targetId, title, onClose }: {
  targetType: 'ASSIGNMENT_SUBMISSION' | 'QUIZ_ATTEMPT'; targetId: string; title: string; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const send = useMutation({
    mutationFn: () => apiClient.post('/api/appeals', { targetType, targetId, reason }),
    onSuccess: () => { setSent(true); queryClient.invalidateQueries({ queryKey: ['student', 'appeals'] }) },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  return (
    <Modal title="Yêu cầu phúc khảo" open onClose={onClose}>
      {sent ? (
        <div>
          <p className="mb-4 text-sm text-slate-700">Đã gửi yêu cầu phúc khảo cho giáo viên. Bạn sẽ nhận thông báo khi có kết quả; theo dõi ở mục Điểm số.</p>
          <div className="flex justify-end"><Button onClick={onClose}>Đóng</Button></div>
        </div>
      ) : (
        <div>
          <p className="mb-3 text-sm text-slate-600">Mục cần xem lại: <strong>{title}</strong></p>
          <label className="mb-1 block text-sm font-medium text-slate-700">Lý do đề nghị xem lại điểm</label>
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={4} className="mb-3 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="Nêu rõ phần bạn cho là chấm chưa đúng..." />
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>Hủy</Button>
            <Button disabled={send.isPending || reason.trim().length < 10} onClick={() => send.mutate()}>{send.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}</Button>
          </div>
          {reason.trim().length < 10 && <p className="mt-2 text-xs text-slate-400">Tối thiểu 10 ký tự.</p>}
        </div>
      )}
    </Modal>
  )
}
