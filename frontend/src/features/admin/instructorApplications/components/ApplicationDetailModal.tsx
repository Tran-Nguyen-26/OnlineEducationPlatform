import {
  X,
  Mail,
  Phone,
  Building2,
  Briefcase,
  Award,
  BookOpen,
  HeartHandshake,
  CheckCircle2,
  XCircle,
  AlertTriangle,
} from 'lucide-react'
import type { InstructorApplicationItem } from '@/api/instructorApplications'

interface ApplicationDetailModalProps {
  application: InstructorApplicationItem | null
  onClose: () => void
  onApprove: (app: InstructorApplicationItem) => void
  onReject: (app: InstructorApplicationItem) => void
}

export function ApplicationDetailModal({
  application,
  onClose,
  onApprove,
  onReject,
}: ApplicationDetailModalProps) {
  if (!application) return null

  const isPending = application.status === 'PENDING'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-2xl overflow-hidden rounded-2xl bg-white shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="flex items-center gap-2.5">
            <BookOpen className="h-5 w-5 text-[#00695c]" />
            <h3 className="text-base font-bold text-slate-900">
              Chi tiết hồ sơ đăng ký giảng viên
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="max-h-[75vh] overflow-y-auto px-6 py-5 space-y-5">
          {/* Candidate Summary Card */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl bg-slate-50 p-4 border border-slate-100">
            <div className="flex items-center gap-4">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#00695c] text-lg font-bold text-white shadow-xs">
                {application.fullName.slice(0, 2).toUpperCase()}
              </div>
              <div>
                <h4 className="text-lg font-bold text-slate-900">{application.fullName}</h4>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-slate-200/80 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
                    Ứng viên Giảng viên
                  </span>
                  {application.status === 'PENDING' && (
                    <span className="rounded-full bg-amber-100 text-amber-800 px-2.5 py-0.5 text-xs font-semibold">
                      Chờ xét duyệt
                    </span>
                  )}
                  {application.status === 'APPROVED' && (
                    <span className="rounded-full bg-emerald-100 text-[#00695c] px-2.5 py-0.5 text-xs font-semibold">
                      Đã phê duyệt
                    </span>
                  )}
                  {application.status === 'REJECTED' && (
                    <span className="rounded-full bg-rose-100 text-rose-800 px-2.5 py-0.5 text-xs font-semibold">
                      Đã từ chối
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="text-xs text-slate-500 sm:text-right">
              <div>Ngày gửi hồ sơ</div>
              <div className="font-semibold text-slate-700 mt-0.5">
                {new Date(application.submittedAt).toLocaleDateString('vi-VN', {
                  day: '2-digit',
                  month: '2-digit',
                  year: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </div>
            </div>
          </div>

          {/* Rejection Alert if rejected */}
          {application.status === 'REJECTED' && application.rejectionReason && (
            <div className="rounded-xl border border-rose-200 bg-rose-50/70 p-4 text-xs text-rose-800 space-y-1">
              <div className="flex items-center gap-2 font-bold text-rose-900">
                <AlertTriangle className="h-4 w-4" />
                <span>Lý do từ chối hồ sơ:</span>
              </div>
              <p className="leading-relaxed pl-6">{application.rejectionReason}</p>
            </div>
          )}

          {/* Contact Details Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 text-sm">
            <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5">
              <Mail className="mt-0.5 h-4 w-4 text-slate-400" />
              <div>
                <div className="text-xs text-slate-400">Địa chỉ Email</div>
                <div className="font-mono text-sm font-semibold text-slate-800">
                  {application.email}
                </div>
              </div>
            </div>

            <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5">
              <Phone className="mt-0.5 h-4 w-4 text-slate-400" />
              <div>
                <div className="text-xs text-slate-400">Số điện thoại</div>
                <div className="font-medium text-slate-800">
                  {application.phone || 'Chưa cung cấp'}
                </div>
              </div>
            </div>

            <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5">
              <Building2 className="mt-0.5 h-4 w-4 text-slate-400" />
              <div>
                <div className="text-xs text-slate-400">Trường học / Đơn vị công tác</div>
                <div className="font-medium text-slate-800">
                  {application.organization || 'Chưa cập nhật'}
                </div>
              </div>
            </div>

            <div className="flex items-start gap-3 rounded-xl border border-slate-100 p-3.5">
              <Briefcase className="mt-0.5 h-4 w-4 text-slate-400" />
              <div>
                <div className="text-xs text-slate-400">Vị trí / Chức danh hiện tại</div>
                <div className="font-medium text-slate-800">
                  {application.jobTitle || 'Chưa cập nhật'}
                </div>
              </div>
            </div>
          </div>

          {/* Expertise & Experience */}
          <div className="rounded-xl border border-slate-100 p-4 space-y-2 text-sm">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-400">
              <Award className="h-4 w-4 text-[#00695c]" />
              <span>Lĩnh vực chuyên môn & Kinh nghiệm</span>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <span className="font-semibold text-slate-900 text-base">
                {application.expertise || 'Chuyên môn chưa xác định'}
              </span>
              <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-[#00695c] border border-emerald-200/50">
                {application.yearsOfExperience != null
                  ? `${application.yearsOfExperience} năm kinh nghiệm`
                  : 'Chưa cập nhật số năm'}
              </span>
            </div>
          </div>

          {/* Professional Bio */}
          {application.professionalBio && (
            <div className="rounded-xl border border-slate-100 p-4 space-y-1.5 text-sm">
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tiểu sử & Quá trình công tác
              </div>
              <p className="text-slate-700 leading-relaxed text-sm whitespace-pre-line">
                {application.professionalBio}
              </p>
            </div>
          )}

          {/* Motivation */}
          {application.motivation && (
            <div className="rounded-xl border border-slate-100 p-4 space-y-1.5 text-sm bg-slate-50/50">
              <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-slate-400">
                <HeartHandshake className="h-3.5 w-3.5 text-indigo-500" />
                <span>Động lực & Mục tiêu tham gia giảng dạy</span>
              </div>
              <p className="text-slate-700 leading-relaxed text-sm italic">
                "{application.motivation}"
              </p>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
          >
            Đóng
          </button>

          {isPending && (
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => onReject(application)}
                className="inline-flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 transition-colors hover:bg-rose-100"
              >
                <XCircle className="h-4 w-4" />
                <span>Từ chối hồ sơ</span>
              </button>

              <button
                type="button"
                onClick={() => onApprove(application)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[#00695c] px-4 py-2 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-[#004d40]"
              >
                <CheckCircle2 className="h-4 w-4" />
                <span>Phê duyệt hồ sơ</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
