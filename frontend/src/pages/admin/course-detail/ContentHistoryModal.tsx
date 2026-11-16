import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../lib/apiClient'
import type { ContentHistoryEntry } from '../../../types/api'
import { Modal } from '../../../components/ui/Modal'
import { Button } from '../../../components/ui/Button'

const LABEL: Record<ContentHistoryEntry['action'], string> = { CREATED: 'Tạo mới', UPDATED: 'Chỉnh sửa nội dung', FILE_REPLACED: 'Thay / tải file' }

/** Who changed a lesson content and when (version history). */
export function ContentHistoryModal({ contentId, title, onClose }: { contentId: string; title: string; onClose: () => void }) {
  const history = useQuery({
    queryKey: ['content-history', contentId],
    queryFn: () => apiClient.get<ContentHistoryEntry[]>(`/api/admin/lesson-contents/${contentId}/history`).then((r) => r.data),
  })
  return (
    <Modal title={`Lịch sử — ${title}`} open onClose={onClose}>
      {history.isLoading && <p className="text-slate-400">Đang tải...</p>}
      <ul className="max-h-80 space-y-2 overflow-y-auto">
        {history.data?.map((h, i) => (
          <li key={i} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-sm">
            <div><span className="font-medium text-slate-900">Phiên bản {h.version}</span> · {LABEL[h.action]}<div className="text-xs text-slate-400">{h.changedBy ?? '—'}</div></div>
            <div className="text-xs text-slate-500">{new Date(h.changedAt).toLocaleString('vi-VN')}</div>
          </li>
        ))}
        {history.data?.length === 0 && <p className="text-sm text-slate-400">Chưa có lịch sử (nội dung được tạo trước khi hệ thống ghi lịch sửa).</p>}
      </ul>
      <div className="mt-4 flex justify-end"><Button onClick={onClose}>Đóng</Button></div>
    </Modal>
  )
}
