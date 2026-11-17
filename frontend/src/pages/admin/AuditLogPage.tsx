import { Pager, usePaged } from '../../components/Pager'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { AuditLogResponse } from '../../types/api'

const ACTION_LABEL: Record<string, string> = {
  CREATE_COURSE: 'Tạo khóa học', UPDATE_COURSE: 'Sửa khóa học', UPDATE_COURSE_STATUS: 'Đổi trạng thái khóa học',
  CREATE_SUBJECT: 'Tạo môn học', UPDATE_SUBJECT: 'Sửa môn học', CREATE_MODULE: 'Tạo chương', CREATE_LESSON: 'Tạo bài học',
  CREATE_LESSON_CONTENT: 'Thêm nội dung bài', UPDATE_LESSON_CONTENT: 'Sửa nội dung bài', DELETE_LESSON_CONTENT: 'Xóa nội dung bài',
  SET_LESSON_COMPLETION_RULE: 'Đặt quy tắc hoàn thành bài', DELETE_LESSON_COMPLETION_RULE: 'Bỏ quy tắc hoàn thành bài',
  CREATE_QUIZ: 'Tạo quiz', DELETE_QUIZ: 'Xóa quiz', ADD_QUESTION: 'Thêm câu hỏi', ADD_QUESTION_FROM_BANK: 'Thêm câu hỏi từ ngân hàng',
  REMOVE_QUESTION: 'Gỡ câu hỏi', CREATE_ASSIGNMENT: 'Tạo bài tập', ASSIGN_INSTRUCTOR: 'Phân công giáo viên',
  GENERATE_INVITE_CODE: 'Tạo mã mời', DEACTIVATE_INVITE_CODE: 'Vô hiệu mã mời', ADD_PREREQUISITE: 'Thêm khóa tiên quyết',
  REMOVE_PREREQUISITE: 'Gỡ khóa tiên quyết', BULK_ENROLL: 'Ghi danh hàng loạt', UPDATE_ENROLLMENT_STATUS: 'Đổi trạng thái ghi danh',
  CREATE_STUDENT: 'Tạo học sinh', CREATE_TEACHER: 'Tạo giáo viên', UPDATE_USER_PROFILE: 'Sửa hồ sơ người dùng',
  UPDATE_USER_STATUS: 'Khóa/mở tài khoản', GENERATE_RESET_LINK: 'Tạo link đặt lại mật khẩu', BROADCAST: 'Gửi thông báo hệ thống',
  RECORD_PAYMENT: 'Ghi nhận thu học phí', RECORD_DISCOUNT: 'Ghi nhận miễn giảm', RECORD_REFUND: 'Ghi nhận hoàn tiền',
  CREATE_SESSION: 'Tạo buổi học', DELETE_SESSION: 'Xóa buổi học', MARK_ATTENDANCE: 'Điểm danh', WAIVE_COMPLETION_ITEM: 'Miễn trừ quiz/bài tập',
  REMOVE_COMPLETION_WAIVER: 'Bỏ miễn trừ', SELF_COMPLETE_COURSE: 'Học sinh tự hoàn thành khóa', DELETE_BANK_QUESTION: 'Xóa câu hỏi khỏi ngân hàng',
}

function formatMetadata(metadata: string | null): string {
  if (!metadata) return '—'
  try {
    const parsed = JSON.parse(metadata) as Record<string, unknown>
    return Object.entries(parsed)
      .map(([k, v]) => `${k}=${v}`)
      .join(', ')
  } catch {
    return metadata
  }
}

export function AuditLogPage() {
  const logsQuery = useQuery({
    queryKey: ['admin', 'audit-logs'],
    queryFn: () => apiClient.get<AuditLogResponse[]>('/api/admin/audit-logs').then((r) => r.data),
  })

  const allRows = logsQuery.data ?? []
  const pg = usePaged(allRows, 25)
  const rows = pg.slice

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Nhật ký hành động</h1>
        <span className="text-sm text-slate-500">{rows.length} bản ghi</span>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Thời gian</th>
              <th className="px-4 py-3 font-medium">Người thực hiện</th>
              <th className="px-4 py-3 font-medium">Hành động</th>
              <th className="px-4 py-3 font-medium">Đối tượng</th>
              <th className="px-4 py-3 font-medium">Chi tiết</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {logsQuery.isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Đang tải...
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="whitespace-nowrap px-4 py-3 text-slate-500">{new Date(row.createdAt).toLocaleString('vi-VN')}</td>
                <td className="px-4 py-3 text-slate-700">{row.actorEmail}</td>
                <td className="px-4 py-3 font-medium text-slate-900">{ACTION_LABEL[row.action] ?? row.action}<div className="font-mono text-[10px] font-normal text-slate-400">{row.action}</div></td>
                <td className="px-4 py-3 text-slate-500">
                  {row.targetType} <span className="text-xs text-slate-400">({row.targetId.slice(0, 8)})</span>
                </td>
                <td className="px-4 py-3 text-xs text-slate-500">{formatMetadata(row.metadata)}</td>
              </tr>
            ))}
            {!logsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Chưa có hành động nào được ghi lại.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager page={pg.page} pages={pg.pages} total={pg.total} setPage={pg.setPage} label="bản ghi" />
    </div>
  )
}
