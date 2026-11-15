import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import type { StudentCourseResponse } from '../../types/api'
import { Button } from '../../components/ui/Button'
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

  const enrollMutation = useMutation({
    mutationFn: () => apiClient.post('/api/enrollments', { courseId: enrolling?.id, inviteCode: code }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student', 'courses'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'enrollments'] })
      setEnrolling(null)
    },
    onError: (err) => setError(getApiErrorMessage(err)),
  })

  function openEnroll(course: StudentCourseResponse) {
    setEnrolling(course)
    setCode('')
    setError(null)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    enrollMutation.mutate()
  }

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Khóa học</h1>
      <p className="mb-6 text-sm text-slate-500">
        Mọi khóa học đều hiện giới thiệu công khai — nhập mã do trung tâm cung cấp để vào học.
      </p>

      {coursesQuery.isLoading && <p className="text-slate-400">Đang tải...</p>}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {coursesQuery.data?.map((course) => (
          <div key={course.id} className="flex flex-col rounded-lg border border-slate-200 bg-white p-5">
            <div className="text-xs font-medium uppercase tracking-wide text-slate-400">{course.subjectName}</div>
            <h2 className="mt-1 text-lg font-semibold text-slate-900">{course.title}</h2>
            {course.description && <p className="mt-2 flex-1 text-sm text-slate-500">{course.description}</p>}
            <div className="mt-4">
              {course.enrolled ? (
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
        ))}
        {coursesQuery.data?.length === 0 && <p className="text-slate-400">Chưa có khóa học nào được công khai.</p>}
      </div>

      <Modal title={`Nhập mã khóa học: ${enrolling?.title ?? ''}`} open={enrolling !== null} onClose={() => setEnrolling(null)}>
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
      </Modal>
    </div>
  )
}
