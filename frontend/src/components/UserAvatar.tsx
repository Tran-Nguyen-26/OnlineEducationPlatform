/** Round avatar for any person: photo when available, otherwise their initial. */
export function UserAvatar({ url, name, size = 32 }: { url?: string | null; name?: string | null; size?: number }) {
  return url ? (
    <img src={url} alt="" style={{ width: size, height: size }} className="shrink-0 rounded-full object-cover" />
  ) : (
    <span style={{ width: size, height: size, fontSize: Math.max(11, size * 0.4) }} className="flex shrink-0 items-center justify-center rounded-full bg-slate-200 font-semibold text-slate-600">
      {(name ?? '?').trim().charAt(0).toUpperCase()}
    </span>
  )
}
