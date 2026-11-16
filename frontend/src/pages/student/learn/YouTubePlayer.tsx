import { useEffect, useRef } from 'react'

/* eslint-disable @typescript-eslint/no-explicit-any */
let apiPromise: Promise<any> | null = null
function loadYouTubeApi(): Promise<any> {
  const w = window as any
  if (w.YT?.Player) return Promise.resolve(w.YT)
  if (!apiPromise) {
    apiPromise = new Promise((resolve) => {
      const prev = w.onYouTubeIframeAPIReady
      w.onYouTubeIframeAPIReady = () => { prev?.(); resolve(w.YT) }
      const tag = document.createElement('script')
      tag.src = 'https://www.youtube.com/iframe_api'
      document.head.appendChild(tag)
    })
  }
  return apiPromise
}

/** Embedded YouTube video using the IFrame API so watched-percentage can be polled and reported. */
export function YouTubePlayer({ videoId, onPercent }: { videoId: string; onPercent: (percent: number) => void }) {
  const host = useRef<HTMLDivElement>(null)
  const cb = useRef(onPercent)
  cb.current = onPercent

  useEffect(() => {
    let player: any
    let timer: number | undefined
    let cancelled = false
    loadYouTubeApi().then((YT) => {
      if (cancelled || !host.current) return
      const el = document.createElement('div')
      host.current.appendChild(el)
      player = new YT.Player(el, {
        videoId,
        width: '100%',
        height: '100%',
        playerVars: { rel: 0, modestbranding: 1 },
        events: {
          onStateChange: (e: any) => {
            // 1 = playing, 0 = ended
            if (e.data === 1 && timer === undefined) {
              timer = window.setInterval(() => {
                const d = player.getDuration?.()
                if (d > 0) cb.current((player.getCurrentTime() / d) * 100)
              }, 3000)
            }
            if (e.data === 0) cb.current(100)
            if ((e.data === 2 || e.data === 0) && timer !== undefined) { window.clearInterval(timer); timer = undefined }
          },
        },
      })
    })
    return () => {
      cancelled = true
      if (timer !== undefined) window.clearInterval(timer)
      try { player?.destroy?.() } catch { /* already gone */ }
    }
  }, [videoId])

  return <div ref={host} className="aspect-video overflow-hidden rounded-lg border border-slate-200 bg-black [&_iframe]:h-full [&_iframe]:w-full" />
}
