import { useQuery } from '@tanstack/react-query'

interface DashboardStats {
  enrolledCourses: number
  completedCourses: number
  pendingQuizzes: number
  totalStudyHours: number
}

function useStats() {
  return useQuery<DashboardStats>({
    queryKey: ['dashboard', 'stats'],
    queryFn: async () => {
      // TODO: apiClient.GET('/dashboard/stats')
      await new Promise((resolve) => setTimeout(resolve, 300))
      return { enrolledCourses: 3, completedCourses: 1, pendingQuizzes: 2, totalStudyHours: 47 }
    },
  })
}

const statCards = (stats: DashboardStats) => [
  {
    label: 'Khóa học đang học',
    value: stats.enrolledCourses,
    icon: '📚',
    color: 'from-blue-500 to-blue-600',
  },
  {
    label: 'Khóa học hoàn thành',
    value: stats.completedCourses,
    icon: '✅',
    color: 'from-green-500 to-green-600',
  },
  {
    label: 'Bài kiểm tra chờ làm',
    value: stats.pendingQuizzes,
    icon: '📝',
    color: 'from-orange-500 to-orange-600',
  },
  {
    label: 'Giờ học tích lũy',
    value: stats.totalStudyHours,
    icon: '⏱',
    color: 'from-purple-500 to-purple-600',
  },
]

const recentActivities = [
  { icon: '📖', text: 'Hoàn thành bài "React Hooks cơ bản"', time: '2 giờ trước' },
  { icon: '✅', text: 'Nộp bài kiểm tra REST API', time: '1 ngày trước' },
  { icon: '🎓', text: 'Đăng ký khóa học DevOps', time: '3 ngày trước' },
  { icon: '⭐', text: 'Đánh giá 5 sao khóa Spring Boot', time: '5 ngày trước' },
]

export default function DashboardPage() {
  const { data: stats, isLoading } = useStats()

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Tổng quan</h1>
        <p className="mt-1 text-sm text-gray-500">Xin chào! Đây là tình hình học tập của bạn.</p>
      </div>

      {/* Stats Grid */}
      <div className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {isLoading
          ? [1, 2, 3, 4].map((i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-gray-200" />
            ))
          : stats &&
            statCards(stats).map((card, idx) => (
              <div
                key={idx}
                className={`flex flex-col justify-between rounded-xl bg-gradient-to-br p-5 text-white shadow-sm ${card.color}`}
              >
                <div className="mb-3 text-2xl">{card.icon}</div>
                <div>
                  <div className="text-3xl font-bold">{card.value}</div>
                  <div className="mt-1 text-sm opacity-90">{card.label}</div>
                </div>
              </div>
            ))}
      </div>

      {/* Recent Activity */}
      <div className="rounded-xl bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-base font-semibold text-gray-900">Hoạt động gần đây</h2>
        <ul className="divide-y divide-gray-50">
          {recentActivities.map((activity, idx) => (
            <li key={idx} className="flex items-center gap-3 py-3">
              <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-gray-100 text-lg">
                {activity.icon}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-gray-700">{activity.text}</p>
              </div>
              <span className="flex-shrink-0 text-xs text-gray-400">{activity.time}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
