import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchInstructorApplications,
  fetchInstructorApplicationStats,
  approveInstructorApplication,
  rejectInstructorApplication,
  type InstructorApplicationItem,
} from '@/api/instructorApplications'
import { ApplicationMetrics } from './components/ApplicationMetrics'
import { ApplicationFilters } from './components/ApplicationFilters'
import { ApplicationTable } from './components/ApplicationTable'
import { ApplicationDetailModal } from './components/ApplicationDetailModal'
import { ApplicationApproveModal } from './components/ApplicationApproveModal'
import { ApplicationRejectModal } from './components/ApplicationRejectModal'
import { AdminUserPagination } from '../users/components/AdminUserPagination'
import { CheckCircle2, UserCheck } from 'lucide-react'

export default function InstructorApplicationsPage() {
  const queryClient = useQueryClient()

  // Filters & pagination state
  const [status, setStatus] = useState('ALL')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const pageSize = 8

  // Modals state
  const [selectedAppForDetail, setSelectedAppForDetail] = useState<InstructorApplicationItem | null>(null)
  const [selectedAppForApprove, setSelectedAppForApprove] = useState<InstructorApplicationItem | null>(null)
  const [selectedAppForReject, setSelectedAppForReject] = useState<InstructorApplicationItem | null>(null)
  const [actionLoading, setActionLoading] = useState(false)

  // Toast alert state
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 4000)
  }

  // Stats query
  const { data: stats = { total: 0, pending: 0, approved: 0, rejected: 0 }, isLoading: statsLoading } =
    useQuery({
      queryKey: ['instructorApplicationStats'],
      queryFn: fetchInstructorApplicationStats,
      staleTime: 30 * 1000,
    })

  // Applications list query
  const { data: appData, isLoading: appLoading } = useQuery({
    queryKey: ['instructorApplications', { status, search, page, pageSize }],
    queryFn: () =>
      fetchInstructorApplications({
        status,
        search,
        page,
        size: pageSize,
      }),
    staleTime: 10 * 1000,
  })

  const applications = appData?.content ?? []
  const totalPages = appData?.totalPages ?? 1
  const totalCount = appData?.totalElements ?? 0

  // Reset filters
  const handleResetFilters = () => {
    setStatus('ALL')
    setSearch('')
    setPage(0)
  }

  // Approve handler
  const handleConfirmApprove = async (app: InstructorApplicationItem) => {
    setActionLoading(true)
    try {
      const res = await approveInstructorApplication(app.id)
      showToast(
        res.message || `Đã phê duyệt thành công hồ sơ của giảng viên ${app.fullName}!`
      )
      setSelectedAppForApprove(null)
      setSelectedAppForDetail(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['instructorApplications'] }),
        queryClient.invalidateQueries({ queryKey: ['instructorApplicationStats'] }),
      ])
    } catch (err: unknown) {
      if (err instanceof Error) {
        alert(err.message)
      } else {
        alert('Có lỗi xảy ra khi phê duyệt đơn đăng ký.')
      }
    } finally {
      setActionLoading(false)
    }
  }

  // Reject handler
  const handleConfirmReject = async (app: InstructorApplicationItem, reason: string) => {
    setActionLoading(true)
    try {
      const res = await rejectInstructorApplication(app.id, reason)
      showToast(
        res.message || `Đã từ chối hồ sơ của giảng viên ${app.fullName} và gửi email thông báo!`
      )
      setSelectedAppForReject(null)
      setSelectedAppForDetail(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['instructorApplications'] }),
        queryClient.invalidateQueries({ queryKey: ['instructorApplicationStats'] }),
      ])
    } catch (err: unknown) {
      if (err instanceof Error) {
        alert(err.message)
      } else {
        alert('Có lỗi xảy ra khi từ chối đơn đăng ký.')
      }
    } finally {
      setActionLoading(false)
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

      {/* ── Page Header ── */}
      <div className="flex flex-col justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Duyệt đơn đăng ký giảng viên
            </h1>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-[#00695c] border border-emerald-200/50">
              <UserCheck className="h-3.5 w-3.5 text-[#00695c]" />
              Xét duyệt hồ sơ
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Thẩm định hồ sơ năng lực, xét duyệt tài khoản và cấp quyền giảng dạy trên hệ thống EduPlatform.
          </p>
        </div>

        {/* Top 4 Metrics Cards */}
        <ApplicationMetrics stats={stats} loading={statsLoading} />
      </div>

      {/* ── Filter Controls ── */}
      <ApplicationFilters
        status={status}
        search={search}
        totalCount={totalCount}
        pendingCount={stats.pending}
        page={page}
        size={pageSize}
        onStatusChange={(val) => {
          setStatus(val)
          setPage(0)
        }}
        onSearchChange={(val) => {
          setSearch(val)
          setPage(0)
        }}
        onReset={handleResetFilters}
      />

      {/* ── Application Data Table ── */}
      <ApplicationTable
        applications={applications}
        loading={appLoading}
        onViewDetail={(app) => setSelectedAppForDetail(app)}
        onApprove={(app) => setSelectedAppForApprove(app)}
        onReject={(app) => setSelectedAppForReject(app)}
      />

      {/* ── Pagination ── */}
      <AdminUserPagination
        currentPage={page}
        totalPages={totalPages}
        size={pageSize}
        onPageChange={(newPage) => setPage(newPage)}
      />

      {/* ── Modals ── */}
      <ApplicationDetailModal
        application={selectedAppForDetail}
        onClose={() => setSelectedAppForDetail(null)}
        onApprove={(app) => setSelectedAppForApprove(app)}
        onReject={(app) => setSelectedAppForReject(app)}
      />

      <ApplicationApproveModal
        application={selectedAppForApprove}
        loading={actionLoading}
        onClose={() => setSelectedAppForApprove(null)}
        onConfirm={handleConfirmApprove}
      />

      <ApplicationRejectModal
        application={selectedAppForReject}
        loading={actionLoading}
        onClose={() => setSelectedAppForReject(null)}
        onConfirm={handleConfirmReject}
      />
    </div>
  )
}
