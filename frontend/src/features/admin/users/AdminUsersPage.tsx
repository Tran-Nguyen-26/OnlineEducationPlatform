import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchAdminUsers,
  fetchAdminUserStats,
  updateAdminUserStatus,
  type AdminUserItem,
} from '@/api/adminUsers'
import { AdminUserMetrics } from './components/AdminUserMetrics'
import { AdminUserFilters } from './components/AdminUserFilters'
import { AdminUserTable } from './components/AdminUserTable'
import { AdminUserPagination } from './components/AdminUserPagination'
import { UserDetailModal } from './components/UserDetailModal'
import { UserLockModal } from './components/UserLockModal'
import { CheckCircle2 } from 'lucide-react'

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
  const { data: stats = { totalUsers: 0, activeUsers: 0, lockedUsers: 0 }, isLoading: statsLoading } =
    useQuery({
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
  const totalCount = usersData?.totalElements ?? 0

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
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800 shadow-lg">
          <CheckCircle2 className="h-5 w-5 text-emerald-600" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── Page Header & Metrics ── */}
      <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-center">
        {/* Title area */}
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Quản lý người dùng
            </h1>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#dbeafe] px-2.5 py-0.5 text-xs font-semibold text-[#1e40af]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#2563eb]" />
              Hệ thống xác thực
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Xem và quản lý tài khoản trên hệ thống, phân quyền truy cập và kiểm soát trạng thái.
          </p>
        </div>

        {/* Top right metric cards */}
        <AdminUserMetrics stats={stats} loading={statsLoading} />
      </div>

      {/* ── Search & Filter Controls ── */}
      <AdminUserFilters
        search={search}
        role={role}
        status={status}
        totalCount={totalCount}
        page={page}
        size={pageSize}
        onSearchChange={(val) => {
          setSearch(val)
          setPage(0)
        }}
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

      {/* ── User Data Table ── */}
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
