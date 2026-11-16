import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { GraduationCap, KeyRound, Search } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { MyEnrollmentResponse, StudentCourseResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
import { PhaseBadge } from '../../components/PhaseBadge'
import { TiltCard } from '../../components/ui/TiltCard'
import { coverFor } from '../../lib/cover'
import { Input } from '../../components/ui/Input'
import { Modal } from '../../components/ui/Modal'

export function CataloguePage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const coursesQuery = useQuery({
    queryKey: ['student', 'courses'],
    queryFn: () => apiClient.get<StudentCourseResponse[]>('/api/courses').then((r) => r.data),
  })

  const [enrolling, setEnrolling] = useState<StudentCourseResponse | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [waitlisted, setWaitlisted] = useState(false)
  const [search, setSearch] = useState('')
  const [subject, setSubject] = useState('')

  const enrollMutation = useMutation({
    mutationFn: () => apiClient.post<MyEnrollmentResponse>('/api/enrollments', { courseId: enrolling?.id, inviteCode: code }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['student', 'courses'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'enrollments'] })
      if (res.data.status === 'WAITLISTED') {
        setWaitlisted(true)
      } else {
        setEnrolling(null)
      }
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function openEnroll(course: StudentCourseResponse) {
    setEnrolling(course)
    setCode('')
    setError(null)
    setWaitlisted(false)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    enrollMutation.mutate()
  }

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="mb-1 text-3xl font-extrabold tracking-tight text-slate-900">Khám phá khóa học</h1>
      <p className="mb-6 text-sm text-slate-500">
        Mọi khóa học đều hiện giới thiệu công khai — nhập mã do trung tâm cung cấp để vào học.
      </p>

      <div className="glass glow-border mb-8 flex flex-wrap items-center gap-2 rounded-2xl p-2 shadow-depth">
        <Search size={18} className="ml-3 shrink-0 text-indigo-500" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Tìm theo tên khóa học..."
          aria-label="Tìm khóa học"
          className="h-11 min-w-0 flex-1 bg-transparent text-[15px] text-slate-900 outline-none placeholder:text-slate-400"
        />
        <select value={subject} onChange={(e) => setSubject(e.target.value)} className="h-11 rounded-xl bg-white px-3 text-sm text-slate-700 ring-1 ring-slate-200 outline-none focus:ring-indigo-400">
          <option value="">Tất cả môn học</option>
          {[...new Set((coursesQuery.data ?? []).map((c) => c.subjectName))].map((n) => (
            <option key={n} value={n}>{n}</option>
          ))}
        </select>
      </div>

      {coursesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {coursesQuery.data
          ?.filter((c) => (!subject || c.subjectName === subject) && c.title.toLowerCase().includes(search.trim().toLowerCase()))
          .map((course) => (
          <TiltCard key={course.id} max={6} className="h-full">
          <div className="surface flex h-full flex-col overflow-hidden">
            <div className={`relative h-24 bg-gradient-to-br ${coverFor(course.subjectName)}`}>
              <div className="absolute inset-0 opacity-30 [background:radial-gradient(circle_at_80%_20%,white,transparent_45%)]" />
              <span className="absolute bottom-3 left-4 rounded-full bg-white/90 px-2.5 py-0.5 text-[11px] font-bold tracking-wide text-slate-800 uppercase">{course.subjectName}</span>
              <GraduationCap className="absolute top-3 right-4 text-white/60" size={24} />
            </div>
            <div className="flex flex-1 flex-col p-5">
            <h2 className="text-lg font-bold text-slate-900"><Link to={`/student/explore/${course.id}`} className="hover:text-indigo-600">{course.title}</Link></h2>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
              <PhaseBadge phase={course.phase} />
              {course.instructors.length > 0 && <span>GV: {course.instructors.join(', ')}</span>}
            </div>
            {course.description && <p className="mt-2 flex-1 text-sm text-slate-500">{course.description}</p>}
            <div className="mt-3 text-xs text-slate-500">
              Học phí: {course.feeAmount ? `${course.feeAmount.toLocaleString('vi-VN')} đ` : 'Miễn phí'}
              {course.capacity ? ` · Sĩ số tối đa ${course.capacity}` : ''}
              {' · '}{course.learningMode === 'SEQUENTIAL' ? 'Học tuần tự' : 'Học tự do'}
            </div>
            {course.prerequisites.length > 0 && (
              <div className="mt-2 text-xs text-slate-500">
                Yêu cầu hoàn thành trước:{' '}
                {course.prerequisites.map((p, i) => (
                  <span key={p.courseId} className={p.met ? 'text-green-700' : 'text-amber-700'}>
                    {i > 0 && ', '}{p.title} {p.met ? '✓' : '(chưa đạt)'}
                  </span>
                ))}
              </div>
            )}
            <div className="mt-4">
              {course.completed ? (
                <div className="flex gap-2">
                  <Button className="flex-1" onClick={() => navigate(`/student/courses/${course.id}`)}>Xem lại khóa học</Button>
                  <Button variant="secondary" className="flex-1" onClick={() => navigate('/student/certificates')}>Chứng chỉ</Button>
                </div>
              ) : course.waitlisted ? (
                <p className="rounded-md bg-blue-50 p-2 text-center text-sm text-blue-700">Đang trong danh sách chờ</p>
              ) : course.enrolled ? (
                <Button className="w-full" onClick={() => navigate(`/student/courses/${course.id}`)}>
                  Vào học
                </Button>
              ) : (
                <Button variant="secondary" className="w-full" onClick={() => openEnroll(course)}>
                  <KeyRound size={16} /> Nhập mã để học
                </Button>
              )}
            </div>
            </div>
          </div>
          </TiltCard>
        ))}
        {coursesQuery.data?.length === 0 && <p className="text-slate-400">Chưa có khóa học nào được công khai.</p>}
      </div>

      <Modal title={`Nhập mã khóa học: ${enrolling?.title ?? ''}`} open={enrolling !== null} onClose={() => setEnrolling(null)}>
        {waitlisted ? (
          <div>
            <p className="mb-4 text-sm text-slate-600">
              Khóa học hiện đã đầy chỗ. Bạn đã được thêm vào <strong>danh sách chờ</strong> — hệ thống sẽ tự động chuyển
              bạn sang trạng thái học khi có chỗ trống. Xem trạng thái tại "Tiến độ của tôi".
            </p>
            <div className="flex justify-end">
              <Button type="button" onClick={() => setEnrolling(null)}>
                Đã hiểu
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-slate-700">Mã kích hoạt</label>
            <Input
              required
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="VD: KB7GJ4FE"
              className="mb-4 font-mono"
            />
            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setEnrolling(null)}>
                Hủy
              </Button>
              <Button type="submit" disabled={enrollMutation.isPending}>
                {enrollMutation.isPending ? 'Đang xác nhận...' : 'Vào học'}
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
