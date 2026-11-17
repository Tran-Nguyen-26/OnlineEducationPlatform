import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Award } from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import { TiltCard } from '../../components/ui/TiltCard'
import type { MyCertificateResponse } from '../../types/api'

export function CertificatesPage() {
  const certsQuery = useQuery({
    queryKey: ['student', 'certificates'],
    queryFn: () => apiClient.get<MyCertificateResponse[]>('/api/users/me/certificates').then((r) => r.data),
  })
  const rows = certsQuery.data ?? []

  return (
    <div>
      <h1 className="mb-1 text-2xl font-extrabold tracking-tight text-slate-900">Chứng chỉ</h1>
      <p className="mb-4 text-sm text-slate-500">Chứng chỉ được cấp khi trung tâm xác nhận bạn hoàn thành khóa học.</p>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {rows.map((c) => (
          <TiltCard key={c.certificateNumber} max={6} className="h-full">
            <Link to={`/student/certificates/${c.enrollmentId}`} className="surface block h-full overflow-hidden">
              <div className="relative bg-brand-gradient p-5 text-white">
                <Award size={28} className="text-amber-300" />
                <div className="mt-3 text-xs font-semibold tracking-widest uppercase opacity-80">Chứng chỉ hoàn thành</div>
                <div className="absolute top-0 right-0 h-24 w-24 rounded-full bg-white/10 blur-xl" />
              </div>
              <div className="p-5">
                <div className="font-bold text-slate-900">{c.courseTitle}</div>
                <div className="text-xs text-slate-400">{c.subjectName}</div>
                <div className="mt-3 font-mono text-xs text-slate-600">{c.certificateNumber}</div>
                <div className="mt-1 text-xs text-slate-500">Cấp ngày {new Date(c.issuedAt).toLocaleDateString('vi-VN')}</div>
                <div className="mt-4 text-sm font-semibold text-indigo-600">Xem / In →</div>
              </div>
            </Link>
          </TiltCard>
        ))}
        {!certsQuery.isLoading && rows.length === 0 && <p className="text-slate-400">Chưa có chứng chỉ nào.</p>}
      </div>
    </div>
  )
}
