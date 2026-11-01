export interface Page<T> {
  items: T[]
  pagination: { page: number; page_size: number; total: number; has_next: boolean }
}

export interface Course {
  id: string
  title: string
  description: string | null
  visibility: 'PUBLIC' | 'PRIVATE'
  lifecycle_status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'DISABLED'
  learning_mode: 'FREE_NAVIGATION' | 'SEQUENTIAL'
  created_at: string
  updated_at: string
}

export interface Module {
  id: string
  course_id: string
  title: string
  description: string | null
  position: number
  is_default: boolean
}

export interface Lesson {
  id: string
  module_id: string
  title: string
  description: string | null
  position: number
  is_required: boolean
}

export type ContentType = 'TEXT' | 'LINK' | 'VIDEO' | 'PDF' | 'SLIDE' | 'DOCUMENT' | 'FILE'
export type UploadStatus = 'PENDING' | 'UPLOADED' | 'AVAILABLE' | 'FAILED'

export interface Content {
  id: string
  lesson_id: string
  content_type: ContentType
  title: string | null
  text_content: string | null
  external_url: string | null
  file_name: string | null
  mime_type: string | null
  file_size: number | null
  duration_seconds: number | null
  upload_status: UploadStatus
  position: number
}

export class ApiError extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('access_token')
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`/api/v1${path}`, { ...init, headers })
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const message = body?.error?.message ?? body?.message ?? `Yêu cầu thất bại (${response.status})`
    throw new ApiError(message, response.status)
  }
  return body as T
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
})

export const courseApi = {
  list: () => api<Page<Course>>('/my/courses?page=1&page_size=100'),
  create: (payload: { title: string; description: string; visibility: 'PUBLIC' | 'PRIVATE'; learning_mode: 'FREE_NAVIGATION' | 'SEQUENTIAL' }) =>
    api<Course>('/courses', json('POST', payload)),
  update: (courseId: string, payload: { title: string; description: string; visibility: 'PUBLIC' | 'PRIVATE' }) =>
    api<Course>(`/courses/${courseId}`, json('PATCH', payload)),
  modules: (courseId: string) => api<Page<Module>>(`/courses/${courseId}/modules?page=1&page_size=100`),
  createModule: (courseId: string, title: string) =>
    api<Module>(`/courses/${courseId}/modules`, json('POST', { title })),
  updateModule: (courseId: string, moduleId: string, title: string) =>
    api<Module>(`/courses/${courseId}/modules/${moduleId}`, json('PATCH', { title })),
  reorderModules: (courseId: string, ids: string[]) =>
    api(`/courses/${courseId}/module-order`, json('PATCH', { ordered_ids: ids })),
  lessons: (courseId: string, moduleId: string) =>
    api<Page<Lesson>>(`/courses/${courseId}/modules/${moduleId}/lessons?page=1&page_size=100`),
  createLesson: (courseId: string, moduleId: string, title: string) =>
    api<Lesson>(`/courses/${courseId}/modules/${moduleId}/lessons`, json('POST', { title })),
  updateLesson: (courseId: string, moduleId: string, lessonId: string, title: string) =>
    api<Lesson>(`/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}`, json('PATCH', { title })),
  reorderLessons: (courseId: string, moduleId: string, ids: string[]) =>
    api(`/courses/${courseId}/modules/${moduleId}/lesson-order`, json('PATCH', { ordered_ids: ids })),
  contents: (courseId: string, moduleId: string, lessonId: string) =>
    api<Page<Content>>(`/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}/contents?page=1&page_size=100`),
  createContent: (courseId: string, moduleId: string, lessonId: string, body: Record<string, unknown>) =>
    api<Content>(`/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}/contents`, json('POST', body)),
  updateContent: (courseId: string, moduleId: string, lessonId: string, contentId: string, body: Record<string, unknown>) =>
    api<Content>(`/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}/contents/${contentId}`, json('PATCH', body)),
  reorderContents: (courseId: string, moduleId: string, lessonId: string, ids: string[]) =>
    api(`/courses/${courseId}/modules/${moduleId}/lessons/${lessonId}/content-order`, json('PATCH', { ordered_ids: ids })),
  publish: (courseId: string) => api(`/courses/${courseId}/publish`, json('POST')),
}

