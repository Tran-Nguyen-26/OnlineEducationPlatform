import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'
import type { ProfileResponse } from '../types/api'

/** Round avatar: the user's uploaded photo, falling back to their initial. Shares the ['profile'] query with the profile page. */
export function Avatar({ size = 36, name }: { size?: number; name?: string | null }) {
  const profile = useQuery({
    queryKey: ['profile'],
    staleTime: 5 * 60 * 1000,
    queryFn: () => apiClient.get<ProfileResponse>('/api/users/me/profile').then((r) => r.data),
  })
  const url = profile.data?.avatarUrl
  const initial = (profile.data?.fullName ?? name ?? '?').trim().charAt(0).toUpperCase()
  return url ? (
    <img src={url} alt="" style={{ width: size, height: size }} className="shrink-0 rounded-full object-cover" />
  ) : (
    <span style={{ width: size, height: size }} className="flex shrink-0 items-center justify-center rounded-full bg-slate-900 text-sm font-semibold text-white">{initial}</span>
  )
}
