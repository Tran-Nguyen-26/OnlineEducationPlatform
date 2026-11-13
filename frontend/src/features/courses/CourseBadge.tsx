import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, QuizRequestError } from '@/api/quiz'
import { toDataURL } from 'qrcode'

type Badge = { id: string; verificationUrl: string; signedCredential: string }

export default function CourseBadge({ courseId, eligible }: { courseId: string; eligible: boolean }) {
  const path = `/api/v1/courses/${courseId}/credential`
  const key = ['course-credential', courseId]
  const cache = useQueryClient()
  const badge = useQuery({
    queryKey: key,
    queryFn: async () => {
      try { return await request<Badge>(path) }
      catch (error) {
        if (error instanceof QuizRequestError && error.status === 404) return null
        throw error
      }
    },
    retry: false,
  })
  const issue = useMutation({
    mutationFn: () => request<Badge>(path, { method: 'POST' }),
    onSuccess: (value) => { cache.setQueryData(key, value) },
  })
  const url = badge.data?.verificationUrl
  const [qr, setQr] = useState('')
  useEffect(() => {
    if (!url) return
    let active = true
    void toDataURL(url, { width: 224 }).then((value) => { if (active) setQr(value) }).catch(() => {})
    return () => { active = false }
  }, [url])

  function download(value: Badge) {
    const objectUrl = URL.createObjectURL(new Blob([value.signedCredential], { type: 'application/vc+jwt' }))
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = `oep-${value.id}.jwt`
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }

  if (badge.data) return (
    <section className="space-y-3 rounded-lg border border-teal-200 bg-teal-50 p-4">
      <h3 className="font-semibold text-teal-900">Chứng chỉ hoàn thành khóa học</h3>
      {qr && <img src={qr} width={224} height={224} alt="Mã QR tra cứu chứng chỉ" />}
      <div className="flex flex-wrap gap-3">
        <a href={badge.data.verificationUrl} className="text-sm font-medium text-teal-800 underline">Tra cứu công khai</a>
        <button type="button" onClick={() => badge.data && download(badge.data)} className="text-sm font-medium text-teal-800 underline">Tải chứng chỉ đã ký</button>
      </div>
    </section>
  )
  if (!eligible) return null
  return (
    <div className="space-y-2">
      <button type="button" disabled={issue.isPending || badge.isPending} onClick={() => issue.mutate()}
        className="rounded bg-teal-700 px-3 py-2 text-sm text-white disabled:opacity-50">Nhận chứng chỉ</button>
      {issue.isError && <p role="alert" className="text-sm text-red-700">{issue.error.message}</p>}
    </div>
  )
}
