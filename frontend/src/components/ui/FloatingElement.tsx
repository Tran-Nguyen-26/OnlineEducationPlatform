import type { CSSProperties, ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** Gently bobbing wrapper (CSS animation, transform-only). Honors prefers-reduced-motion via the global rule in index.css. */
export function FloatingElement({ children, className, delay = 0, distance = 12, slow = false, style }: {
  children: ReactNode
  className?: string
  /** seconds */
  delay?: number
  /** px travelled up and down */
  distance?: number
  slow?: boolean
  style?: CSSProperties
}) {
  return (
    <div
      className={cn(slow ? 'animate-float-slow' : 'animate-float', className)}
      style={{ animationDelay: `${delay}s`, ['--float-y' as string]: `${distance}px`, ...style }}
    >
      {children}
    </div>
  )
}
