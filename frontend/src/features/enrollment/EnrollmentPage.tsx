import { useQuery } from '@tanstack/react-query'

interface Enrollment {
  id: string
  courseTitle: string
  progress: number
  status: 'active' | 'completed' | 'paused'
  enrolledAt: string
}

const mockEnrollments: Enrollment[] = [
  {
    id: '1',
    courseTitle: 'Lập trình React từ cơ bản đến nâng cao',
    progress: 65,
    status: 'active',
    enrolledAt: '2024-01-15',
  },
  {
    id: '2',
    courseTitle: 'Spring Boot & Microservices',
    progress: 100,
    status: 'completed',
    enrolledAt: '2024-01-01',
  },
  {
    id: '3',
    courseTitle: 'DevOps với Docker & Kubernetes',
    progress: 20,
    status: 'paused',
    enrolledAt: '2024-02-10',
  },
]

const statusConfig = {
  active: { label: 'Đang học', class: 'bg-blue-100 text-blue-700' },
  completed: { label: 'Hoàn thành', class: 'bg-green-100 text-green-700' },
  paused: { label: 'Tạm dừng', class: 'bg-gray-100 text-gray-700' },
}

export default function EnrollmentPage() {
  const { data: enrollments, isLoading } = useQuery({
    queryKey: ['enrollments'],
    queryFn: async () => {
      // TODO: apiClient.GET('/enrollments/me')
      await new Promise((resolve) => setTimeout(resolve, 300))
      return mockEnrollments
    },
  })

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Khóa học của tôi</h1>
        <p className="mt-1 text-sm text-gray-500">Theo dõi tiến độ học tập của bạn</p>
      </div>

      {isLoading ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-200" />
          ))}
        </div>
      ) : (
        <div className="space-y-4">
          {enrollments?.map((enrollment) => {
            const statusCfg = statusConfig[enrollment.status]
            return (
              <div key={enrollment.id} className="rounded-xl bg-white p-5 shadow-sm">
                <div className="mb-3 flex items-start justify-between">
                  <h2 className="font-semibold text-gray-900">{enrollment.courseTitle}</h2>
                  <span
                    className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusCfg.class}`}
                  >
                    {statusCfg.label}
                  </span>
                </div>
                <div className="mb-1 flex justify-between text-sm text-gray-500">
                  <span>Tiến độ</span>
                  <span className="font-medium text-gray-900">{enrollment.progress}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                  <div
                    className="h-full rounded-full bg-blue-600 transition-all"
                    style={{ width: `${enrollment.progress}%` }}
                  />
                </div>
                <p className="mt-2 text-xs text-gray-400">
                  Đăng ký: {new Date(enrollment.enrolledAt).toLocaleDateString('vi-VN')}
                </p>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
