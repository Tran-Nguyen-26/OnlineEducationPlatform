import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type Hls from 'hls.js'
import LessonDiscussion from './LessonDiscussion'
import AiTutorPanel from './AiTutorPanel'
import { useSearchParams } from 'react-router-dom'

type Video = { enrollmentId: string; lessonId: string; title: string; videoId: string }
type Session = { streamToken: string; watermark: string; archived: boolean }
type Cue = { start: number; label: string; text: string }
type Lesson = { id: string; title: string; unlocked: boolean; video: boolean; documents: boolean; miniQuiz: boolean; fastTrack: boolean }
type DocumentItem = { id: string; filename: string }

export function parseTranscript(text: string): Cue[] {
  return text.replace(/\r/g, '').split(/\n\s*\n/).flatMap((block) => {
    const lines = block.split('\n')
    const index = lines.findIndex((line) => line.includes('-->'))
    if (index < 0 || /^NOTE(?:\s|$)/.test(lines[0])) return []
    const label = lines[index].split('-->')[0].trim()
    if (!/^(?:\d{2,}:)?[0-5]\d:[0-5]\d[.,]\d{3}$/.test(label)) return []
    const start = label.replace(',', '.').split(':').reduce((seconds, part) => seconds * 60 + Number(part), 0)
    const content = lines.slice(index + 1).join(' ').replace(/<[^>]+>/g, '').trim()
    return content ? [{ start, label: label.split(/[.,]/)[0], text: content }] : []
  })
}

