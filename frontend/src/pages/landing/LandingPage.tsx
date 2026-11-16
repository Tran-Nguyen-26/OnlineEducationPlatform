import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import {
  Award, BarChart3, BookOpenCheck, CalendarCheck, CheckCircle2, ChevronDown, ClipboardCheck, Flame, GraduationCap, KeyRound, Layers,
  Menu, MessageCircle, PlayCircle, Receipt, Search, ShieldCheck, Sparkles, Users, X,
} from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import { cn } from '../../lib/cn'
import { coverFor } from '../../lib/cover'
import { TiltCard } from '../../components/ui/TiltCard'
import { FloatingElement } from '../../components/ui/FloatingElement'
import { AnimatedSection } from '../../components/ui/AnimatedSection'
import { ProgressRing } from '../../components/ui/ProgressRing'

interface PublicCourse { id: string; title: string; description: string | null; subjectName: string; feeAmount: number | null; learningMode: string; lessonCount: number }
interface Overview { stats: { courses: number; lessons: number; students: number; instructors: number }; courses: PublicCourse[] }

const NAV = [
  { href: '#tinh-nang', label: 'Tính năng' },
  { href: '#quy-trinh', label: 'Cách học' },
  { href: '#vai-tro', label: 'Dành cho ai' },
  { href: '#khoa-hoc', label: 'Khóa học' },
  { href: '#faq', label: 'Hỏi đáp' },
]

const STEPS = [
  { icon: KeyRound, title: 'Nhận mã khóa học', text: 'Đăng ký tài khoản, nhập mã mời do trung tâm cấp để ghi danh vào khóa học.' },
  { icon: BookOpenCheck, title: 'Học theo bài', text: 'Đọc bài giảng, xem video, ghi chú và hỏi đáp ngay trong từng bài học.' },
  { icon: ClipboardCheck, title: 'Kiểm tra & nộp bài', text: 'Làm quiz, nộp bài tập, nhận điểm và nhận xét từ giáo viên.' },
  { icon: Award, title: 'Nhận chứng chỉ', text: 'Đủ điều kiện hoàn thành, hệ thống cấp chứng chỉ có mã tra cứu.' },
]

const ROLES = [
  { icon: GraduationCap, name: 'Học sinh', tone: 'from-indigo-500 to-violet-500', points: ['Trang học liền mạch, nhớ bài đang học dở', 'Quiz, bài tập, điểm số và tiến độ ở một nơi', 'Lịch học, thông báo, hỏi đáp với giáo viên', 'Chứng chỉ và lịch sử học phí của riêng mình'] },
  { icon: Users, name: 'Giáo viên', tone: 'from-sky-500 to-indigo-500', points: ['Tổng quan các khóa đang phụ trách', 'Hàng đợi bài cần chấm gom từ mọi khóa', 'Bảng điểm theo khóa, phân tích từng câu quiz', 'Đăng thông báo, lên lịch, điểm danh'] },
  { icon: ShieldCheck, name: 'Quản trị', tone: 'from-teal-500 to-emerald-500', points: ['Môn học, khóa học, mã mời, ghi danh hàng loạt', 'Học phí, công nợ, hoàn tiền, biên lai', 'Báo cáo hoàn thành / bỏ học theo khóa', 'Thông báo toàn hệ thống, nhật ký thao tác'] },
]

const FAQ = [
  { q: 'Làm sao để đăng ký học một khóa?', a: 'Tạo tài khoản học sinh miễn phí, sau đó vào mục Khóa học và nhập mã mời mà trung tâm cung cấp. Khóa học đầy chỗ sẽ đưa bạn vào danh sách chờ và tự động xếp chỗ khi có người rút.' },
  { q: 'Tôi có cần hoàn thành tất cả bài mới nhận được chứng chỉ?', a: 'Bạn cần hoàn thành các bài học bắt buộc, làm mọi quiz, nộp mọi bài tập và đạt điểm tổng kết tối thiểu (nếu khóa có yêu cầu). Khi đủ điều kiện, nút "Hoàn thành khóa học" sẽ sáng lên và chứng chỉ được cấp ngay.' },
  { q: 'Làm sao kiểm tra một chứng chỉ có thật hay không?', a: 'Dùng ô tra cứu chứng chỉ ở cuối trang này (hoặc trang Xác thực chứng chỉ) và nhập mã số in trên chứng chỉ. Không cần đăng nhập.' },
  { q: 'Tôi quên mật khẩu thì sao?', a: 'Chọn "Quên mật khẩu" ở trang đăng nhập để nhận liên kết đặt lại, hoặc nhờ quản trị viên tạo liên kết đặt lại cho bạn.' },
  { q: 'Học xong khóa rồi còn xem lại bài được không?', a: 'Được. Sau khi hoàn thành, bạn vẫn xem lại toàn bộ nội dung khóa học ở chế độ chỉ đọc.' },
]

