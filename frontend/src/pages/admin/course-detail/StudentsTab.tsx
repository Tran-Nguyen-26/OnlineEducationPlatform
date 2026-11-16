import { Pager, usePaged } from '../../../components/Pager'
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { AdminEnrollmentResponse, BulkEnrollRowResult, InviteCodeResponse, TuitionPaymentResponse } from '../../../types/api'
import { Badge } from '../../../components/Badge'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { ENTRY_LABEL, printReceipt } from '../../../lib/receipt'
import { Modal } from '../../../components/ui/Modal'
import { WaiverModal } from './WaiverModal'

function formatVnd(amount: number | null): string {
  if (amount == null) return '—'
  return amount.toLocaleString('vi-VN') + ' đ'
}

function PaymentModal({
  courseId,
  enrollment,
  onClose,
}: {
  courseId: string
  enrollment: AdminEnrollmentResponse
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const courseTitle = queryClient.getQueryData<{ id: string; title: string }[]>(['courses'])?.find((c) => c.id === courseId)?.title ?? ''
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [entryType, setEntryType] = useState<'PAYMENT' | 'DISCOUNT' | 'REFUND'>('PAYMENT')
  const [error, setError] = useState<string | null>(null)

  const paymentsQuery = useQuery({
    queryKey: ['enrollments', enrollment.enrollmentId, 'payments'],
    queryFn: () => apiClient.get<TuitionPaymentResponse[]>(`/api/admin/enrollments/${enrollment.enrollmentId}/payments`).then((r) => r.data),
  })

  const recordMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/enrollments/${enrollment.enrollmentId}/payments`, { amount: Number(amount), note: note || null, type: entryType }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enrollments', enrollment.enrollmentId, 'payments'] })
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'enrollments'] })
      queryClient.invalidateQueries({ queryKey: ['finance'] })
      setAmount('')
      setNote('')
      setError(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    recordMutation.mutate()
  }

  const totalPaid = paymentsQuery.data?.reduce((sum, p) => sum + (p.type === 'REFUND' ? -p.amount : p.amount), 0) ?? enrollment.totalPaid ?? 0
  const feeAmount = enrollment.feeAmount
  const liveStatus =
    feeAmount == null || feeAmount <= 0 ? 'NOT_APPLICABLE' : totalPaid <= 0 ? 'UNPAID' : totalPaid >= feeAmount ? 'PAID' : 'PARTIAL'

  return (
    <Modal title={`Học phí — ${enrollment.studentFullName ?? enrollment.studentEmail}`} open onClose={onClose}>
      <div className="mb-4 flex items-center justify-between rounded-md bg-slate-50 p-3 text-sm">
        <span>
          Học phí: <strong>{formatVnd(feeAmount)}</strong>
        </span>
        <span>
          Đã ghi nhận: <strong>{formatVnd(totalPaid)}</strong>
        </span>
        <Badge status={liveStatus} />
      </div>

      <h3 className="mb-2 text-sm font-medium text-slate-700">Lịch sử thanh toán</h3>
      <div className="mb-4 max-h-40 space-y-1 overflow-y-auto">
        {paymentsQuery.data?.length === 0 && <p className="text-sm text-slate-400">Chưa có khoản thanh toán nào.</p>}
        {paymentsQuery.data?.map((p) => (
          <div key={p.id} className="flex items-center justify-between rounded border border-slate-100 px-2 py-1 text-xs text-slate-600">
            <span>
              <span className="font-medium">{ENTRY_LABEL[p.type]}</span> · {new Date(p.paidAt).toLocaleDateString('vi-VN')} — {p.type === 'REFUND' ? '−' : ''}{formatVnd(p.amount)} {p.note && `(${p.note})`}
            </span>
            <span className="flex items-center gap-2 text-slate-400">
              {p.receiptNo}
              <button
                type="button"
                className="text-blue-600 hover:underline"
                onClick={() => printReceipt({ ...p, studentName: enrollment.studentFullName ?? enrollment.studentEmail, studentEmail: enrollment.studentEmail, courseTitle: courseTitle, recordedBy: p.recordedByEmail })}
              >
                In
              </button>
            </span>
          </div>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="flex items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Loại</label>
          <select value={entryType} onChange={(e) => setEntryType(e.target.value as 'PAYMENT' | 'DISCOUNT' | 'REFUND')} className="h-10 rounded-md border border-slate-300 px-2 text-sm">
            <option value="PAYMENT">Thu học phí</option>
            <option value="DISCOUNT">Miễn giảm</option>
            <option value="REFUND">Hoàn tiền</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Số tiền (VNĐ)</label>
          <Input type="number" min={1} step={1000} required value={amount} onChange={(e) => setAmount(e.target.value)} className="w-32" />
        </div>
        <div className="flex-1">
          <label className="mb-1 block text-xs font-medium text-slate-600">Ghi chú</label>
          <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="VD: đóng đợt 1" />
        </div>
        <Button type="submit" size="sm" disabled={recordMutation.isPending}>
          {recordMutation.isPending ? 'Đang lưu...' : 'Ghi nhận'}
        </Button>
      </form>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
    </Modal>
  )
}

function BulkEnrollModal({ courseId, onClose }: { courseId: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [inviteCodeId, setInviteCodeId] = useState('')
  const [emailsText, setEmailsText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [results, setResults] = useState<BulkEnrollRowResult[] | null>(null)

  const inviteCodesQuery = useQuery({
    queryKey: ['courses', courseId, 'invite-codes'],
    queryFn: () => apiClient.get<InviteCodeResponse[]>(`/api/admin/courses/${courseId}/invite-codes`).then((r) => r.data),
  })
  const activeCodes = (inviteCodesQuery.data ?? []).filter((c) => c.active)

  const bulkMutation = useMutation({
    mutationFn: () => {
      const emails = emailsText
        .split(/[\n,;]+/)
        .map((e) => e.trim())
        .filter(Boolean)
      return apiClient
        .post<BulkEnrollRowResult[]>(`/api/admin/courses/${courseId}/bulk-enroll`, { inviteCodeId, emails })
        .then((r) => r.data)
    },
    onSuccess: (data) => {
      setResults(data)
      setError(null)
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'enrollments'] })
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    bulkMutation.mutate()
  }

  return (
    <Modal title="Ghi danh hàng loạt" open onClose={onClose}>
      {results ? (
        <div>
          <div className="mb-3 max-h-64 space-y-1 overflow-y-auto">
            {results.map((r) => (
              <div key={r.email} className="flex items-center justify-between rounded border border-slate-100 px-2 py-1 text-xs">
                <span className="font-medium text-slate-700">{r.email}</span>
                <span className="text-slate-500">
                  {r.message}
                  {r.temporaryPassword && <> — mật khẩu tạm: <span className="font-mono">{r.temporaryPassword}</span></>}
                </span>
              </div>
            ))}
          </div>
          <p className="mb-3 text-xs text-slate-400">
            Ghi lại mật khẩu tạm của các tài khoản mới để gửi cho học sinh — mật khẩu chỉ hiện đúng một lần.
          </p>
          <div className="flex justify-end">
            <Button onClick={onClose}>Đóng</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Mã mời dùng cho tất cả</label>
          <select
            required
            value={inviteCodeId}
            onChange={(e) => setInviteCodeId(e.target.value)}
            className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
          >
            <option value="">Chọn mã...</option>
            {activeCodes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code}
              </option>
            ))}
          </select>
          {activeCodes.length === 0 && (
            <p className="mb-4 text-xs text-amber-600">Chưa có mã mời còn hiệu lực — sinh một mã ở tab Tổng quan trước.</p>
          )}

          <label className="mb-1 block text-sm font-medium text-slate-700">Danh sách email (mỗi dòng một email, hoặc phân cách bằng dấu phẩy)</label>
          <textarea
            required
            rows={6}
            value={emailsText}
            onChange={(e) => setEmailsText(e.target.value)}
            placeholder={'student1@example.com\nstudent2@example.com'}
            className="mb-4 w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-mono"
          />

          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit" disabled={bulkMutation.isPending || !inviteCodeId}>
              {bulkMutation.isPending ? 'Đang xử lý...' : 'Ghi danh hàng loạt'}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  )
}

export function StudentsTab({ courseId }: { courseId: string }) {
  const queryClient = useQueryClient()
  const enrollmentsQuery = useQuery({
    queryKey: ['courses', courseId, 'enrollments'],
    queryFn: () => apiClient.get<AdminEnrollmentResponse[]>(`/api/admin/courses/${courseId}/enrollments`).then((r) => r.data),
  })

  const [removing, setRemoving] = useState<AdminEnrollmentResponse | null>(null)
  const [removeError, setRemoveError] = useState<string | null>(null)
  const [paying, setPaying] = useState<AdminEnrollmentResponse | null>(null)
  const [waiving, setWaiving] = useState<AdminEnrollmentResponse | null>(null)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  async function exportCsv() {
    setExporting(true)
    try {
      const response = await apiClient.get(`/api/admin/courses/${courseId}/enrollments/export`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.download = `roster-${courseId}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } finally {
      setExporting(false)
    }
  }

  const statusMutation = useMutation({
    mutationFn: ({ enrollmentId, status }: { enrollmentId: string; status: 'COMPLETED' | 'REMOVED' | 'ACTIVE' }) =>
      apiClient.patch(`/api/admin/enrollments/${enrollmentId}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'enrollments'] })
      setRemoving(null)
    },
    onError: (err) => setRemoveError(getApiErrorMessage(err)),
  })

  const allRows = enrollmentsQuery.data ?? []
  const pg = usePaged(allRows, 20)
  const rows = pg.slice

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Học sinh ghi danh</h2>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500">{allRows.length} học sinh</span>
          <Button size="sm" variant="secondary" disabled={exporting} onClick={exportCsv}>
            {exporting ? 'Đang xuất...' : 'Xuất CSV'}
          </Button>
          <Button size="sm" onClick={() => setBulkOpen(true)}>
            Ghi danh hàng loạt
          </Button>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Học sinh</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Ngày ghi danh</th>
              <th className="px-4 py-3 font-medium">Học phí</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={row.enrollmentId}>
                <td className="px-4 py-3 font-medium text-slate-900">{row.studentFullName ?? '—'}</td>
                <td className="px-4 py-3 text-slate-500">{row.studentEmail}</td>
                <td className="px-4 py-3">
                  <Badge status={row.status} />
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(row.enrolledAt).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3">
                  {row.paymentStatus === 'NOT_APPLICABLE' ? (
                    <span className="text-xs text-slate-400">Miễn phí</span>
                  ) : (
                    <button onClick={() => setPaying(row)} className="hover:opacity-80">
                      <Badge status={row.paymentStatus} />
                    </button>
                  )}
                </td>
                <td className="px-4 py-3 text-right">
                  {row.status === 'ACTIVE' && (
                    <div className="flex justify-end gap-3">
                      <button className="text-sm text-slate-500 hover:text-blue-700" onClick={() => setWaiving(row)}>
                        Miễn trừ
                      </button>
                      <button
                        className="text-sm text-slate-500 hover:text-green-700"
                        disabled={statusMutation.isPending}
                        onClick={() => statusMutation.mutate({ enrollmentId: row.enrollmentId, status: 'COMPLETED' })}
                      >
                        Đánh dấu hoàn thành
                      </button>
                      <button
                        className="text-sm text-slate-500 hover:text-red-600"
                        onClick={() => {
                          setRemoving(row)
                          setRemoveError(null)
                        }}
                      >
                        Loại khỏi khóa
                      </button>
                    </div>
                  )}
                  {row.status === 'WAITLISTED' && (
                    <div className="flex justify-end gap-3">
                      <button
                        className="text-sm text-slate-500 hover:text-green-700"
                        disabled={statusMutation.isPending}
                        onClick={() => statusMutation.mutate({ enrollmentId: row.enrollmentId, status: 'ACTIVE' })}
                      >
                        Thăng vào học
                      </button>
                      <button
                        className="text-sm text-slate-500 hover:text-red-600"
                        onClick={() => {
                          setRemoving(row)
                          setRemoveError(null)
                        }}
                      >
                        Loại khỏi danh sách chờ
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {!enrollmentsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={6}>
                  Chưa có học sinh ghi danh khóa này.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Modal title="Loại học sinh khỏi khóa học" open={removing !== null} onClose={() => setRemoving(null)}>
        <p className="mb-4 text-sm text-slate-600">
          Loại <strong>{removing?.studentFullName ?? removing?.studentEmail}</strong> khỏi khóa học? Học sinh sẽ mất
          quyền truy cập nội dung; để vào lại cần một mã mời còn hiệu lực.
        </p>
        {removeError && <p className="mb-4 text-sm text-red-600">{removeError}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => setRemoving(null)}>
            Hủy
          </Button>
          <Button
            variant="danger"
            disabled={statusMutation.isPending}
            onClick={() => removing && statusMutation.mutate({ enrollmentId: removing.enrollmentId, status: 'REMOVED' })}
          >
            {statusMutation.isPending ? 'Đang xử lý...' : 'Xác nhận loại khỏi khóa'}
          </Button>
        </div>
      </Modal>

      <Pager page={pg.page} pages={pg.pages} total={pg.total} setPage={pg.setPage} label="học sinh" />
      {waiving && <WaiverModal enrollment={waiving} onClose={() => setWaiving(null)} />}
      {paying && <PaymentModal courseId={courseId} enrollment={paying} onClose={() => setPaying(null)} />}
      {bulkOpen && <BulkEnrollModal courseId={courseId} onClose={() => setBulkOpen(false)} />}
    </div>
  )
}
