import { afterEach, describe, expect, it, vi } from 'vitest'
import { uploadFile } from './courseApi'

afterEach(() => vi.unstubAllGlobals())

describe('multipart upload client', () => {
  it('uploads a 500 MiB video using 50 parts with no more than three concurrent requests', async () => {
    const parts = Array.from({ length: 50 }, (_, index) => ({
      part_number: index + 1,
      upload_url: `https://storage.test/part/${index + 1}`,
    }))
    let activeRequests = 0
    let maximumConcurrentRequests = 0
    let uploadedParts = 0
    let completionBody: { parts: { part_number: number; e_tag: string }[] } | undefined
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/contents/content-1/multipart-upload') {
        return new Response(JSON.stringify({ upload_id: 'upload-1', part_size_bytes: 10 * 1024 * 1024, parts, expires_at: '' }), { status: 200 })
      }
      if (url.startsWith('https://storage.test/part/')) {
        activeRequests++
        maximumConcurrentRequests = Math.max(maximumConcurrentRequests, activeRequests)
        await new Promise((resolve) => setTimeout(resolve, 1))
        activeRequests--
        uploadedParts++
        return new Response(null, { status: 200, headers: { ETag: `"${url.split('/').at(-1)}"` } })
      }
      if (url === '/api/v1/contents/content-1/complete-multipart-upload') {
        completionBody = JSON.parse(String(init?.body)) as { parts: { part_number: number; e_tag: string }[] }
        return new Response('{}', { status: 200 })
      }
      throw new Error(`Unexpected request ${url}`)
    }))

    const video = new File([new Uint8Array([1])], 'lecture.mp4', { type: 'video/mp4' })
    Object.defineProperty(video, 'size', { value: 500 * 1024 * 1024 })
    const progress: number[] = []
    await uploadFile('content-1', video, (value) => progress.push(value), new AbortController().signal)

    expect(uploadedParts).toBe(50)
    expect(maximumConcurrentRequests).toBe(3)
    expect(completionBody?.parts).toHaveLength(50)
    expect(progress.at(-1)).toBe(100)
  })

  it('marks a failed single PUT so the instructor can retry it', async () => {
    const request = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/v1/contents/content-2/upload-url') {
        return new Response(JSON.stringify({ upload_url: 'https://storage.test/put', required_headers: { 'Content-Type': 'application/pdf' } }), { status: 200 })
      }
      if (url === 'https://storage.test/put') return new Response(null, { status: 500 })
      if (url === '/api/v1/contents/content-2/fail-upload') return new Response(null, { status: 200 })
      throw new Error(`Unexpected request ${url}`)
    })
    vi.stubGlobal('fetch', request)
    vi.stubGlobal('XMLHttpRequest', class {
      status = 500
      upload = { onprogress: null as ((event: ProgressEvent) => void) | null }
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      onabort: (() => void) | null = null
      open() {}
      setRequestHeader() {}
      send() { this.onload?.() }
      abort() { this.onabort?.() }
    })

    await expect(uploadFile('content-2', new File(['pdf'], 'lesson.pdf', { type: 'application/pdf' }), () => undefined, new AbortController().signal))
      .rejects.toThrow('Tải tệp lên kho lưu trữ thất bại')

    expect(request).toHaveBeenCalledWith('/api/v1/contents/content-2/fail-upload', expect.objectContaining({ method: 'POST' }))
  })
})
