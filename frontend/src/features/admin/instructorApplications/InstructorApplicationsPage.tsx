import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchInstructorApplications,
  approveInstructorApplication,
  rejectInstructorApplication,
  type InstructorApplicationItem,
} from '@/api/instructorApplications'
import { ApplicationFilters } from './components/ApplicationFilters'
import { ApplicationTable } from './components/ApplicationTable'
import { ApplicationDetailModal } from './components/ApplicationDetailModal'
import { ApplicationApproveModal } from './components/ApplicationApproveModal'
import { ApplicationRejectModal } from './components/ApplicationRejectModal'
import { AdminUserPagination } from '../users/components/AdminUserPagination'
import { CheckCircle2, Search } from 'lucide-react'

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
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 rounded-lg border border-emerald-200 bg-white px-4 py-3 text-xs font-semibold text-emerald-800 shadow-xl">
          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── Top Header Bar (DUYỆT GIẢNG VIÊN + COUNT + SEARCH) ── */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        {/* Title & Total Count Pill */}
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-black tracking-tight text-slate-900 uppercase">
            DUYỆT GIẢNG VIÊN
          </h1>
          <span className="rounded bg-slate-200/90 px-2.5 py-1 text-xs font-bold tracking-wider text-slate-700 uppercase">
            {totalCount.toLocaleString('vi-VN')} HỒ SƠ
          </span>
        </div>

        {/* Right Action: Quick Search Box */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative w-64">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              placeholder="Tìm kiếm theo tên hoặc email..."
              className="w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-xs text-slate-800 placeholder-slate-400 focus:border-slate-500 focus:outline-none"
            />
          </div>
        </div>
      </div>

      {/* ── Secondary Minimalist Filters (Status & Reset) ── */}
      <ApplicationFilters
        status={status}
        onStatusChange={(val) => {
          setStatus(val)
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
        totalCount={totalCount}
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
