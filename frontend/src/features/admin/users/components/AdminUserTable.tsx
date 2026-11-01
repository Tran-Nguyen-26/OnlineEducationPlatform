import { Eye, Lock, Unlock } from 'lucide-react'
import type { AdminUserItem, UserRole, AccountStatus } from '@/api/adminUsers'

interface AdminUserTableProps {
  users: AdminUserItem[]
  loading?: boolean
  onViewDetail: (user: AdminUserItem) => void
  onToggleLock: (user: AdminUserItem) => void
}

function getAvatarStyle(fullName: string, role: UserRole): { bg: string; text: string; initials: string } {
  // Extract initials (up to 2 letters)
  const parts = fullName.replace(/^(TS\.|ThS\.|KS\.|PGS\.|GS\.)\s*/i, '').trim().split(/\s+/)
  let initials = ''
  if (parts.length >= 2) {
    initials = `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
  } else if (parts.length === 1 && parts[0].length > 0) {
    initials = parts[0].slice(0, 2).toUpperCase()
  } else {
    initials = 'US'
  }

  if (role === 'ADMIN') {
    return { bg: 'bg-[#00695c]', text: 'text-white font-semibold', initials }
  }

  // Consistent color mapping based on initials
  const palette = [
    { bg: 'bg-emerald-100', text: 'text-emerald-800' },
    { bg: 'bg-blue-100', text: 'text-blue-800' },
    { bg: 'bg-teal-100', text: 'text-teal-800' },
    { bg: 'bg-indigo-100', text: 'text-indigo-800' },
    { bg: 'bg-rose-100', text: 'text-rose-800' },
    { bg: 'bg-purple-100', text: 'text-purple-800' },
    { bg: 'bg-amber-100', text: 'text-amber-800' },
    { bg: 'bg-cyan-100', text: 'text-cyan-800' },
  ]
  const charCode = (initials.charCodeAt(0) + (initials.charCodeAt(1) || 0)) % palette.length
  return { ...palette[charCode], initials }
}

function formatDate(dateString: string): string {
  try {
    const d = new Date(dateString)
    if (isNaN(d.getTime())) return dateString
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${day}/${month}/${year}`
  } catch {
    return dateString
  }
}

function renderRoleBadge(role: UserRole) {
  switch (role) {
    case 'STUDENT':
      return (
        <span className="inline-flex items-center rounded-full bg-[#dbeafe] px-3 py-1 text-xs font-medium text-[#1e40af]">
          Học viên (STUDENT)
        </span>
      )
    case 'INSTRUCTOR':
      return (
        <span className="inline-flex items-center rounded-full bg-[#ede9fe] px-3 py-1 text-xs font-medium text-[#5b21b6]">
          Giảng viên (INSTRUCTOR)
        </span>
      )
    case 'ADMIN':
      return (
        <span className="inline-flex items-center rounded-full bg-[#d1fae5] px-3 py-1 text-xs font-semibold text-[#065f46]">
          Quản trị viên (ADMIN)
        </span>
      )
    default:
      return (
        <span className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
          {role}
        </span>
      )
  }
}

function renderStatusBadge(status: AccountStatus) {
  const isLocked = status === 'LOCKED' || status === 'SUSPENDED'

  if (isLocked) {
    return (
      <div className="inline-flex items-center gap-1.5 rounded-full bg-[#fee2e2] px-3 py-1 text-xs font-semibold text-[#991b1b]">
        <span className="h-2 w-1 rounded-full bg-[#ef4444]" />
        <span>Đã khóa</span>
      </div>
    )
  }

  return (
    <div className="inline-flex items-center gap-1.5 rounded-full bg-[#e6f4ea] px-3 py-1 text-xs font-semibold text-[#065f46]">
      <span className="h-2 w-1 rounded-full bg-[#10b981]" />
      <span>Đang hoạt động</span>
    </div>
  )
}

