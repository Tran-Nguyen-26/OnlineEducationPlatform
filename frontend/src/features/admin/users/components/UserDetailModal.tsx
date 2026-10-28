import { X, Mail, Phone, Building2, Calendar, Clock, Shield } from 'lucide-react'
import type { AdminUserItem } from '@/api/adminUsers'

interface UserDetailModalProps {
  user: AdminUserItem | null
  onClose: () => void
}

export function UserDetailModal({ user, onClose }: UserDetailModalProps) {
  if (!user) return null

  const isLocked = user.accountStatus === 'LOCKED' || user.accountStatus === 'SUSPENDED'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="flex items-center gap-2">
            <Shield className="h-5 w-5 text-[#00695c]" />
            <h3 className="text-base font-bold text-slate-900">Chi tiết tài khoản</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="max-h-[80vh] overflow-y-auto px-6 py-5">
          {/* User Profile Header */}
          <div className="flex items-center gap-4 rounded-xl bg-slate-50 p-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-[#00695c] text-lg font-bold text-white shadow-xs">
              {user.fullName.slice(0, 2).toUpperCase()}
            </div>
            <div>
              <h4 className="text-lg font-bold text-slate-900">{user.fullName}</h4>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <span className="rounded-full bg-slate-200/80 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
                  {user.role}
                </span>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    isLocked
                      ? 'bg-rose-100 text-rose-700'
                      : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {isLocked ? 'Đã khóa' : 'Đang hoạt động'}
                </span>
              </div>
            </div>
          </div>

          {/* Details Grid */}
          <div className="mt-5 space-y-3.5 text-sm">
            <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
              <Mail className="mt-0.5 h-4 w-4 text-slate-400" />
              <div className="flex-1">
                <div className="text-xs text-slate-400">Địa chỉ Email</div>
                <div className="font-mono text-sm font-medium text-slate-800">{user.email}</div>
              </div>
            </div>

            <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
              <Phone className="mt-0.5 h-4 w-4 text-slate-400" />
              <div className="flex-1">
                <div className="text-xs text-slate-400">Số điện thoại</div>
                <div className="font-medium text-slate-800">{user.phone || 'Chưa cập nhật'}</div>
              </div>
            </div>

            <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
              <Building2 className="mt-0.5 h-4 w-4 text-slate-400" />
              <div className="flex-1">
                <div className="text-xs text-slate-400">Tổ chức / Đơn vị</div>
                <div className="font-medium text-slate-800">{user.organization || 'Không có'}</div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
                <Calendar className="mt-0.5 h-4 w-4 text-slate-400" />
                <div className="flex-1">
                  <div className="text-xs text-slate-400">Ngày sinh</div>
                  <div className="font-medium text-slate-800">
                    {user.dateOfBirth || 'Chưa cập nhật'}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
                <Clock className="mt-0.5 h-4 w-4 text-slate-400" />
                <div className="flex-1">
                  <div className="text-xs text-slate-400">Ngày tạo tài khoản</div>
                  <div className="font-medium text-slate-800">
                    {new Date(user.createdAt).toLocaleDateString('vi-VN')}
                  </div>
                </div>
              </div>
            </div>

            {user.bio && (
              <div className="rounded-lg border border-slate-100 p-3">
                <div className="text-xs text-slate-400">Giới thiệu</div>
                <p className="mt-1 text-sm text-slate-700 leading-relaxed">{user.bio}</p>
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end border-t border-slate-100 bg-slate-50/50 px-6 py-3.5">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  )
}
