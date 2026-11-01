import { CheckCircle2, AlertCircle, X } from 'lucide-react'
import type { InstructorApplicationItem } from '@/api/instructorApplications'

interface ApplicationApproveModalProps {
  application: InstructorApplicationItem | null
  loading?: boolean
  onClose: () => void
  onConfirm: (app: InstructorApplicationItem) => Promise<void>
}

export function ApplicationApproveModal({
  application,
  loading,
  onClose,
  onConfirm,
}: ApplicationApproveModalProps) {
  if (!application) return null

  const handleApprove = async () => {
    await onConfirm(application)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity"
        onClick={loading ? undefined : onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-[#00695c]" />
            <h3 className="text-base font-bold text-slate-900">
              Phê duyệt hồ sơ giảng viên
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

        {/* Content */}
        <div className="p-6 space-y-4">
          {/* Notification Banner */}
          <div className="flex items-start gap-3 rounded-xl bg-emerald-50 p-3.5 text-xs text-emerald-800 border border-emerald-200/50">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#00695c]" />
            <div className="leading-relaxed">
              Khi được phê duyệt, hệ thống sẽ <strong>tự động tạo tài khoản Giảng viên</strong>, sinh mật khẩu ngẫu nhiên an toàn và gửi thư mời đăng nhập đến email của ứng viên.
            </div>
          </div>

          {/* Candidate Card */}
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-xs space-y-1.5">
            <div className="text-sm font-bold text-slate-900">{application.fullName}</div>
            <div className="font-mono text-slate-600">{application.email}</div>
            {application.organization && (
              <div className="text-slate-500">
                Đơn vị: <span className="font-medium text-slate-700">{application.organization}</span>
              </div>
            )}
            {application.expertise && (
              <div className="text-slate-500">
                Chuyên môn: <span className="font-medium text-slate-700">{application.expertise}</span>
              </div>
            )}
          </div>
        </div>

        {/* Actions */}
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
            type="button"
            disabled={loading}
            onClick={handleApprove}
            className="inline-flex items-center gap-1.5 rounded-xl bg-[#00695c] px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-[#004d40] disabled:opacity-50"
          >
            <CheckCircle2 className="h-4 w-4" />
            <span>{loading ? 'Đang kích hoạt tài khoản...' : 'Xác nhận phê duyệt'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
