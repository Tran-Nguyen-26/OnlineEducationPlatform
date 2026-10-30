import { Eye, CheckCircle2, XCircle, Clock, Building2, Briefcase } from 'lucide-react'
import type { InstructorApplicationItem, InstructorApplicationStatus } from '@/api/instructorApplications'

interface ApplicationTableProps {
  applications: InstructorApplicationItem[]
  loading?: boolean
  onViewDetail: (app: InstructorApplicationItem) => void
  onApprove: (app: InstructorApplicationItem) => void
  onReject: (app: InstructorApplicationItem) => void
}

function getAvatarStyle(fullName: string): { bg: string; text: string; initials: string } {
  const parts = fullName.replace(/^(TS\.|ThS\.|KS\.|PGS\.|GS\.)\s*/i, '').trim().split(/\s+/)
  let initials = ''
  if (parts.length >= 2) {
    initials = `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
  } else if (parts.length === 1 && parts[0].length > 0) {
    initials = parts[0].slice(0, 2).toUpperCase()
  } else {
    initials = 'GV'
  }

  const palette = [
    { bg: 'bg-emerald-100', text: 'text-emerald-800' },
    { bg: 'bg-blue-100', text: 'text-blue-800' },
    { bg: 'bg-teal-100', text: 'text-teal-800' },
    { bg: 'bg-indigo-100', text: 'text-indigo-800' },
    { bg: 'bg-purple-100', text: 'text-purple-800' },
    { bg: 'bg-amber-100', text: 'text-amber-800' },
    { bg: 'bg-cyan-100', text: 'text-cyan-800' },
  ]
  const charCode = (initials.charCodeAt(0) + (initials.charCodeAt(1) || 0)) % palette.length
  return { ...palette[charCode], initials }
}

function formatDateTime(dateString: string): string {
  try {
    const d = new Date(dateString)
    if (isNaN(d.getTime())) return dateString
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    return `${day}/${month}/${year} ${hours}:${minutes}`
  } catch {
    return dateString
  }
}

function renderStatusBadge(status: InstructorApplicationStatus) {
  switch (status) {
    case 'PENDING':
      return (
        <div className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 border border-amber-200/60 px-3 py-1 text-xs font-semibold text-amber-800">
          <span className="h-2 w-1 rounded-full bg-amber-500" />
          <span>Chờ xét duyệt</span>
        </div>
      )
    case 'APPROVED':
      return (
        <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-200/60 px-3 py-1 text-xs font-semibold text-[#00695c]">
          <span className="h-2 w-1 rounded-full bg-[#00695c]" />
          <span>Đã phê duyệt</span>
        </div>
      )
    case 'REJECTED':
      return (
        <div className="inline-flex items-center gap-1.5 rounded-full bg-rose-50 border border-rose-200/60 px-3 py-1 text-xs font-semibold text-rose-800">
          <span className="h-2 w-1 rounded-full bg-rose-500" />
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
  if (loading) {
    return (
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xs">
        <div className="flex h-80 flex-col items-center justify-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#00695c] border-t-transparent" />
          <p className="text-sm font-medium text-slate-500">Đang tải danh sách đơn đăng ký...</p>
        </div>
      </div>
    )
  }

  if (applications.length === 0) {
    return (
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-12 text-center shadow-xs">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
          <Clock className="h-6 w-6" />
        </div>
        <h3 className="text-base font-semibold text-slate-800">Không tìm thấy đơn đăng ký nào</h3>
        <p className="mt-1 text-sm text-slate-500">
          Không có hồ sơ nào phù hợp với bộ lọc tìm kiếm hiện tại.
        </p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-200/80 bg-white shadow-xs">
      <table className="w-full text-left text-sm text-slate-600">
        <thead className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-bold tracking-wider text-slate-400 uppercase">
          <tr>
            <th scope="col" className="px-5 py-4 font-semibold">
              STT
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Ứng viên
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Đơn vị & Chức danh
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Chuyên môn & Kinh nghiệm
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Ngày nộp đơn
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Trạng thái
            </th>
            <th scope="col" className="px-5 py-4 text-right font-semibold">
              Thao tác
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {applications.map((app) => {
            const avatar = getAvatarStyle(app.fullName)
            const isPending = app.status === 'PENDING'

            return (
              <tr
                key={app.id}
                className="transition-colors hover:bg-slate-50/70"
              >
                {/* STT */}
                <td className="px-5 py-4 font-mono text-xs text-slate-400">
                  {app.stt || '01'}
                </td>

                {/* Ứng viên */}
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-xs font-bold ${avatar.bg} ${avatar.text}`}
                    >
                      {avatar.initials}
                    </div>
                    <div>
                      <div className="font-semibold text-slate-900 leading-tight">
                        {app.fullName}
                      </div>
                      <div className="font-mono text-xs text-slate-500 mt-0.5">
                        {app.email}
                      </div>
                      {app.phone && (
                        <div className="text-[11px] text-slate-400">
                          {app.phone}
                        </div>
                      )}
                    </div>
                  </div>
                </td>

                {/* Đơn vị & Chức danh */}
                <td className="px-5 py-4">
                  <div className="flex items-start gap-1.5">
                    <Building2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" />
                    <div>
                      <div className="font-medium text-slate-800 leading-tight">
                        {app.organization || 'Chưa cập nhật'}
                      </div>
                      {app.jobTitle && (
                        <div className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                          <Briefcase className="h-3 w-3 text-slate-400" />
                          <span>{app.jobTitle}</span>
                        </div>
                      )}
                    </div>
                  </div>
                </td>

                {/* Chuyên môn & Kinh nghiệm */}
                <td className="px-5 py-4">
                  <div>
                    <div className="font-medium text-slate-800">
                      {app.expertise || 'Chuyên môn chung'}
                    </div>
                    <div className="mt-0.5 inline-flex items-center rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                      {app.yearsOfExperience != null
                        ? `${app.yearsOfExperience} năm kinh nghiệm`
                        : 'Chưa rõ năm KN'}
                    </div>
                  </div>
                </td>

                {/* Ngày nộp */}
                <td className="px-5 py-4 text-xs text-slate-500 whitespace-nowrap">
                  {formatDateTime(app.submittedAt)}
                </td>

                {/* Trạng thái */}
                <td className="px-5 py-4 whitespace-nowrap">
                  {renderStatusBadge(app.status)}
                </td>

                {/* Thao tác */}
                <td className="px-5 py-4 text-right whitespace-nowrap">
                  <div className="flex items-center justify-end gap-2.5">
                    {/* View detail button */}
                    <button
                      type="button"
                      onClick={() => onViewDetail(app)}
                      className="inline-flex items-center gap-1 text-xs font-semibold text-[#00695c] transition-colors hover:text-[#004d40] hover:underline"
                    >
                      <Eye className="h-4 w-4" />
                      <span>Xem chi tiết</span>
                    </button>

                    {/* Approve & Reject buttons for PENDING apps */}
                    {isPending && (
                      <>
                        <button
                          type="button"
                          onClick={() => onApprove(app)}
                          title="Phê duyệt hồ sơ"
                          className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 px-2.5 py-1.5 text-xs font-semibold text-[#00695c] transition-colors hover:bg-emerald-100"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          <span>Duyệt</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => onReject(app)}
                          title="Từ chối hồ sơ"
                          className="inline-flex items-center gap-1 rounded-lg bg-rose-50 px-2.5 py-1.5 text-xs font-semibold text-rose-700 transition-colors hover:bg-rose-100"
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          <span>Từ chối</span>
                        </button>
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
