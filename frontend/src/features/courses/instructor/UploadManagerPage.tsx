import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, FileUp, LoaderCircle, RotateCcw, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { courseApi, type Content, type ContentType, uploadFile } from './courseApi'

function contentTypeFor(file: File): ContentType {
  if (file.type.startsWith('video/')) return 'VIDEO'
  if (file.type === 'application/pdf') return 'PDF'
  if (file.type.includes('presentation')) return 'SLIDE'
  if (file.type.includes('word') || file.type.startsWith('text/')) return 'DOCUMENT'
  return 'FILE'
}

function videoDuration(file: File): Promise<number> {
  if (!file.type.startsWith('video/')) return Promise.resolve(0)
  return new Promise((resolve) => {
    const video = document.createElement('video')
    const objectUrl = URL.createObjectURL(file)
    video.preload = 'metadata'
    video.onloadedmetadata = () => { URL.revokeObjectURL(objectUrl); resolve(Math.max(1, Math.round(video.duration))) }
    video.onerror = () => { URL.revokeObjectURL(objectUrl); resolve(1) }
    video.src = objectUrl
  })
}

export default function UploadManagerPage() {
  const { courseId = '', lessonId = '' } = useParams()
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [activeContent, setActiveContent] = useState<Content | null>(null)
  const [progress, setProgress] = useState(0)
  const [message, setMessage] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const route = useQuery({ queryKey: ['upload-route', courseId, lessonId], queryFn: () => courseApi.modules(courseId).then(async (page) => {
    for (const module of page.items) {
      const lessons = await courseApi.lessons(courseId, module.id)
      const lesson = lessons.items.find((item) => item.id === lessonId)
      if (lesson) return { module, lesson }
    }
    throw new Error('Không tìm thấy bài học')
  }) })
  const moduleId = route.data?.module.id ?? ''
  const contentKey = ['lesson-contents', courseId, moduleId, lessonId]
  const contentQuery = useQuery({ queryKey: contentKey, queryFn: () => courseApi.contents(courseId, moduleId, lessonId), enabled: Boolean(moduleId) })
  const createContent = useMutation({ mutationFn: async (selectedFile: File) => {
    const type = contentTypeFor(selectedFile)
    const duration = await videoDuration(selectedFile)
    return courseApi.createContent(courseId, moduleId, lessonId, {
      content_type: type,
      title: selectedFile.name,
      file_name: selectedFile.name,
      mime_type: selectedFile.type || 'application/octet-stream',
      file_size: selectedFile.size,
      ...(type === 'VIDEO' ? { duration_seconds: duration } : {}),
    })
  } })

  const runUpload = async (content: Content, selectedFile: File) => {
    const controller = new AbortController()
    abortRef.current = controller
    setActiveContent(content)
    setIsUploading(true)
    setProgress(0)
    setMessage('Đang tải lên…')
    try {
      await uploadFile(content.id, selectedFile, setProgress, controller.signal)
      setMessage('Tải lên và xác minh thành công')
      await queryClient.invalidateQueries({ queryKey: contentKey })
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Tải lên thất bại. Bạn có thể thử lại.')
      await queryClient.invalidateQueries({ queryKey: contentKey })
    } finally {
      setIsUploading(false)
      abortRef.current = null
    }
  }

  const startUpload = async () => {
    if (!file) return
    setMessage('Đang tạo mục nội dung…')
    try {
      const content = await createContent.mutateAsync(file)
      setActiveContent(content)
      await queryClient.invalidateQueries({ queryKey: contentKey })
      await runUpload(content, file)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể bắt đầu tải lên')
    }
  }

  const cancelUpload = () => abortRef.current?.abort()
  const retry = () => { if (file && activeContent) void runUpload(activeContent, file) }

  if (route.isLoading) return <div aria-label="Đang tải upload" className="mx-auto h-80 max-w-4xl animate-pulse rounded-2xl bg-slate-100" />
  if (!route.data) return <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">Không tải được bài học.</p>

  return <main className="mx-auto max-w-4xl space-y-6">
    <header><Link to={`/instructor/courses/${courseId}/lessons/${lessonId}/content`} className="text-sm font-semibold text-indigo-700">← Soạn nội dung</Link><p className="mt-4 text-sm font-semibold text-indigo-600">{route.data.lesson.title}</p><h1 className="mt-1 text-3xl font-bold text-slate-950">Tải tài liệu lên</h1><p className="mt-2 text-slate-600">PDF và slide tối đa 50 MiB. Video tối đa 2 GiB, chia phần 10 MiB và tải song song tối đa 3 phần.</p></header>

    <section className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 md:p-7">
      <input ref={inputRef} type="file" accept="video/mp4,video/webm,video/quicktime,application/pdf,.ppt,.pptx,.doc,.docx,.txt,.csv" className="sr-only" aria-label="Chọn tệp tải lên" onChange={(event) => { setFile(event.target.files?.[0] ?? null); setActiveContent(null); setProgress(0); setMessage('') }} />
      <button onClick={() => inputRef.current?.click()} className="flex min-h-44 w-full flex-col items-center justify-center rounded-2xl border-2 border-dashed border-indigo-200 bg-indigo-50/50 p-5 text-center hover:border-indigo-400"><FileUp size={30} className="text-indigo-600" /><span className="mt-3 font-semibold text-slate-900">{file ? file.name : 'Chọn PDF, slide, tài liệu hoặc video'}</span><span className="mt-1 text-sm text-slate-500">{file ? `${(file.size / 1024 / 1024).toFixed(1)} MiB` : 'Tệp được tải trực tiếp lên object storage'}</span></button>
      {file && !isUploading && !activeContent && <button onClick={() => void startUpload()} disabled={createContent.isPending} className="rounded-xl bg-indigo-600 px-5 py-3 font-semibold text-white disabled:opacity-50">{createContent.isPending ? 'Đang chuẩn bị…' : 'Bắt đầu tải lên'}</button>}
      {activeContent && <div className="space-y-3" aria-label="Tiến trình tải lên"><div className="flex justify-between text-sm"><span className="font-medium">{activeContent.file_name}</span><span>{progress}%</span></div><div role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100} className="h-3 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-indigo-600 transition-all" style={{ width: `${progress}%` }} /></div><p role="status" className="text-sm text-slate-600">{message}</p><div className="flex gap-2">{isUploading ? <button onClick={cancelUpload} className="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold"><X size={15} />Hủy upload</button> : progress < 100 && file ? <button onClick={retry} className="inline-flex items-center gap-2 rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-700"><RotateCcw size={15} />Thử lại</button> : progress === 100 ? <span className="inline-flex items-center gap-2 text-sm font-semibold text-emerald-700"><CheckCircle2 size={17} />Sẵn sàng</span> : <LoaderCircle className="animate-spin text-indigo-600" />}</div></div>}
      {message && !activeContent && <p role="status" className="text-sm text-slate-700">{message}</p>}
    </section>

    <section className="rounded-2xl border border-slate-200 bg-white p-5"><h2 className="font-bold text-slate-900">Tệp của bài học</h2><ul className="mt-4 divide-y divide-slate-100">{(contentQuery.data?.items ?? []).filter((item) => item.file_name).map((item) => <li key={item.id} className="flex items-center justify-between gap-3 py-3"><div className="min-w-0"><p className="truncate font-medium">{item.file_name}</p><p className="text-xs text-slate-500">{item.upload_status} · {item.file_size ? `${(item.file_size / 1024 / 1024).toFixed(1)} MiB` : ''}</p></div>{item.upload_status === 'AVAILABLE' && <CheckCircle2 size={18} className="shrink-0 text-emerald-600" />}</li>)}{contentQuery.data?.items.filter((item) => item.file_name).length === 0 && <li className="py-4 text-sm text-slate-500">Chưa có tệp nào.</li>}</ul></section>
  </main>
}
