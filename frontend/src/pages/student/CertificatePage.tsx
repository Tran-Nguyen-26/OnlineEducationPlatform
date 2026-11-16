import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Printer } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { CertificateResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'

export function CertificatePage() {
  const { enrollmentId } = useParams<{ enrollmentId: string }>()

  const certQuery = useQuery({
    queryKey: ['student', 'enrollments', enrollmentId, 'certificate'],
    queryFn: () => apiClient.get<CertificateResponse>(`/api/enrollments/${enrollmentId}/certificate`).then((r) => r.data),
    enabled: !!enrollmentId,
  })

  if (certQuery.isLoading) return <p className="p-8 text-slate-400">Đang tải...</p>
  if (certQuery.isError) {
    return <p className="p-8 text-red-600">{getApiErrorMessage(certQuery.error)}</p>
  }
  const cert = certQuery.data
  if (!cert) return null

  return (
    <div className="min-h-screen bg-slate-100 p-8 print:bg-white print:p-0">
      <div className="mx-auto mb-6 flex max-w-3xl items-center justify-between print:hidden">
        <Link to="/student/certificates" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
          <ArrowLeft size={16} /> Quay lại danh sách chứng chỉ
        </Link>
        <Button onClick={() => window.print()}>
          <Printer size={16} /> In / Lưu PDF
        </Button>
      </div>

      <div className="mx-auto max-w-3xl border-8 border-double border-slate-300 bg-white p-12 text-center shadow-sm print:border-slate-900 print:shadow-none">
        <p className="text-sm uppercase tracking-[0.3em] text-slate-400">Chứng chỉ hoàn thành khóa học</p>
        <h1 className="mt-6 text-3xl font-bold text-slate-900">{cert.studentFullName ?? cert.studentEmail}</h1>
        <p className="mt-4 text-slate-600">đã hoàn thành khóa học</p>
        <h2 className="mt-2 text-2xl font-semibold text-slate-900">{cert.courseTitle}</h2>
        <p className="mt-1 text-sm text-slate-500">Môn: {cert.subjectName}</p>

        <div className="mx-auto mt-10 h-px w-32 bg-slate-300" />

        <div className="mt-6 flex items-center justify-between text-sm text-slate-500">
          <span>Ngày cấp: {new Date(cert.issuedAt).toLocaleDateString('vi-VN')}</span>
          <span>Số chứng chỉ: {cert.certificateNumber}</span>
        </div>
        <p className="mt-6 text-xs text-slate-400">
          Xác thực chứng chỉ này tại {window.location.origin}/verify-certificate?code={cert.certificateNumber}
        </p>
      </div>
    </div>
  )
}
