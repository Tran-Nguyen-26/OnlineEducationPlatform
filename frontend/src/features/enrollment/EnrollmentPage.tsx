import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { accessCodeError, fetchEnrollments, redeemAccessCode } from '@/api/accessCodes'

const statusLabels = {
  ACTIVE: 'Đang học',
  ARCHIVED_STATIC: 'Xem lưu trữ',
  UNAVAILABLE: 'Đã ngừng truy cập',
}

export default function EnrollmentPage() {
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('')
  const { data: enrollments, isPending, isError } = useQuery({
    queryKey: ['enrollments'],
    queryFn: fetchEnrollments,
  })
  const redeem = useMutation({
    mutationFn: redeemAccessCode,
    onSuccess: async (enrollment) => {
      setCode('')
      setMessage(`Đã cập nhật quyền học cho ${enrollment.courseTitle}.`)
      await queryClient.invalidateQueries({ queryKey: ['enrollments'] })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage('')
    if (code.trim()) redeem.mutate(code.trim())
  }

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Khóa học của tôi</h1>
      <form onSubmit={handleSubmit} className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
        <label htmlFor="access-code" className="block font-medium text-slate-900">
          Mã kích hoạt hoặc gia hạn
        </label>
        <div className="flex flex-wrap gap-2">
          <input id="access-code" value={code} onChange={(event) => setCode(event.target.value)}
            autoComplete="off" required maxLength={64} className="min-w-0 flex-1 rounded-lg border px-3 py-2" />
          <button disabled={redeem.isPending || !code.trim()} className="rounded-lg bg-teal-700 px-4 py-2 text-white disabled:opacity-60">
            {redeem.isPending ? 'Đang kiểm tra…' : 'Áp dụng mã'}
          </button>
        </div>
        {message && <p role="status" className="text-sm text-teal-800">{message}</p>}
        {redeem.isError && <p role="alert" className="text-sm text-red-700">{accessCodeError(redeem.error, 'Không thể áp dụng mã.')}</p>}
      </form>
      {isPending && <p role="status">Đang tải khóa học…</p>}
      {isError && <p role="alert">Không tải được danh sách khóa học.</p>}
      {enrollments?.length === 0 && <p className="text-slate-600">Nhập mã do trung tâm cấp để mở khóa học đầu tiên.</p>}
      <ul className="space-y-3">
        {enrollments?.map((enrollment) => (
          <li key={enrollment.id} className="space-y-1 rounded-xl border border-slate-200 bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              {enrollment.accessState === 'UNAVAILABLE'
                ? <h2 className="font-semibold">{enrollment.courseTitle}</h2>
                : <Link className="font-semibold text-teal-800 hover:underline" to={`/courses/${enrollment.courseId}`}>{enrollment.courseTitle}</Link>}
              <span className="rounded-full bg-slate-100 px-3 py-1 text-sm">{statusLabels[enrollment.accessState]}</span>
            </div>
            {enrollment.accessExpiresAt && (
              <p className="text-sm text-slate-600">
                Hết hạn học ngày {new Date(enrollment.accessExpiresAt).toLocaleDateString('vi-VN')}
              </p>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
