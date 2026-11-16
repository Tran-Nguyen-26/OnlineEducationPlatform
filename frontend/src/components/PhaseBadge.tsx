import type { CoursePhase } from '../types/api'

const MAP: Record<CoursePhase, { label: string; cls: string }> = {
  UPCOMING: { label: 'Sắp khai giảng', cls: 'bg-blue-50 text-blue-700' },
  ONGOING: { label: 'Đang diễn ra', cls: 'bg-green-50 text-green-700' },
  ENDED: { label: 'Đã kết thúc', cls: 'bg-slate-100 text-slate-600' },
}

export function PhaseBadge({ phase }: { phase: CoursePhase }) {
  const m = MAP[phase]
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${m.cls}`}>{m.label}</span>
}
