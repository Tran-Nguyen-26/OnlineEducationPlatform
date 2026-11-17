import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Award, Printer } from 'lucide-react'
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
    <div className="min-h-[70vh] print:bg-white print:p-0">
      <div className="mx-auto mb-6 flex max-w-3xl items-center justify-between print:hidden">
        <Link to="/student/certificates" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:underline">
          <ArrowLeft size={16} /> Quay lại danh sách chứng chỉ
        </Link>
        <Button onClick={() => window.print()}>
          <Printer size={16} /> In / Lưu PDF
        </Button>
      </div>

      <div className="mx-auto max-w-3xl rounded-3xl bg-brand-gradient p-1.5 shadow-depth [print-color-adjust:exact] print:rounded-none print:shadow-none">
        <div className="relative overflow-hidden rounded-[1.35rem] bg-white px-8 py-12 text-center sm:px-14 print:rounded-none">
          <div className="pointer-events-none absolute -top-24 -left-24 h-64 w-64 rounded-full bg-indigo-200/50 blur-3xl print:hidden" />
          <div className="pointer-events-none absolute -right-24 -bottom-24 h-64 w-64 rounded-full bg-teal-200/50 blur-3xl print:hidden" />
          <div className="pointer-events-none absolute inset-4 rounded-2xl border border-indigo-200/70" />

          <div className="relative">
            <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><Award size={30} /></span>
            <p className="mt-5 text-xs font-semibold tracking-[0.35em] text-indigo-600 uppercase">Chứng chỉ hoàn thành khóa học</p>
            <p className="mt-8 text-sm text-slate-500">Chứng nhận</p>
            <h1 className="text-brand-gradient mt-2 text-4xl font-extrabold tracking-tight sm:text-5xl">{cert.studentFullName ?? cert.studentEmail}</h1>
            <p className="mt-6 text-slate-600">đã hoàn thành xuất sắc khóa học</p>
            <h2 className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">{cert.courseTitle}</h2>
            <p className="mt-1 text-sm text-slate-500">Môn: {cert.subjectName}</p>

            <div className="mx-auto mt-10 h-px w-40 bg-gradient-to-r from-transparent via-indigo-300 to-transparent" />

            <div className="mt-6 grid gap-4 text-sm text-slate-600 sm:grid-cols-3 sm:items-end">
              <div className="sm:text-left"><div className="text-xs text-slate-400">Ngày cấp</div><div className="font-semibold">{new Date(cert.issuedAt).toLocaleDateString('vi-VN')}</div></div>
              <div><div className="mx-auto mb-1 h-px w-32 bg-slate-300" /><div className="text-xs text-slate-400">Online Education Platform</div></div>
              <div className="sm:text-right"><div className="text-xs text-slate-400">Số chứng chỉ</div><div className="font-mono font-semibold text-slate-800">{cert.certificateNumber}</div></div>
            </div>
            <p className="mt-8 text-xs text-slate-400">
              Xác thực chứng chỉ này tại {window.location.origin}/verify-certificate?code={cert.certificateNumber}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