export function AdminUserTable({
  users,
  loading,
  onViewDetail,
  onToggleLock,
}: AdminUserTableProps) {
  if (loading) {
    return (
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xs">
        <div className="flex h-96 flex-col items-center justify-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#00695c] border-t-transparent" />
          <p className="text-sm font-medium text-slate-500">Đang tải danh sách người dùng...</p>
        </div>
      </div>
    )
  }

  if (users.length === 0) {
    return (
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-12 text-center shadow-xs">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
          <Eye className="h-6 w-6" />
        </div>
        <h3 className="text-base font-semibold text-slate-800">Không tìm thấy người dùng</h3>
        <p className="mt-1 text-sm text-slate-500">
          Không có tài khoản nào phù hợp với bộ lọc tìm kiếm hiện tại.
        </p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-200/80 bg-white shadow-xs">
      <table className="w-full text-left text-sm text-slate-600">
        <thead className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-bold tracking-wider text-slate-400 uppercase">
          <tr>
            <th scope="col" className="px-6 py-4 font-semibold">
              STT
            </th>
            <th scope="col" className="px-6 py-4 font-semibold">
              Họ và tên
            </th>
            <th scope="col" className="px-6 py-4 font-semibold">
              Email
            </th>
            <th scope="col" className="px-6 py-4 font-semibold">
              Vai trò
            </th>
            <th scope="col" className="px-6 py-4 font-semibold">
              Ngày tạo tài khoản
            </th>
            <th scope="col" className="px-6 py-4 font-semibold">
              Trạng thái
            </th>
            <th scope="col" className="px-6 py-4 text-right font-semibold">
              Thao tác
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {users.map((user) => {
            const avatar = getAvatarStyle(user.fullName, user.role)
            const isLocked = user.accountStatus === 'LOCKED' || user.accountStatus === 'SUSPENDED'
            const isAdmin = user.role === 'ADMIN'

            return (
              <tr
                key={user.id}
                className="transition-colors hover:bg-slate-50/70"
              >
                {/* STT */}
                <td className="px-6 py-4.5 font-mono text-xs text-slate-400">
                  {user.stt || '01'}
                </td>

                {/* Họ và tên with Avatar */}
                <td className="px-6 py-4.5">
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-bold ${avatar.bg} ${avatar.text}`}
                    >
                      {avatar.initials}
                    </div>
                    <span className="font-semibold text-slate-900">
                      {user.fullName}
                    </span>
                  </div>
                </td>

                {/* Email */}
                <td className="px-6 py-4.5 font-mono text-xs text-slate-600">
                  {user.email}
                </td>

                {/* Vai trò */}
                <td className="px-6 py-4.5 whitespace-nowrap">
                  {renderRoleBadge(user.role)}
                </td>

                {/* Ngày tạo tài khoản */}
                <td className="px-6 py-4.5 text-slate-600 whitespace-nowrap">
                  {formatDate(user.createdAt)}
                </td>

                {/* Trạng thái */}
                <td className="px-6 py-4.5 whitespace-nowrap">
                  {renderStatusBadge(user.accountStatus)}
                </td>

                {/* Thao tác */}
                <td className="px-6 py-4.5 text-right whitespace-nowrap">
                  <div className="flex items-center justify-end gap-3.5">
                    {/* Xem chi tiết button */}
                    <button
                      type="button"
                      onClick={() => onViewDetail(user)}
                      className="inline-flex items-center gap-1.5 text-xs font-medium text-[#00695c] transition-colors hover:text-[#004d40] hover:underline"
                    >
                      <Eye className="h-4 w-4" />
                      <span>Xem chi tiết</span>
                    </button>

                    {/* Khóa / Mở khóa button (hidden for ADMIN accounts) */}
                    {!isAdmin && (
                      <>
                        {isLocked ? (
                          <button
                            type="button"
                            onClick={() => onToggleLock(user)}
                            className="inline-flex items-center gap-1 text-xs font-medium text-[#00695c] transition-colors hover:text-[#004d40] hover:underline"
                          >
                            <Unlock className="h-4 w-4" />
                            <span>Mở khóa</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => onToggleLock(user)}
                            className="inline-flex items-center gap-1 text-xs font-medium text-rose-600 transition-colors hover:text-rose-700 hover:underline"
                          >
                            <Lock className="h-4 w-4" />
                            <span>Khóa</span>
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