const vnd = (n: number | null) => (n ? `${n.toLocaleString('vi-VN')} đ` : 'Miễn phí')

function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2.5">
      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><GraduationCap size={19} /></span>
      <span className={cn('text-lg font-extrabold tracking-tight', light ? 'text-white' : 'text-slate-900')}>OEP</span>
    </Link>
  )
}

function SectionHead({ eyebrow, title, subtitle, light = false }: { eyebrow?: string; title: string; subtitle?: string; light?: boolean }) {
  return (
    <AnimatedSection className="mx-auto mb-14 max-w-2xl text-center">
      {eyebrow && <div className={cn('mb-3 inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold tracking-wide uppercase', light ? 'bg-white/10 text-indigo-200' : 'bg-indigo-50 text-indigo-700')}><Sparkles size={12} />{eyebrow}</div>}
      <h2 className={cn('text-3xl font-extrabold tracking-tight sm:text-4xl', light ? 'text-white' : 'text-slate-900')}>{title}</h2>
      {subtitle && <p className={cn('mt-4 text-lg', light ? 'text-indigo-100/80' : 'text-slate-600')}>{subtitle}</p>}
    </AnimatedSection>
  )
}

/** Layered 3D cluster of product cards (lesson, progress, quiz, certificate) shown beside the hero copy. */
function HeroCluster() {
  return (
    <div className="relative mx-auto h-[420px] w-full max-w-[560px] sm:h-[480px]" aria-hidden>
      <div className="absolute -inset-6 rounded-[2rem] bg-gradient-to-br from-indigo-300/40 via-violet-300/30 to-teal-200/40 blur-3xl" />
      <TiltCard max={5} scale={1} glare={false} className="h-full w-full [perspective:1400px]">
        <div className="relative h-full w-full" style={{ transformStyle: 'preserve-3d' }}>
          {/* Main lesson card */}
          <FloatingElement slow distance={8} className="absolute top-14 left-1/2 w-[300px] -translate-x-1/2 sm:left-[44%] sm:w-[350px]" style={{ transform: 'translateZ(20px)' }}>
            <div className="glass rounded-2xl p-4 shadow-depth [transform:rotateY(-9deg)_rotateX(4deg)]">
              <div className="flex items-center justify-between">
                <div><div className="text-[10px] font-medium text-slate-500">Nhập môn › Bài 2</div><div className="text-sm font-bold text-slate-900">Vòng lặp trong lập trình</div></div>
                <ProgressRing value={68} size={44} stroke={5}><span className="text-[10px]">68%</span></ProgressRing>
              </div>
              <div className="mt-3 space-y-1.5"><div className="h-2 w-full rounded bg-slate-200/80" /><div className="h-2 w-11/12 rounded bg-slate-200/80" /><div className="h-2 w-3/5 rounded bg-slate-200/80" /></div>
              <div className="mt-3 rounded-lg bg-slate-900 p-3 font-mono text-[11px] leading-5 text-slate-100"><span className="text-violet-300">for</span> i <span className="text-violet-300">in</span> <span className="text-teal-300">range</span>(3):<br />&nbsp;&nbsp;print(i)</div>
              <div className="mt-3 flex items-center gap-2 text-[11px] text-slate-600"><PlayCircle size={14} className="text-indigo-600" /> Video minh họa · 4:32<span className="ml-auto rounded-full bg-emerald-100 px-2 py-0.5 font-semibold text-emerald-700">Đang học</span></div>
            </div>
          </FloatingElement>
          {/* Progress card */}
          <FloatingElement distance={14} delay={0.6} className="absolute -top-2 -right-6 hidden w-[190px] sm:block" style={{ transform: 'translateZ(70px)' }}>
            <div className="glass rounded-2xl p-3.5 shadow-depth [transform:rotateY(-14deg)_rotateX(5deg)]">
              <div className="flex items-center gap-3"><ProgressRing value={82} size={54} stroke={6} /><div><div className="text-[10px] text-slate-500">Tiến độ khóa</div><div className="text-sm font-bold text-slate-900">9/11 bài</div></div></div>
            </div>
          </FloatingElement>
          {/* Quiz card */}
          <FloatingElement distance={10} delay={1.2} className="absolute bottom-4 left-0 w-[210px] sm:w-[230px]" style={{ transform: 'translateZ(60px)' }}>
            <div className="glass rounded-2xl p-3.5 shadow-depth [transform:rotateY(10deg)_rotateX(-3deg)]">
              <div className="text-[10px] font-semibold tracking-wide text-indigo-600 uppercase">Quiz · Câu 2/5</div>
              <div className="mt-1 text-xs font-semibold text-slate-900">Kiểu dữ liệu của 5 là gì?</div>
              <div className="mt-2 space-y-1.5 text-[11px]">
                <div className="flex items-center gap-2 rounded-lg bg-emerald-50 px-2 py-1.5 font-medium text-emerald-700 ring-1 ring-emerald-200"><CheckCircle2 size={13} /> int</div>
                <div className="rounded-lg bg-slate-100 px-2 py-1.5 text-slate-600">str</div>
              </div>
            </div>
          </FloatingElement>
          {/* Certificate card */}
          <FloatingElement distance={12} delay={0.3} className="absolute -right-2 bottom-0 w-[200px] sm:right-0" style={{ transform: 'translateZ(90px)' }}>
            <div className="rounded-2xl bg-gradient-to-br from-indigo-600 to-violet-600 p-3.5 text-white shadow-depth [transform:rotateY(-12deg)_rotateX(4deg)]">
              <Award size={22} className="text-amber-300" />
              <div className="mt-2 text-xs font-bold">Chứng chỉ hoàn thành</div>
              <div className="font-mono text-[10px] text-indigo-100">CERT-7K3M9X2A</div>
              <div className="mt-2 inline-flex items-center gap-1 rounded-full bg-white/15 px-2 py-0.5 text-[10px] font-semibold"><CheckCircle2 size={11} /> Hợp lệ</div>
            </div>
          </FloatingElement>
          {/* Chips */}
          <FloatingElement distance={9} delay={0.9} className="absolute top-[46%] -left-4 hidden sm:block" style={{ transform: 'translateZ(100px)' }}>
            <div className="flex items-center gap-1.5 rounded-full bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 shadow-depth"><Flame size={14} className="text-orange-500" /> 7 ngày liên tiếp</div>
          </FloatingElement>
        </div>
      </TiltCard>
    </div>
  )
}

