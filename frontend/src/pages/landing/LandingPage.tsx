import { useState, type FormEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import {
  Award, BarChart3, BookOpenCheck, CalendarCheck, CheckCircle2, ChevronDown, ClipboardCheck, GraduationCap, KeyRound, Layers,
  Menu, MessageCircle, PlayCircle, Receipt, Search, ShieldCheck, Users, X,
} from 'lucide-react'
import { apiClient } from '../../lib/apiClient'
import { cn } from '../../lib/cn'

interface PublicCourse { id: string; title: string; description: string | null; subjectName: string; feeAmount: number | null; learningMode: string; lessonCount: number }
interface Overview { stats: { courses: number; lessons: number; students: number; instructors: number }; courses: PublicCourse[] }

const NAV = [
  { href: '#tinh-nang', label: 'Tính năng' },
  { href: '#quy-trinh', label: 'Cách học' },
  { href: '#vai-tro', label: 'Dành cho ai' },
  { href: '#khoa-hoc', label: 'Khóa học' },
  { href: '#faq', label: 'Câu hỏi thường gặp' },
]

const FEATURES = [
  { icon: Layers, title: 'Học theo lộ trình', text: 'Khóa học chia chương – bài rõ ràng. Có thể bật chế độ học tuần tự: hoàn thành bài trước mới mở bài sau.' },
  { icon: PlayCircle, title: 'Bài giảng đa phương tiện', text: 'Văn bản Markdown, video, PDF, slide trong cùng một trang đọc, có mục lục "Trên trang này" và ghi chú cá nhân.' },
  { icon: ClipboardCheck, title: 'Quiz & bài tập chấm điểm', text: 'Quiz có đồng hồ, tự chấm; bài tập nộp văn bản hoặc file, giáo viên chấm và nhận xét ngay trên hệ thống.' },
  { icon: BarChart3, title: 'Tiến độ & bảng điểm', text: 'Học sinh thấy tiến độ từng khóa; giáo viên có bảng điểm tổng hợp, thống kê từng câu hỏi, xuất CSV.' },
  { icon: Award, title: 'Chứng chỉ xác thực được', text: 'Hoàn thành khóa học tự động cấp chứng chỉ có mã số; bất kỳ ai cũng có thể tra cứu tính hợp lệ.' },
  { icon: CalendarCheck, title: 'Lịch học & điểm danh', text: 'Lên lịch buổi học, điểm danh từng buổi, theo dõi chuyên cần của từng học sinh.' },
  { icon: MessageCircle, title: 'Hỏi đáp & thông báo', text: 'Hỏi đáp theo từng bài học, thông báo khóa học và thông báo hệ thống gửi thẳng tới người học.' },
  { icon: Receipt, title: 'Học phí & công nợ', text: 'Ghi nhận thu, miễn giảm, hoàn tiền, in biên lai; báo cáo công nợ toàn trung tâm.' },
  { icon: ShieldCheck, title: 'Phân quyền & nhật ký', text: 'Ba vai trò tách biệt, mọi thao tác quan trọng của quản trị đều được ghi nhật ký kiểm tra.' },
]

const STEPS = [
  { icon: KeyRound, title: 'Nhận mã khóa học', text: 'Đăng ký tài khoản, nhập mã mời do trung tâm cấp để ghi danh vào khóa học.' },
  { icon: BookOpenCheck, title: 'Học theo bài', text: 'Đọc bài giảng, xem video, ghi chú và hỏi đáp ngay trong từng bài học.' },
  { icon: ClipboardCheck, title: 'Kiểm tra & nộp bài', text: 'Làm quiz, nộp bài tập, nhận điểm và nhận xét từ giáo viên.' },
  { icon: Award, title: 'Nhận chứng chỉ', text: 'Đủ điều kiện hoàn thành, hệ thống cấp chứng chỉ có mã tra cứu.' },
]

const ROLES = [
  { icon: GraduationCap, name: 'Học sinh', points: ['Trang học liền mạch, nhớ bài đang học dở', 'Quiz, bài tập, điểm số và tiến độ ở một nơi', 'Lịch học, thông báo, hỏi đáp với giáo viên', 'Chứng chỉ và lịch sử học phí của riêng mình'] },
  { icon: Users, name: 'Giáo viên', points: ['Tổng quan các khóa đang phụ trách', 'Hàng đợi bài cần chấm gom từ mọi khóa', 'Bảng điểm theo khóa, phân tích từng câu quiz', 'Đăng thông báo, lên lịch, điểm danh'] },
  { icon: ShieldCheck, name: 'Quản trị', points: ['Quản lý môn học, khóa học, mã mời, ghi danh hàng loạt', 'Học phí, công nợ, hoàn tiền, biên lai', 'Báo cáo hoàn thành / bỏ học theo khóa và giáo viên', 'Thông báo toàn hệ thống, nhật ký thao tác'] },
]

const FAQ = [
  { q: 'Làm sao để đăng ký học một khóa?', a: 'Tạo tài khoản học sinh miễn phí, sau đó vào mục Khóa học và nhập mã mời mà trung tâm cung cấp. Khóa học đầy chỗ sẽ đưa bạn vào danh sách chờ và tự động xếp chỗ khi có người rút.' },
  { q: 'Tôi có cần hoàn thành tất cả bài mới nhận được chứng chỉ?', a: 'Bạn cần hoàn thành các bài học bắt buộc, làm mọi quiz và nộp mọi bài tập của khóa. Khi đủ điều kiện, nút "Hoàn thành khóa học" sẽ sáng lên và chứng chỉ được cấp ngay.' },
  { q: 'Làm sao kiểm tra một chứng chỉ có thật hay không?', a: 'Dùng ô tra cứu chứng chỉ ở cuối trang này (hoặc trang Xác thực chứng chỉ) và nhập mã số in trên chứng chỉ. Không cần đăng nhập.' },
  { q: 'Tôi quên mật khẩu thì sao?', a: 'Chọn "Quên mật khẩu" ở trang đăng nhập để nhận liên kết đặt lại, hoặc nhờ quản trị viên tạo liên kết đặt lại cho bạn.' },
  { q: 'Học xong khóa rồi còn xem lại bài được không?', a: 'Được. Sau khi hoàn thành, bạn vẫn xem lại toàn bộ nội dung khóa học ở chế độ chỉ đọc.' },
]

const vnd = (n: number | null) => (n ? `${n.toLocaleString('vi-VN')} đ` : 'Miễn phí')

function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white"><GraduationCap size={18} /></span>
      <span className={cn('text-lg font-bold tracking-tight', light ? 'text-white' : 'text-slate-900')}>OEP</span>
    </Link>
  )
}

