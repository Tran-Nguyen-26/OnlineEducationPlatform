import { useState, useRef, useEffect } from 'react'
import { MoreVertical, Eye, CheckCircle2, XCircle } from 'lucide-react'
import type { InstructorApplicationItem, InstructorApplicationStatus } from '@/api/instructorApplications'

interface ApplicationTableProps {
  applications: InstructorApplicationItem[]
  loading?: boolean
  onViewDetail: (app: InstructorApplicationItem) => void
  onApprove: (app: InstructorApplicationItem) => void
  onReject: (app: InstructorApplicationItem) => void
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

function renderStatusText(status: InstructorApplicationStatus) {
  switch (status) {
    case 'PENDING':
      return (
        <div className="inline-flex items-center gap-1.5 text-xs text-amber-600 font-medium">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          <span>Chờ xét duyệt</span>
        </div>
      )
    case 'APPROVED':
      return (
        <div className="inline-flex items-center gap-1.5 text-xs text-emerald-600 font-medium">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
          <span>Đã phê duyệt</span>
        </div>
      )
    case 'REJECTED':
      return (
        <div className="inline-flex items-center gap-1.5 text-xs text-rose-600 font-medium">
          <span className="h-1.5 w-1.5 rounded-full bg-rose-500" />
          <span>Đã từ chối</span>
        </div>
      )
  }
}

export function ApplicationTable({
  applications,
  loading,
  onViewDetail,
  onApprove,
  onReject,
}: ApplicationTableProps) {
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
          <p className="text-xs font-medium text-slate-500">Đang tải danh sách hồ sơ giảng viên...</p>
        </div>
      </div>
    )
  }

  if (applications.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-12 text-center shadow-xs">
        <h3 className="text-sm font-semibold text-slate-800">Không tìm thấy hồ sơ nào</h3>
        <p className="mt-1 text-xs text-slate-500">
          Không có đơn đăng ký giảng viên nào phù hợp với bộ lọc tìm kiếm hiện tại.
        </p>
      </div>
    )
  }

  return (
    <div className="relative rounded-lg border border-slate-200 bg-white shadow-xs">
      <table className="w-full text-left text-xs text-slate-600">
        <thead className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-bold tracking-wider text-slate-500 uppercase">
          <tr>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Họ và tên
            </th>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Email
            </th>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Đơn vị công tác
            </th>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Chuyên môn
            </th>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Ngày nộp
            </th>
            <th scope="col" className="px-5 py-3.5 font-bold">
              Trạng thái
            </th>
            <th scope="col" className="px-5 py-3.5 text-right font-bold">
              Thao tác
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {applications.map((app) => {
            const isPending = app.status === 'PENDING'
            const isMenuOpen = activeMenuId === app.id

            return (
              <tr
                key={app.id}
                className="transition-colors hover:bg-slate-50/60"
              >
                {/* 1. Họ và tên - Duy nhất 1 thông tin */}
                <td className="px-5 py-4 font-semibold text-slate-900 whitespace-nowrap">
                  {app.fullName}
                </td>

                {/* 2. Email - Duy nhất 1 thông tin */}
                <td className="px-5 py-4 font-mono text-xs text-slate-600 whitespace-nowrap">
                  {app.email}
                </td>

                {/* 3. Đơn vị công tác - Duy nhất 1 thông tin */}
                <td className="px-5 py-4 text-slate-700 whitespace-nowrap">
                  {app.organization || 'Chưa cập nhật'}
                </td>

                {/* 4. Chuyên môn - Duy nhất 1 thông tin */}
                <td className="px-5 py-4 text-slate-700 whitespace-nowrap">
                  {app.expertise || 'Chuyên môn chung'}
                </td>

                {/* 5. Ngày nộp - Duy nhất 1 thông tin */}
                <td className="px-5 py-4 text-slate-500 whitespace-nowrap">
                  {formatDate(app.submittedAt)}
                </td>

                {/* 6. Trạng thái - Tối giản, không nền badge màu lòe loẹt */}
                <td className="px-5 py-4 whitespace-nowrap">
                  {renderStatusText(app.status)}
                </td>

                {/* 7. Thao tác - Menu 3 chấm (⋮) */}
                <td className="px-5 py-4 text-right whitespace-nowrap relative">
                  <div className="relative inline-block text-left">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        setActiveMenuId(isMenuOpen ? null : app.id)
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
                        className="absolute right-0 top-full mt-1 w-40 rounded-lg border border-slate-200 bg-white py-1 shadow-lg z-30"
                      >
                        <button
                          type="button"
                          onClick={() => {
                            setActiveMenuId(null)
                            onViewDetail(app)
                          }}
                          className="flex w-full items-center gap-2 px-3 py-2 text-xs text-slate-700 hover:bg-slate-50 transition-colors"
                        >
                          <Eye className="h-3.5 w-3.5 text-slate-500" />
                          <span>Xem chi tiết</span>
                        </button>

                        {isPending && (
                          <>
                            <button
                              type="button"
                              onClick={() => {
                                setActiveMenuId(null)
                                onApprove(app)
                              }}
                              className="flex w-full items-center gap-2 px-3 py-2 text-xs text-emerald-700 hover:bg-emerald-50 transition-colors"
                            >
                              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                              <span>Phê duyệt</span>
                            </button>

                            <button
                              type="button"
                              onClick={() => {
                                setActiveMenuId(null)
                                onReject(app)
                              }}
                              className="flex w-full items-center gap-2 px-3 py-2 text-xs text-rose-600 hover:bg-rose-50 transition-colors"
                            >
                              <XCircle className="h-3.5 w-3.5 text-rose-500" />
                              <span>Từ chối</span>
                            </button>
                          </>
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