function Tile({ className, children, tilt = true }: { className?: string; children: ReactNode; tilt?: boolean }) {
  const body = <div className={cn('surface h-full overflow-hidden p-6', className)}>{children}</div>
  return tilt ? <TiltCard max={3} scale={1.01} className="h-full">{body}</TiltCard> : body
}

function TileIcon({ icon: Icon, tone = 'from-indigo-500 to-violet-500' }: { icon: typeof Layers; tone?: string }) {
  return <span className={cn('mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg shadow-indigo-500/20', tone)}><Icon size={20} /></span>
}

function CourseCard({ c }: { c: PublicCourse }) {
  return (
    <TiltCard max={7} className="h-full">
      <div className="surface flex h-full flex-col overflow-hidden">
        <div className={cn('relative h-28 bg-gradient-to-br', coverFor(c.subjectName))}>
          <div className="absolute inset-0 opacity-30 [background:radial-gradient(circle_at_80%_20%,white,transparent_45%)]" />
          <span className="absolute bottom-3 left-4 rounded-full bg-white/90 px-2.5 py-0.5 text-[11px] font-bold tracking-wide text-slate-800 uppercase">{c.subjectName}</span>
          <GraduationCap className="absolute top-3 right-4 text-white/60" size={26} />
        </div>
        <div className="flex flex-1 flex-col p-5">
          <h3 className="text-lg font-bold text-slate-900">{c.title}</h3>
          <p className="mt-2 line-clamp-3 flex-1 text-sm text-slate-600">{c.description ?? 'Khóa học được thiết kế theo lộ trình rõ ràng với bài giảng, quiz và bài tập.'}</p>
          <div className="mt-4 flex flex-wrap gap-2 text-xs text-slate-600">
            <span className="rounded-full bg-slate-100 px-2.5 py-1">{c.lessonCount} bài học</span>
            <span className="rounded-full bg-slate-100 px-2.5 py-1">{c.learningMode === 'SEQUENTIAL' ? 'Học tuần tự' : 'Học tự do'}</span>
            <span className="rounded-full bg-indigo-50 px-2.5 py-1 font-semibold text-indigo-700">{vnd(c.feeAmount)}</span>
          </div>
          <Link to="/register" className="mt-5 inline-flex h-10 items-center justify-center rounded-lg bg-slate-900 text-sm font-semibold text-white transition hover:bg-indigo-600">Đăng ký để học</Link>
        </div>
      </div>
    </TiltCard>
  )
}

