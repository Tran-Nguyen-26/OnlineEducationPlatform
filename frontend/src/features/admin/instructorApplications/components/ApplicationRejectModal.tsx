import { useState } from 'react'
import { XCircle, AlertTriangle, X, Sparkles } from 'lucide-react'
import type { InstructorApplicationItem } from '@/api/instructorApplications'

interface ApplicationRejectModalProps {
  application: InstructorApplicationItem | null
  loading?: boolean
  onClose: () => void
  onConfirm: (app: InstructorApplicationItem, rejectionReason: string) => Promise<void>
}

const TEMPLATE_REASONS = [
  'Hồ sơ của bạn hiện chưa đáp ứng đủ yêu cầu tối thiểu về số năm kinh nghiệm giảng dạy thực tế trong lĩnh vực đăng ký (yêu cầu tối thiểu từ 3 năm kinh nghiệm).',
  'Thông tin chuyên môn và kinh nghiệm công tác chưa đầy đủ hoặc chưa đủ cơ sở xác thực theo tiêu chuẩn của EduPlatform.',
  'Lĩnh vực chuyên môn hiện tại chưa phù hợp với định hướng phát triển khóa học của nền tảng trong giai đoạn tuyển dụng này.',
]

export function ApplicationRejectModal({
  application,
  loading,
  onClose,
  onConfirm,
}: ApplicationRejectModalProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')

  if (!application) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    const trimmed = reason.trim()
    if (!trimmed) {
      setError('Vui lòng nhập lý do từ chối hồ sơ.')
      return
    }
    if (trimmed.length < 5) {
      setError('Lý do từ chối phải có tối thiểu 5 ký tự.')
      return
    }

    await onConfirm(application, trimmed)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity"
        onClick={loading ? undefined : onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="flex items-center gap-2">
            <XCircle className="h-5 w-5 text-rose-600" />
            <h3 className="text-base font-bold text-slate-900">
              Từ chối hồ sơ giảng viên
            </h3>
          </div>
          <button
            type="button"
            disabled={loading}
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit}>
          <div className="p-6 space-y-4">
            {/* Warning Banner */}
            <div className="flex items-start gap-3 rounded-xl bg-rose-50 p-3.5 text-xs text-rose-800 border border-rose-200/60">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
              <div className="leading-relaxed">
                Thư thông báo từ chối lịch sự kèm lý do cụ thể sẽ được gửi trực tiếp đến ứng viên{' '}
                <strong>{application.fullName}</strong> ({application.email}).
              </div>
            </div>

            {/* Quick Template Reasons */}
            <div className="space-y-1.5">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                <Sparkles className="h-3.5 w-3.5 text-amber-500" />
                <span>Gợi ý mẫu lý do thường gặp:</span>
              </div>
              <div className="space-y-1.5">
                {TEMPLATE_REASONS.map((t, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => {
                      setReason(t)
                      setError('')
                    }}
                    className="block w-full text-left rounded-lg border border-slate-200/70 bg-slate-50/70 p-2 text-[11px] text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 leading-snug"
                  >
                    • {t}
                  </button>
                ))}
              </div>
            </div>

            {/* Rejection Reason Input */}
            <div>
              <label
                htmlFor="rejectionReason"
                className="block text-xs font-semibold text-slate-700"
              >
                Lý do từ chối cụ thể <span className="text-rose-500">*</span>
              </label>
              <textarea
                id="rejectionReason"
                rows={4}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value)
                  if (error) setError('')
                }}
                placeholder="Nhập chi tiết lý do từ chối hồ sơ giảng viên..."
                className="mt-1.5 w-full rounded-xl border border-slate-200 p-3 text-xs text-slate-800 placeholder-slate-400 transition-all focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500/20"
              />
              {error && <p className="mt-1 text-xs text-rose-600 font-medium">{error}</p>}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex justify-end gap-2.5 border-t border-slate-100 bg-slate-50/50 px-6 py-3.5">
            <button
              type="button"
              disabled={loading}
              onClick={onClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-xl bg-rose-600 px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-rose-700 disabled:opacity-50"
            >
              <XCircle className="h-4 w-4" />
              <span>{loading ? 'Đang gửi email thông báo...' : 'Xác nhận từ chối'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
