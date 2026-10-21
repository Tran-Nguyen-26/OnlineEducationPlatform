import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

interface Course {
  id: string
  title: string
  instructor: string
  thumbnail: string
  category: string
  level: 'Cơ bản' | 'Trung cấp' | 'Nâng cao'
  studentsCount: number
  rating: number
}

// Mock data — replace with apiClient.GET('/courses') when backend ready
const mockCourses: Course[] = [
  {
    id: '1',
    title: 'Lập trình React từ cơ bản đến nâng cao',
    instructor: 'Nguyễn Văn A',
    thumbnail: 'https://placehold.co/400x225/3B82F6/white?text=React',
    category: 'Frontend',
    level: 'Cơ bản',
    studentsCount: 1240,
    rating: 4.8,
  },
  {
    id: '2',
    title: 'Spring Boot & Microservices',
    instructor: 'Trần Thị B',
    thumbnail: 'https://placehold.co/400x225/10B981/white?text=Spring',
    category: 'Backend',
    level: 'Trung cấp',
    studentsCount: 890,
    rating: 4.6,
  },
  {
    id: '3',
    title: 'DevOps với Docker & Kubernetes',
    instructor: 'Lê Văn C',
    thumbnail: 'https://placehold.co/400x225/F59E0B/white?text=DevOps',
    category: 'DevOps',
    level: 'Nâng cao',
    studentsCount: 560,
    rating: 4.9,
  },
]

function useCoursesQuery() {
  return useQuery({
    queryKey: ['courses'],
    queryFn: async () => {
      // TODO: return apiClient.GET('/courses')
      await new Promise((resolve) => setTimeout(resolve, 300))
      return mockCourses
    },
  })
}

const levelColor: Record<Course['level'], string> = {
  'Cơ bản': 'bg-green-100 text-green-700',
  'Trung cấp': 'bg-yellow-100 text-yellow-700',
  'Nâng cao': 'bg-red-100 text-red-700',
}

export default function CoursesPage() {
  const { data: courses, isLoading } = useCoursesQuery()

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Khóa học</h1>
          <p className="mt-1 text-sm text-gray-500">Khám phá tất cả khóa học trên nền tảng</p>
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse rounded-xl bg-white p-4 shadow-sm">
              <div className="mb-4 h-40 rounded-lg bg-gray-200" />
              <div className="mb-2 h-4 w-3/4 rounded bg-gray-200" />
              <div className="h-3 w-1/2 rounded bg-gray-200" />
            </div>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {courses?.map((course) => (
            <Link
              key={course.id}
              to={`/courses/${course.id}`}
              className="group overflow-hidden rounded-xl bg-white shadow-sm transition-all hover:shadow-md"
            >
              <img
                src={course.thumbnail}
                alt={course.title}
                className="h-44 w-full object-cover transition-transform group-hover:scale-105"
              />
              <div className="p-4">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-medium text-blue-600">{course.category}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${levelColor[course.level]}`}
                  >
                    {course.level}
                  </span>
                </div>
                <h2 className="mb-1 line-clamp-2 font-semibold text-gray-900">{course.title}</h2>
                <p className="mb-3 text-sm text-gray-500">{course.instructor}</p>
                <div className="flex items-center justify-between text-sm text-gray-500">
                  <span>⭐ {course.rating}</span>
                  <span>{course.studentsCount.toLocaleString()} học viên</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