function Section({ id, eyebrow, title, subtitle, children, tone = 'white' }: { id?: string; eyebrow?: string; title: string; subtitle?: string; children: ReactNode; tone?: 'white' | 'gray' }) {
  return (
    <section id={id} className={cn('scroll-mt-16 py-20', tone === 'gray' ? 'bg-slate-50' : 'bg-white')}>
      <div className="mx-auto max-w-6xl px-5">
        <div className="mx-auto mb-12 max-w-2xl text-center">
          {eyebrow && <div className="mb-2 text-sm font-semibold tracking-wide text-blue-600 uppercase">{eyebrow}</div>}
          <h2 className="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">{title}</h2>
          {subtitle && <p className="mt-4 text-lg text-slate-600">{subtitle}</p>}
        </div>
        {children}
      </div>
    </section>
  )
}

/** Static mock of the real learning page, so visitors see what they will get. */
function ProductMock() {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl shadow-blue-900/10">
      <div className="flex items-center gap-1.5 border-b border-slate-200 bg-slate-50 px-4 py-2.5">
        <span className="h-2.5 w-2.5 rounded-full bg-red-300" /><span className="h-2.5 w-2.5 rounded-full bg-amber-300" /><span className="h-2.5 w-2.5 rounded-full bg-green-300" />
        <div className="mx-auto h-5 w-1/2 rounded bg-slate-200/70" />
      </div>
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2 text-xs">
        <span className="font-semibold text-slate-800">Lập trình cơ bản</span>
        <span className="flex items-center gap-2 text-slate-500"><span className="h-1.5 w-24 overflow-hidden rounded-full bg-slate-200"><span className="block h-full w-2/3 bg-emerald-500" /></span>2/3 bài</span>
      </div>
      <div className="grid grid-cols-[38%_1fr] text-left">
        <div className="space-y-1 border-r border-slate-200 p-3 text-xs">
          <div className="font-semibold text-slate-800">1. Nhập môn</div>
          {[['Biến và kiểu dữ liệu', 'done'], ['Vòng lặp', 'active'], ['Hàm', 'todo']].map(([t, s]) => (
            <div key={t} className={cn('flex items-center gap-2 rounded px-2 py-1.5', s === 'active' ? 'bg-blue-50 font-medium text-blue-800' : 'text-slate-600')}>
              {s === 'done' ? <CheckCircle2 size={13} className="text-green-600" /> : <span className={cn('h-3 w-3 rounded-full border', s === 'active' ? 'border-amber-400 bg-amber-200' : 'border-slate-300')} />}{t}
            </div>
          ))}
          <div className="pt-2 font-semibold text-slate-800">2. Nâng cao</div>
          <div className="flex items-center gap-2 px-2 py-1.5 text-slate-400"><ShieldCheck size={13} /> Đối tượng</div>
        </div>
        <div className="p-4">
          <div className="text-[10px] text-slate-400">Nhập môn</div>
          <div className="text-base font-bold text-slate-900">Vòng lặp</div>
          <div className="mt-3 space-y-1.5"><div className="h-2 w-full rounded bg-slate-200" /><div className="h-2 w-11/12 rounded bg-slate-200" /><div className="h-2 w-4/5 rounded bg-slate-200" /></div>
          <div className="mt-3 rounded bg-slate-900 p-3 font-mono text-[10px] leading-4 text-slate-100"><span className="text-sky-300">for</span> i <span className="text-sky-300">in</span> range(3):<br />&nbsp;&nbsp;print(i)</div>
          <div className="mt-3 rounded border-l-4 border-blue-300 bg-blue-50 p-2 text-[10px] text-blue-900">Mẹo: dùng range() để lặp đúng số lần.</div>
        </div>
      </div>
    </div>
  )
}

