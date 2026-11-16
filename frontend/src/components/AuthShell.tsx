import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Award, CheckCircle2, GraduationCap, Sparkles } from 'lucide-react'
import { FloatingElement } from './ui/FloatingElement'
import { ProgressRing } from './ui/ProgressRing'

/** Split-screen frame for the auth pages: brand panel with floating product cards + the page's own form on the right. */
export function AuthShell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="bg-mesh-dark relative hidden overflow-hidden p-12 text-white lg:flex lg:flex-col">
        <Link to="/" className="relative z-10 flex items-center gap-2.5">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/25 backdrop-blur"><GraduationCap size={21} /></span>
          <span className="text-xl font-extrabold tracking-tight">OEP</span>
        </Link>

        <div className="relative z-10 mt-auto mb-auto max-w-md">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-100 ring-1 ring-white/15"><Sparkles size={12} /> Học có lộ trình</span>
          <h2 className="mt-5 text-4xl leading-tight font-extrabold tracking-tight">Mỗi bài học là một bước tiến.</h2>
          <p className="mt-4 text-indigo-100/80">Học tập trung, theo dõi tiến độ rõ ràng và nhận chứng chỉ khi hoàn thành khóa học.</p>
          <ul className="mt-6 space-y-2.5 text-sm text-indigo-100">
            {['Trang học liền mạch, nhớ bài đang học dở', 'Quiz, bài tập, điểm số ở một nơi', 'Chứng chỉ có mã tra cứu công khai'].map((t) => (
              <li key={t} className="flex items-center gap-2"><CheckCircle2 size={16} className="text-teal-300" />{t}</li>
            ))}
          </ul>
        </div>

        <FloatingElement slow distance={14} className="absolute top-24 right-10 z-10">
          <div className="glass-dark flex items-center gap-3 rounded-2xl p-3.5 shadow-depth [transform:rotate(3deg)]">
            <ProgressRing value={76} size={50} stroke={6} /><div><div className="text-[11px] text-indigo-200">Tiến độ khóa học</div><div className="text-sm font-bold">Lập trình cơ bản</div></div>
          </div>
        </FloatingElement>
        <FloatingElement delay={1.2} distance={12} className="absolute right-16 bottom-24 z-10">
          <div className="flex items-center gap-2 rounded-2xl bg-white p-3 text-slate-800 shadow-depth [transform:rotate(-4deg)]">
            <Award className="text-amber-500" size={22} /><div><div className="text-xs font-bold">Chứng chỉ hoàn thành</div><div className="font-mono text-[10px] text-slate-500">CERT-7K3M9X2A</div></div>
          </div>
        </FloatingElement>
        <div className="pointer-events-none absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-indigo-400/25 blur-3xl" />
      </aside>

      <main className="bg-mesh flex items-center justify-center p-5">
        <div className={wide ? 'w-full max-w-md' : 'w-full max-w-sm'}>
          <Link to="/" className="mb-6 flex items-center justify-center gap-2 lg:hidden">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-gradient text-white"><GraduationCap size={19} /></span>
            <span className="text-lg font-extrabold text-slate-900">OEP</span>
          </Link>
          {children}
        </div>
      </main>
    </div>
  )
}
