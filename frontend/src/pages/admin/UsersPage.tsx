import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { AccountStatus, AdminUserResponse, CreateTeacherResponse, PageResponse, ResetLinkResponse, UserRole } from '../../types/api'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Badge } from '../../components/Badge'
import { Modal } from '../../components/ui/Modal'

const PAGE_SIZE = 15
const ROLE_LABEL: Record<UserRole, string> = { ADMIN: 'Admin', INSTRUCTOR: 'Giáo viên', STUDENT: 'Học sinh' }

export function UsersPage() {
  const { user: currentUser } = useAuth()
  const queryClient = useQueryClient()
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(q)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [q])

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
  if (debouncedQ.trim()) params.set('q', debouncedQ.trim())
  if (role) params.set('role', role)
  if (status) params.set('status', status)

  const usersQuery = useQuery({
    queryKey: ['users', 'search', debouncedQ, role, status, page],
    queryFn: () => apiClient.get<PageResponse<AdminUserResponse>>(`/api/admin/users/search?${params.toString()}`).then((r) => r.data),
  })
  const result = usersQuery.data

  const toggleStatusMutation = useMutation({
    mutationFn: ({ id, newStatus }: { id: string; newStatus: AccountStatus }) =>
      apiClient.patch(`/api/admin/users/${id}/status`, { status: newStatus }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setError(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  const [resetLink, setResetLink] = useState<{ user: AdminUserResponse; link: ResetLinkResponse } | null>(null)
  const resetLinkMutation = useMutation({
    mutationFn: (target: AdminUserResponse) =>
      apiClient.post<ResetLinkResponse>(`/api/admin/users/${target.id}/reset-password-link`).then((r) => ({ user: target, link: r.data })),
    onSuccess: (res) => {
      setResetLink(res)
      setError(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })
  const resetUrl = resetLink ? `${window.location.origin}/reset-password?token=${resetLink.link.token}` : ''

  const [createOpen, setCreateOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [created, setCreated] = useState<CreateTeacherResponse | null>(null)
  const createStudent = useMutation({
    mutationFn: () => apiClient.post<CreateTeacherResponse>('/api/admin/students', { email, fullName }).then((r) => r.data),
    onSuccess: (res) => {
      setCreated(res)
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err) => setCreateError(getApiErrorMessage(err)),
  })
  function submitCreate(e: FormEvent) {
    e.preventDefault()
    createStudent.mutate()
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Người dùng</h1>
        <Button
          onClick={() => {
            setEmail('')
            setFullName('')
            setCreateError(null)
            setCreated(null)
            setCreateOpen(true)
          }}
        >
          <Plus size={16} /> Thêm học sinh
        </Button>
      </div>

      <div className="mb-4 flex flex-wrap gap-3">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Tìm theo họ tên hoặc email..."
          className="h-9 w-72 rounded-md border border-slate-300 px-3 text-sm"
        />
        <select
          value={role}
          onChange={(e) => { setRole(e.target.value as UserRole | ''); setPage(0) }}
          className="h-9 rounded-md border border-slate-300 px-3 text-sm"
        >
          <option value="">Tất cả vai trò</option>
          {(Object.keys(ROLE_LABEL) as UserRole[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
        </select>
        <select
          value={status}
          onChange={(e) => { setStatus(e.target.value as AccountStatus | ''); setPage(0) }}
          className="h-9 rounded-md border border-slate-300 px-3 text-sm"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="DISABLED">Đã vô hiệu hóa</option>
        </select>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Họ tên</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Vai trò</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
              <th className="px-4 py-3 font-medium">Ngày tạo</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {usersQuery.isLoading && (
              <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Đang tải...</td></tr>
            )}
            {result?.items.map((user) => (
              <tr key={user.id}>
                <td className="px-4 py-3 font-medium text-slate-900">
                  <Link to={`/admin/users/${user.id}`} className="hover:underline">{user.fullName ?? '—'}</Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{user.email}</td>
                <td className="px-4 py-3 text-slate-500">{ROLE_LABEL[user.role]}</td>
                <td className="px-4 py-3"><Badge status={user.accountStatus} /></td>
                <td className="px-4 py-3 text-slate-500">{new Date(user.createdAt).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <Button size="sm" variant="secondary" disabled={resetLinkMutation.isPending} onClick={() => resetLinkMutation.mutate(user)}>
                      Tạo link đặt lại MK
                    </Button>
                    {user.id !== currentUser?.id && (
                      <Button
                        size="sm"
                        variant={user.accountStatus === 'ACTIVE' ? 'danger' : 'secondary'}
                        disabled={toggleStatusMutation.isPending}
                        onClick={() => toggleStatusMutation.mutate({ id: user.id, newStatus: user.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}
                      >
                        {user.accountStatus === 'ACTIVE' ? 'Vô hiệu hóa' : 'Kích hoạt lại'}
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {result?.items.length === 0 && (
              <tr><td className="px-4 py-6 text-slate-400" colSpan={6}>Không có người dùng nào khớp bộ lọc.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {result && result.totalPages > 0 && (
        <div className="mt-3 flex items-center justify-between text-sm text-slate-500">
          <span>{result.total} người dùng · trang {result.page + 1}/{result.totalPages}</span>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Trước</Button>
            <Button size="sm" variant="secondary" disabled={page + 1 >= result.totalPages} onClick={() => setPage((p) => p + 1)}>Sau</Button>
          </div>
        </div>
      )}

      <Modal title="Link đặt lại mật khẩu" open={resetLink !== null} onClose={() => setResetLink(null)}>
        <p className="mb-3 text-sm text-slate-600">
          Gửi link này cho <strong>{resetLink?.user.fullName ?? resetLink?.user.email}</strong>. Link có hiệu lực tới{' '}
          {resetLink && new Date(resetLink.link.expiresAt).toLocaleString('vi-VN')} và chỉ dùng được một lần.
        </p>
        <div className="mb-4 break-all rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">{resetUrl}</div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => { navigator.clipboard?.writeText(resetUrl).catch(() => {}) }}>Sao chép link</Button>
          <Button onClick={() => setResetLink(null)}>Đóng</Button>
        </div>
      </Modal>

      <Modal title="Thêm học sinh" open={createOpen} onClose={() => setCreateOpen(false)}>
        {created ? (
          <div>
            <p className="mb-3 text-sm text-slate-600">
              Đã tạo tài khoản cho <strong>{created.fullName}</strong> ({created.email}). Mật khẩu tạm chỉ hiện một lần — hãy gửi cho học sinh; học sinh phải đổi mật khẩu ở lần đăng nhập đầu.
            </p>
            <div className="mb-4 rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-sm">{created.temporaryPassword}</div>
            <div className="flex justify-end"><Button onClick={() => setCreateOpen(false)}>Đóng</Button></div>
          </div>
        ) : (
          <form onSubmit={submitCreate}>
            <label className="mb-1 block text-sm font-medium text-slate-700">Họ và tên</label>
            <Input required value={fullName} onChange={(e) => setFullName(e.target.value)} className="mb-4" />
            <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
            <Input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="mb-4" />
            {createError && <p className="mb-4 text-sm text-red-600">{createError}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>Hủy</Button>
              <Button type="submit" disabled={createStudent.isPending}>{createStudent.isPending ? 'Đang tạo...' : 'Tạo tài khoản'}</Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
