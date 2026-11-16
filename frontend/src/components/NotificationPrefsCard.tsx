import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'
import type { NotificationPrefs } from '../types/api'

const ROWS: { key: keyof NotificationPrefs; title: string; text: string }[] = [
  { key: 'COURSE_UPDATES', title: 'Thông báo khóa học', text: 'Thông báo từ giáo viên, buổi học mới, được xếp chỗ từ danh sách chờ' },
  { key: 'GRADING', title: 'Chấm điểm & phúc khảo', text: 'Bài được chấm, điểm điều chỉnh, kết quả phúc khảo, bài nộp mới (giáo viên)' },
  { key: 'REMINDERS', title: 'Nhắc hạn', text: 'Quiz và bài tập sắp đến hạn, mã mời sắp hết hạn' },
  { key: 'MESSAGES', title: 'Tin nhắn hỏi đáp', text: 'Câu hỏi và trả lời trong khóa học' },
]

/** Per-category switches for in-app notifications. System announcements and certificates are always delivered. */
export function NotificationPrefsCard() {
  const queryClient = useQueryClient()
  const [local, setLocal] = useState<Partial<NotificationPrefs>>({})
  const prefs = useQuery({
    queryKey: ['notification-prefs'],
    queryFn: () => apiClient.get<NotificationPrefs>('/api/users/me/notification-preferences').then((r) => r.data),
  })
  const update = useMutation({
    mutationFn: (v: { category: string; enabled: boolean }) => apiClient.put<NotificationPrefs>('/api/users/me/notification-preferences', v).then((r) => r.data),
    // Optimistic: the switch flips immediately and is corrected by the server response (or rolled back on error).
    onMutate: async (v) => {
      const prev = queryClient.getQueryData<NotificationPrefs>(['notification-prefs'])
      queryClient.setQueryData(['notification-prefs'], { ...(prev ?? {}), [v.category]: v.enabled })
      return { prev }
    },
    onError: (_e, _v, ctx) => queryClient.setQueryData(['notification-prefs'], ctx?.prev),
    onSuccess: (data) => queryClient.setQueryData(['notification-prefs'], data),
  })
  return (
    <div className="mt-6 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="mb-1 text-lg font-semibold text-slate-900">Nhận thông báo</h2>
      <p className="mb-4 text-sm text-slate-500">Chọn loại thông báo trong ứng dụng bạn muốn nhận. Thông báo hệ thống và chứng chỉ luôn được gửi.</p>
      <div className="divide-y divide-slate-100">
        {ROWS.map((r) => (
          <label key={r.key} className="flex cursor-pointer items-center justify-between gap-4 py-3">
            <div>
              <div className="text-sm font-medium text-slate-900">{r.title}</div>
              <div className="text-xs text-slate-500">{r.text}</div>
            </div>
            <input type="checkbox" className="h-4 w-4" checked={local[r.key] ?? prefs.data?.[r.key] ?? true} disabled={prefs.isLoading}
              onChange={(e) => { setLocal((l) => ({ ...l, [r.key]: e.target.checked })); update.mutate({ category: r.key, enabled: e.target.checked }, { onSettled: () => setLocal((l) => { const n = { ...l }; delete n[r.key]; return n }) }) }} />
          </label>
        ))}
      </div>
    </div>
  )
}
