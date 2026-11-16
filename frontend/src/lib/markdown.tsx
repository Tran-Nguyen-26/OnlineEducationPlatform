import { useState, type ReactNode } from 'react'
import { AlertTriangle, Check, Copy, Info, Lightbulb } from 'lucide-react'

/** Small dependency-free Markdown renderer (React elements only — no innerHTML, so lesson text cannot inject HTML/JS). */

export interface Heading { id: string; level: number; text: string }

type Block =
  | { t: 'h'; level: number; text: string; id: string }
  | { t: 'p'; text: string }
  | { t: 'ul' | 'ol'; items: string[] }
  | { t: 'code'; lang: string; code: string }
  | { t: 'quote'; kind: 'NOTE' | 'TIP' | 'WARNING' | null; lines: string[] }
  | { t: 'hr' }
  | { t: 'img'; alt: string; src: string }
  | { t: 'table'; head: string[]; rows: string[][] }

const slug = (s: string) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

function safeUrl(u: string) {
  return /^(https?:\/\/|mailto:|\/|#)/i.test(u.trim()) ? u.trim() : '#'
}

function parse(text: string, idPrefix: string): Block[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const out: Block[] = []
  let i = 0
  let hCount = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }
    const fence = line.match(/^```\s*(\w*)/)
    if (fence) {
      const buf: string[] = []
      i++
      while (i < lines.length && !lines[i].startsWith('```')) buf.push(lines[i++])
      i++
      out.push({ t: 'code', lang: fence[1], code: buf.join('\n') })
      continue
    }
    const h = line.match(/^(#{1,4})\s+(.*)$/)
    if (h) { out.push({ t: 'h', level: h[1].length, text: h[2].trim(), id: `${idPrefix}-${slug(h[2]) || 'h'}-${hCount++}` }); i++; continue }
    if (/^(-{3,}|\*{3,})\s*$/.test(line)) { out.push({ t: 'hr' }); i++; continue }
    const img = line.match(/^!\[([^\]]*)\]\(([^)]+)\)\s*$/)
    if (img) { out.push({ t: 'img', alt: img[1], src: img[2] }); i++; continue }
    if (line.trim().startsWith('>')) {
      const buf: string[] = []
      while (i < lines.length && lines[i].trim().startsWith('>')) buf.push(lines[i++].replace(/^\s*>\s?/, ''))
      const m = buf[0]?.match(/^\[!(NOTE|TIP|WARNING)\]\s*$/i)
      out.push({ t: 'quote', kind: m ? (m[1].toUpperCase() as 'NOTE' | 'TIP' | 'WARNING') : null, lines: m ? buf.slice(1) : buf })
      continue
    }
    if (line.trim().startsWith('|') && lines[i + 1]?.match(/^\s*\|?\s*:?-{2,}/)) {
      const cells = (l: string) => l.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
      const head = cells(line)
      i += 2
      const rows: string[][] = []
      while (i < lines.length && lines[i].trim().startsWith('|')) rows.push(cells(lines[i++]))
      out.push({ t: 'table', head, rows })
      continue
    }
    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*[-*+]\s+/, ''))
      out.push({ t: 'ul', items }); continue
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*\d+[.)]\s+/, ''))
      out.push({ t: 'ol', items }); continue
    }
    const buf: string[] = []
    while (i < lines.length && lines[i].trim() && !/^(```|#{1,4}\s|>|\s*[-*+]\s|\s*\d+[.)]\s|!\[)/.test(lines[i]) && !lines[i].trim().startsWith('|')) buf.push(lines[i++])
    if (buf.length === 0) { buf.push(lines[i++]) }
    out.push({ t: 'p', text: buf.join(' ') })
  }
  return out
}

export function extractHeadings(text: string, idPrefix: string): Heading[] {
  return parse(text, idPrefix).filter((b): b is Extract<Block, { t: 'h' }> => b.t === 'h' && b.level >= 2 && b.level <= 3)
    .map((b) => ({ id: b.id, level: b.level, text: b.text }))
}

function inline(text: string): ReactNode[] {
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\s][^*]*\*)|(\[[^\]]+\]\([^)]+\))/g
  const out: ReactNode[] = []
  let last = 0
  let m: RegExpExecArray | null
  let k = 0
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index))
    const tok = m[0]
    if (m[1]) out.push(<code key={k++} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[0.85em] text-slate-800">{tok.slice(1, -1)}</code>)
    else if (m[2]) out.push(<strong key={k++}>{inline(tok.slice(2, -2))}</strong>)
    else if (m[3]) out.push(<em key={k++}>{inline(tok.slice(1, -1))}</em>)
    else {
      const lm = tok.match(/^\[([^\]]+)\]\(([^)]+)\)$/)!
      out.push(<a key={k++} href={safeUrl(lm[2])} target="_blank" rel="noreferrer noopener" className="text-blue-600 underline underline-offset-2 hover:text-blue-800">{lm[1]}</a>)
    }
    last = m.index + tok.length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}

function CodeBlock({ lang, code }: { lang: string; code: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <div className="my-5 overflow-hidden rounded-lg border border-slate-200 bg-slate-900">
      <div className="flex items-center justify-between border-b border-slate-700 px-4 py-1.5 text-xs text-slate-400">
        <span>{lang || 'code'}</span>
        <button
          className="flex items-center gap-1 hover:text-white"
          onClick={() => { navigator.clipboard?.writeText(code).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) }).catch(() => {}) }}
        >
          {copied ? <Check size={12} /> : <Copy size={12} />} {copied ? 'Đã chép' : 'Sao chép'}
        </button>
      </div>
      <pre className="overflow-x-auto p-4 text-sm leading-relaxed text-slate-100"><code>{code}</code></pre>
    </div>
  )
}

const CALLOUT = {
  NOTE: { icon: Info, cls: 'border-blue-300 bg-blue-50 text-blue-900', label: 'Lưu ý' },
  TIP: { icon: Lightbulb, cls: 'border-emerald-300 bg-emerald-50 text-emerald-900', label: 'Mẹo' },
  WARNING: { icon: AlertTriangle, cls: 'border-amber-300 bg-amber-50 text-amber-900', label: 'Cảnh báo' },
}

export function Markdown({ text, idPrefix }: { text: string; idPrefix: string }) {
  return (
    <div className="text-[15px] leading-7 text-slate-700">
      {parse(text, idPrefix).map((b, idx) => {
        switch (b.t) {
          case 'h': {
            const cls = b.level === 1 ? 'mt-8 mb-3 text-2xl font-bold text-slate-900' : b.level === 2 ? 'mt-10 mb-3 border-b border-slate-200 pb-2 text-xl font-semibold text-slate-900' : 'mt-7 mb-2 text-lg font-semibold text-slate-900'
            const Tag = (b.level === 1 ? 'h2' : b.level === 2 ? 'h2' : b.level === 3 ? 'h3' : 'h4') as 'h2' | 'h3' | 'h4'
            return <Tag key={idx} id={b.id} className={`${cls} scroll-mt-20`}>{inline(b.text)}</Tag>
          }
          case 'p': return <p key={idx} className="my-4">{inline(b.text)}</p>
          case 'ul': return <ul key={idx} className="my-4 list-disc space-y-1.5 pl-6">{b.items.map((it, j) => <li key={j}>{inline(it)}</li>)}</ul>
          case 'ol': return <ol key={idx} className="my-4 list-decimal space-y-1.5 pl-6">{b.items.map((it, j) => <li key={j}>{inline(it)}</li>)}</ol>
          case 'code': return <CodeBlock key={idx} lang={b.lang} code={b.code} />
          case 'hr': return <hr key={idx} className="my-8 border-slate-200" />
          case 'img': return <img key={idx} src={safeUrl(b.src)} alt={b.alt} className="my-5 max-w-full rounded-lg border border-slate-200" />
          case 'table': return (
            <div key={idx} className="my-5 overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-600"><tr>{b.head.map((c, j) => <th key={j} className="px-3 py-2 font-medium">{inline(c)}</th>)}</tr></thead>
                <tbody className="divide-y divide-slate-100">{b.rows.map((r, j) => <tr key={j}>{r.map((c, k) => <td key={k} className="px-3 py-2">{inline(c)}</td>)}</tr>)}</tbody>
              </table>
            </div>
          )
          case 'quote': {
            if (b.kind) {
              const c = CALLOUT[b.kind]
              const Icon = c.icon
              return (
                <div key={idx} className={`my-5 flex gap-3 rounded-lg border-l-4 p-4 ${c.cls}`}>
                  <Icon size={18} className="mt-1 shrink-0" />
                  <div><div className="text-sm font-semibold">{c.label}</div><div className="text-sm leading-6">{inline(b.lines.join(' '))}</div></div>
                </div>
              )
            }
            return <blockquote key={idx} className="my-5 border-l-4 border-slate-300 pl-4 text-slate-600 italic">{inline(b.lines.join(' '))}</blockquote>
          }
        }
      })}
    </div>
  )
}
