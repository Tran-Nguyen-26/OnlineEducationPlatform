import { useQuery } from '@tanstack/react-query'
import { ExternalLink, FileText, Link2 } from 'lucide-react'
import { apiClient } from '../../../lib/apiClient'
import { Markdown } from '../../../lib/markdown'
import type { OutlineContent, ViewUrlResponse } from '../../../types/api'
import { YouTubePlayer } from './YouTubePlayer'
import { HlsVideo } from './HlsVideo'

function youtubeId(url: string): string | null {
  const m = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]{11})/)
  return m ? m[1] : null
}

function useViewUrl(contentId: string, enabled: boolean) {
  return useQuery({
    enabled,
    queryKey: ['view-url', contentId],
    staleTime: 10 * 60 * 1000,
    queryFn: () => apiClient.get<ViewUrlResponse>(`/api/lesson-contents/${contentId}/view-url`).then((r) => r.data),
  })
}

/** Renders one lesson block inline (like a docs page): markdown text, embedded video/PDF viewer, link cards, downloads. */
export function LessonContent({ content, idPrefix, onVideoPercent }: { content: OutlineContent; idPrefix: string; onVideoPercent: (percent: number) => void }) {
  const hosted = content.contentType !== 'TEXT' && content.contentType !== 'LINK'
  const ready = hosted && content.uploadStatus === 'AVAILABLE'
  const view = useViewUrl(content.id, ready)

  if (content.contentType === 'TEXT') {
    return (
      <section>
        {content.title && <h2 className="mt-8 mb-2 text-xl font-semibold text-slate-900">{content.title}</h2>}
        <Markdown text={content.textContent ?? ''} idPrefix={idPrefix} />
      </section>
    )
  }

  if (content.contentType === 'LINK' && content.externalUrl) {
    const ytId = youtubeId(content.externalUrl)
    return (
      <section className="my-6">
        {content.title && <h3 className="mb-2 font-semibold text-slate-900">{content.title}</h3>}
        {ytId ? (
          <YouTubePlayer videoId={ytId} onPercent={onVideoPercent} />
        ) : (
          <a href={content.externalUrl} target="_blank" rel="noreferrer noopener" className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-400">
            <Link2 size={18} className="text-slate-400" />
            <span className="flex-1 truncate text-sm text-blue-600">{content.externalUrl}</span>
            <ExternalLink size={14} className="text-slate-400" />
          </a>
        )}
      </section>
    )
  }

  if (!ready) {
    return <p className="my-6 rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-400">{content.title ?? 'Tài liệu'} — nội dung đang được chuẩn bị...</p>
  }
  const url = view.data?.viewUrl
  const hlsPath = view.data?.hlsUrl ?? null
  const isVideo = content.contentType === 'VIDEO' || content.mimeType?.startsWith('video/')
  const isPdf = content.contentType === 'PDF' || content.mimeType === 'application/pdf'

  return (
    <section className="my-6">
      {content.title && <h3 className="mb-2 font-semibold text-slate-900">{content.title}</h3>}
      {view.isLoading && <p className="text-sm text-slate-400">Đang tải...</p>}
      {url && isVideo && hlsPath && <HlsVideo hlsPath={hlsPath} fallbackUrl={url} onPercent={onVideoPercent} />}
      {url && isVideo && !hlsPath && (
        <video
          controls
          src={url}
          className="w-full rounded-lg border border-slate-200 bg-black"
          onTimeUpdate={(e) => { const v = e.currentTarget; if (v.duration > 0) onVideoPercent((v.currentTime / v.duration) * 100) }}
          onEnded={() => onVideoPercent(100)}
        />
      )}
      {url && isPdf && <iframe src={url} title={content.title ?? 'PDF'} className="h-[70vh] w-full rounded-lg border border-slate-200" />}
      {url && !isVideo && !isPdf && (
        <a href={url} target="_blank" rel="noreferrer noopener" className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-400">
          <FileText size={18} className="text-slate-400" />
          <span className="flex-1 text-sm text-slate-800">{content.fileName ?? 'Tải tài liệu'}</span>
          <ExternalLink size={14} className="text-slate-400" />
        </a>
      )}
      {url && (isVideo || isPdf) && (
        <a href={url} target="_blank" rel="noreferrer noopener" className="mt-1 inline-flex items-center gap-1 text-xs text-slate-500 hover:underline">
          <ExternalLink size={12} /> Mở trong tab mới{content.fileName ? ` (${content.fileName})` : ''}
        </a>
      )}
    </section>
  )
}
