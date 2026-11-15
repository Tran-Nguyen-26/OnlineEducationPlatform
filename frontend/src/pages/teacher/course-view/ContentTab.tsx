import { useState } from 'react'
import {
  ChevronDown,
  ChevronRight,
  File as FileIcon,
  FileText,
  Link2,
  Loader2,
  Presentation,
  Type,
  Video,
} from 'lucide-react'
import { apiClient } from '../../../lib/apiClient'
import type { ContentItem, ContentLesson, ContentType, CourseContentResponse, ViewUrlResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'

const TYPE_ICON: Record<ContentType, typeof Type> = {
  TEXT: Type,
  LINK: Link2,
  VIDEO: Video,
  PDF: FileText,
  SLIDE: Presentation,
  DOCUMENT: FileText,
  FILE: FileIcon,
}

function ContentView({ content }: { content: ContentItem }) {
  const [opening, setOpening] = useState(false)
  const Icon = TYPE_ICON[content.contentType]

  async function openFile() {
    setOpening(true)
    try {
      const { data } = await apiClient.get<ViewUrlResponse>(`/api/lesson-contents/${content.id}/view-url`)
      window.open(data.viewUrl, '_blank', 'noopener')
    } finally {
      setOpening(false)
    }
  }

  return (
    <div className="rounded-md border border-slate-200 bg-white p-4">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-800">
        <Icon size={16} className="text-slate-400" />
        {content.title || 'Nội dung'}
      </div>
      {content.contentType === 'TEXT' && <p className="whitespace-pre-wrap text-sm text-slate-600">{content.textContent}</p>}
      {content.contentType === 'LINK' && content.externalUrl && (
        <a href={content.externalUrl} target="_blank" rel="noreferrer" className="text-sm text-blue-600 hover:underline">
          {content.externalUrl}
        </a>
      )}
      {content.contentType !== 'TEXT' && content.contentType !== 'LINK' && (
        content.uploadStatus === 'AVAILABLE' ? (
          <Button size="sm" variant="secondary" disabled={opening} onClick={openFile}>
            {opening ? <Loader2 size={14} className="animate-spin" /> : null}
            Xem / Tải xuống {content.fileName ? `(${content.fileName})` : ''}
          </Button>
        ) : (
          <p className="text-xs text-slate-400">Nội dung đang được chuẩn bị...</p>
        )
      )}
    </div>
  )
}

function LessonPanel({ lesson }: { lesson: ContentLesson }) {
  return (
    <div>
      <h2 className="mb-1 text-lg font-semibold text-slate-900">{lesson.title}</h2>
      {lesson.description && <p className="mb-4 text-sm text-slate-500">{lesson.description}</p>}
      <div className="space-y-3">
        {lesson.contents.map((content) => (
          <ContentView key={content.id} content={content} />
        ))}
        {lesson.contents.length === 0 && <p className="text-sm text-slate-400">Chưa có nội dung.</p>}
      </div>
    </div>
  )
}

export function ContentTab({ content }: { content: CourseContentResponse }) {
  const allLessons = content.modules.flatMap((m) => m.lessons)
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(allLessons[0]?.id ?? null)
  const [expandedModules, setExpandedModules] = useState<Set<string>>(new Set(content.modules.map((m) => m.id)))

  function toggleModule(id: string) {
    setExpandedModules((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const selectedLesson = allLessons.find((l) => l.id === selectedLessonId) ?? null

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-[280px_1fr]">
      <div className="space-y-2">
        {content.modules.map((module) => (
          <div key={module.id} className="rounded-lg border border-slate-200 bg-white">
            <button
              onClick={() => toggleModule(module.id)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-slate-800"
            >
              {expandedModules.has(module.id) ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
              {module.title}
            </button>
            {expandedModules.has(module.id) && (
              <div className="border-t border-slate-100 py-1">
                {module.lessons.map((lesson) => (
                  <button
                    key={lesson.id}
                    onClick={() => setSelectedLessonId(lesson.id)}
                    className={`flex w-full items-center px-3 py-2 text-left text-sm ${
                      lesson.id === selectedLessonId ? 'bg-slate-100 font-medium text-slate-900' : 'text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    {lesson.title}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
        {content.modules.length === 0 && <p className="text-sm text-slate-400">Khóa học chưa có nội dung.</p>}
      </div>

      <div>{selectedLesson ? <LessonPanel lesson={selectedLesson} /> : <p className="text-slate-400">Chọn một bài học.</p>}</div>
    </div>
  )
}
