import { cn } from '../lib/cn'

const COLORS: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  PUBLISHED: 'bg-green-100 text-green-700',
  DRAFT: 'bg-slate-100 text-slate-600',
  ARCHIVED: 'bg-amber-100 text-amber-700',
  DISABLED: 'bg-red-100 text-red-700',
}

export function Badge({ status }: { status: string }) {
  return (
    <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', COLORS[status] ?? 'bg-slate-100 text-slate-600')}>
      {status}
    </span>
  )
}
