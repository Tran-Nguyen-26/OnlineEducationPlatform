import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'

type Verification = { id: string; courseTitle: string; issuedAt: string; issuer: string }

export default function VerifyCredentialPage() {
  const { id } = useParams()
  const result = useQuery({
    queryKey: ['public-credential', id],
    queryFn: async () => {
      const response = await fetch(`/api/public/credentials/${encodeURIComponent(id ?? '')}`)
      if (response.status === 400 || response.status === 404) return null
      if (!response.ok) throw new Error()
      return await response.json() as Verification
    },
    retry: false,
  })
  return (
    <main className="min-h-screen bg-slate-50 px-4 py-16">
      <section className="mx-auto max-w-xl space-y-4 rounded-xl border border-slate-200 bg-white p-6">
        <h1 className="text-2xl font-bold text-slate-900">Xác minh chứng chỉ</h1>
        {result.isPending && <p role="status">Đang xác minh chữ ký…</p>}
        {result.isError && <p role="alert">Không thể kết nối dịch vụ xác minh.</p>}
        {result.isSuccess && !result.data && <p role="status">Không tìm thấy chứng chỉ hợp lệ.</p>}
        {result.data && (
          <div className="space-y-2">
            <p className="font-semibold text-teal-800">Chữ ký hợp lệ</p>
            <h2 className="text-lg font-medium">{result.data.courseTitle}</h2>
            <p className="text-sm text-slate-600">Đơn vị cấp {result.data.issuer}</p>
            <p className="text-sm text-slate-600">Ngày cấp {new Date(result.data.issuedAt).toLocaleDateString('vi-VN')}</p>
          </div>
        )}
      </section>
    </main>
  )
}
