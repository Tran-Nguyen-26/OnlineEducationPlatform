import type { ComponentType, ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** Page title row used by admin/teacher pages: title + optional subtitle on the left, actions on the right. */
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

const TONES = {
  indigo: 'from-indigo-500 to-violet-500',
  teal: 'from-teal-500 to-emerald-500',
  amber: 'from-amber-400 to-orange-500',
  sky: 'from-sky-500 to-indigo-500',
  rose: 'from-rose-400 to-pink-500',
} as const

/** Compact KPI tile with a gradient icon chip. */
export function StatCard({ label, value, icon: Icon, tone = 'indigo', hint }: {
  label: string
  value: ReactNode
  icon?: ComponentType<{ size?: number }>
  tone?: keyof typeof TONES
  hint?: string
}) {
  return (
    <div className="surface flex items-center gap-3 p-4">
      {Icon && <span className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg shadow-indigo-500/15', TONES[tone])}><Icon size={20} /></span>}
      <div className="min-w-0">
        <div className="truncate text-2xl leading-none font-extrabold text-slate-900">{value}</div>
        <div className="mt-1 truncate text-xs text-slate-500">{label}</div>
        {hint && <div className="truncate text-[11px] text-slate-400">{hint}</div>}
      </div>
    </div>
  )
}

/** Segmented tab bar (scrolls horizontally on narrow screens). Purely presentational: parent owns the active key. */
export function TabBar<K extends string>({ tabs, active, onChange, className }: {
  tabs: readonly { key: K; label: string }[]
  active: K
  onChange: (key: K) => void
  className?: string
}) {
  return (
    <div className={cn('scroll-thin mb-6 flex gap-1 overflow-x-auto rounded-xl bg-slate-100/80 p-1', className)} role="tablist">
      {tabs.map((t) => (
        <button
          key={t.key}
          role="tab"
          aria-selected={active === t.key}
          onClick={() => onChange(t.key)}
          className={cn(
            'shrink-0 rounded-lg px-4 py-2 text-sm font-semibold whitespace-nowrap transition',
            active === t.key ? 'bg-white text-indigo-700 shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-800',
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}
