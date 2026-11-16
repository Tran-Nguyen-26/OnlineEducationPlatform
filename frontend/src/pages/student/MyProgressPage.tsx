import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { MyProgressResponse } from '../../types/api'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ProgressRing } from '../../components/ui/ProgressRing'

export function MyProgressPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const progressQuery = useQuery({
    queryKey: ['student', 'progress'],
    queryFn: () => apiClient.get<MyProgressResponse[]>('/api/enrollments/progress').then((r) => r.data),
  })

  const [withdrawing, setWithdrawing] = useState<MyProgressResponse | null>(null)
  const [withdrawError, setWithdrawError] = useState<string | null>(null)

  const withdrawMutation = useMutation({
    mutationFn: (enrollmentId: string) => apiClient.post(`/api/enrollments/${enrollmentId}/withdraw`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student', 'progress'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'courses'] })
      setWithdrawing(null)
    },
    onError: (err) => setWithdrawError(getApiErrorMessage(err)),
  })

  const items = progressQuery.data ?? []
  const overallPercent = items.length === 0 ? 0
    : Math.round((items.reduce((sum, i) => sum + i.percent, 0) / items.length) * 10) / 10

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="mb-1 text-3xl font-extrabold tracking-tight text-slate-900">Khóa học của tôi</h1>
      <p className="mb-6 text-sm text-slate-500">Tổng hợp tiến độ học tập trên tất cả khóa học đã ghi danh.</p>

      {progressQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}

      {items.length > 0 && (
        <div className="mb-6 rounded-2xl bg-brand-gradient p-5 text-white shadow-depth [&_.text-slate-900]:text-white">
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="font-medium text-slate-900">Tiến độ trung bình</span>
            <span className="font-semibold text-slate-900">{overallPercent}%</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-white/25">
            <div className="h-full rounded-full bg-white transition-all duration-700" style={{ width: `${overallPercent}%` }} />
          </div>
        </div>
      )}

      <div className="space-y-3">
        {items.map((item) => (
          <div key={item.enrollmentId} className="surface surface-hover p-5">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-xs font-medium uppercase tracking-wide text-slate-400">{item.subjectName}</div>
                <h2 className="mt-1 text-lg font-semibold text-slate-900">{item.courseTitle}</h2>
              </div>
              <div className="flex items-center gap-2">
                {item.paymentStatus !== 'NOT_APPLICABLE' && <Badge status={item.paymentStatus} />}
                <Badge status={item.status} />
              </div>
            </div>
            {item.feeAmount != null && (
              <p className="mt-1 text-xs text-slate-500">
                Học phí: {item.feeAmount.toLocaleString('vi-VN')} đ — đã đóng {(item.totalPaid ?? 0).toLocaleString('vi-VN')} đ
              </p>
            )}

            <div className="mt-4 flex items-center gap-4">
              <ProgressRing value={item.percent} size={64} stroke={7} />
              <div className="min-w-0 flex-1">
                <div className="mb-1.5 text-sm text-slate-600">{item.completedLessons}/{item.totalLessons} bài học đã hoàn thành</div>
                <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-gradient-to-r from-indigo-500 via-violet-500 to-teal-400 transition-all duration-700" style={{ width: `${item.percent}%` }} />
                </div>
              </div>
            </div>

            {item.status === 'WAITLISTED' && (
              <p className="mt-2 text-xs text-blue-600">Khóa đã đầy — bạn đang ở danh sách chờ, sẽ tự động vào học khi có chỗ trống.</p>
            )}

            <div className="mt-4 flex gap-2">
              {(item.status === 'ACTIVE' || item.status === 'COMPLETED') && (
                <Button variant="secondary" size="sm" onClick={() => navigate(`/student/courses/${item.courseId}`)}>
                  Tiếp tục học
                </Button>
              )}
              {item.status === 'COMPLETED' && (
                <Button variant="secondary" size="sm" onClick={() => navigate(`/student/certificates/${item.enrollmentId}`)}>
                  Xem chứng chỉ
                </Button>
              )}
              {(item.status === 'ACTIVE' || item.status === 'WAITLISTED') && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-600 hover:bg-red-50"
                  onClick={() => {
                    setWithdrawing(item)
                    setWithdrawError(null)
                  }}
                >
                  {item.status === 'WAITLISTED' ? 'Rời danh sách chờ' : 'Rút khỏi khóa học'}
                </Button>
              )}
            </div>
          </div>
        ))}
        {!progressQuery.isLoading && items.length === 0 && (
          <p className="text-slate-400">Chưa ghi danh khóa học nào.</p>
        )}
      </div>

      <Modal title="Rút khỏi khóa học" open={withdrawing !== null} onClose={() => setWithdrawing(null)}>
        <p className="mb-4 text-sm text-slate-600">
          Bạn sắp rút khỏi khóa <strong>{withdrawing?.courseTitle}</strong>. Bạn sẽ mất quyền truy cập nội dung khóa học
          này; để học lại, bạn cần một mã mời còn hiệu lực.
        </p>
        {withdrawError && <p className="mb-4 text-sm text-red-600">{withdrawError}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => setWithdrawing(null)}>
            Hủy
          </Button>
          <Button
            variant="danger"
            disabled={withdrawMutation.isPending}
            onClick={() => withdrawing && withdrawMutation.mutate(withdrawing.enrollmentId)}
          >
            {withdrawMutation.isPending ? 'Đang xử lý...' : 'Xác nhận rút khóa'}
          </Button>
        </div>
      </Modal>
    </div>
  )
}
