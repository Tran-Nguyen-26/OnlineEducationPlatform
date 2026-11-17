import { Pager, usePaged } from '../../components/Pager'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { AuditLogResponse } from '../../types/api'

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
                <td className="px-4 py-3 font-medium text-slate-900">{row.action}</td>
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
