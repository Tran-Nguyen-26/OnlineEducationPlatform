import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { AccountStatus, AdminUserResponse, UserRole } from '../../types/api'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Badge } from '../../components/Badge'

const ROLE_OPTIONS: Array<UserRole | ''> = ['', 'ADMIN', 'INSTRUCTOR', 'STUDENT']
const STATUS_OPTIONS: Array<AccountStatus | ''> = ['', 'ACTIVE', 'DISABLED']

export function UsersPage() {
  const { user: currentUser } = useAuth()
  const queryClient = useQueryClient()
  const [role, setRole] = useState<UserRole | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [error, setError] = useState<string | null>(null)

  const params = new URLSearchParams()
  if (role) params.set('role', role)
  if (status) params.set('status', status)

  const { data: users, isLoading } = useQuery({
    queryKey: ['users', role, status],
    queryFn: () => apiClient.get<AdminUserResponse[]>(`/api/admin/users?${params.toString()}`).then((r) => r.data),
  })

  const toggleStatusMutation = useMutation({
    mutationFn: ({ id, newStatus }: { id: string; newStatus: AccountStatus }) =>
      apiClient.patch(`/api/admin/users/${id}/status`, { status: newStatus }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setError(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Người dùng</h1>

      <div className="mb-4 flex gap-3">
        <select
          value={role}
          onChange={(e) => setRole(e.target.value as UserRole | '')}
          className="h-9 rounded-md border border-slate-300 px-3 text-sm"
        >
          {ROLE_OPTIONS.map((r) => (
            <option key={r} value={r}>
              {r || 'Tất cả vai trò'}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as AccountStatus | '')}
          className="h-9 rounded-md border border-slate-300 px-3 text-sm"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s || 'Tất cả trạng thái'}
            </option>
          ))}
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
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Đang tải...
                </td>
              </tr>
            )}
            {users?.map((user) => (
              <tr key={user.id}>
                <td className="px-4 py-3 font-medium text-slate-900">{user.fullName ?? '—'}</td>
                <td className="px-4 py-3 text-slate-500">{user.email}</td>
                <td className="px-4 py-3 text-slate-500">{user.role}</td>
                <td className="px-4 py-3">
                  <Badge status={user.accountStatus} />
                </td>
                <td className="px-4 py-3 text-right">
                  {user.id !== currentUser?.id && (
                    <Button
                      size="sm"
                      variant={user.accountStatus === 'ACTIVE' ? 'danger' : 'secondary'}
                      disabled={toggleStatusMutation.isPending}
                      onClick={() =>
                        toggleStatusMutation.mutate({
                          id: user.id,
                          newStatus: user.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                        })
                      }
                    >
                      {user.accountStatus === 'ACTIVE' ? 'Vô hiệu hóa' : 'Kích hoạt lại'}
                    </Button>
                  )}
                </td>
              </tr>
            ))}
            {users?.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={5}>
                  Không có người dùng nào khớp bộ lọc.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
