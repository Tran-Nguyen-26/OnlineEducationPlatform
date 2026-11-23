import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ChevronDown,
  ChevronRight,
  File as FileIcon,
  FileText,
  Link2,
  ListChecks,
  History,
  Pencil,
  Plus,
  Presentation,
  Trash2,
  Type,
  Upload,
  Video,
} from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type {
  ContentType,
  LessonCompletionRuleResponse,
  LessonContentResponse,
  LessonResponse,
  UploadUrlResponse,
} from '../../../types/api'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { ContentHistoryModal } from './ContentHistoryModal'
import { Badge } from '../../../components/Badge'

const FILE_BASED_TYPES: ContentType[] = ['VIDEO', 'PDF', 'SLIDE', 'DOCUMENT', 'FILE']
const PDF_MAX_BYTES = 20 * 1024 * 1024
const MARKDOWN_JSON_MAX_BYTES = 5 * 1024 * 1024

function aiUploadError(contentType: ContentType, file: File): string | null {
  const extension = file.name.toLowerCase().split('.').pop()
  if (contentType === 'PDF') {
    if (extension !== 'pdf') return 'Tài liệu PDF cần tệp .pdf.'
    if (file.size > PDF_MAX_BYTES) return 'Tài liệu PDF tối đa 20 MiB.'
  }
  if (contentType === 'FILE' && (extension === 'md' || extension === 'json') && file.size > MARKDOWN_JSON_MAX_BYTES) {
    return 'Tệp Markdown/JSON tối đa 5 MiB.'
  }
  return null
}