export function LandingPage() {
  const navigate = useNavigate()
  const [menu, setMenu] = useState(false)
  const [open, setOpen] = useState<number | null>(0)
  const [code, setCode] = useState('')
  const [search, setSearch] = useState('')
  const [subject, setSubject] = useState('')
  const overview = useQuery({
    queryKey: ['public-overview'],
    retry: 1,
    queryFn: () => apiClient.get<Overview>('/api/public/overview').then((r) => r.data),
  })
  const stats = overview.data?.stats
  const courses = overview.data?.courses ?? []
  const subjects = useMemo(() => [...new Set(courses.map((c) => c.subjectName))], [courses])
  const shown = courses.filter((c) => (!subject || c.subjectName === subject) && `${c.title} ${c.subjectName} ${c.description ?? ''}`.toLowerCase().includes(search.trim().toLowerCase()))

  function verify(e: FormEvent) {
    e.preventDefault()
    if (code.trim()) navigate(`/verify-certificate?code=${encodeURIComponent(code.trim().toUpperCase())}`)
  }
  function searchCourses(e: FormEvent) {
    e.preventDefault()
    document.getElementById('khoa-hoc')?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <div className="min-h-screen overflow-x-clip bg-white text-slate-700 antialiased">
      <header className="glass sticky top-0 z-40 border-x-0 border-t-0 border-b-slate-200/70">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
          <Logo />
          <nav className="hidden items-center gap-8 text-sm font-medium text-slate-600 lg:flex">
            {NAV.map((n) => <a key={n.href} href={n.href} className="transition hover:text-indigo-600">{n.label}</a>)}
          </nav>
          <div className="hidden items-center gap-3 lg:flex">
            <Link to="/login" className="px-3 text-sm font-semibold text-slate-700 hover:text-indigo-600">Đăng nhập</Link>
            <Link to="/register" className="inline-flex h-10 items-center rounded-lg bg-brand-gradient px-5 text-sm font-semibold text-white shadow-md shadow-indigo-500/30 transition hover:brightness-110">Đăng ký miễn phí</Link>
          </div>
          <button className="rounded-md p-2 text-slate-700 lg:hidden" onClick={() => setMenu((v) => !v)} aria-label="Menu">{menu ? <X size={22} /> : <Menu size={22} />}</button>
        </div>
        {menu && (
          <div className="border-t border-slate-200 bg-white/95 px-5 pb-4 lg:hidden">
            {NAV.map((n) => <a key={n.href} href={n.href} onClick={() => setMenu(false)} className="block py-3 text-sm font-medium text-slate-700">{n.label}</a>)}
            <div className="mt-2 flex gap-3">
              <Link to="/login" className="flex h-10 flex-1 items-center justify-center rounded-lg border border-slate-300 text-sm font-semibold">Đăng nhập</Link>
              <Link to="/register" className="flex h-10 flex-1 items-center justify-center rounded-lg bg-brand-gradient text-sm font-semibold text-white">Đăng ký</Link>
            </div>
          </div>
        )}
      </header>

      {/* Hero */}
      <section className="bg-mesh relative">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-5 pt-14 pb-20 lg:grid-cols-[1.05fr_1fr] lg:pt-20">
          <div className="animate-fade-up">
            <span className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-white/80 px-3.5 py-1.5 text-xs font-semibold text-indigo-700 shadow-sm backdrop-blur">
              <Sparkles size={13} /> Nền tảng học & quản lý lớp học trực tuyến
            </span>
            <h1 className="mt-6 text-4xl leading-[1.12] font-extrabold tracking-tight text-slate-900 sm:text-5xl lg:text-[3.4rem]">
              Học có lộ trình.<br /><span className="text-brand-gradient">Tiến bộ thấy được từng ngày.</span>
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-8 text-slate-600">
              Bài giảng, quiz, bài tập, điểm danh, học phí và chứng chỉ — tất cả trong một nền tảng. Học sinh học tập trung, giáo viên chấm gọn, trung tâm quản lý minh bạch.
            </p>

            <form onSubmit={searchCourses} className="glass glow-border mt-8 flex max-w-xl items-center gap-2 rounded-2xl p-2 shadow-depth">
              <Search size={18} className="ml-3 shrink-0 text-indigo-500" />
              <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm khóa học: Toán, Vật lý, Hóa học..." aria-label="Tìm khóa học"
                className="h-11 min-w-0 flex-1 bg-transparent text-[15px] text-slate-900 outline-none placeholder:text-slate-400" />
              <button className="inline-flex h-11 shrink-0 items-center rounded-xl bg-brand-gradient px-5 text-sm font-semibold text-white shadow-md shadow-indigo-500/30 transition hover:brightness-110">Tìm khóa học</button>
            </form>
            {subjects.length > 0 && (
              <div className="mt-4 flex flex-wrap items-center gap-2 text-xs">
                <span className="text-slate-500">Phổ biến:</span>
                {subjects.slice(0, 5).map((s) => (
                  <button key={s} onClick={() => { setSubject(s); document.getElementById('khoa-hoc')?.scrollIntoView({ behavior: 'smooth' }) }}
                    className="rounded-full bg-white/80 px-3 py-1 font-medium text-slate-700 ring-1 ring-slate-200 transition hover:bg-indigo-50 hover:text-indigo-700 hover:ring-indigo-200">{s}</button>
                ))}
              </div>
            )}

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link to="/register" className="inline-flex h-12 items-center rounded-xl bg-slate-900 px-6 text-base font-semibold text-white shadow-lg shadow-slate-900/20 transition hover:bg-indigo-600">Bắt đầu học miễn phí</Link>
              <Link to="/login" className="inline-flex h-12 items-center rounded-xl bg-white px-6 text-base font-semibold text-slate-800 ring-1 ring-slate-200 transition hover:ring-indigo-300">Đăng nhập</Link>
            </div>
            <ul className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-slate-600">
              {['Không cần cài đặt', 'Học trên điện thoại & máy tính', 'Chứng chỉ tra cứu được'].map((t) => (
                <li key={t} className="flex items-center gap-1.5"><CheckCircle2 size={16} className="text-teal-600" /> {t}</li>
              ))}
            </ul>
          </div>
          <HeroCluster />
        </div>
      </section>

      {/* Stats */}
      <section className="relative z-10 -mt-8">
        <AnimatedSection className="mx-auto max-w-5xl px-5">
          <div className="glass grid grid-cols-2 gap-6 rounded-2xl p-6 shadow-depth lg:grid-cols-4">
            {[[stats?.courses, 'Khóa học đang mở'], [stats?.lessons, 'Bài học'], [stats?.students, 'Học sinh'], [stats?.instructors, 'Giáo viên']].map(([n, l]) => (
              <div key={String(l)} className="text-center">
                <div className="text-brand-gradient text-4xl font-extrabold tracking-tight">{n ?? '—'}</div>
                <div className="mt-1 text-sm text-slate-500">{l}</div>
              </div>
            ))}
          </div>
        </AnimatedSection>
      </section>

      {/* Bento features */}
      <section id="tinh-nang" className="scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-5">
          <SectionHead eyebrow="Tính năng" title="Mọi thứ trung tâm cần để vận hành lớp học" subtitle="Một hệ thống thay cho nhiều bảng tính và công cụ rời rạc." />
          <div className="grid gap-4 md:grid-cols-6 md:auto-rows-[minmax(190px,auto)]">
            <AnimatedSection className="md:col-span-4 md:row-span-2">
              <Tile className="flex flex-col bg-gradient-to-br from-white to-indigo-50/60">
                <TileIcon icon={Layers} />
                <h3 className="text-xl font-bold text-slate-900">Trang học kiểu tài liệu</h3>
                <p className="mt-2 max-w-md text-sm leading-6 text-slate-600">Mục lục chương – bài bên trái, bài đọc Markdown ở giữa, "Trên trang này" bên phải. Video, PDF, ghi chú và hỏi đáp ngay trong bài; chế độ học tuần tự khóa bài chưa mở.</p>
                <div className="mt-6 grid flex-1 grid-cols-[34%_1fr_22%] gap-3 rounded-xl bg-white p-3 ring-1 ring-slate-200">
                  <div className="space-y-1.5 text-[11px]">
                    <div className="font-bold text-slate-800">1. Nhập môn</div>
                    {['Biến', 'Vòng lặp', 'Hàm'].map((t, i) => <div key={t} className={cn('flex items-center gap-1.5 rounded px-2 py-1', i === 1 ? 'bg-indigo-50 font-semibold text-indigo-700' : 'text-slate-500')}>{i === 0 ? <CheckCircle2 size={11} className="text-teal-600" /> : <span className="h-2.5 w-2.5 rounded-full border border-slate-300" />}{t}</div>)}
                  </div>
                  <div><div className="text-sm font-bold text-slate-900">Vòng lặp</div><div className="mt-2 space-y-1.5"><div className="h-1.5 rounded bg-slate-200" /><div className="h-1.5 w-5/6 rounded bg-slate-200" /><div className="h-1.5 w-2/3 rounded bg-slate-200" /></div><div className="mt-3 rounded bg-slate-900 p-2 font-mono text-[10px] text-slate-200">for i in range(3)</div></div>
                  <div className="space-y-1 border-l border-slate-200 pl-2 text-[10px] text-slate-400"><div className="font-semibold text-slate-600">Trên trang này</div><div className="text-indigo-600">Khái niệm</div><div>Ví dụ</div><div>Bài tập</div></div>
                </div>
              </Tile>
            </AnimatedSection>
            <AnimatedSection delay={80} className="md:col-span-2">
              <Tile><TileIcon icon={ClipboardCheck} tone="from-violet-500 to-fuchsia-500" /><h3 className="font-bold text-slate-900">Quiz & bài tập chấm điểm</h3><p className="mt-2 text-sm text-slate-600">Quiz có đồng hồ, rút câu ngẫu nhiên, tự luận chấm tay, phúc khảo điểm.</p></Tile>
            </AnimatedSection>
            <AnimatedSection delay={140} className="md:col-span-2">
              <Tile className="bg-gradient-to-br from-indigo-600 to-violet-600 text-white !ring-0"><TileIcon icon={Award} tone="from-white/25 to-white/10" /><h3 className="font-bold">Chứng chỉ xác thực được</h3><p className="mt-2 text-sm text-indigo-100">Cấp tự động khi đạt, có mã số để bất kỳ ai tra cứu.</p></Tile>
            </AnimatedSection>
            <AnimatedSection className="md:col-span-2">
              <Tile><TileIcon icon={BarChart3} tone="from-sky-500 to-indigo-500" /><h3 className="font-bold text-slate-900">Tiến độ & bảng điểm</h3>
                <div className="mt-3 space-y-2">{[88, 64, 41].map((v) => <div key={v} className="h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-teal-400" style={{ width: `${v}%` }} /></div>)}</div>
                <p className="mt-3 text-sm text-slate-600">Điểm tổng kết theo trọng số, xuất CSV.</p></Tile>
            </AnimatedSection>
            <AnimatedSection delay={80} className="md:col-span-2">
              <Tile><TileIcon icon={CalendarCheck} tone="from-teal-500 to-emerald-500" /><h3 className="font-bold text-slate-900">Lịch học & điểm danh</h3><p className="mt-2 text-sm text-slate-600">Lịch buổi học, hạn quiz/bài tập, chuyên cần từng học sinh.</p></Tile>
            </AnimatedSection>
            <AnimatedSection delay={140} className="md:col-span-2">
              <Tile><TileIcon icon={MessageCircle} tone="from-amber-400 to-orange-500" /><h3 className="font-bold text-slate-900">Hỏi đáp & thông báo</h3><p className="mt-2 text-sm text-slate-600">Hỏi đáp theo từng bài, đính kèm file, chọn loại thông báo muốn nhận.</p></Tile>
            </AnimatedSection>
            <AnimatedSection className="md:col-span-3">
              <Tile><div className="flex items-start gap-4"><TileIcon icon={Receipt} tone="from-rose-400 to-pink-500" /><div><h3 className="font-bold text-slate-900">Học phí & công nợ</h3><p className="mt-2 text-sm text-slate-600">Ghi nhận thu, miễn giảm, hoàn tiền, in biên lai; báo cáo công nợ toàn trung tâm.</p></div></div></Tile>
            </AnimatedSection>
            <AnimatedSection delay={80} className="md:col-span-3">
              <Tile><div className="flex items-start gap-4"><TileIcon icon={ShieldCheck} tone="from-slate-700 to-slate-900" /><div><h3 className="font-bold text-slate-900">Phân quyền & nhật ký</h3><p className="mt-2 text-sm text-slate-600">Ba vai trò tách biệt; mọi thao tác quan trọng của quản trị đều được ghi nhật ký.</p></div></div></Tile>
            </AnimatedSection>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="quy-trinh" className="bg-mesh scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-5">
          <SectionHead eyebrow="Cách học" title="Từ đăng ký đến chứng chỉ trong 4 bước" />
          <ol className="relative grid gap-6 md:grid-cols-4">
            <div className="absolute top-10 right-[12%] left-[12%] hidden h-px bg-gradient-to-r from-indigo-300 via-violet-300 to-teal-300 md:block" />
            {STEPS.map((s, i) => (
              <AnimatedSection as="li" key={s.title} delay={i * 90} className="relative">
                <TiltCard max={5} className="h-full"><div className="surface relative h-full p-6">
                  <span className="relative z-10 mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-gradient text-white shadow-lg shadow-indigo-500/30"><s.icon size={22} /></span>
                  <span className="absolute top-5 right-5 text-3xl font-extrabold text-slate-100">{i + 1}</span>
                  <h3 className="font-bold text-slate-900">{s.title}</h3>
                  <p className="mt-2 text-sm leading-6 text-slate-600">{s.text}</p>
                </div></TiltCard>
              </AnimatedSection>
            ))}
          </ol>
        </div>
      </section>

      {/* Roles */}
      <section id="vai-tro" className="scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-5">
          <SectionHead eyebrow="Dành cho ai" title="Một nền tảng, ba góc nhìn" />
          <div className="grid gap-6 lg:grid-cols-3">
            {ROLES.map((r, i) => (
              <AnimatedSection key={r.name} delay={i * 100}>
                <TiltCard max={5} className="h-full"><div className="surface h-full p-7">
                  <span className={cn('flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-lg', r.tone)}><r.icon size={22} /></span>
                  <h3 className="mt-5 text-xl font-bold text-slate-900">{r.name}</h3>
                  <ul className="mt-4 space-y-3 text-sm text-slate-600">
                    {r.points.map((p) => <li key={p} className="flex gap-2"><CheckCircle2 size={16} className="mt-0.5 shrink-0 text-teal-600" />{p}</li>)}
                  </ul>
                </div></TiltCard>
              </AnimatedSection>
            ))}
          </div>
        </div>
      </section>

      {/* Courses */}
      <section id="khoa-hoc" className="scroll-mt-16 bg-slate-50 py-24">
        <div className="mx-auto max-w-6xl px-5">
          <SectionHead eyebrow="Khóa học" title="Các khóa học đang mở" subtitle="Đăng ký tài khoản và nhập mã mời từ trung tâm để bắt đầu học." />
          {subjects.length > 0 && (
            <div className="mb-8 flex flex-wrap items-center justify-center gap-2">
              {['', ...subjects].map((s) => (
                <button key={s || 'all'} onClick={() => setSubject(s)}
                  className={cn('rounded-full px-4 py-1.5 text-sm font-semibold transition', subject === s ? 'bg-brand-gradient text-white shadow-md shadow-indigo-500/30' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:ring-indigo-300')}>{s || 'Tất cả'}</button>
              ))}
            </div>
          )}
          {overview.isLoading && <p className="text-center text-slate-400">Đang tải...</p>}
          {!overview.isLoading && shown.length === 0 && <p className="text-center text-slate-500">{courses.length === 0 ? 'Chưa có khóa học nào được công khai. Hãy quay lại sau.' : 'Không có khóa học nào khớp tìm kiếm.'}</p>}
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {shown.slice(0, 9).map((c, i) => <AnimatedSection key={c.id} delay={(i % 3) * 90}><CourseCard c={c} /></AnimatedSection>)}
          </div>
        </div>
      </section>

      {/* Certificate lookup */}
      <section className="bg-mesh-dark relative overflow-hidden py-20">
        <FloatingElement slow distance={18} className="absolute -top-10 -left-10 h-48 w-48 rounded-full bg-indigo-400/20 blur-2xl"><div className="h-full w-full" /></FloatingElement>
        <FloatingElement slow delay={2} distance={16} className="absolute -right-10 -bottom-10 h-56 w-56 rounded-full bg-teal-400/20 blur-2xl"><div className="h-full w-full" /></FloatingElement>
        <AnimatedSection className="relative mx-auto max-w-3xl px-5 text-center">
          <Award className="mx-auto text-amber-300" size={32} />
          <h2 className="mt-3 text-3xl font-extrabold text-white">Tra cứu chứng chỉ</h2>
          <p className="mt-2 text-indigo-100/80">Nhập mã số trên chứng chỉ để xác thực tính hợp lệ — không cần đăng nhập.</p>
          <form onSubmit={verify} className="glass-dark glow-border mx-auto mt-7 flex max-w-lg gap-2 rounded-2xl p-2">
            <div className="relative flex-1">
              <Search size={16} className="absolute top-3.5 left-3 text-indigo-200" />
              <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Ví dụ: CERT-ABCD1234" aria-label="Mã chứng chỉ" className="h-11 w-full bg-transparent pr-3 pl-9 text-sm text-white outline-none placeholder:text-indigo-200/60" />
            </div>
            <button className="h-11 rounded-xl bg-white px-5 text-sm font-semibold text-indigo-700 transition hover:bg-indigo-50">Tra cứu</button>
          </form>
        </AnimatedSection>
      </section>

      {/* FAQ */}
      <section id="faq" className="scroll-mt-16 py-24">
        <div className="mx-auto max-w-6xl px-5">
          <SectionHead eyebrow="Hỏi đáp" title="Câu hỏi thường gặp" />
          <div className="surface mx-auto max-w-3xl divide-y divide-slate-100 overflow-hidden">
            {FAQ.map((f, i) => (
              <div key={f.q}>
                <button onClick={() => setOpen(open === i ? null : i)} aria-expanded={open === i} className="flex w-full items-center justify-between gap-4 px-6 py-4 text-left font-semibold text-slate-900 transition hover:bg-indigo-50/50">
                  {f.q}<ChevronDown size={18} className={cn('shrink-0 text-indigo-500 transition-transform duration-300', open === i && 'rotate-180')} />
                </button>
                <div className="collapse-grid" data-open={open === i}><div><p className="px-6 pb-5 text-sm leading-6 text-slate-600">{f.a}</p></div></div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="px-5 pb-20">
        <AnimatedSection className="bg-mesh-dark relative mx-auto max-w-5xl overflow-hidden rounded-3xl px-6 py-16 text-center shadow-depth">
          <h2 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">Sẵn sàng bắt đầu học?</h2>
          <p className="mx-auto mt-3 max-w-xl text-indigo-100/85">Tạo tài khoản trong chưa đầy một phút và nhập mã mời để vào khóa học của bạn.</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/register" className="inline-flex h-12 items-center rounded-xl bg-white px-7 font-semibold text-indigo-700 shadow-lg transition hover:bg-indigo-50">Đăng ký miễn phí</Link>
            <Link to="/login" className="inline-flex h-12 items-center rounded-xl border border-white/30 px-7 font-semibold text-white transition hover:bg-white/10">Tôi đã có tài khoản</Link>
          </div>
        </AnimatedSection>
      </section>

      <footer className="bg-slate-950 py-12 text-sm text-slate-400">
        <div className="mx-auto grid max-w-6xl gap-8 px-5 sm:grid-cols-2 lg:grid-cols-4">
          <div><Logo light /><p className="mt-3 max-w-xs leading-6">Online Education Platform — nền tảng quản lý và học trực tuyến cho trung tâm đào tạo.</p></div>
          <div><div className="mb-3 font-semibold text-white">Sản phẩm</div><ul className="space-y-2">{NAV.slice(0, 4).map((n) => <li key={n.href}><a href={n.href} className="hover:text-white">{n.label}</a></li>)}</ul></div>
          <div><div className="mb-3 font-semibold text-white">Tài khoản</div><ul className="space-y-2">
            <li><Link to="/login" className="hover:text-white">Đăng nhập</Link></li><li><Link to="/register" className="hover:text-white">Đăng ký</Link></li>
            <li><Link to="/forgot-password" className="hover:text-white">Quên mật khẩu</Link></li><li><Link to="/verify-certificate" className="hover:text-white">Xác thực chứng chỉ</Link></li></ul></div>
          <div><div className="mb-3 font-semibold text-white">Hỗ trợ</div><ul className="space-y-2"><li><a href="#faq" className="hover:text-white">Câu hỏi thường gặp</a></li><li>Liên hệ trung tâm của bạn để nhận mã khóa học</li></ul></div>
        </div>
        <div className="mx-auto mt-10 max-w-6xl border-t border-slate-800 px-5 pt-6 text-xs">© {new Date().getFullYear()} Online Education Platform. Bảo lưu mọi quyền.</div>
      </footer>
    </div>
  )
}
