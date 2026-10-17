import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

interface Quiz {
  id: string
  title: string
  courseTitle: string
  dueDate: string
  status: 'pending' | 'submitted' | 'graded'
  score?: number
}

const mockQuizzes: Quiz[] = [
  {
    id: '1',
    title: 'Kiểm tra giữa kỳ - React Hooks',
    courseTitle: 'Lập trình React',
    dueDate: '2024-03-15',
    status: 'graded',
    score: 85,
  },
  {
    id: '2',
    title: 'Bài kiểm tra REST API',
    courseTitle: 'Spring Boot',
    dueDate: '2024-03-20',
    status: 'pending',
  },
  {
    id: '3',
    title: 'Quiz Docker Networking',
    courseTitle: 'DevOps',
    dueDate: '2024-03-25',
    status: 'submitted',
  },
]

const statusConfig = {
  pending: { label: 'Chưa làm', class: 'bg-orange-100 text-orange-700' },
  submitted: { label: 'Đã nộp', class: 'bg-blue-100 text-blue-700' },
  graded: { label: 'Đã chấm', class: 'bg-green-100 text-green-700' },
}

export default function AssessmentPage() {
  const [activeTab, setActiveTab] = useState<Quiz['status'] | 'all'>('all')

  const { data: quizzes, isLoading } = useQuery({
    queryKey: ['quizzes'],
    queryFn: async () => {
      // TODO: apiClient.GET('/assessment/quizzes/me')
      await new Promise((resolve) => setTimeout(resolve, 300))
      return mockQuizzes
    },
  })

  const filtered = activeTab === 'all' ? quizzes : quizzes?.filter((q) => q.status === activeTab)

  const tabs: { key: Quiz['status'] | 'all'; label: string }[] = [
    { key: 'all', label: 'Tất cả' },
    { key: 'pending', label: 'Chưa làm' },
    { key: 'submitted', label: 'Đã nộp' },
    { key: 'graded', label: 'Đã chấm' },
  ]

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Kiểm tra & Bài tập</h1>
        <p className="mt-1 text-sm text-gray-500">Quản lý bài kiểm tra và theo dõi kết quả</p>
      </div>

      {/* Tabs */}
      <div className="mb-6 flex gap-1 rounded-xl bg-gray-100 p-1">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex-1 rounded-lg py-2 text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded-xl bg-gray-200" />
          ))}
        </div>
      ) : (
        <div className="space-y-3">
          {filtered?.map((quiz) => {
            const statusCfg = statusConfig[quiz.status]
            return (
              <div
                key={quiz.id}
                className="flex items-center justify-between rounded-xl bg-white p-4 shadow-sm"
              >
                <div>
                  <h2 className="font-semibold text-gray-900">{quiz.title}</h2>
                  <p className="text-sm text-gray-500">{quiz.courseTitle}</p>
                  <p className="mt-1 text-xs text-gray-400">
                    Hạn: {new Date(quiz.dueDate).toLocaleDateString('vi-VN')}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  {quiz.score !== undefined && (
                    <span className="text-lg font-bold text-green-600">{quiz.score}/100</span>
                  )}
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusCfg.class}`}
                  >
                    {statusCfg.label}
                  </span>
                  {quiz.status === 'pending' && (
                    <button className="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700">
                      Làm bài
                    </button>
                  )}
                </div>
              </div>
            )
          })}
          {filtered?.length === 0 && (
            <p className="py-12 text-center text-gray-400">Không có bài kiểm tra nào.</p>
          )}
        </div>
      )}
    </div>
  )
}
