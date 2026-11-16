import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import { downloadCsv } from '../lib/csv'
import type { AttendanceRosterRow, AttendanceStatus, AttendanceSummaryRow, SessionResponse } from '../types/api'
import { Button } from './ui/Button'
import { Input } from './ui/Input'
import { Modal } from './ui/Modal'

const STATUS_LABEL: Record<AttendanceStatus, string> = { PRESENT: 'Có mặt', LATE: 'Đi muộn', EXCUSED: 'Có phép', ABSENT: 'Vắng' }
const STATUS_ORDER: AttendanceStatus[] = ['PRESENT', 'LATE', 'EXCUSED', 'ABSENT']

function when(s: SessionResponse) {
  const start = new Date(s.startsAt)
  const end = s.endsAt ? new Date(s.endsAt) : null
  return `${start.toLocaleString('vi-VN')}${end ? ` → ${end.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}` : ''}`
}

/** Class schedule + attendance. staff = admin/assigned teacher (create sessions, mark attendance, see summary); otherwise a read-only student view. */
export function SessionsPanel({ courseId, staff }: { courseId: string; staff: boolean }) {
  const queryClient = useQueryClient()
  const sessions = useQuery({
    queryKey: ['sessions', courseId],
    queryFn: () => apiClient.get<SessionResponse[]>(`/api/courses/${courseId}/sessions`).then((r) => r.data),
  })
  const summary = useQuery({
    enabled: staff,
    queryKey: ['attendance-summary', courseId],
    queryFn: () => apiClient.get<AttendanceSummaryRow[]>(`/api/courses/${courseId}/attendance-summary`).then((r) => r.data),
  })
  const [createOpen, setCreateOpen] = useState(false)
  const [markFor, setMarkFor] = useState<SessionResponse | null>(null)
  const [title, setTitle] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [location, setLocation] = useState('')
  const [error, setError] = useState<string | null>(null)

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['sessions', courseId] })
    queryClient.invalidateQueries({ queryKey: ['attendance-summary', courseId] })
  }
  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/courses/${courseId}/sessions`, {
      title, startsAt: new Date(startsAt).toISOString(), endsAt: endsAt ? new Date(endsAt).toISOString() : null, location: location || null,
    }),
    onSuccess: () => { setCreateOpen(false); setTitle(''); setStartsAt(''); setEndsAt(''); setLocation(''); setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/courses/${courseId}/sessions/${id}`),
    onSuccess: refresh,
    onError: (e) => window.alert(getApiErrorMessage(e)),
  })

  function submit(e: FormEvent) { e.preventDefault(); create.mutate() }
  function exportSummary() {
    downloadCsv(`diem-danh-${courseId}.csv`, [
      ['Học sinh', 'Email', 'Số buổi đã điểm danh', 'Có mặt', 'Đi muộn', 'Có phép', 'Vắng', 'Chuyên cần (%)'],
      ...(summary.data ?? []).map((r) => [r.studentName, r.studentEmail, r.sessionsMarked, r.present, r.late, r.excused, r.absent, r.attendanceRate ?? '']),
    ])
  }

  return (
    <div className="space-y-6">
      <div>
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm text-slate-500">{staff ? 'Lịch các buổi học của khóa. Tạo buổi học sẽ gửi thông báo cho học sinh đang học.' : 'Lịch các buổi học và trạng thái điểm danh của bạn.'}</p>
          {staff && <Button size="sm" onClick={() => { setError(null); setCreateOpen(true) }}>Thêm buổi học</Button>}
        </div>
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr>
              <th className="px-4 py-3 font-medium">Buổi học</th><th className="px-4 py-3 font-medium">Thời gian</th><th className="px-4 py-3 font-medium">Địa điểm</th>
              <th className="px-4 py-3 font-medium">{staff ? 'Điểm danh' : 'Của tôi'}</th>{staff && <th className="px-4 py-3" />}
            </tr></thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.data?.map((s) => (
                <tr key={s.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{s.title}</td>
                  <td className="px-4 py-3 text-slate-600">{when(s)}</td>
                  <td className="px-4 py-3 text-slate-500">{s.location ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {staff ? (s.marked ? `${s.present + s.late + s.excused}/${s.marked} có mặt/phép` : 'Chưa điểm danh')
                      : s.myStatus ? STATUS_LABEL[s.myStatus] : new Date(s.startsAt) > new Date() ? 'Sắp diễn ra' : 'Chưa ghi nhận'}
                  </td>
                  {staff && (
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="secondary" onClick={() => setMarkFor(s)}>Điểm danh</Button>
                        <Button size="sm" variant="danger" disabled={del.isPending} onClick={() => { if (window.confirm('Xóa buổi học này và toàn bộ điểm danh của nó?')) del.mutate(s.id) }}>Xóa</Button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
              {sessions.data?.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={staff ? 5 : 4}>Chưa có buổi học nào được lên lịch.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {staff && (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Chuyên cần theo học sinh</h3>
            <Button size="sm" variant="secondary" onClick={exportSummary} disabled={!summary.data?.length}>Xuất CSV</Button>
          </div>
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500"><tr>
                <th className="px-4 py-3 font-medium">Học sinh</th><th className="px-4 py-3 font-medium">Đã điểm danh</th><th className="px-4 py-3 font-medium">Có mặt</th>
                <th className="px-4 py-3 font-medium">Muộn</th><th className="px-4 py-3 font-medium">Có phép</th><th className="px-4 py-3 font-medium">Vắng</th><th className="px-4 py-3 font-medium">Chuyên cần</th>
              </tr></thead>
              <tbody className="divide-y divide-slate-100">
                {summary.data?.map((r) => (
                  <tr key={r.enrollmentId}>
                    <td className="px-4 py-3"><div className="font-medium text-slate-900">{r.studentName}</div><div className="text-xs text-slate-400">{r.studentEmail}</div></td>
                    <td className="px-4 py-3">{r.sessionsMarked}</td><td className="px-4 py-3">{r.present}</td><td className="px-4 py-3">{r.late}</td><td className="px-4 py-3">{r.excused}</td>
                    <td className={`px-4 py-3 ${r.absent >= 3 ? 'font-medium text-red-600' : ''}`}>{r.absent}</td>
                    <td className={`px-4 py-3 font-medium ${r.attendanceRate !== null && r.attendanceRate < 80 ? 'text-red-600' : 'text-slate-900'}`}>{r.attendanceRate === null ? '—' : `${r.attendanceRate}%`}</td>
                  </tr>
                ))}
                {summary.data?.length === 0 && <tr><td className="px-4 py-6 text-slate-400" colSpan={7}>Chưa có học sinh.</td></tr>}
              </tbody>
            </table>
          </div>
          <p className="mt-1 text-xs text-slate-400">Chuyên cần = (có mặt + muộn + có phép) / số buổi đã điểm danh. Dưới 80% hoặc vắng từ 3 buổi được tô đỏ.</p>
        </div>
      )}

      <Modal title="Thêm buổi học" open={createOpen} onClose={() => setCreateOpen(false)}>
        <form onSubmit={submit}>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tên buổi học</label>
          <Input required value={title} onChange={(e) => setTitle(e.target.value)} className="mb-3" />
          <label className="mb-1 block text-sm font-medium text-slate-700">Bắt đầu</label>
          <Input required type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} className="mb-3" />
          <label className="mb-1 block text-sm font-medium text-slate-700">Kết thúc (tùy chọn)</label>
          <Input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} className="mb-3" />
          <label className="mb-1 block text-sm font-medium text-slate-700">Địa điểm / link học online (tùy chọn)</label>
          <Input value={location} onChange={(e) => setLocation(e.target.value)} className="mb-3" />
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>Hủy</Button>
            <Button type="submit" disabled={create.isPending}>{create.isPending ? 'Đang lưu...' : 'Tạo buổi học'}</Button>
          </div>
        </form>
      </Modal>

      {markFor && <MarkModal courseId={courseId} session={markFor} onClose={() => { setMarkFor(null); refresh() }} />}
    </div>
  )
}

function MarkModal({ courseId, session, onClose }: { courseId: string; session: SessionResponse; onClose: () => void }) {
  const roster = useQuery({
    queryKey: ['attendance', session.id],
    queryFn: () => apiClient.get<AttendanceRosterRow[]>(`/api/courses/${courseId}/sessions/${session.id}/attendance`).then((r) => r.data),
  })
  const [draft, setDraft] = useState<Record<string, AttendanceStatus>>({})
  const [error, setError] = useState<string | null>(null)
  const statusOf = (r: AttendanceRosterRow) => draft[r.enrollmentId] ?? r.status ?? 'PRESENT'

  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/courses/${courseId}/sessions/${session.id}/attendance`, {
      entries: (roster.data ?? []).map((r) => ({ enrollmentId: r.enrollmentId, status: statusOf(r), note: r.note })),
    }),
    onSuccess: onClose,
    onError: (e) => setError(getApiErrorMessage(e)),
  })

  return (
    <Modal title={`Điểm danh — ${session.title}`} open onClose={onClose}>
      {roster.isLoading && <p className="text-slate-400">Đang tải...</p>}
      <div className="mb-3 flex gap-2">
        <Button size="sm" variant="secondary" onClick={() => setDraft(Object.fromEntries((roster.data ?? []).map((r) => [r.enrollmentId, 'PRESENT' as AttendanceStatus])))}>Tất cả có mặt</Button>
      </div>
      <div className="max-h-96 space-y-1 overflow-y-auto">
        {roster.data?.map((r) => (
          <div key={r.enrollmentId} className="flex items-center justify-between rounded border border-slate-100 px-3 py-2">
            <div><div className="text-sm font-medium text-slate-900">{r.studentName}</div><div className="text-xs text-slate-400">{r.status ? '' : 'Chưa điểm danh · '}{r.studentEmail}</div></div>
            <select value={statusOf(r)} onChange={(e) => setDraft((d) => ({ ...d, [r.enrollmentId]: e.target.value as AttendanceStatus }))} className="h-9 rounded-md border border-slate-300 px-2 text-sm">
              {STATUS_ORDER.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </div>
        ))}
        {roster.data?.length === 0 && <p className="text-sm text-slate-400">Khóa chưa có học sinh.</p>}
      </div>
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Hủy</Button>
        <Button disabled={save.isPending || !roster.data?.length} onClick={() => save.mutate()}>{save.isPending ? 'Đang lưu...' : 'Lưu điểm danh'}</Button>
      </div>
    </Modal>
  )
}
