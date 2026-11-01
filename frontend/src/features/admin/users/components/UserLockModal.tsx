import { useState } from 'react'
import { AlertTriangle, Lock, Unlock, X } from 'lucide-react'
import type { AdminUserItem } from '@/api/adminUsers'

interface UserLockModalProps {
  user: AdminUserItem | null
  loading?: boolean
  onClose: () => void
  onConfirm: (user: AdminUserItem, reason: string) => Promise<void>
}

export function UserLockModal({
  user,
  loading,
  onClose,
  onConfirm,
}: UserLockModalProps) {
  const [reason, setReason] = useState('')

  if (!user) return null

  const isLocked = user.accountStatus === 'LOCKED' || user.accountStatus === 'SUSPENDED'

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    await onConfirm(user, reason)
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
            {isLocked ? (
              <Unlock className="h-5 w-5 text-[#00695c]" />
            ) : (
              <Lock className="h-5 w-5 text-rose-600" />
            )}
            <h3 className="text-base font-bold text-slate-900">
              {isLocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}
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
          <div className="p-6">
            {/* Alert Banner */}
            <div
              className={`flex items-start gap-3 rounded-xl p-3.5 text-xs ${
                isLocked
                  ? 'bg-emerald-50 text-emerald-800'
                  : 'bg-rose-50 text-rose-800'
              }`}
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                {isLocked ? (
                  <span>
                    Sau khi mở khóa, người dùng <strong>{user.fullName}</strong> sẽ có thể đăng nhập và truy cập nền tảng bình thường.
                  </span>
                ) : (
                  <span>
                    Sau khi bị khóa, người dùng <strong>{user.fullName}</strong> sẽ bị thu hồi phiên làm việc và không thể đăng nhập vào hệ thống.
                  </span>
                )}
              </div>
            </div>

            {/* Target User Info */}
            <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-3.5 text-xs">
              <div className="font-semibold text-slate-900">{user.fullName}</div>
              <div className="mt-0.5 font-mono text-slate-500">{user.email}</div>
              <div className="mt-1 text-slate-400">
                Vai trò:{' '}
                <span className="font-medium text-slate-700">{user.role}</span>
              </div>
            </div>

            {/* Reason Input */}
            <div className="mt-4">
              <label
                htmlFor="reason"
                className="block text-xs font-semibold text-slate-700"
              >
                Lý do thực hiện {isLocked ? 'mở khóa' : 'khóa'} (tùy chọn)
              </label>
              <textarea
                id="reason"
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={
                  isLocked
                    ? 'Nhập lý do kích hoạt lại tài khoản...'
                    : 'Nhập lý do khóa tài khoản (ví dụ: vi phạm điều khoản sử dụng)...'
                }
                className="mt-1.5 w-full rounded-xl border border-slate-200 p-3 text-xs text-slate-800 placeholder-slate-400 transition-all focus:border-[#00695c] focus:outline-none focus:ring-2 focus:ring-[#00695c]/20"
              />
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
              className={`rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors disabled:opacity-50 ${
                isLocked
                  ? 'bg-[#00695c] hover:bg-[#004d40]'
                  : 'bg-rose-600 hover:bg-rose-700'
              }`}
            >
              {loading
                ? 'Đang xử lý...'
                : isLocked
                  ? 'Xác nhận mở khóa'
                  : 'Xác nhận khóa'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
