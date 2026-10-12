import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>()

  const { data: course, isLoading } = useQuery({
    queryKey: ['courses', id],
    queryFn: async () => {
      // TODO: apiClient.GET('/courses/{id}', { params: { path: { id: id! } } })
      await new Promise((resolve) => setTimeout(resolve, 300))
      return {
        id,
        title: 'Chi tiết khóa học',
        description: 'Nội dung chi tiết khóa học sẽ được tải từ API.',
        instructor: 'Giảng viên',
        lessons: 24,
        duration: '12 giờ',
      }
    },
    enabled: !!id,
  })

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-8 w-1/2 rounded bg-gray-200" />
        <div className="h-48 rounded-xl bg-gray-200" />
      </div>
    )
  }

  return (
    <div>
      <Link
        to="/courses"
        className="mb-6 flex items-center gap-2 text-sm text-blue-600 hover:underline"
      >
        ← Quay lại danh sách
      </Link>
      <h1 className="mb-2 text-2xl font-bold text-gray-900">{course?.title}</h1>
      <p className="mb-6 text-gray-500">{course?.description}</p>
      <div className="grid grid-cols-3 gap-4">
        <div className="rounded-xl bg-blue-50 p-4 text-center">
          <div className="text-2xl font-bold text-blue-600">{course?.lessons}</div>
          <div className="text-sm text-gray-500">Bài học</div>
        </div>
        <div className="rounded-xl bg-green-50 p-4 text-center">
          <div className="text-2xl font-bold text-green-600">{course?.duration}</div>
          <div className="text-sm text-gray-500">Thời lượng</div>
        </div>
        <div className="rounded-xl bg-purple-50 p-4 text-center">
          <div className="text-2xl font-bold text-purple-600">🎓</div>
          <div className="text-sm text-gray-500">{course?.instructor}</div>
        </div>
      </div>
    </div>
  )
}
