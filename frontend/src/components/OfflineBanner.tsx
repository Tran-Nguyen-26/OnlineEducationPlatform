import { useEffect, useState } from 'react'

/** Tells the user when the browser loses connectivity (requests are retried automatically once it is back). */
export function OfflineBanner() {
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])
  if (online) return null
  return (
    <div role="alert" className="fixed inset-x-0 top-0 z-[100] bg-amber-500 px-4 py-2 text-center text-sm font-semibold text-white">
      Mất kết nối mạng. Thao tác sẽ được thử lại khi có mạng.
    </div>
  )
}
