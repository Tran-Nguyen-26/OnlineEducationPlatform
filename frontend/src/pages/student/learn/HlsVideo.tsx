import { useEffect, useRef } from 'react'
import Hls from 'hls.js'
import { API_BASE_URL } from '../../../lib/apiClient'
import { tokenStore } from '../../../lib/tokenStore'

/**
 * Adaptive-bitrate player (HLS). Playlists come from the API (they need the user's token); the video segments are
 * pre-signed object-storage URLs, so the Authorization header must NOT be sent to them (it would break the signature).
 * Falls back to the plain file when HLS is unavailable.
 */
export function HlsVideo({ hlsPath, fallbackUrl, onPercent }: { hlsPath: string; fallbackUrl: string; onPercent: (percent: number) => void }) {
  const ref = useRef<HTMLVideoElement>(null)
  // Seconds actually played (not skipped): seeking to the end must not count as having watched the video.
  const watched = useRef<Set<number>>(new Set())
  const lastTime = useRef(0)

  useEffect(() => {
    const video = ref.current
    if (!video) return
    watched.current = new Set()
    lastTime.current = 0
    const masterUrl = `${API_BASE_URL}${hlsPath}`
    if (Hls.isSupported()) {
      const hls = new Hls({
        xhrSetup: (xhr, url) => {
          if (url.startsWith(`${API_BASE_URL}/api/`)) {
            const token = tokenStore.getAccessToken()
            if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
          }
        },
      })
      hls.on(Hls.Events.ERROR, (_e, data) => {
        if (data.fatal) {
          hls.destroy()
          video.src = fallbackUrl // e.g. expired playlist or storage hiccup: play the original file instead
        }
      })
      hls.loadSource(masterUrl)
      hls.attachMedia(video)
      return () => hls.destroy()
    }
    video.src = fallbackUrl
  }, [hlsPath, fallbackUrl])

  return (
    <video
      ref={ref}
      controls
      className="w-full rounded-lg border border-slate-200 bg-black"
      onTimeUpdate={(e) => {
        const v = e.currentTarget
        if (!(v.duration > 0)) return
        const t = v.currentTime
        // Natural playback advances by well under 2s between events; larger jumps are seeks and are not counted.
        if (!v.seeking && t >= lastTime.current && t - lastTime.current < 2) {
          for (let s = Math.floor(lastTime.current); s <= Math.floor(t); s++) watched.current.add(s)
        }
        lastTime.current = t
        onPercent(Math.min(100, (watched.current.size / Math.ceil(v.duration)) * 100))
      }}
    />
  )
}
