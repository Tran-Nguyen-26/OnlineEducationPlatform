import { useEffect, useState, type FormEvent } from 'react'
import { fetchTeachingCourses, request, type TeachingCourse } from '@/api/quiz'

interface QueueItem {
  id: string
  code: string
  title: string
  appeal: boolean
}

interface Claim {
  item: QueueItem
  token: string
  expiresAt: string
  text: string
  rubric: { code: string; description: string; maxScore: number }[] | null
  appealCriterion: string | null
  appealReason: string | null
}

export default function BlindGradingPage() {
  const [courses, setCourses] = useState<TeachingCourse[]>([])
  const [course, setCourse] = useState('')
  const [page, setPage] = useState(0)
  const [items, setItems] = useState<QueueItem[]>([])
  const [claim, setClaim] = useState<Claim | null>(null)
  const [scores, setScores] = useState<Record<string, string>>({})
  const [feedback, setFeedback] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const url = '/api/v1/courses/' + course + '/grading'

  useEffect(() => {
    fetchTeachingCourses()
      .then((rows) => {
        setCourses(rows)
        setCourse(rows[0]?.id ?? '')
      })
      .catch((error: Error) => setMessage(error.message))
  }, [])

  useEffect(() => {
    if (!course) return
    const controller = new AbortController()
    request<QueueItem[]>(url + '?page=' + page, { signal: controller.signal })
      .then(setItems)
      .catch((error: Error) => {
        if (!controller.signal.aborted) setMessage(error.message)
      })
    return () => controller.abort()
  }, [course, page, refresh, url])

  const claimId = claim?.item.id
  const token = claim?.token
  useEffect(() => {
    if (!claimId || !token) return
    let active = true
    const timer = window.setInterval(
      () => {
        request<Claim>(url + '/' + claimId + '/renew', {
          method: 'POST',
          body: JSON.stringify({ token }),
        })
          .then((next) => {
            if (active) setClaim(next)
          })
          .catch((error: Error) => {
            if (active) setMessage(error.message)
          })
      },
      5 * 60 * 1000
    )
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [claimId, token, url])

  async function take(item: QueueItem) {
    setBusy(true)
    setMessage('')
    try {
      setClaim(await request<Claim>(url + '/' + item.id + '/claim', { method: 'POST' }))
      setScores({})
      setFeedback('')
    } catch (error) {
      setMessage((error as Error).message)
      setRefresh((value) => value + 1)
    } finally {
      setBusy(false)
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!claim) return
    setBusy(true)
    setMessage('')
    try {
      const values = Object.fromEntries(
        Object.entries(scores).map(([code, value]) => [code, Number(value)])
      )
      await request<void>(url + '/' + claim.item.id + '/grade', {
        method: 'POST',
        body: JSON.stringify({ token: claim.token, scores: values, feedback }),
      })
      setClaim(null)
      setRefresh((value) => value + 1)
      setMessage('Đã lưu điểm.')
    } catch (error) {
      setMessage((error as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="space-y-4 rounded-xl bg-white p-6 text-slate-900 shadow-sm">
      <h1 className="text-2xl font-semibold">Hàng đợi chấm ẩn danh</h1>
      <label className="block">
        Khóa học
        <select
          className="ml-2 rounded border p-2"
          value={course}
          disabled={busy}
          onChange={(event) => {
            setCourse(event.target.value)
            setClaim(null)
            setItems([])
            setPage(0)
            setMessage('')
          }}
        >
          {courses.map((row) => (
            <option key={row.id} value={row.id}>
              {row.title}
            </option>
          ))}
        </select>
      </label>
      <p role="status">{message}</p>
      {!claim && (
        <>
          {items.length === 0 && <p>Không có bài trong hàng đợi.</p>}
          <ul className="divide-y">
            {items.map((item) => (
              <li className="flex flex-wrap items-center justify-between gap-2 py-3" key={item.id}>
                <span>
                  {item.title} · {item.code}
                  {item.appeal && ' · Phúc khảo'}
                </span>
                <button
                  className="rounded border px-3 py-2"
                  disabled={busy}
                  onClick={() => void take(item)}
                >
                  Nhận chấm
                </button>
              </li>
            ))}
          </ul>
          <div className="flex gap-3">
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>
              Trang trước
            </button>
            <button disabled={items.length < 50} onClick={() => setPage(page + 1)}>
              Trang sau
            </button>
            <button onClick={() => setRefresh((value) => value + 1)}>Tải lại</button>
          </div>
        </>
      )}
      {claim && (
        <form className="space-y-4" onSubmit={submit}>
          <h2 className="font-semibold">
            {claim.item.title} · {claim.item.code}
          </h2>
          <p>Giữ bài đến {new Date(claim.expiresAt).toLocaleString('vi-VN')}</p>
          {claim.appealCriterion && (
            <p>
              Phúc khảo {claim.appealCriterion} · {claim.appealReason}
            </p>
          )}
          <p className="rounded bg-slate-50 p-4 whitespace-pre-wrap">{claim.text}</p>
          {claim.rubric?.map((criterion) => (
            <label className="flex items-center gap-3" key={criterion.code}>
              {criterion.description} (tối đa {criterion.maxScore})
              <input
                className="w-24 rounded border p-2"
                type="number"
                step="0.01"
                min="0"
                max={criterion.maxScore}
                required
                value={scores[criterion.code] ?? ''}
                onChange={(event) => setScores({ ...scores, [criterion.code]: event.target.value })}
              />
            </label>
          ))}
          {!claim.rubric?.length && <p>Bài tập chưa có rubric.</p>}
          <label className="block">
            Nhận xét
            <textarea
              className="block w-full rounded border p-2"
              value={feedback}
              maxLength={10000}
              onChange={(event) => setFeedback(event.target.value)}
            />
          </label>
          <div className="flex gap-3">
            <button
              type="submit"
              className="rounded bg-indigo-700 px-4 py-2 text-white"
              disabled={busy || !claim.rubric?.length}
            >
              Lưu điểm
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setClaim(null)
                setRefresh((value) => value + 1)
              }}
            >
              Về hàng đợi
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
