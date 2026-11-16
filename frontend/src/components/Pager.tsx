import { useEffect, useState } from 'react'
import { Button } from './ui/Button'

/** Client-side paging for lists that the API returns whole; page resets when the list shrinks (e.g. after filtering). */
export function usePaged<T>(items: T[], size = 15) {
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(items.length / size))
  useEffect(() => { if (page >= pages) setPage(pages - 1) }, [page, pages])
  return { page, setPage, pages, total: items.length, slice: items.slice(page * size, page * size + size) }
}

export function Pager({ page, pages, total, setPage, label = 'mục' }: { page: number; pages: number; total: number; setPage: (p: number) => void; label?: string }) {
  if (total === 0 || pages <= 1) return null
  return (
    <div className="mt-3 flex items-center justify-between text-sm text-slate-500">
      <span>{total} {label} · trang {page + 1}/{pages}</span>
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</Button>
        <Button size="sm" variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Sau</Button>
      </div>
    </div>
  )
}
