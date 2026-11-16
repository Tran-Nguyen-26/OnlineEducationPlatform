import { useEffect, useState } from 'react'
import type { Heading } from '../../../lib/markdown'

/** "Trên trang này": section links with scroll-spy against the scrolling article container. */
export function Toc({ headings, scrollEl }: { headings: Heading[]; scrollEl: HTMLElement | null }) {
  const [active, setActive] = useState<string | null>(headings[0]?.id ?? null)

  useEffect(() => {
    if (!scrollEl || headings.length === 0) return
    const onScroll = () => {
      const top = scrollEl.getBoundingClientRect().top + 90
      let cur = headings[0].id
      for (const h of headings) {
        const el = document.getElementById(h.id)
        if (el && el.getBoundingClientRect().top <= top) cur = h.id
      }
      setActive(cur)
    }
    onScroll()
    scrollEl.addEventListener('scroll', onScroll, { passive: true })
    return () => scrollEl.removeEventListener('scroll', onScroll)
  }, [headings, scrollEl])

  if (headings.length < 2) return null
  return (
    <nav aria-label="Trên trang này" className="sticky top-6 text-sm">
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Trên trang này</div>
      <ul className="space-y-1 border-l border-slate-200">
        {headings.map((h) => (
          <li key={h.id}>
            <a
              href={`#${h.id}`}
              onClick={(e) => { e.preventDefault(); document.getElementById(h.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }}
              className={`-ml-px block border-l-2 py-1 pr-2 ${h.level === 3 ? 'pl-6' : 'pl-3'} ${active === h.id ? 'border-blue-600 font-medium text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-900'}`}
            >
              {h.text}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}