export interface MultipartUploadResponse {
  upload_id: string
  part_size_bytes: number
  parts: { part_number: number; upload_url: string }[]
  expires_at: string
}

function putFileWithProgress(
  url: string,
  file: File,
  headers: Record<string, string>,
  onProgress: (percentage: number) => void,
  signal: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    const abort = () => request.abort()
    request.open('PUT', url)
    Object.entries(headers).forEach(([name, value]) => request.setRequestHeader(name, value))
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.min(90, Math.round((event.loaded / event.total) * 90)))
    }
    request.onload = () => {
      signal.removeEventListener('abort', abort)
      if (request.status >= 200 && request.status < 300) resolve()
      else reject(new Error('Tải tệp lên kho lưu trữ thất bại'))
    }
    request.onerror = () => {
      signal.removeEventListener('abort', abort)
      reject(new Error('Mất kết nối khi tải tệp lên'))
    }
    request.onabort = () => {
      signal.removeEventListener('abort', abort)
      reject(new DOMException('Upload cancelled', 'AbortError'))
    }
    if (signal.aborted) {
      reject(new DOMException('Upload cancelled', 'AbortError'))
      return
    }
    signal.addEventListener('abort', abort, { once: true })
    request.send(file)
  })
}

export async function uploadFile(
  contentId: string,
  file: File,
  onProgress: (percentage: number) => void,
  signal: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('access_token')
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined
  const isMultipart = file.type.startsWith('video/') && file.size > 50 * 1024 * 1024

  if (!isMultipart) {
    const signed = await api<{ upload_url: string; required_headers: Record<string, string> }>(
      `/contents/${contentId}/upload-url`,
      { method: 'POST' },
    )
    try {
      await putFileWithProgress(signed.upload_url, file, signed.required_headers, onProgress, signal)
      await api(`/contents/${contentId}/complete-upload`, { method: 'POST' })
      onProgress(100)
    } catch (error) {
      await api(`/contents/${contentId}/fail-upload`, { method: 'POST' }).catch(() => undefined)
      throw error
    }
    return
  }

  const multipart = await api<MultipartUploadResponse>(`/contents/${contentId}/multipart-upload`, {
    method: 'POST',
    headers,
  })
  const completed: { part_number: number; e_tag: string }[] = []
  let nextPart = 0
  let uploadedBytes = 0
  const workers = Array.from({ length: Math.min(3, multipart.parts.length) }, async () => {
    while (nextPart < multipart.parts.length) {
      if (signal.aborted) throw new DOMException('Upload cancelled', 'AbortError')
      const part = multipart.parts[nextPart++]
      const start = (part.part_number - 1) * multipart.part_size_bytes
      const chunk = file.slice(start, Math.min(start + multipart.part_size_bytes, file.size))
      let result: Response | undefined
      for (let attempt = 0; attempt < 3; attempt++) {
        result = await fetch(part.upload_url, { method: 'PUT', body: chunk, signal })
        if (result.ok) break
      }
      if (!result?.ok) throw new Error(`Không tải được phần ${part.part_number}`)
      const eTag = result.headers.get('ETag')
      if (!eTag) throw new Error('Kho lưu trữ không trả ETag cho phần tải lên')
      completed.push({ part_number: part.part_number, e_tag: eTag })
      uploadedBytes += chunk.size
      onProgress(Math.min(90, Math.round((uploadedBytes / file.size) * 90)))
    }
  })

  try {
    await Promise.all(workers)
    await api(`/contents/${contentId}/complete-multipart-upload`, {
      method: 'POST',
      body: JSON.stringify({ parts: completed }),
    })
    onProgress(100)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      await api(`/contents/${contentId}/multipart-upload`, { method: 'DELETE' }).catch(() => undefined)
    } else {
      await api(`/contents/${contentId}/fail-upload`, { method: 'POST' }).catch(() => undefined)
    }
    throw error
  }
}
