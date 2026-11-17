import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export function BroadcastPage() {
  const [audience, setAudience] = useState('STUDENTS')
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null)

  const send = useMutation({
    mutationFn: () => apiClient.post<{ recipients: number }>('/api/admin/broadcast', { audience, title, message }).then((r) => r.data),
    onSuccess: (d) => {
      setResult({ ok: true, text: `Đã gửi tới ${d.recipients} người dùng.` })
      setTitle('')
      setMessage('')
    },
    onError: (err) => setResult({ ok: false, text: getApiErrorMessage(err) }),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setResult(null)
    if (window.confirm('Gửi thông báo này tới toàn bộ nhóm người nhận đã chọn?')) send.mutate()
  }

  return (
    <div className="max-w-xl">
      <h1 className="mb-1 text-2xl font-extrabold tracking-tight text-slate-900">Thông báo toàn hệ thống</h1>
      <p className="mb-4 text-sm text-slate-500">
        Gửi thông báo trong ứng dụng tới mọi tài khoản đang hoạt động của nhóm được chọn (VD: nghỉ lễ, thay đổi lịch, bảo trì). Để thông báo riêng cho một khóa, dùng tab "Thông báo" trong khóa đó.
      </p>
      <form onSubmit={submit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Người nhận</label>
          <select value={audience} onChange={(e) => setAudience(e.target.value)} className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm">
            <option value="STUDENTS">Tất cả học sinh</option>
            <option value="INSTRUCTORS">Tất cả giáo viên</option>
            <option value="ALL">Học sinh và giáo viên</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Tiêu đề</label>
          <Input required maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Nội dung</label>
          <textarea required rows={4} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
        </div>
        {result && <p className={`text-sm ${result.ok ? 'text-green-700' : 'text-red-600'}`}>{result.text}</p>}
        <Button type="submit" disabled={send.isPending}>{send.isPending ? 'Đang gửi...' : 'Gửi thông báo'}</Button>
      </form>
    </div>
  )
}
