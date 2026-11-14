import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera, UserRound } from 'lucide-react'
import { request } from '@/api/quiz'

interface Profile {
  user_id: string
  email: string
  role: string
  full_name: string
  phone: string | null
  organization: string | null
  bio: string | null
  date_of_birth: string | null
  avatar_url: string | null
}

export default function MyProfilePage() {
  const cache = useQueryClient()
  const [avatar, setAvatar] = useState<File>()
  const [avatarError, setAvatarError] = useState('')
  const profile = useQuery({
    queryKey: ['my-profile'],
    queryFn: () => request<Profile>('/api/me/profile'),
  })
  const save = useMutation({
    mutationFn: (value: Pick<Profile, 'full_name' | 'phone' | 'organization' | 'bio' | 'date_of_birth'>) =>
      request<Profile>('/api/me/profile', { method: 'PATCH', body: JSON.stringify(value) }),
    onSuccess: (value) => cache.setQueryData(['my-profile'], value),
  })
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const ticket = await request<{ object_key: string; upload_url: string; headers: Record<string, string[]> }>(
        '/api/me/profile/avatar', { method: 'POST', body: JSON.stringify({ filename: file.name, size: file.size }) },
      )
      const signedHeaders = Object.fromEntries(Object.entries(ticket.headers)
        .filter(([name]) => !['host', 'content-length'].includes(name.toLowerCase()))
        .map(([name, values]) => [name, values.join(',')]))
      const sent = await fetch(ticket.upload_url, { method: 'PUT', body: file, headers: signedHeaders })
      if (!sent.ok) throw new Error('Không tải được ảnh lên kho lưu trữ.')
      return request<Profile>('/api/me/profile/avatar/confirm', {
        method: 'POST', body: JSON.stringify({ object_key: ticket.object_key }),
      })
    },
    onSuccess: (value) => {
      setAvatar(undefined)
      cache.setQueryData(['my-profile'], value)
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    save.mutate({
      full_name: String(fields.get('fullName')).trim(),
      phone: String(fields.get('phone')).trim() || null,
      organization: String(fields.get('organization')).trim() || null,
      bio: String(fields.get('bio')).trim() || null,
      date_of_birth: String(fields.get('dateOfBirth')) || null,
    })
  }

  if (profile.isPending) return <p role="status" className="rounded-xl bg-white p-5">Đang tải hồ sơ…</p>
  if (profile.isError) return <p role="alert" className="rounded-xl border border-rose-200 bg-white p-5 text-rose-800">{profile.error.message}</p>
  const user = profile.data

  return <section className="mx-auto max-w-3xl space-y-5">
    <header><p className="text-sm font-semibold uppercase tracking-wide text-teal-700">Tài khoản</p><h1 className="mt-1 text-2xl font-bold text-slate-900">Hồ sơ cá nhân</h1><p className="mt-2 text-sm text-slate-600">Cập nhật thông tin liên hệ và ảnh đại diện của bạn.</p></header>
    <div className="flex flex-wrap items-center gap-4 rounded-xl border border-slate-200 bg-white p-5">
      {user.avatar_url
        ? <img src={user.avatar_url} alt={`Ảnh đại diện của ${user.full_name}`} className="h-20 w-20 rounded-full object-cover" />
        : <div aria-hidden="true" className="flex h-20 w-20 items-center justify-center rounded-full bg-teal-50 text-teal-800"><UserRound className="h-9 w-9" /></div>}
      <div className="min-w-0 flex-1"><p className="font-semibold text-slate-900">{user.full_name}</p><p className="text-sm text-slate-600">{user.email} · {user.role}</p></div>
      <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50">
        <Camera className="h-4 w-4" aria-hidden="true" />Chọn ảnh
        <input type="file" accept="image/png,image/jpeg,image/webp" className="sr-only" onChange={(event) => {
          const selected = event.target.files?.[0]
          setAvatarError('')
          if (selected && selected.size > 2 * 1024 * 1024) {
            setAvatar(undefined)
            setAvatarError('Ảnh đại diện không được vượt quá 2 MB.')
            event.target.value = ''
          } else setAvatar(selected)
        }} />
      </label>
      <button type="button" disabled={!avatar || upload.isPending} onClick={() => avatar && upload.mutate(avatar)} className="rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">
        {upload.isPending ? 'Đang tải…' : 'Tải ảnh lên'}
      </button>
      <p className="basis-full text-xs text-slate-500">PNG, JPG hoặc WebP · tối đa 2 MB</p>
      {avatarError && <p role="alert" className="basis-full text-sm text-rose-700">{avatarError}</p>}
      {upload.isError && <p role="alert" className="basis-full text-sm text-rose-700">{upload.error.message}</p>}
      {upload.isSuccess && <p role="status" className="basis-full text-sm text-teal-800">Đã cập nhật ảnh đại diện.</p>}
    </div>
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <label className="grid gap-1 text-sm font-medium text-slate-700">Họ và tên
        <input name="fullName" required maxLength={255} defaultValue={user.full_name} className="rounded-lg border px-3 py-2" />
      </label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="grid gap-1 text-sm font-medium text-slate-700">Email
          <input value={user.email} readOnly className="rounded-lg border bg-slate-50 px-3 py-2 text-slate-500" />
        </label>
        <label className="grid gap-1 text-sm font-medium text-slate-700">Điện thoại
          <input name="phone" maxLength={50} defaultValue={user.phone ?? ''} className="rounded-lg border px-3 py-2" />
        </label>
        <label className="grid gap-1 text-sm font-medium text-slate-700">Đơn vị
          <input name="organization" maxLength={255} defaultValue={user.organization ?? ''} className="rounded-lg border px-3 py-2" />
        </label>
        <label className="grid gap-1 text-sm font-medium text-slate-700">Ngày sinh
          <input name="dateOfBirth" type="date" max={new Date().toISOString().slice(0, 10)} defaultValue={user.date_of_birth ?? ''} className="rounded-lg border px-3 py-2" />
        </label>
      </div>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Giới thiệu
        <textarea name="bio" maxLength={4000} rows={5} defaultValue={user.bio ?? ''} className="rounded-lg border px-3 py-2" />
      </label>
      {save.isError && <p role="alert" className="text-sm text-rose-700">{save.error.message}</p>}
      {save.isSuccess && <p role="status" className="text-sm text-teal-800">Đã lưu hồ sơ.</p>}
      <button disabled={save.isPending} className="rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{save.isPending ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
    </form>
  </section>
}
