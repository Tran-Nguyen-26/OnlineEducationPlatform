import { useState, useRef, useEffect } from 'react'
import { MoreVertical, Eye, Lock, Unlock } from 'lucide-react'
import type { AdminUserItem, UserRole, AccountStatus } from '@/api/adminUsers'

interface AdminUserTableProps {
  users: AdminUserItem[]
  loading?: boolean
  onViewDetail: (user: AdminUserItem) => void
  onToggleLock: (user: AdminUserItem) => void
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

function renderRoleText(role: UserRole) {
  switch (role) {
    case 'STUDENT':
      return 'Học viên'
    case 'INSTRUCTOR':
      return 'Giảng viên'
    case 'ADMIN':
      return 'Quản trị viên'
    default:
      return role
  }
}

function renderStatusText(status: AccountStatus) {
  const isLocked = status === 'LOCKED' || status === 'SUSPENDED'
  if (isLocked) {
    return (
      <div className="inline-flex items-center gap-1.5 text-xs text-rose-600 font-medium">
        <span className="h-1.5 w-1.5 rounded-full bg-rose-500" />
        <span>Đã khóa</span>
      </div>
    )
  }
  return (
    <div className="inline-flex items-center gap-1.5 text-xs text-emerald-600 font-medium">
      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
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
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null)
  const menuRef = useRef<HTMLDivElement | null>(null)

  // Close popup menu when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setActiveMenuId(null)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [])

  if (loading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white shadow-xs">
        <div className="flex h-80 flex-col items-center justify-center gap-3">
          <div className="h-7 w-7 animate-spin rounded-full border-2 border-[#f5b823] border-t-transparent" />
          <p className="text-xs font-medium text-slate-500">Đang tải danh sách người dùng...</p>
        </div>
      </div>
    )
  }

  if (users.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-12 text-center shadow-xs">
        <h3 className="text-sm font-semibold text-slate-800">Không tìm thấy người dùng</h3>
        <p className="mt-1 text-xs text-slate-500">
          Không có tài khoản nào phù hợp với bộ lọc tìm kiếm hiện tại.
        </p>
      </div>
    )
  }

  return (
    <div className="relative rounded-lg border border-slate-200 bg-white shadow-xs">
      <table className="w-full text-left text-xs text-slate-600">
        <thead className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-bold tracking-wider text-slate-500 uppercase">
          <tr>
            <th scope="col" className="px-6 py-3.5 font-bold">
              Họ và tên
            </th>
            <th scope="col" className="px-6 py-3.5 font-bold">
              Email
            </th>
            <th scope="col" className="px-6 py-3.5 font-bold">
              Vai trò
            </th>
            <th scope="col" className="px-6 py-3.5 font-bold">
              Ngày tạo
            </th>
            <th scope="col" className="px-6 py-3.5 font-bold">
              Trạng thái
            </th>
            <th scope="col" className="px-6 py-3.5 text-right font-bold">
              Thao tác
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {users.map((user) => {
            const isLocked = user.accountStatus === 'LOCKED' || user.accountStatus === 'SUSPENDED'
            const isAdmin = user.role === 'ADMIN'
            const isMenuOpen = activeMenuId === user.id

            return (
              <tr
                key={user.id}
                className="transition-colors hover:bg-slate-50/60"
              >
                {/* 1. Họ và tên - Duy nhất 1 thông tin */}
                <td className="px-6 py-4 font-semibold text-slate-900 whitespace-nowrap">
                  {user.fullName}
                </td>

                {/* 2. Email - Duy nhất 1 thông tin */}
                <td className="px-6 py-4 font-mono text-xs text-slate-600 whitespace-nowrap">
                  {user.email}
                </td>

                {/* 3. Vai trò - Tối giản không màu sắc, duy nhất 1 thông tin */}
                <td className="px-6 py-4 text-slate-700 whitespace-nowrap">
                  {renderRoleText(user.role)}
                </td>

                {/* 4. Ngày tạo - Duy nhất 1 thông tin */}
                <td className="px-6 py-4 text-slate-500 whitespace-nowrap">
                  {formatDate(user.createdAt)}
                </td>

                {/* 5. Trạng thái - Tối giản, không nền badge màu lòe loẹt */}
                <td className="px-6 py-4 whitespace-nowrap">
                  {renderStatusText(user.accountStatus)}
                </td>

                {/* 6. Thao tác - Menu 3 chấm (⋮) tối giản */}
                <td className="px-6 py-4 text-right whitespace-nowrap relative">
                  <div className="relative inline-block text-left">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        setActiveMenuId(isMenuOpen ? null : user.id)
                      }}
                      className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
                      title="Thao tác"
                    >
                      <MoreVertical className="h-4 w-4" />
                    </button>

                    {/* Popover Action Menu */}
                    {isMenuOpen && (
                      <div
                        ref={menuRef}
                        className="absolute right-0 top-full mt-1 w-36 rounded-lg border border-slate-200 bg-white py-1 shadow-lg z-30"
                      >
                        <button
                          type="button"
                          onClick={() => {
                            setActiveMenuId(null)
                            onViewDetail(user)
                          }}
                          className="flex w-full items-center gap-2 px-3 py-2 text-xs text-slate-700 hover:bg-slate-50 transition-colors"
                        >
                          <Eye className="h-3.5 w-3.5 text-slate-500" />
                          <span>Xem chi tiết</span>
                        </button>

                        {!isAdmin && (
                          <button
                            type="button"
                            onClick={() => {
                              setActiveMenuId(null)
                              onToggleLock(user)
                            }}
                            className={`flex w-full items-center gap-2 px-3 py-2 text-xs transition-colors ${
                              isLocked
                                ? 'text-emerald-700 hover:bg-emerald-50'
                                : 'text-rose-600 hover:bg-rose-50'
                            }`}
                          >
                            {isLocked ? (
                              <>
                                <Unlock className="h-3.5 w-3.5 text-emerald-600" />
                                <span>Mở khóa</span>
                              </>
                            ) : (
                              <>
                                <Lock className="h-3.5 w-3.5 text-rose-500" />
                                <span>Khóa</span>
                              </>
                            )}
                          </button>
                        )}
                      </div>
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
