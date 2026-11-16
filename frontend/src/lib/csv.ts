/** Client-side CSV download; UTF-8 BOM so Excel shows Vietnamese correctly. */
export function downloadCsv(filename: string, rows: (string | number | null | undefined)[][]) {
  const escape = (v: string | number | null | undefined) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const body = rows.map((r) => r.map(escape).join(',')).join('\n')
  const blob = new Blob(['﻿' + body], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export const vnd = (n: number | null | undefined) => (n == null ? '—' : n.toLocaleString('vi-VN') + ' đ')
