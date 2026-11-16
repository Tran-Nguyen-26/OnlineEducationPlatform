import { vnd } from './csv'

export const ENTRY_LABEL: Record<string, string> = { PAYMENT: 'Thu học phí', DISCOUNT: 'Miễn giảm', REFUND: 'Hoàn tiền' }

export interface ReceiptData {
  receiptNo: string
  type: string
  amount: number
  paidAt: string
  studentName: string
  studentEmail?: string
  courseTitle: string
  note?: string | null
  recordedBy: string
}

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!)

/** Opens a print-ready receipt (biên lai) in a new window; the browser's print dialog can save it as PDF. */
export function printReceipt(r: ReceiptData) {
  const w = window.open('', '_blank', 'width=640,height=760')
  if (!w) return
  const title = r.type === 'REFUND' ? 'PHIẾU HOÀN TIỀN' : r.type === 'DISCOUNT' ? 'PHIẾU MIỄN GIẢM HỌC PHÍ' : 'BIÊN LAI THU HỌC PHÍ'
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(r.receiptNo)}</title>
<style>body{font-family:Arial,sans-serif;padding:40px;color:#111}h1{text-align:center;font-size:20px;margin-bottom:4px}
.c{text-align:center;color:#555;margin-bottom:24px}table{width:100%;border-collapse:collapse}td{padding:8px 0;border-bottom:1px dashed #ccc}
td:first-child{color:#555;width:38%}.amt{font-size:22px;font-weight:bold}.sig{display:flex;justify-content:space-between;margin-top:60px;text-align:center}</style></head><body>
<h1>${title}</h1><div class="c">Số: ${esc(r.receiptNo)}</div>
<table>
<tr><td>Học sinh</td><td>${esc(r.studentName)}${r.studentEmail ? ` (${esc(r.studentEmail)})` : ''}</td></tr>
<tr><td>Khóa học</td><td>${esc(r.courseTitle)}</td></tr>
<tr><td>Ngày</td><td>${new Date(r.paidAt).toLocaleString('vi-VN')}</td></tr>
<tr><td>Nội dung</td><td>${esc(ENTRY_LABEL[r.type] ?? r.type)}${r.note ? ` — ${esc(r.note)}` : ''}</td></tr>
<tr><td>Số tiền</td><td class="amt">${esc(vnd(r.amount))}</td></tr>
<tr><td>Người lập</td><td>${esc(r.recordedBy)}</td></tr>
</table>
<div class="sig"><div>Người nộp / nhận<br><br><br></div><div>Người lập phiếu<br><br><br></div></div>
<script>window.onload=()=>window.print()</script></body></html>`)
  w.document.close()
}