function CourseCard({ c }: { c: PublicCourse }) {
  return (
    <div className="flex flex-col rounded-xl border border-slate-200 bg-white p-6 transition hover:border-blue-300 hover:shadow-lg">
      <div className="text-xs font-semibold tracking-wide text-blue-600 uppercase">{c.subjectName}</div>
      <h3 className="mt-2 text-lg font-semibold text-slate-900">{c.title}</h3>
      <p className="mt-2 line-clamp-3 flex-1 text-sm text-slate-600">{c.description ?? 'Khóa học được thiết kế theo lộ trình rõ ràng với bài giảng, quiz và bài tập.'}</p>
      <div className="mt-4 flex flex-wrap gap-2 text-xs text-slate-600">
        <span className="rounded-full bg-slate-100 px-2.5 py-1">{c.lessonCount} bài học</span>
        <span className="rounded-full bg-slate-100 px-2.5 py-1">{c.learningMode === 'SEQUENTIAL' ? 'Học tuần tự' : 'Học tự do'}</span>
        <span className="rounded-full bg-blue-50 px-2.5 py-1 font-medium text-blue-700">{vnd(c.feeAmount)}</span>
      </div>
      <Link to="/register" className="mt-5 inline-flex h-10 items-center justify-center rounded-lg border border-slate-300 text-sm font-medium text-slate-800 hover:bg-slate-50">Đăng ký để học</Link>
    </div>
  )
}

