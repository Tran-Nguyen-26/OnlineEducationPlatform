import { useEffect, useRef, useState, type ElementType, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** Fades and rises into view the first time it scrolls on screen (IntersectionObserver). `delay` in ms staggers siblings. */
export function AnimatedSection({ children, className, delay = 0, as: Tag = 'div', id }: {
  children: ReactNode
  className?: string
  delay?: number
  as?: ElementType
  id?: string
}) {
  const ref = useRef<HTMLElement>(null)
  const [state, setState] = useState<'out' | 'in'>('out')

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (typeof IntersectionObserver === 'undefined') { setState('in'); return }
    const io = new IntersectionObserver(
      (entries) => { if (entries.some((e) => e.isIntersecting)) { setState('in'); io.disconnect() } },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <Tag ref={ref} id={id} data-reveal={state} style={{ ['--reveal-delay' as string]: `${delay}ms` }} className={cn(className)}>
      {children}
    </Tag>
  )
}