function uploadMimeType(contentType: ContentType, file: File): string {
  const extension = file.name.toLowerCase().split('.').pop()
  if (contentType === 'PDF' && extension === 'pdf') return 'application/pdf'
  if (contentType === 'FILE' && extension === 'md') return 'text/markdown'
  if (contentType === 'FILE' && extension === 'json') return 'application/json'
  return file.type || 'application/octet-stream'
}

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
  const [historyFor, setHistoryFor] = useState<LessonContentResponse | null>(null)

  async function uploadFile(content: LessonContentResponse, file: File) {
    const validationError = aiUploadError(content.contentType, file)
    if (validationError) {
      setUploadErrors((prev) => ({ ...prev, [content.id]: validationError }))
      return
    }
    setUploadingIds((prev) => new Set(prev).add(content.id))
    setUploadErrors((prev) => ({ ...prev, [content.id]: '' }))
    try {
      const mimeType = uploadMimeType(content.contentType, file)
      const { data } = await apiClient.post<UploadUrlResponse>(`/api/admin/lesson-contents/${content.id}/upload-url`, {
        fileName: file.name,
        mimeType,
        fileSize: file.size,
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
    if (editing === 'new' && file) {
      const validationError = aiUploadError(contentType, file)
      if (validationError) {
        setFormError(validationError)
        return
      }
    }
    if (editing === 'new') createMutation.mutate()
    else if (editing) updateMutation.mutate(editing)
  }

  const saving = createMutation.isPending || updateMutation.isPending

  // --- Completion rule modal ---
  const [ruleOpen, setRuleOpen] = useState(false)
  const [minimumTimeSeconds, setMinimumTimeSeconds] = useState(0)
  const [minVideoPercentage, setMinVideoPercentage] = useState(80)
  const [requireQuiz, setRequireQuiz] = useState(false)
  const [requireAssignment, setRequireAssignment] = useState(false)
  const [ruleError, setRuleError] = useState<string | null>(null)

  const ruleQuery = useQuery({
    queryKey: ['lessons', lesson.id, 'completion-rule'],
    queryFn: () => apiClient.get<LessonCompletionRuleResponse>(`/api/admin/lessons/${lesson.id}/completion-rule`).then((r) => r.data),
    enabled: ruleOpen,
  })

  useEffect(() => {
    if (ruleQuery.data) {
      setMinimumTimeSeconds(ruleQuery.data.minimumTimeSeconds)
      setMinVideoPercentage(ruleQuery.data.minVideoPercentage)
      setRequireQuiz(ruleQuery.data.requireQuiz)
      setRequireAssignment(ruleQuery.data.requireAssignment)
    }
  }, [ruleQuery.data])

  const saveRuleMutation = useMutation({
    mutationFn: () =>
      apiClient.put(`/api/admin/lessons/${lesson.id}/completion-rule`, {
        minimumTimeSeconds,
        minVideoPercentage,
        requireQuiz,
        requireAssignment,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lessons', lesson.id, 'completion-rule'] })
      setRuleOpen(false)
    },
    onError: (err) => setRuleError(getApiErrorMessage(err)),
  })

  const deleteRuleMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/admin/lessons/${lesson.id}/completion-rule`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lessons', lesson.id, 'completion-rule'] })
      setRuleOpen(false)
    },
    onError: (err) => setRuleError(getApiErrorMessage(err)),
  })

  return (
    <div className="border-t border-slate-200">
      <div className="flex items-center justify-between">
        <button
          onClick={() => setExpanded((v) => !v)}
          className="flex flex-1 items-center gap-2 px-3 py-2 text-left text-xs text-slate-500 hover:bg-slate-100"
        >
          {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
          Nội dung ({contentsQuery.data?.length ?? '...'})
        </button>
        <button
          onClick={() => {
            setRuleError(null)
            setRuleOpen(true)
          }}
          className="flex items-center gap-1 px-3 py-2 text-xs text-slate-400 hover:text-slate-700"
        >
          <ListChecks size={12} /> Điều kiện hoàn thành
        </button>
      </div>

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
                    {content.contentType === 'VIDEO' && content.transcodeStatus && (
                      <span
                        title="Chuyển mã sang HLS để phát thích ứng theo đường truyền"
                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${content.transcodeStatus === 'READY' ? 'bg-emerald-100 text-emerald-700' : content.transcodeStatus === 'FAILED' ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'}`}
                      >
                        {{ PENDING: 'HLS: chờ', PROCESSING: 'HLS: đang chuyển mã', READY: 'HLS sẵn sàng', FAILED: 'HLS lỗi' }[content.transcodeStatus]}
                      </span>
                    )}
                    {content.contentType === 'VIDEO' && content.transcodeStatus === 'FAILED' && (
                      <button
                        className="text-[11px] text-blue-600 hover:underline"
                        onClick={() => apiClient.post(`/api/admin/lesson-contents/${content.id}/transcode`).then(() => invalidateContents())}
                      >
                        Thử lại
                      </button>
                    )}
                  </div>
                  <div className="flex items-center gap-1">
                    <button onClick={() => setHistoryFor(content)} className="p-1 text-slate-400 hover:text-slate-700" aria-label="Lịch sử" title="Lịch sử chỉnh sửa">
                      <History size={13} />
                    </button>
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
              <label className="mb-1 block text-sm font-medium text-slate-700">Nội dung văn bản (hỗ trợ Markdown)</label>
              <textarea
                required
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
                rows={12}
                className="mb-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
              />
              <p className="mb-4 text-xs text-slate-400">
                Dùng <code>## Tiêu đề</code> (tạo mục lục "Trên trang này"), <code>**đậm**</code>, <code>- danh sách</code>, <code>```code```</code>, bảng <code>| a | b |</code>,
                và hộp chú ý <code>&gt; [!NOTE]</code> / <code>[!TIP]</code> / <code>[!WARNING]</code>.
              </p>
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

      <Modal title="Điều kiện hoàn thành bài học" open={ruleOpen} onClose={() => setRuleOpen(false)}>
        {ruleQuery.isLoading ? (
          <p className="text-sm text-slate-400">Đang tải...</p>
        ) : (
          <div>
            <p className="mb-4 text-xs text-slate-500">
              Học sinh chỉ có thể đánh dấu bài học này "hoàn thành" khi đáp ứng đủ các điều kiện dưới đây. Để trống/0 nghĩa
              là không yêu cầu.
            </p>

            <label className="mb-1 block text-sm font-medium text-slate-700">Thời gian học tối thiểu (giây)</label>
            <Input
              type="number"
              min={0}
              value={minimumTimeSeconds}
              onChange={(e) => setMinimumTimeSeconds(Number(e.target.value))}
              className="mb-4"
            />

            <label className="mb-1 block text-sm font-medium text-slate-700">
              % video tối thiểu đã xem <span className="text-xs text-slate-400">(chưa áp dụng được — xem ghi chú bên dưới)</span>
            </label>
            <Input
              type="number"
              min={0}
              max={100}
              value={minVideoPercentage}
              onChange={(e) => setMinVideoPercentage(Number(e.target.value))}
              className="mb-4"
              disabled
            />

            <label className="mb-3 flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={requireQuiz} onChange={(e) => setRequireQuiz(e.target.checked)} />
              Bắt buộc đã làm quiz gắn với bài học này
            </label>
            <label className="mb-4 flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={requireAssignment} onChange={(e) => setRequireAssignment(e.target.checked)} />
              Bắt buộc đã nộp bài tập gắn với bài học này
            </label>

            <p className="mb-4 text-xs text-slate-400">
              Ghi chú: % video chưa enforce được vì trình xem nội dung hiện mở file ở tab mới, chưa có player theo dõi
              tiến độ xem — xem chi tiết trong GAPS.md.
            </p>

            {ruleError && <p className="mb-4 text-sm text-red-600">{ruleError}</p>}

            <div className="flex justify-between gap-2">
              {ruleQuery.data?.configured ? (
                <Button type="button" variant="danger" disabled={deleteRuleMutation.isPending} onClick={() => deleteRuleMutation.mutate()}>
                  Xóa điều kiện
                </Button>
              ) : (
                <span />
              )}
              <div className="flex gap-2">
                <Button type="button" variant="secondary" onClick={() => setRuleOpen(false)}>
                  Hủy
                </Button>
                <Button type="button" disabled={saveRuleMutation.isPending} onClick={() => saveRuleMutation.mutate()}>
                  {saveRuleMutation.isPending ? 'Đang lưu...' : 'Lưu'}
                </Button>
              </div>
            </div>
          </div>
        )}
      </Modal>
      {historyFor && <ContentHistoryModal contentId={historyFor.id} title={historyFor.title || 'Nội dung'} onClose={() => setHistoryFor(null)} />}
    </div>
  )
}