export function LandingPage() {
  const navigate = useNavigate()
  const [menu, setMenu] = useState(false)
  const [open, setOpen] = useState<number | null>(0)
  const [code, setCode] = useState('')
  const overview = useQuery({
    queryKey: ['public-overview'],
    retry: 1,
    queryFn: () => apiClient.get<Overview>('/api/public/overview').then((r) => r.data),
  })
  const stats = overview.data?.stats
  const courses = overview.data?.courses ?? []

  function verify(e: FormEvent) {
    e.preventDefault()
    if (code.trim()) navigate(`/verify-certificate?code=${encodeURIComponent(code.trim().toUpperCase())}`)
  }

  return (
    <div className="min-h-screen bg-white text-slate-700 antialiased">
      <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
          <Logo />
          <nav className="hidden items-center gap-7 text-sm font-medium text-slate-600 lg:flex">
            {NAV.map((n) => <a key={n.href} href={n.href} className="hover:text-slate-900">{n.label}</a>)}
          </nav>
          <div className="hidden items-center gap-3 lg:flex">
            <Link to="/login" className="text-sm font-medium text-slate-700 hover:text-slate-900">Đăng nhập</Link>
            <Link to="/register" className="inline-flex h-9 items-center rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white hover:bg-blue-700">Đăng ký miễn phí</Link>
          </div>
          <button className="rounded-md p-2 text-slate-700 lg:hidden" onClick={() => setMenu((v) => !v)} aria-label="Menu">{menu ? <X size={22} /> : <Menu size={22} />}</button>
        </div>
        {menu && (
          <div className="border-t border-slate-200 bg-white px-5 pb-4 lg:hidden">
            {NAV.map((n) => <a key={n.href} href={n.href} onClick={() => setMenu(false)} className="block py-3 text-sm font-medium text-slate-700">{n.label}</a>)}
            <div className="mt-2 flex gap-3">
              <Link to="/login" className="flex h-10 flex-1 items-center justify-center rounded-lg border border-slate-300 text-sm font-medium">Đăng nhập</Link>
              <Link to="/register" className="flex h-10 flex-1 items-center justify-center rounded-lg bg-blue-600 text-sm font-semibold text-white">Đăng ký</Link>
            </div>
          </div>
        )}
      </header>

      <section className="relative overflow-hidden bg-gradient-to-b from-blue-50 via-white to-white">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pt-16 pb-20 lg:grid-cols-[1.05fr_1fr] lg:pt-24">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-white px-3 py-1 text-xs font-medium text-blue-700">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-600" /> Nền tảng quản lý & học trực tuyến cho trung tâm đào tạo
            </span>
            <h1 className="mt-5 text-4xl leading-tight font-bold tracking-tight text-slate-900 sm:text-5xl">
              Học tập có lộ trình.<br /><span className="text-blue-600">Quản lý gọn trong một nơi.</span>
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-8 text-slate-600">
              Từ bài giảng, quiz, bài tập, điểm danh đến học phí và chứng chỉ — học sinh, giáo viên và quản trị viên cùng làm việc trên một hệ thống thống nhất.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/register" className="inline-flex h-12 items-center rounded-lg bg-blue-600 px-6 text-base font-semibold text-white shadow-sm hover:bg-blue-700">Bắt đầu học miễn phí</Link>
              <Link to="/login" className="inline-flex h-12 items-center rounded-lg border border-slate-300 bg-white px-6 text-base font-semibold text-slate-800 hover:bg-slate-50">Đăng nhập</Link>
            </div>
            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-sm text-slate-600">
              {['Không cần cài đặt', 'Học trên điện thoại & máy tính', 'Chứng chỉ tra cứu được'].map((t) => (
                <li key={t} className="flex items-center gap-1.5"><CheckCircle2 size={16} className="text-green-600" /> {t}</li>
              ))}
            </ul>
          </div>
          <ProductMock />
        </div>
      </section>

      <section className="border-y border-slate-200 bg-white">
        <div className="mx-auto grid max-w-6xl grid-cols-2 gap-6 px-5 py-10 text-center lg:grid-cols-4">
          {[
            [stats?.courses, 'Khóa học đang mở'],
            [stats?.lessons, 'Bài học'],
            [stats?.students, 'Học sinh'],
            [stats?.instructors, 'Giáo viên'],
          ].map(([n, l]) => (
            <div key={String(l)}>
              <div className="text-4xl font-bold tracking-tight text-slate-900">{n ?? '—'}</div>
              <div className="mt-1 text-sm text-slate-500">{l}</div>
            </div>
          ))}
        </div>
      </section>

      <Section id="tinh-nang" eyebrow="Tính năng" title="Mọi thứ trung tâm cần để vận hành lớp học" subtitle="Một hệ thống thay cho nhiều bảng tính và công cụ rời rạc.">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title} className="rounded-xl border border-slate-200 bg-white p-6">
              <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600"><f.icon size={20} /></span>
              <h3 className="mt-4 font-semibold text-slate-900">{f.title}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">{f.text}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section id="quy-trinh" tone="gray" eyebrow="Cách học" title="Từ đăng ký đến chứng chỉ trong 4 bước">
        <ol className="grid gap-6 md:grid-cols-4">
          {STEPS.map((s, i) => (
            <li key={s.title} className="relative rounded-xl border border-slate-200 bg-white p-6">
              <span className="absolute -top-3 left-6 flex h-7 w-7 items-center justify-center rounded-full bg-blue-600 text-sm font-bold text-white">{i + 1}</span>
              <s.icon size={22} className="mt-2 text-blue-600" />
              <h3 className="mt-3 font-semibold text-slate-900">{s.title}</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">{s.text}</p>
            </li>
          ))}
        </ol>
      </Section>

      <Section id="vai-tro" eyebrow="Dành cho ai" title="Một nền tảng, ba góc nhìn">
        <div className="grid gap-6 lg:grid-cols-3">
          {ROLES.map((r) => (
            <div key={r.name} className="rounded-xl border border-slate-200 bg-white p-7 shadow-sm">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-900 text-white"><r.icon size={20} /></span>
              <h3 className="mt-4 text-xl font-semibold text-slate-900">{r.name}</h3>
              <ul className="mt-4 space-y-3 text-sm text-slate-600">
                {r.points.map((p) => <li key={p} className="flex gap-2"><CheckCircle2 size={16} className="mt-0.5 shrink-0 text-green-600" />{p}</li>)}
              </ul>
            </div>
          ))}
        </div>
      </Section>

      <Section id="khoa-hoc" tone="gray" eyebrow="Khóa học" title="Các khóa học đang mở" subtitle="Đăng ký tài khoản và nhập mã mời từ trung tâm để bắt đầu học.">
        {overview.isLoading && <p className="text-center text-slate-400">Đang tải...</p>}
        {!overview.isLoading && courses.length === 0 && <p className="text-center text-slate-500">Chưa có khóa học nào được công khai. Hãy quay lại sau.</p>}
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {courses.slice(0, 6).map((c) => <CourseCard key={c.id} c={c} />)}
        </div>
      </Section>

      <section className="bg-white py-16">
        <div className="mx-auto max-w-3xl px-5">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-8 text-center">
            <Award className="mx-auto text-blue-600" size={28} />
            <h2 className="mt-3 text-2xl font-bold text-slate-900">Tra cứu chứng chỉ</h2>
            <p className="mt-2 text-slate-600">Nhập mã số trên chứng chỉ để xác thực tính hợp lệ — không cần đăng nhập.</p>
            <form onSubmit={verify} className="mx-auto mt-6 flex max-w-md gap-2">
              <div className="relative flex-1">
                <Search size={16} className="absolute top-3.5 left-3 text-slate-400" />
                <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Ví dụ: CERT-ABCD1234" className="h-11 w-full rounded-lg border border-slate-300 bg-white pr-3 pl-9 text-sm" />
              </div>
              <button className="h-11 rounded-lg bg-slate-900 px-5 text-sm font-semibold text-white hover:bg-slate-700">Tra cứu</button>
            </form>
          </div>
        </div>
      </section>

      <Section id="faq" tone="gray" eyebrow="Hỏi đáp" title="Câu hỏi thường gặp">
        <div className="mx-auto max-w-3xl divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white">
          {FAQ.map((f, i) => (
            <div key={f.q}>
              <button onClick={() => setOpen(open === i ? null : i)} className="flex w-full items-center justify-between gap-4 px-6 py-4 text-left font-medium text-slate-900">
                {f.q}<ChevronDown size={18} className={cn('shrink-0 text-slate-400 transition', open === i && 'rotate-180')} />
              </button>
              {open === i && <p className="px-6 pb-5 text-sm leading-6 text-slate-600">{f.a}</p>}
            </div>
          ))}
        </div>
      </Section>

      <section className="bg-slate-900 py-16">
        <div className="mx-auto max-w-4xl px-5 text-center">
          <h2 className="text-3xl font-bold tracking-tight text-white">Sẵn sàng bắt đầu học?</h2>
          <p className="mx-auto mt-3 max-w-xl text-slate-300">Tạo tài khoản trong chưa đầy một phút và nhập mã mời để vào khóa học của bạn.</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/register" className="inline-flex h-12 items-center rounded-lg bg-blue-600 px-6 font-semibold text-white hover:bg-blue-500">Đăng ký miễn phí</Link>
            <Link to="/login" className="inline-flex h-12 items-center rounded-lg border border-slate-600 px-6 font-semibold text-white hover:bg-slate-800">Tôi đã có tài khoản</Link>
          </div>
        </div>
      </section>

      <footer className="bg-slate-950 py-12 text-sm text-slate-400">
        <div className="mx-auto grid max-w-6xl gap-8 px-5 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <Logo light />
            <p className="mt-3 max-w-xs leading-6">Online Education Platform — nền tảng quản lý và học trực tuyến cho trung tâm đào tạo.</p>
          </div>
          <div>
            <div className="mb-3 font-semibold text-white">Sản phẩm</div>
            <ul className="space-y-2">{NAV.slice(0, 4).map((n) => <li key={n.href}><a href={n.href} className="hover:text-white">{n.label}</a></li>)}</ul>
          </div>
          <div>
            <div className="mb-3 font-semibold text-white">Tài khoản</div>
            <ul className="space-y-2">
              <li><Link to="/login" className="hover:text-white">Đăng nhập</Link></li>
              <li><Link to="/register" className="hover:text-white">Đăng ký</Link></li>
              <li><Link to="/forgot-password" className="hover:text-white">Quên mật khẩu</Link></li>
              <li><Link to="/verify-certificate" className="hover:text-white">Xác thực chứng chỉ</Link></li>
            </ul>
          </div>
          <div>
            <div className="mb-3 font-semibold text-white">Hỗ trợ</div>
            <ul className="space-y-2"><li><a href="#faq" className="hover:text-white">Câu hỏi thường gặp</a></li><li>Liên hệ trung tâm của bạn để nhận mã khóa học</li></ul>
          </div>
        </div>
        <div className="mx-auto mt-10 max-w-6xl border-t border-slate-800 px-5 pt-6 text-xs">© {new Date().getFullYear()} Online Education Platform. Bảo lưu mọi quyền.</div>
      </footer>
    </div>
  )
}
