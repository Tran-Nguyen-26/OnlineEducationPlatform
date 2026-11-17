import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { vnd } from '../../lib/csv'
import type { AdminUserDetailResponse } from '../../types/api'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['users', 'detail', userId],
    queryFn: () => apiClient.get<AdminUserDetailResponse>(`/api/admin/users/${userId}`).then((r) => r.data),
  })
  const u = query.data

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [dob, setDob] = useState('')
  const [organization, setOrganization] = useState('')
  const [bio, setBio] = useState('')
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)

  useEffect(() => {
    if (!u) return
    setFullName(u.fullName ?? '')
    setPhone(u.phone ?? '')
    setDob(u.dateOfBirth ?? '')
    setOrganization(u.organization ?? '')
    setBio(u.bio ?? '')
  }, [u])

  const anonymize = useMutation({
    mutationFn: () => apiClient.post(`/api/admin/users/${userId}/anonymize`),
    onSuccess: () => { setMsg({ ok: true, text: 'Đã ẩn danh tài khoản.' }); queryClient.invalidateQueries({ queryKey: ['admin', 'user', userId] }) },
    onError: (err) => setMsg({ ok: false, text: getApiErrorMessage(err) }),
  })

  const save = useMutation({
    mutationFn: () =>
      apiClient.patch(`/api/admin/users/${userId}/profile`, {
        fullName, phone: phone || null, dateOfBirth: dob || null, organization: organization || null, bio: bio || null,
      }),
    onSuccess: () => {
      setMsg({ ok: true, text: 'Đã lưu.' })
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err) => setMsg({ ok: false, text: getApiErrorMessage(err) }),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setMsg(null)
    save.mutate()
  }

  if (query.isLoading) return <p className="text-slate-400">Đang tải...</p>
  if (!u) return <p className="text-red-600">Không tìm thấy người dùng.</p>

  return (
    <div>
      <Link to="/admin/users" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
        <ArrowLeft size={16} /> Quay lại danh sách người dùng
      </Link>
      <div className="mb-6 flex items-center gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">{u.fullName ?? u.email}</h1>
        <Badge status={u.accountStatus} />
        <span className="text-sm text-slate-500">{u.role === 'ADMIN' ? 'Admin' : u.role === 'INSTRUCTOR' ? 'Giáo viên' : 'Học sinh'} · {u.email} · tạo ngày {new Date(u.createdAt).toLocaleDateString('vi-VN')}</span>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <form onSubmit={submit} className="space-y-3 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">Thông tin cá nhân</h2>
          <div><label className="mb-1 block text-sm text-slate-600">Họ và tên</label><Input required value={fullName} onChange={(e) => setFullName(e.target.value)} /></div>
          <div><label className="mb-1 block text-sm text-slate-600">Số điện thoại</label><Input value={phone} onChange={(e) => setPhone(e.target.value)} /></div>
          <div><label className="mb-1 block text-sm text-slate-600">Ngày sinh</label><Input type="date" value={dob} onChange={(e) => setDob(e.target.value)} /></div>
          <div><label className="mb-1 block text-sm text-slate-600">Trường / đơn vị</label><Input value={organization} onChange={(e) => setOrganization(e.target.value)} /></div>
          <div><label className="mb-1 block text-sm text-slate-600">Giới thiệu</label><textarea value={bio} onChange={(e) => setBio(e.target.value)} rows={3} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" /></div>
          {msg && <p className={`text-sm ${msg.ok ? 'text-green-700' : 'text-red-600'}`}>{msg.text}</p>}
          <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}</Button>
          {u.role !== 'ADMIN' && !u.email.endsWith('@anonymized.invalid') && (
            <div className="border-t border-slate-200 pt-3">
              <p className="mb-2 text-xs text-slate-500">Quyền được xóa: xóa danh tính (tên, email, liên hệ, thông báo), giữ lại kết quả học tập. Không thể hoàn tác.</p>
              <Button type="button" variant="secondary" disabled={anonymize.isPending}
                onClick={() => { if (window.confirm('Ẩn danh tài khoản này? Hành động KHÔNG thể hoàn tác.')) anonymize.mutate() }}>
                Ẩn danh tài khoản
              </Button>
            </div>
          )}
        </form>

        <div className="space-y-6">
          {u.role === 'STUDENT' && (
            <section className="rounded-lg border border-slate-200 bg-white p-5">
              <h2 className="mb-2 font-semibold text-slate-900">Khóa học đã ghi danh</h2>
              <table className="w-full text-left text-sm">
                <tbody className="divide-y divide-slate-100">
                  {u.enrollments.map((e) => (
                    <tr key={e.enrollmentId}>
                      <td className="py-2"><Link to={`/admin/courses/${e.courseId}`} className="font-medium text-slate-900 hover:underline">{e.courseTitle}</Link>
                        <div className="text-xs text-slate-400">Ghi danh {new Date(e.enrolledAt).toLocaleDateString('vi-VN')}</div></td>
                      <td className="py-2"><Badge status={e.status} /></td>
                      <td className="py-2 text-right text-xs text-slate-500">
                        {e.paymentStatus === 'NOT_APPLICABLE' ? 'Miễn phí' : <>{vnd(e.totalPaid)} / {vnd(e.feeAmount)} <Badge status={e.paymentStatus} /></>}
                      </td>
                    </tr>
                  ))}
                  {u.enrollments.length === 0 && <tr><td className="py-3 text-slate-400">Chưa ghi danh khóa nào.</td></tr>}
                </tbody>
              </table>
            </section>
          )}
          {u.role === 'INSTRUCTOR' && (
            <section className="rounded-lg border border-slate-200 bg-white p-5">
              <h2 className="mb-2 font-semibold text-slate-900">Khóa học được phân công</h2>
              {u.assignedCourses.map((c) => (
                <div key={c.courseId} className="flex items-center justify-between border-b border-slate-50 py-2 text-sm last:border-0">
                  <Link to={`/admin/courses/${c.courseId}`} className="font-medium text-slate-900 hover:underline">{c.courseTitle}</Link>
                  <Badge status={c.lifecycleStatus} />
                </div>
              ))}
              {u.assignedCourses.length === 0 && <p className="text-sm text-slate-400">Chưa được gán khóa học nào.</p>}
            </section>
          )}
        </div>
      </div>
    </div>
  )
}
