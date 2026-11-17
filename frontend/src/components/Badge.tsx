import { cn } from '../lib/cn'

const COLORS: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  PUBLISHED: 'bg-green-100 text-green-700',
  AVAILABLE: 'bg-green-100 text-green-700',
  DRAFT: 'bg-slate-100 text-slate-600',
  PENDING: 'bg-amber-100 text-amber-700',
  UPLOADED: 'bg-amber-100 text-amber-700',
  ARCHIVED: 'bg-amber-100 text-amber-700',
  DISABLED: 'bg-red-100 text-red-700',
  FAILED: 'bg-red-100 text-red-700',
  GRADED: 'bg-green-100 text-green-700',
  SUBMITTED: 'bg-amber-100 text-amber-700',
  PENDING_REGRADE: 'bg-amber-100 text-amber-700',
  COMPLETED: 'bg-green-100 text-green-700',
  WITHDRAWN: 'bg-slate-100 text-slate-600',
  REMOVED: 'bg-red-100 text-red-700',
  PAID: 'bg-green-100 text-green-700',
  PARTIAL: 'bg-amber-100 text-amber-700',
  UNPAID: 'bg-red-100 text-red-700',
  WAITLISTED: 'bg-blue-100 text-blue-700',
  OPEN: 'bg-amber-100 text-amber-700',
  ACCEPTED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
}

const LABELS: Record<string, string> = {
  OPEN: 'Đang chờ xử lý', ACCEPTED: 'Được chấp nhận', REJECTED: 'Không chấp nhận',
  ACTIVE: 'Đang hoạt động', PUBLISHED: 'Công khai', DRAFT: 'Lưu nháp', ARCHIVED: 'Lưu trữ', DISABLED: 'Vô hiệu hóa',
  COMPLETED: 'Hoàn thành', WITHDRAWN: 'Đã rút', REMOVED: 'Đã loại', WAITLISTED: 'Danh sách chờ',
  PAID: 'Đã đóng đủ', PARTIAL: 'Đóng một phần', UNPAID: 'Chưa đóng',
  GRADED: 'Đã chấm', SUBMITTED: 'Đã nộp', PENDING_REGRADE: 'Chờ chấm lại', PENDING: 'Đang chờ', UPLOADED: 'Đã tải lên',
  AVAILABLE: 'Sẵn sàng', FAILED: 'Lỗi',
}

export function Badge({ status }: { status: string }) {
  return (
    <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', COLORS[status] ?? 'bg-slate-100 text-slate-600')}>
      {LABELS[status] ?? status}
    </span>
  )
}