function headers() {
  return { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
}

async function readList<T>(path: string, signal: AbortSignal): Promise<T[]> {
  const response = await fetch(path, { headers: headers(), signal })
  if (response.status === 404) return []
  if (!response.ok) throw new Error('Không tải được dữ liệu bài học.')
  return await response.json() as T[]
}

function LessonDocuments({ base, lessonId, onOpened }: { base: string; lessonId: string; onOpened: () => void }) {
  const path = `${base}/lessons/${lessonId}/documents`
  const [error, setError] = useState('')
  const [opening, setOpening] = useState(false)
  const documents = useQuery({
    queryKey: ['lesson-documents', base, lessonId],
    queryFn: ({ signal }) => readList<DocumentItem>(path, signal),
  })
  async function openDocument(id: string) {
    const tab = window.open('about:blank', '_blank')
    if (!tab) {
      setError('Hãy cho phép mở cửa sổ mới để xem tài liệu.')
      return
    }
    tab.opener = null
    setOpening(true)
    setError('')
    try {
      const response = await fetch(`${path}/${id}`, { method: 'POST', headers: headers() })
      if (!response.ok) throw new Error()
      const { url } = await response.json() as { url: string }
      tab.location.replace(url)
      onOpened()
    } catch {
      tab.close()
      setError('Không thể mở tài liệu.')
    } finally {
      setOpening(false)
    }
  }
  return (
    <>
      {documents.data?.map((document) => (
        <button key={document.id} type="button" disabled={opening} onClick={() => void openDocument(document.id)}
          className="mr-2 rounded border px-2 py-1 text-sm text-teal-800">{document.filename}</button>
      ))}
      {(error || documents.isError) && <p role="alert" className="text-sm text-red-700">{error || 'Không tải được tài liệu.'}</p>}
    </>
  )
}

export default function LessonPlayer({ courseId }: { courseId: string }) {
  const base = `/api/v1/courses/${courseId}/playback`
  const cache = useQueryClient()
  const [search] = useSearchParams()
  const [discussion, setDiscussion] = useState<string | undefined>(() => search.get('discussion') ?? undefined)
  const [questionContext, setQuestionContext] = useState<{ videoId: string; timestampSec: number }>()
  const [aiContext, setAiContext] = useState<{ lessonId: string; videoId: string; timestampSec: number }>()
  const seekAfterLoad = useRef<number | undefined>(undefined)
  const autoOpenedVideo = useRef<string | undefined>(undefined)
  const [documentsFor, setDocumentsFor] = useState<string>()
  const [selected, setSelected] = useState<Video>()
  const [session, setSession] = useState<Session>()
  const [message, setMessage] = useState('')
  const [opening, setOpening] = useState(false)
  const [corner, setCorner] = useState(0)
  const [cues, setCues] = useState<Cue[]>([])
  const element = useRef<HTMLVideoElement>(null)
  const engine = useRef<typeof Hls>(null)
  const videos = useQuery({
    queryKey: ['course-playback', courseId],
    queryFn: ({ signal }) => readList<Video>(base, signal),
  })
  const quota = useQuery({
    queryKey: ['archive-video-quota', courseId],
    queryFn: async ({ signal }) => {
      const response = await fetch(`${base}/archive-quota`, { headers: headers(), signal })
      if (response.status === 204 || response.status === 404) return null
      if (!response.ok) throw new Error('Không tải được hạn mức video.')
      return await response.json() as { remainingSeconds: number; resetsOn: string }
    },
    refetchInterval: session?.archived ? 30000 : false,
  })
  const lessons = useQuery({
    queryKey: ['course-mastery', courseId],
    queryFn: ({ signal }) => readList<Lesson>(`${base}/lessons`, signal),
  })
  const discussionLessons = useQuery({
    queryKey: ['discussion-lessons', courseId],
    queryFn: ({ signal }) => readList<{ id: string; title: string }>(`/api/v1/courses/${courseId}/discussions`, signal),
  })

  const open = useCallback(async (video: Video, at?: number) => {
    seekAfterLoad.current = at
    setOpening(true)
    setMessage('')
    setCues([])
    setSession(undefined)
    setSelected(video)
    try {
      const { default: HlsEngine } = await import('hls.js')
      if (!HlsEngine.isSupported()) throw new Error('Trình duyệt này chưa hỗ trợ trình phát riêng tư.')
      engine.current = HlsEngine
      const response = await fetch(`${base}/${video.videoId}`, { method: 'POST', headers: headers() })
      if (!response.ok) {
        const error = await response.json().catch(() => ({}))
        throw new Error(error.message ?? 'Không mở được phiên phát video.')
      }
      setSession(await response.json() as Session)
    } catch (error) {
      void cache.invalidateQueries({ queryKey: ['archive-video-quota', courseId] })
      setMessage(error instanceof Error ? error.message : 'Không mở được phiên phát video.')
    } finally {
      setOpening(false)
    }
  }, [base, cache, courseId])

  const autoVideoId = search.get('video')
  const autoTime = Math.max(0, Number(search.get('time') ?? 0))
  useEffect(() => {
    if (!autoVideoId || !videos.data || selected?.videoId === autoVideoId || autoOpenedVideo.current === autoVideoId) return
    const video = videos.data.find((item) => item.videoId === autoVideoId)
    if (!video) return
    autoOpenedVideo.current = autoVideoId
    const timer = window.setTimeout(() => void open(video, autoTime), 0)
    return () => window.clearTimeout(timer)
  }, [autoTime, autoVideoId, open, selected, videos.data])

  useEffect(() => {
    const player = element.current
    const HlsEngine = engine.current
    if (!player || !selected || !session || !HlsEngine) return
    const abort = new AbortController()
    let sequence = 0
    let lastTick = performance.now()
    let lastPosition = player.currentTime || 0
    let watched = 0
    let sending = false
    const sessionHeaders = { ...headers(), 'X-Playback-Session': session.streamToken }
    const onSeeking = () => {
      const target = Math.floor(player.currentTime || 0)
      const previous = Math.floor(lastPosition)
      if (previous - target >= 10) {
        void fetch(`${base}/${selected.videoId}/rewinds`, {
          method: 'POST', signal: abort.signal,
          headers: { ...sessionHeaders, 'Content-Type': 'application/json' },
          body: JSON.stringify({ fromSec: previous, toSec: target }),
        }).catch(() => {})
      }
      lastPosition = target
    }
    const onTimeUpdate = () => { if (!player.seeking) lastPosition = player.currentTime || 0 }
    player.addEventListener('seeking', onSeeking)
    player.addEventListener('timeupdate', onTimeUpdate)
    void fetch(`${base}/${selected.videoId}/transcript`, { headers: sessionHeaders, signal: abort.signal })
      .then((response) => response.ok ? response.text() : '')
      .then((text) => { if (!abort.signal.aborted) setCues(parseTranscript(text)) })
      .catch(() => {})
    const hls = new HlsEngine({
      xhrSetup: (request, url) => {
        if (new URL(url, window.location.href).origin !== window.location.origin) {
          throw new Error('Invalid playback origin')
        }
        for (const [key, value] of Object.entries(sessionHeaders)) request.setRequestHeader(key, value)
      },
    })
    function stop(reason: string) {
      if (abort.signal.aborted) return
      abort.abort()
      player.removeEventListener('seeking', onSeeking)
      player.removeEventListener('timeupdate', onTimeUpdate)
      player?.pause()
      setMessage(reason)
      setSession(undefined)
    }
    hls.on(HlsEngine.Events.ERROR, (_event, error) => {
      if (error.response?.code === 409) stop('Phiên phát đã hết hạn hoặc được mở trên thiết bị khác.')
      else if (error.response?.code === 403 && session.archived) {
        void cache.invalidateQueries({ queryKey: ['archive-video-quota', courseId] })
        stop('Không thể tải thêm video lưu trữ. Kiểm tra hạn mức còn lại.')
      }
      else if (error.fatal) stop('Không phát được video. Hãy mở lại phiên phát.')
    })
    hls.loadSource(`${base}/${selected.videoId}/master.m3u8`)
    hls.attachMedia(player)
    const sample = window.setInterval(() => {
      const now = performance.now()
      if (!player.paused && !player.seeking && player.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) {
        watched += Math.min(1000, now - lastTick) / 1000
      }
      lastTick = now
    }, 500)
    const heartbeat = window.setInterval(async () => {
      if (sending || abort.signal.aborted) return
      sending = true
      const delta = Math.min(30, Math.floor(watched))
      watched -= delta
      try {
        const response = await fetch('/api/progress/heartbeat', {
          method: 'POST', signal: abort.signal,
          headers: { ...headers(), 'Content-Type': 'application/json' },
          body: JSON.stringify({
            enrollment_id: selected.enrollmentId, lesson_id: selected.lessonId,
            stream_token: session.streamToken, sequence: ++sequence,
            current_playback_sec: Math.floor(player.currentTime || 0), watched_delta_sec: delta,
          }),
        })
        if ([401, 403, 409].includes(response.status)) stop('Phiên phát đã hết hạn hoặc được mở trên thiết bị khác.')
        else if (!response.ok) stop('Không đồng bộ được tiến độ. Hãy mở lại phiên phát.')
        else if (delta > 0) {
          void cache.invalidateQueries({ queryKey: ['course-mastery', courseId] })
          void cache.invalidateQueries({ queryKey: ['course-playback', courseId] })
        }
      } catch {
        if (!abort.signal.aborted) stop('Mất kết nối với phiên phát. Hãy thử lại.')
      } finally {
        sending = false
      }
    }, 30000)
    const watermark = window.setInterval(() => setCorner((position) => (position + 1) % 4), 12000)
    return () => {
      abort.abort()
      window.clearInterval(sample)
      window.clearInterval(heartbeat)
      window.clearInterval(watermark)
      hls.destroy()
    }
  }, [base, cache, courseId, selected, session])

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="font-semibold">Lộ trình bài học</h2>
      {quota.data && <p role="status" className="rounded bg-slate-50 p-3 text-sm text-slate-700">
        Xem lưu trữ · Còn {Math.floor(quota.data.remainingSeconds / 60)} phút {quota.data.remainingSeconds % 60} giây video.
        Hạn mức được đặt lại ngày {quota.data.resetsOn.split('-').reverse().join('/')}.
      </p>}
      {(videos.isError || lessons.isError) && <p role="alert">Không tải được lộ trình bài học.</p>}
      {lessons.isSuccess && !lessons.data.length && <p className="text-sm text-slate-600">Chưa có bài học mở.</p>}
      <ol className="space-y-2">
        {lessons.data?.map((lesson) => (
          <li key={lesson.id} className="space-y-2 rounded-lg border p-3 text-sm">
            <div className="flex flex-wrap justify-between gap-2">
              <strong>{lesson.title}</strong>
              <span>{lesson.fastTrack ? 'Fast-track' : lesson.unlocked ? 'Đã mở' : 'Đang khóa'}</span>
            </div>
            <p className="text-slate-600">
              {lesson.video ? '✓' : '○'} Video · {lesson.documents ? '✓' : '○'} Tài liệu · {lesson.miniQuiz ? '✓' : '○'} Mini-quiz
            </p>
            {lesson.unlocked && (
              <div className="flex flex-wrap gap-3">
              <button type="button" aria-expanded={documentsFor === lesson.id}
                className="text-teal-800 underline" onClick={() => setDocumentsFor(documentsFor === lesson.id ? undefined : lesson.id)}>
                Tài liệu đính kèm
              </button>
              <button type="button" className="text-teal-800 underline" onClick={() => {
                setDiscussion(lesson.id); setQuestionContext(undefined)
              }}>Thảo luận</button>
              </div>
            )}
            {documentsFor === lesson.id && lesson.unlocked && (
              <LessonDocuments key={lesson.id} base={base} lessonId={lesson.id} onOpened={() => {
                void cache.invalidateQueries({ queryKey: ['course-mastery', courseId] })
                void cache.invalidateQueries({ queryKey: ['course-playback', courseId] })
              }} />
            )}
          </li>
        ))}
      </ol>
      {videos.data?.map((video) => (
        <button key={video.videoId} disabled={opening || quota.data?.remainingSeconds === 0} onClick={() => void open(video)}
          className="mr-2 rounded-lg border px-3 py-2 text-sm text-teal-800 disabled:opacity-50">
          Mở video · {video.title}
        </button>
      ))}
      {message && <p role="alert" className="text-sm text-red-700">{message}</p>}
      {session && (
        <div className="relative overflow-hidden rounded-lg bg-slate-950">
          <video ref={element} className="aspect-video w-full" aria-label={selected?.title}
            controls playsInline disablePictureInPicture onLoadedMetadata={(event) => {
              if (seekAfterLoad.current !== undefined) event.currentTarget.currentTime = seekAfterLoad.current
              seekAfterLoad.current = undefined
            }} />
          <div aria-hidden="true" className={`pointer-events-none absolute max-w-[85%] break-all rounded bg-black/40 px-2 py-1 text-xs text-white ${
            ['left-4 top-4', 'right-4 top-4', 'right-4 bottom-14', 'left-4 bottom-14'][corner]
          }`}>{session.watermark}</div>
        </div>
      )}
      {session && selected && !session.archived && (
        <div className="flex flex-wrap gap-2">
          <button type="button" className="rounded border px-3 py-2 text-sm text-teal-800" onClick={() => {
            setDiscussion(selected.lessonId)
            setQuestionContext({ videoId: selected.videoId, timestampSec: Math.floor(element.current?.currentTime ?? 0) })
          }}>Đặt câu hỏi thảo luận tại mốc</button>
          <button type="button" className="rounded border border-teal-700 px-3 py-2 text-sm font-medium text-teal-800" onClick={() => {
            setAiContext({ lessonId: selected.lessonId, videoId: selected.videoId, timestampSec: Math.floor(element.current?.currentTime ?? 0) })
          }}>Hỏi AI tại mốc đang xem</button>
        </div>
      )}
      {aiContext && <AiTutorPanel key={`${aiContext.lessonId}-${aiContext.timestampSec}`} courseId={courseId}
        lessonId={aiContext.lessonId} timestampSeconds={aiContext.timestampSec}
        onSeek={(citationVideoId, seconds) => {
          const targetVideoId = citationVideoId || aiContext.videoId
          if (targetVideoId === selected?.videoId && element.current && session) element.current.currentTime = seconds
          else {
            const video = videos.data?.find((item) => item.videoId === targetVideoId)
            if (video) void open(video, seconds)
          }
        }} />}
      {!!discussionLessons.data?.length && (
        <label className="grid gap-1 text-sm">Chọn bài để xem thảo luận
          <select className="rounded border p-2" value={discussion ?? ''} onChange={(event) => {
            setDiscussion(event.target.value || undefined); setQuestionContext(undefined)
          }}>
            <option value="">Chọn bài học</option>
            {discussionLessons.data.map((lesson) => <option key={lesson.id} value={lesson.id}>{lesson.title}</option>)}
          </select>
        </label>
      )}
      {discussion && <LessonDiscussion key={discussion} courseId={courseId} lessonId={discussion}
        context={selected?.lessonId === discussion ? questionContext : undefined}
        onSeek={videos.data?.length ? (videoId, seconds) => {
          if (videoId === selected?.videoId && element.current && session) element.current.currentTime = seconds
          else {
            const video = videos.data.find((item) => item.videoId === videoId)
            if (video) void open(video, seconds)
          }
        } : undefined} />}
      {session && cues.length > 0 && (
        <details className="rounded-lg border p-3">
          <summary className="cursor-pointer font-medium">Phụ đề và mốc thời gian</summary>
          <ol className="mt-2 max-h-64 space-y-1 overflow-auto">
            {cues.map((cue, index) => (
              <li key={index}>
                <button type="button" className="flex w-full gap-3 rounded p-2 text-left text-sm hover:bg-teal-50"
                  onClick={() => { if (element.current) element.current.currentTime = cue.start }}>
                  <span className="font-mono text-teal-800">{cue.label}</span>
                  <span>{cue.text}</span>
                </button>
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  )
}
