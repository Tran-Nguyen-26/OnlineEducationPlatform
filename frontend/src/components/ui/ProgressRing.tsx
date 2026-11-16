import { useId } from 'react'

/** Circular progress (0-100) with a brand gradient stroke; children render in the centre (defaults to the percentage). */
export function ProgressRing({ value, size = 64, stroke = 7, children, className }: {
  value: number
  size?: number
  stroke?: number
  children?: React.ReactNode
  className?: string
}) {
  const id = useId()
  const v = Math.max(0, Math.min(100, value))
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  return (
    <div className={className} style={{ width: size, height: size, position: 'relative' }} role="img" aria-label={`Tiến độ ${Math.round(v)}%`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
        <defs>
          <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#6366f1" />
            <stop offset="60%" stopColor="#8b5cf6" />
            <stop offset="100%" stopColor="#14b8a6" />
          </linearGradient>
        </defs>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#e2e8f0" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={`url(#${id})`} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} style={{ transition: 'stroke-dashoffset .8s cubic-bezier(.22,1,.36,1)' }} />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center text-xs font-bold text-slate-800">{children ?? `${Math.round(v)}%`}</div>
    </div>
  )
}
