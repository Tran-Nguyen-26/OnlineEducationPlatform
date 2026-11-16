import { useRef, type CSSProperties, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** True when the user asked for reduced motion or the device has no hover (touch) — 3D effects are then skipped. */
function motionAllowed() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return !window.matchMedia('(prefers-reduced-motion: reduce)').matches && window.matchMedia('(hover: hover) and (pointer: fine)').matches
}

/**
 * Card that tilts in 3D towards the pointer and shows a soft light glare.
 * Pure presentation: children, click handlers and layout are untouched. API mirrors a framer-motion based version
 * (max = max tilt in degrees, scale = hover scale) so it can be swapped later without touching call sites.
 */
export function TiltCard({ children, className, style, max = 8, scale = 1.02, glare = true }: {
  children: ReactNode
  className?: string
  style?: CSSProperties
  max?: number
  scale?: number
  glare?: boolean
}) {
  const ref = useRef<HTMLDivElement>(null)
  const glareRef = useRef<HTMLDivElement>(null)
  const raf = useRef(0)

  function onMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!motionAllowed()) return
    const el = ref.current
    if (!el) return
    const r = el.getBoundingClientRect()
    const px = (e.clientX - r.left) / r.width
    const py = (e.clientY - r.top) / r.height
    cancelAnimationFrame(raf.current)
    raf.current = requestAnimationFrame(() => {
      el.style.transform = `perspective(900px) rotateX(${((0.5 - py) * max * 2).toFixed(2)}deg) rotateY(${((px - 0.5) * max * 2).toFixed(2)}deg) scale(${scale})`
      if (glareRef.current) {
        glareRef.current.style.opacity = '1'
        glareRef.current.style.background = `radial-gradient(circle at ${(px * 100).toFixed(0)}% ${(py * 100).toFixed(0)}%, rgb(255 255 255 / 0.35), transparent 55%)`
      }
    })
  }

  function onLeave() {
    cancelAnimationFrame(raf.current)
    if (ref.current) ref.current.style.transform = ''
    if (glareRef.current) glareRef.current.style.opacity = '0'
  }

  return (
    <div
      ref={ref}
      onPointerMove={onMove}
      onPointerLeave={onLeave}
      style={{ transformStyle: 'preserve-3d', transition: 'transform .25s ease-out', willChange: 'transform', ...style }}
      className={cn('relative', className)}
    >
      {children}
      {glare && <div ref={glareRef} aria-hidden className="pointer-events-none absolute inset-0 rounded-[inherit] opacity-0 transition-opacity duration-300" />}
    </div>
  )
}
