import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import type { ProfileResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Avatar } from '../../components/Avatar'
import { NotificationPrefsCard } from '../../components/NotificationPrefsCard'

export function ProfilePage() {
  const { refreshMe, user } = useAuth()
  const queryClient = useQueryClient()
  const [avatarBusy, setAvatarBusy] = useState(false)

  async function uploadAvatar(file: File) {
    setAvatarBusy(true)
    setMessage(null)
    try {
      const { data } = await apiClient.post<{ uploadUrl: string; objectKey: string }>('/api/users/me/avatar/upload-url', { fileName: file.name, mimeType: file.type, fileSize: file.size })
      const put = await fetch(data.uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file })
      if (!put.ok) throw new Error('Tải ảnh lên thất bại')
      await apiClient.post('/api/users/me/avatar/confirm', { objectKey: data.objectKey })
      await queryClient.invalidateQueries({ queryKey: ['profile'] })
      setMessage({ ok: true, text: 'Đã cập nhật ảnh đại diện.' })
    } catch (err) {
      setMessage({ ok: false, text: getApiErrorMessage(err) })
    } finally {
      setAvatarBusy(false)
    }
  }

  async function removeAvatar() {
    await apiClient.delete('/api/users/me/avatar')
    await queryClient.invalidateQueries({ queryKey: ['profile'] })
  }
  const query = useQuery({
    queryKey: ['profile'],
    queryFn: () => apiClient.get<ProfileResponse>('/api/users/me/profile').then((r) => r.data),
  })
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [dob, setDob] = useState('')
  const [organization, setOrganization] = useState('')
  const [bio, setBio] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  useEffect(() => {
    const p = query.data
    if (!p) return
    setFullName(p.fullName ?? '')
    setPhone(p.phone ?? '')
    setDob(p.dateOfBirth ?? '')
    setOrganization(p.organization ?? '')
    setBio(p.bio ?? '')
  }, [query.data])

  const save = useMutation({
    mutationFn: () =>
      apiClient.patch('/api/users/me/profile', {
        fullName,
        phone: phone || null,
        dateOfBirth: dob || null,
        organization: organization || null,
        bio: bio || null,
      }),
    onSuccess: async () => {
      await refreshMe()
      setMessage({ ok: true, text: 'Đã lưu thông tin.' })
    },
    onError: (err) => setMessage({ ok: false, text: getApiErrorMessage(err) }),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setMessage(null)
    save.mutate()
  }

  return (
    <div className="max-w-xl">
      <h1 className="mb-4 text-2xl font-extrabold tracking-tight text-slate-900">Hồ sơ cá nhân</h1>
      <div className="mb-4 flex items-center gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <Avatar size={72} name={user?.fullName} />
        <div>
          <div className="mb-2 text-sm font-medium text-slate-800">Ảnh đại diện</div>
          <div className="flex items-center gap-3">
            <label className="inline-flex h-8 cursor-pointer items-center rounded-md bg-slate-100 px-3 text-sm font-medium text-slate-900 hover:bg-slate-200">
              {avatarBusy ? 'Đang tải lên...' : 'Chọn ảnh'}
              <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" disabled={avatarBusy}
                onChange={(e) => { const f = e.target.files?.[0]; if (f) uploadAvatar(f); e.target.value = '' }} />
            </label>
            {query.data?.avatarUrl && <button type="button" className="text-sm text-red-600 hover:underline" onClick={removeAvatar}>Xóa ảnh</button>}
          </div>
          <p className="mt-1 text-xs text-slate-400">PNG, JPG, WEBP hoặc GIF, tối đa 2MB.</p>
        </div>
      </div>
      <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Email (không đổi được)</label>
          <Input value={query.data?.email ?? ''} disabled />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Họ và tên</label>
          <Input required value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Số điện thoại</label>
          <Input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Ngày sinh</label>
          <Input type="date" value={dob} onChange={(e) => setDob(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Trường / đơn vị</label>
          <Input value={organization} onChange={(e) => setOrganization(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Giới thiệu</label>
          <textarea value={bio} onChange={(e) => setBio(e.target.value)} rows={3} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
        </div>
        {message && <p className={`text-sm ${message.ok ? 'text-green-700' : 'text-red-600'}`}>{message.text}</p>}
        <div className="flex items-center justify-between">
          <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}</Button>
          <Link to="/change-password" className="text-sm text-blue-600 hover:underline">Đổi mật khẩu</Link>
        </div>
      </form>
      <div className="surface mt-6 p-5">
        <h2 className="font-bold text-slate-900">Dữ liệu cá nhân của tôi</h2>
        <p className="mt-1 text-sm text-slate-500">Tải về toàn bộ dữ liệu nền tảng đang lưu về bạn (hồ sơ, đăng ký, tiến độ, bài làm, thông báo) dưới dạng JSON.</p>
        <Button className="mt-3" variant="secondary" onClick={async () => {
          const { data } = await apiClient.get('/api/users/me/export')
          const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
          const a = document.createElement('a'); a.href = url; a.download = 'oep-du-lieu-cua-toi.json'; a.click(); URL.revokeObjectURL(url)
        }}>Tải dữ liệu của tôi</Button>
      </div>
      <NotificationPrefsCard />
    </div>
  )
}
