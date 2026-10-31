import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchAdminUsers,
  fetchAdminUserStats,
  updateAdminUserStatus,
  type AdminUserItem,
} from '@/api/adminUsers'
import { AdminUserFilters } from './components/AdminUserFilters'
import { AdminUserTable } from './components/AdminUserTable'
import { AdminUserPagination } from './components/AdminUserPagination'
import { UserDetailModal } from './components/UserDetailModal'
import { UserLockModal } from './components/UserLockModal'
import { CheckCircle2, Search, Plus } from 'lucide-react'

export default function AdminUsersPage() {
  const queryClient = useQueryClient()

  // Query & pagination state
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('ALL')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const pageSize = 8

  // Modal state
  const [selectedUserForDetail, setSelectedUserForDetail] = useState<AdminUserItem | null>(null)
  const [selectedUserForLock, setSelectedUserForLock] = useState<AdminUserItem | null>(null)
  const [lockActionLoading, setLockActionLoading] = useState(false)

  // Notification toast state
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 4000)
  }

  // Stats query from database
  const { data: stats = { totalUsers: 0, activeUsers: 0, lockedUsers: 0 } } = useQuery({
    queryKey: ['adminUserStats'],
    queryFn: fetchAdminUserStats,
    staleTime: 30 * 1000,
  })

  // Users query from database
  const { data: usersData, isLoading: usersLoading } = useQuery({
    queryKey: ['adminUsers', { search, role, status, page, pageSize }],
    queryFn: () =>
      fetchAdminUsers({
        search,
        role,
        status,
        page,
        size: pageSize,
      }),
    staleTime: 10 * 1000,
  })

  const users = usersData?.content ?? []
  const totalPages = usersData?.totalPages ?? 1
  const totalCount = usersData?.totalElements ?? stats.totalUsers ?? 0

  // Reset all filters
  const handleResetFilters = () => {
    setSearch('')
    setRole('ALL')
    setStatus('ALL')
    setPage(0)
  }

  // Handle lock/unlock submit
  const handleConfirmLock = async (user: AdminUserItem, reason: string) => {
    setLockActionLoading(true)
    try {
      const nextStatus = user.accountStatus === 'LOCKED' ? 'ACTIVE' : 'LOCKED'
      await updateAdminUserStatus(user.id, nextStatus, reason)

      const actionText = nextStatus === 'ACTIVE' ? 'Mở khóa' : 'Khóa'
      showToast(`${actionText} tài khoản ${user.fullName} thành công!`)

      setSelectedUserForLock(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['adminUsers'] }),
        queryClient.invalidateQueries({ queryKey: ['adminUserStats'] }),
      ])
    } catch (err: unknown) {
      if (err instanceof Error) {
        alert(err.message)
      } else {
        alert('Có lỗi xảy ra khi cập nhật trạng thái người dùng.')
      }
    } finally {
      setLockActionLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 rounded-lg border border-emerald-200 bg-white px-4 py-3 text-xs font-semibold text-emerald-800 shadow-xl">
          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── Top Header Bar (USER MANAGEMENT + COUNT + SEARCH + ADD BUTTON) ── */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        {/* Title & Total Users Pill */}
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-black tracking-tight text-slate-900 uppercase">
            QUẢN LÝ NGƯỜI DÙNG
          </h1>
          <span className="rounded bg-slate-200/90 px-2.5 py-1 text-xs font-bold tracking-wider text-slate-700 uppercase">
            {totalCount.toLocaleString('vi-VN')} NGƯỜI DÙNG
          </span>
        </div>

        {/* Right Action: Search Box & + THÊM NGƯỜI DÙNG */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Quick Search */}
          <div className="relative w-64">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              placeholder="Tìm kiếm theo tên hoặc ID..."
              className="w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-xs text-slate-800 placeholder-slate-400 focus:border-slate-500 focus:outline-none"
            />
          </div>

          {/* + THÊM NGƯỜI DÙNG Button */}
          <button
            type="button"
            onClick={() => alert('Chức năng thêm người dùng đang được hoàn thiện.')}
            className="flex items-center gap-1.5 rounded-md bg-[#f5b823] px-4 py-2 text-xs font-black tracking-wider text-slate-950 uppercase shadow-xs hover:bg-[#e6ab1f] transition-colors"
          >
            <Plus className="h-4 w-4 stroke-[3]" />
            <span>THÊM NGƯỜI DÙNG</span>
          </button>
        </div>
      </div>

      {/* ── Secondary Minimalist Filters (Role, Status, Reset) ── */}
      <AdminUserFilters
        role={role}
        status={status}
        onRoleChange={(val) => {
          setRole(val)
          setPage(0)
        }}
        onStatusChange={(val) => {
          setStatus(val)
          setPage(0)
        }}
        onReset={handleResetFilters}
      />

      {/* ── Minimalist User Data Table ── */}
      <AdminUserTable
        users={users}
        loading={usersLoading}
        onViewDetail={(u) => setSelectedUserForDetail(u)}
        onToggleLock={(u) => setSelectedUserForLock(u)}
      />

      {/* ── Pagination ── */}
      <AdminUserPagination
        currentPage={page}
        totalPages={totalPages}
        size={pageSize}
        totalCount={totalCount}
        onPageChange={(newPage) => setPage(newPage)}
      />

      {/* ── Modals ── */}
      <UserDetailModal
        user={selectedUserForDetail}
        onClose={() => setSelectedUserForDetail(null)}
      />

      <UserLockModal
        user={selectedUserForLock}
        loading={lockActionLoading}
        onClose={() => setSelectedUserForLock(null)}
        onConfirm={handleConfirmLock}
      />
    </div>
  )
}
