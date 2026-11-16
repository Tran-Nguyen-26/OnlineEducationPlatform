import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { StickyNote } from 'lucide-react'
import { apiClient } from '../../../lib/apiClient'

/** Private per-lesson notes with debounced autosave. */
export function NotesPanel({ lessonId }: { lessonId: string }) {
  const note = useQuery({
    queryKey: ['student', 'note', lessonId],
    queryFn: () => apiClient.get<{ content: string }>(`/api/lessons/${lessonId}/note`).then((r) => r.data),
  })
  const [text, setText] = useState('')
  const [state, setState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const loaded = useRef<string | null>(null)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    if (note.data && loaded.current !== lessonId) {
      loaded.current = lessonId
      setText(note.data.content)
      setState('idle')
    }
  }, [note.data, lessonId])

  useEffect(() => () => window.clearTimeout(timer.current), [lessonId])

  function change(value: string) {
    setText(value)
    setState('saving')
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => {
      apiClient.put(`/api/lessons/${lessonId}/note`, { content: value }).then(() => setState('saved')).catch(() => setState('error'))
    }, 800)
  }

  return (
    <section className="mt-12 border-t border-slate-200 pt-6">
      <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-slate-800">
        <StickyNote size={16} /> Ghi chú của tôi
        <span className="text-xs font-normal text-slate-400">
          {state === 'saving' ? 'Đang lưu...' : state === 'saved' ? 'Đã lưu' : state === 'error' ? 'Lưu thất bại' : 'Chỉ mình bạn xem được'}
        </span>
      </h2>
      <textarea
        value={text}
        onChange={(e) => change(e.target.value)}
        disabled={note.isLoading}
        rows={5}
        placeholder="Ghi lại ý chính, câu hỏi cần hỏi giáo viên..."
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      />
    </section>
  )
}
