import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../lib/apiClient'
import type { MyCertificateResponse } from '../../types/api'

export function CertificatesPage() {
  const certsQuery = useQuery({
    queryKey: ['student', 'certificates'],
    queryFn: () => apiClient.get<MyCertificateResponse[]>('/api/users/me/certificates').then((r) => r.data),
  })
  const rows = certsQuery.data ?? []

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Chứng chỉ</h1>
      <p className="mb-4 text-sm text-slate-500">Chứng chỉ được cấp khi trung tâm xác nhận bạn hoàn thành khóa học.</p>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Khóa học</th>
              <th className="px-4 py-3 font-medium">Số chứng chỉ</th>
              <th className="px-4 py-3 font-medium">Ngày cấp</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((c) => (
              <tr key={c.certificateNumber}>
                <td className="px-4 py-3 font-medium text-slate-900">{c.courseTitle} <span className="text-xs text-slate-400">({c.subjectName})</span></td>
                <td className="px-4 py-3 font-mono text-slate-600">{c.certificateNumber}</td>
                <td className="px-4 py-3 text-slate-500">{new Date(c.issuedAt).toLocaleDateString('vi-VN')}</td>
                <td className="px-4 py-3 text-right">
                  <Link to={`/student/certificates/${c.enrollmentId}`} className="text-blue-600 hover:underline">Xem / In</Link>
                </td>
              </tr>
            ))}
            {!certsQuery.isLoading && rows.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-slate-400" colSpan={4}>Chưa có chứng chỉ nào.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
