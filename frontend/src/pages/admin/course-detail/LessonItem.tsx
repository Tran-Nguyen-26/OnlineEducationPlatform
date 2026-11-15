import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ChevronDown,
  ChevronRight,
  File as FileIcon,
  FileText,
  Link2,
  Pencil,
  Plus,
  Presentation,
  Trash2,
  Type,
  Upload,
  Video,
} from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { ContentType, LessonContentResponse, LessonResponse, UploadUrlResponse } from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { Badge } from '../../../components/Badge'

const FILE_BASED_TYPES: ContentType[] = ['VIDEO', 'PDF', 'SLIDE', 'DOCUMENT', 'FILE']

const TYPE_LABEL: Record<ContentType, string> = {
  TEXT: 'Văn bản',
  LINK: 'Liên kết',
  VIDEO: 'Video',
  PDF: 'Tài liệu PDF',
  SLIDE: 'Slide',
  DOCUMENT: 'Tài liệu',
  FILE: 'File khác',
}

const TYPE_ICON: Record<ContentType, typeof Type> = {
  TEXT: Type,
  LINK: Link2,
  VIDEO: Video,
  PDF: FileText,
  SLIDE: Presentation,
  DOCUMENT: FileText,
  FILE: FileIcon,
}

export function LessonItem({ lesson }: { lesson: LessonResponse }) {
  const [expanded, setExpanded] = useState(false)
  const queryClient = useQueryClient()

  const contentsQuery = useQuery({
    queryKey: ['lessons', lesson.id, 'contents'],
    queryFn: () => apiClient.get<LessonContentResponse[]>(`/api/admin/lessons/${lesson.id}/contents`).then((r) => r.data),
    enabled: expanded,
  })

  const invalidateContents = () => queryClient.invalidateQueries({ queryKey: ['lessons', lesson.id, 'contents'] })

  const [uploadingIds, setUploadingIds] = useState<Set<string>>(new Set())
  const [uploadErrors, setUploadErrors] = useState<Record<string, string>>({})

  async function uploadFile(content: LessonContentResponse, file: File) {
    setUploadingIds((prev) => new Set(prev).add(content.id))
    setUploadErrors((prev) => ({ ...prev, [content.id]: '' }))
    try {
      const mimeType = file.type || 'application/octet-stream'
      const { data } = await apiClient.post<UploadUrlResponse>(`/api/admin/lesson-contents/${content.id}/upload-url`, {
        fileName: file.name,
        mimeType,
      })
      const putResponse = await fetch(data.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': mimeType },
        body: file,
      })
      if (!putResponse.ok) {
        throw new Error(`Tải file lên thất bại (HTTP ${putResponse.status})`)
      }
      await apiClient.post(`/api/admin/lesson-contents/${content.id}/complete-upload`, { fileSize: file.size })
      invalidateContents()
    } catch (err) {
      setUploadErrors((prev) => ({ ...prev, [content.id]: getApiErrorMessage(err) }))
    } finally {
      setUploadingIds((prev) => {
        const next = new Set(prev)
        next.delete(content.id)
        return next
      })
    }
  }

  // --- Add / edit content modal ---
  const [editing, setEditing] = useState<LessonContentResponse | 'new' | null>(null)
  const [contentType, setContentType] = useState<ContentType>('TEXT')
  const [title, setTitle] = useState('')
  const [textContent, setTextContent] = useState('')
  const [externalUrl, setExternalUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  function openCreate() {
    setEditing('new')
    setContentType('TEXT')
    setTitle('')
    setTextContent('')
    setExternalUrl('')
    setFile(null)
    setFormError(null)
  }

  function openEdit(content: LessonContentResponse) {
    setEditing(content)
    setContentType(content.contentType)
    setTitle(content.title ?? '')
    setTextContent(content.textContent ?? '')
    setExternalUrl(content.externalUrl ?? '')
    setFile(null)
    setFormError(null)
  }

  const nextPosition = contentsQuery.data?.length ?? 0

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post<LessonContentResponse>(`/api/admin/lessons/${lesson.id}/contents`, {
        contentType,
        title: title || null,
        position: nextPosition,
        textContent: contentType === 'TEXT' ? textContent : null,
        externalUrl: contentType === 'LINK' ? externalUrl : null,
      }),
    onSuccess: (res) => {
      invalidateContents()
      setEditing(null)
      if (FILE_BASED_TYPES.includes(contentType) && file) {
        uploadFile(res.data, file)
      }
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: (content: LessonContentResponse) =>
      apiClient.patch(`/api/admin/lesson-contents/${content.id}`, {
        title: title || null,
        position: content.position,
        textContent: content.contentType === 'TEXT' ? textContent : null,
        externalUrl: content.contentType === 'LINK' ? externalUrl : null,
      }),
    onSuccess: () => {
      invalidateContents()
      setEditing(null)
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (contentId: string) => apiClient.delete(`/api/admin/lesson-contents/${contentId}`),
    onSuccess: invalidateContents,
    onError: (err) => window.alert(getApiErrorMessage(err)),
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (editing === 'new') createMutation.mutate()
    else if (editing) updateMutation.mutate(editing)
  }

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <div className="border-t border-slate-200">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-slate-500 hover:bg-slate-100"
      >
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        Nội dung ({contentsQuery.data?.length ?? '...'})
      </button>

      {expanded && (
        <div className="space-y-1.5 px-3 pb-2">
          {contentsQuery.data?.map((content) => {
            const Icon = TYPE_ICON[content.contentType]
            const isFileBased = FILE_BASED_TYPES.includes(content.contentType)
            const isUploading = uploadingIds.has(content.id)
            const uploadError = uploadErrors[content.id]
            return (
              <div key={content.id} className="rounded border border-slate-200 bg-white px-3 py-2 text-sm">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-slate-700">
                    <Icon size={14} className="text-slate-400" />
                    <span className="font-medium">{content.title || `(${TYPE_LABEL[content.contentType]})`}</span>
                    <span className="text-xs text-slate-400">{TYPE_LABEL[content.contentType]}</span>
                    {isFileBased && <Badge status={content.uploadStatus} />}
                  </div>
                  <div className="flex items-center gap-1">
                    <button onClick={() => openEdit(content)} className="p-1 text-slate-400 hover:text-slate-700" aria-label="Sửa">
                      <Pencil size={13} />
                    </button>
                    <button
                      onClick={() => deleteMutation.mutate(content.id)}
                      className="p-1 text-slate-400 hover:text-red-600"
                      aria-label="Xóa"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                </div>

                {content.contentType === 'LINK' && content.externalUrl && (
                  <a href={content.externalUrl} target="_blank" rel="noreferrer" className="mt-1 block truncate text-xs text-blue-600 hover:underline">
                    {content.externalUrl}
                  </a>
                )}

                {isFileBased && content.uploadStatus === 'AVAILABLE' && (
                  <p className="mt-1 text-xs text-slate-400">
                    {content.fileName} · {content.fileSize ? `${Math.round(content.fileSize / 1024)} KB` : ''}
                  </p>
                )}

                {isFileBased && content.uploadStatus !== 'AVAILABLE' && (
                  <div className="mt-2 flex items-center gap-2">
                    <label className="inline-flex cursor-pointer items-center gap-1 rounded border border-dashed border-slate-300 px-2 py-1 text-xs text-slate-500 hover:border-slate-400">
                      <Upload size={12} />
                      {isUploading ? 'Đang tải lên...' : 'Chọn & tải file lên'}
                      <input
                        type="file"
                        className="hidden"
                        disabled={isUploading}
                        onChange={(e) => {
                          const f = e.target.files?.[0]
                          if (f) uploadFile(content, f)
                        }}
                      />
                    </label>
                    {uploadError && <span className="text-xs text-red-600">{uploadError}</span>}
                  </div>
                )}
              </div>
            )
          })}

          <Button size="sm" variant="secondary" onClick={openCreate}>
            <Plus size={14} /> Thêm nội dung
          </Button>
        </div>
      )}

      <Modal title={editing === 'new' ? 'Thêm nội dung' : 'Sửa nội dung'} open={editing !== null} onClose={() => setEditing(null)}>
        <form onSubmit={handleSubmit}>
          {editing === 'new' && (
            <>
              <label className="mb-1 block text-sm font-medium text-slate-700">Loại nội dung</label>
              <select
                value={contentType}
                onChange={(e) => setContentType(e.target.value as ContentType)}
                className="mb-4 h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
              >
                {Object.entries(TYPE_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </>
          )}

          <label className="mb-1 block text-sm font-medium text-slate-700">Tiêu đề (tùy chọn)</label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} className="mb-4" />

          {contentType === 'TEXT' && (
            <>
              <label className="mb-1 block text-sm font-medium text-slate-700">Nội dung văn bản</label>
              <textarea
                required
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
                rows={5}
                className="mb-4 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </>
          )}

          {contentType === 'LINK' && (
            <>
              <label className="mb-1 block text-sm font-medium text-slate-700">Đường dẫn (URL)</label>
              <Input
                required
                type="url"
                value={externalUrl}
                onChange={(e) => setExternalUrl(e.target.value)}
                placeholder="https://..."
                className="mb-4"
              />
            </>
          )}

          {editing === 'new' && FILE_BASED_TYPES.includes(contentType) && (
            <>
              <label className="mb-1 block text-sm font-medium text-slate-700">Chọn file ({TYPE_LABEL[contentType]})</label>
              <input
                ref={fileInputRef}
                type="file"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mb-4 block w-full text-sm"
              />
              <p className="mb-4 text-xs text-slate-400">Sau khi lưu, file sẽ được tải thẳng lên kho lưu trữ (có thể mất vài giây).</p>
            </>
          )}

          {formError && <p className="mb-4 text-sm text-red-600">{formError}</p>}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>
              Hủy
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
