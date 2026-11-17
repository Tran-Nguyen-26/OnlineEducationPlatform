import { Pager, usePaged } from '../../components/Pager'
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Copy, Plus } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { AdminUserResponse, CreateTeacherResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Modal } from '../../components/ui/Modal'
import { Badge } from '../../components/Badge'

export function TeachersPage() {
  const queryClient = useQueryClient()
  const { data: teachers, isLoading } = useQuery({
    queryKey: ['users', 'INSTRUCTOR'],
    queryFn: () => apiClient.get<AdminUserResponse[]>('/api/admin/users?role=INSTRUCTOR').then((r) => r.data),
  })
  const pgd = usePaged(teachers ?? [], 15)

  const [open, setOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<CreateTeacherResponse | null>(null)

  function openCreate() {
    setEmail('')
    setFullName('')
    setError(null)
    setCreated(null)
    setOpen(true)
  }

  const createMutation = useMutation({
    mutationFn: () => apiClient.post<CreateTeacherResponse>('/api/admin/teachers', { email, fullName }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['users', 'INSTRUCTOR'] })
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setCreated(res.data)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    createMutation.mutate()
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Giáo viên</h1>
        <Button onClick={openCreate}>
          <Plus size={16} /> Tạo tài khoản giáo viên
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Họ tên</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Trạng thái</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={3}>
                  Đang tải...
                </td>
              </tr>
            )}
            {pgd.slice.map((teacher) => (
              <tr key={teacher.id}>
                <td className="px-4 py-3 font-medium text-slate-900"><Link to={`/admin/users/${teacher.id}`} className="hover:underline">{teacher.fullName ?? '—'}</Link></td>
                <td className="px-4 py-3 text-slate-500">{teacher.email}</td>
                <td className="px-4 py-3">
                  <Badge status={teacher.accountStatus} />
                </td>
              </tr>
            ))}
            {teachers?.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={3}>
                  Chưa có giáo viên nào.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Modal title="Tạo tài khoản giáo viên" open={open} onClose={() => setOpen(false)}>
        {created ? (
          <div>
            <p className="mb-3 text-sm text-slate-600">
              Đã tạo tài khoản cho <span className="font-medium">{created.email}</span>. Gửi mật khẩu tạm dưới đây cho
              giáo viên — mật khẩu chỉ hiển thị <span className="font-semibold">một lần duy nhất</span>, giáo viên sẽ
              phải đổi mật khẩu ở lần đăng nhập đầu.
            </p>
            <div className="mb-4 flex items-center justify-between rounded-md bg-slate-100 px-3 py-2 font-mono text-sm">
              {created.temporaryPassword}
              <button
                onClick={() => navigator.clipboard.writeText(created.temporaryPassword)}
                className="text-slate-400 hover:text-slate-700"
                aria-label="Copy"
              >
                <Copy size={16} />
              </button>
            </div>
            <div className="flex justify-end">
              <Button onClick={() => setOpen(false)}>Đóng</Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-slate-700">Họ tên</label>
            <Input required value={fullName} onChange={(e) => setFullName(e.target.value)} className="mb-4" />

            <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
            <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mb-4" />

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Đang tạo...' : 'Tạo tài khoản'}
              </Button>
            </div>
          </form>
        )}
      </Modal>
      <Pager page={pgd.page} pages={pgd.pages} total={pgd.total} setPage={pgd.setPage} label="giáo viên" />
    </div>
  )
}
