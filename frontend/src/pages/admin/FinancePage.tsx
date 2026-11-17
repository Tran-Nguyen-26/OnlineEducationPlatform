import { CheckCircle2, Receipt, Wallet } from 'lucide-react'
import { StatCard, TabBar } from '../../components/ui/Page'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import { ENTRY_LABEL, printReceipt } from '../../lib/receipt'
import { downloadCsv, vnd } from '../../lib/csv'
import type { FinanceCourseRow, FinanceOutstandingRow, FinancePaymentRow, FinanceSummary } from '../../types/api'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/ui/Button'

const TABS = [
  { key: 'courses', label: 'Theo khóa học' },
  { key: 'outstanding', label: 'Công nợ' },
  { key: 'payments', label: 'Lịch sử thu' },
] as const

export function FinancePage() {
  const [tab, setTab] = useState<(typeof TABS)[number]['key']>('courses')
  const summary = useQuery({ queryKey: ['finance', 'summary'], queryFn: () => apiClient.get<FinanceSummary>('/api/admin/finance/summary').then((r) => r.data) })
  const byCourse = useQuery({ queryKey: ['finance', 'by-course'], queryFn: () => apiClient.get<FinanceCourseRow[]>('/api/admin/finance/by-course').then((r) => r.data) })
  const outstanding = useQuery({ queryKey: ['finance', 'outstanding'], queryFn: () => apiClient.get<FinanceOutstandingRow[]>('/api/admin/finance/outstanding').then((r) => r.data) })
  const payments = useQuery({ queryKey: ['finance', 'payments'], queryFn: () => apiClient.get<FinancePaymentRow[]>('/api/admin/finance/payments').then((r) => r.data) })
  const s = summary.data

  function exportCurrent() {
    if (tab === 'courses') {
      downloadCsv('hoc-phi-theo-khoa.csv', [
        ['Khóa học', 'Học phí', 'Số học sinh', 'Đã đóng đủ', 'Phải thu', 'Đã thu', 'Còn nợ'],
        ...(byCourse.data ?? []).map((r) => [r.courseTitle, r.feeAmount, r.enrollments, r.paidEnrollments, r.expected, r.collected, r.outstanding]),
      ])
    } else if (tab === 'outstanding') {
      downloadCsv('cong-no.csv', [
        ['Học sinh', 'Email', 'Khóa học', 'Học phí', 'Đã đóng', 'Còn nợ', 'Trạng thái'],
        ...(outstanding.data ?? []).map((r) => [r.studentName, r.studentEmail, r.courseTitle, r.feeAmount, r.paid, r.due, r.paymentStatus]),
      ])
    } else {
      downloadCsv('lich-su-thu.csv', [
        ['Ngày', 'Học sinh', 'Email', 'Khóa học', 'Số tiền', 'Ghi chú', 'Người ghi nhận', 'Loại', 'Số phiếu'],
        ...(payments.data ?? []).map((r) => [new Date(r.paidAt).toLocaleString('vi-VN'), r.studentName, r.studentEmail, r.courseTitle, r.amount, r.note, r.recordedByEmail, r.type, r.receiptNo]),
      ])
    }
  }

  const card = (label: string, value: string) => (
    <StatCard label={label} value={value} icon={label.startsWith('Còn') ? Wallet : label.startsWith('Đã') ? CheckCircle2 : Receipt} tone={label.startsWith('Còn') ? 'rose' : label.startsWith('Đã') ? 'teal' : 'indigo'} />
  )
  const th = 'px-4 py-3 font-medium'

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Học phí & công nợ</h1>
        <Button variant="secondary" size="sm" onClick={exportCurrent}>Xuất CSV</Button>
      </div>
      <p className="mb-4 text-sm text-slate-500">
        Chỉ tính học sinh đang học hoặc đã hoàn thành ở các khóa có học phí. Ghi nhận khoản thu tại tab "Học sinh" trong từng khóa học.
      </p>
      <div className="mb-6 grid grid-cols-3 gap-4">
        {card('Phải thu', vnd(s?.expected))}
        {card('Đã thu', vnd(s?.collected))}
        {card('Còn nợ', vnd(s?.outstanding))}
      </div>

      <TabBar tabs={TABS} active={tab} onChange={setTab} className="!mb-4" />

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        {tab === 'courses' && (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Khóa học</th><th className={th}>Học phí</th><th className={th}>Học sinh</th><th className={th}>Đã đóng đủ</th>
              <th className={th}>Phải thu</th><th className={th}>Đã thu</th><th className={th}>Còn nợ</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {byCourse.data?.map((r) => (
                <tr key={r.courseId}>
                  <td className="px-4 py-3"><Link to={`/admin/courses/${r.courseId}`} className="font-medium text-slate-900 hover:underline">{r.courseTitle}</Link></td>
                  <td className="px-4 py-3">{vnd(r.feeAmount)}</td><td className="px-4 py-3">{r.enrollments}</td><td className="px-4 py-3">{r.paidEnrollments}</td>
                  <td className="px-4 py-3">{vnd(r.expected)}</td><td className="px-4 py-3">{vnd(r.collected)}</td><td className="px-4 py-3 font-medium">{vnd(r.outstanding)}</td>
                </tr>
              ))}
              {byCourse.data?.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={7}>Chưa có khóa học nào đặt học phí.</td></tr>}
            </tbody>
          </table>
        )}
        {tab === 'outstanding' && (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Học sinh</th><th className={th}>Khóa học</th><th className={th}>Học phí</th><th className={th}>Đã đóng</th><th className={th}>Còn nợ</th><th className={th}>Trạng thái</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {outstanding.data?.map((r) => (
                <tr key={r.enrollmentId}>
                  <td className="px-4 py-3"><Link to={`/admin/users/${r.studentId}`} className="font-medium text-slate-900 hover:underline">{r.studentName}</Link><div className="text-xs text-slate-400">{r.studentEmail}</div></td>
                  <td className="px-4 py-3">{r.courseTitle}</td><td className="px-4 py-3">{vnd(r.feeAmount)}</td><td className="px-4 py-3">{vnd(r.paid)}</td>
                  <td className="px-4 py-3 font-medium text-red-700">{vnd(r.due)}</td><td className="px-4 py-3"><Badge status={r.paymentStatus} /></td>
                </tr>
              ))}
              {outstanding.data?.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Không có công nợ.</td></tr>}
            </tbody>
          </table>
        )}
        {tab === 'payments' && (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className={th}>Số</th><th className={th}>Ngày</th><th className={th}>Loại</th><th className={th}>Học sinh</th><th className={th}>Khóa học</th><th className={th}>Số tiền</th><th className={th}>Ghi chú</th><th className={th}>Người ghi nhận</th><th className={th} />
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {payments.data?.map((r) => (
                <tr key={r.paymentId}>
                  <td className="px-4 py-3 text-xs text-slate-500">{r.receiptNo}</td>
                  <td className="px-4 py-3 text-slate-500">{new Date(r.paidAt).toLocaleString('vi-VN')}</td>
                  <td className="px-4 py-3">{ENTRY_LABEL[r.type]}</td>
                  <td className="px-4 py-3">{r.studentName}<div className="text-xs text-slate-400">{r.studentEmail}</div></td>
                  <td className="px-4 py-3">{r.courseTitle}</td><td className={`px-4 py-3 font-medium ${r.type === 'REFUND' ? 'text-red-600' : ''}`}>{r.type === 'REFUND' ? '−' : ''}{vnd(r.amount)}</td>
                  <td className="px-4 py-3 text-slate-500">{r.note ?? '—'}</td><td className="px-4 py-3 text-slate-500">{r.recordedByEmail}</td>
                  <td className="px-4 py-3"><button className="text-blue-600 hover:underline" onClick={() => printReceipt({ ...r, recordedBy: r.recordedByEmail })}>In</button></td>
                </tr>
              ))}
              {payments.data?.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={9}>Chưa có khoản thu nào.</td></tr>}
            </tbody>
          </table>
        )}
      </div>
      {tab === 'payments' && <p className="mt-2 text-xs text-slate-400">Hiển thị tối đa 200 khoản gần nhất.</p>}
    </div>
  )
}
