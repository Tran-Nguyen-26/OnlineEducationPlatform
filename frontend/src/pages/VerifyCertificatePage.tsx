import { AuthShell } from '../components/AuthShell'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../lib/apiClient'
import type { CertificateResponse } from '../types/api'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'

export function VerifyCertificatePage() {
  const [searchParams] = useSearchParams()
  const [code, setCode] = useState(searchParams.get('code') ?? '')
  const [submittedCode, setSubmittedCode] = useState(searchParams.get('code') ?? '')

  const certQuery = useQuery({
    queryKey: ['verify-certificate', submittedCode],
    queryFn: () => apiClient.get<CertificateResponse>(`/api/certificates/verify/${submittedCode}`).then((r) => r.data),
    enabled: submittedCode.length > 0,
    retry: false,
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmittedCode(code.trim().toUpperCase())
  }

  return (
    <AuthShell wide>
      <div className="w-full rounded-2xl bg-white p-8 shadow-depth ring-1 ring-slate-200/70">
        <div className="mb-6 flex items-center gap-2">
          <ShieldCheck className="text-slate-700" />
          <h1 className="text-xl font-bold text-slate-900">Xác thực chứng chỉ</h1>
        </div>

        <form onSubmit={handleSubmit} className="mb-6 flex gap-2">
          <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="VD: CERT-ABCD1234" className="flex-1" />
          <Button type="submit">Kiểm tra</Button>
        </form>

        {certQuery.isLoading && <p className="text-sm text-slate-400">Đang kiểm tra...</p>}
        {certQuery.isError && (
          <p className="rounded-md bg-red-50 p-3 text-sm text-red-700">{getApiErrorMessage(certQuery.error)}</p>
        )}
        {certQuery.data && (
          <div className="rounded-md bg-green-50 p-4 text-sm text-green-800">
            <p className="mb-2 font-medium">Chứng chỉ hợp lệ.</p>
            <p>
              <strong>{certQuery.data.studentFullName ?? certQuery.data.studentEmail}</strong> đã hoàn thành khóa học{' '}
              <strong>{certQuery.data.courseTitle}</strong>.
            </p>
            <p className="mt-1 text-xs text-green-700">
              Cấp ngày {new Date(certQuery.data.issuedAt).toLocaleDateString('vi-VN')} — Mã: {certQuery.data.certificateNumber}
            </p>
          </div>
        )}
      </div>
    </AuthShell>
  )
}
