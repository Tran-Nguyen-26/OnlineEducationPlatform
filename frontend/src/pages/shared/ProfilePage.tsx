import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { useAuth } from '../../auth/AuthContext'
import type { ProfileResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export function ProfilePage() {
  const { refreshMe } = useAuth()
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
      <h1 className="mb-4 text-2xl font-bold text-slate-900">Hồ sơ cá nhân</h1>
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
    </div>
  )
}
