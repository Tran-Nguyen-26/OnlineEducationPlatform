import { useQueries, useQuery } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import type { MyProgressResponse, StudentAssignmentResponse, StudentQuizResponse } from '../../types/api'

export type TaskStatus = 'TODO' | 'DONE' | 'GRADED' | 'OVERDUE'

export interface CourseTask {
  key: string
  kind: 'QUIZ' | 'ASSIGNMENT'
  courseId: string
  courseTitle: string
  title: string
  deadline: string | null
  status: TaskStatus
  score: number | null
  maxScore: number | null
}

/** Quizzes/assignments live in assessment-service per course, so aggregate across the student's ACTIVE courses client-side. */
export function useCourseTasks() {
  const progressQuery = useQuery({
    queryKey: ['student', 'progress'],
    queryFn: () => apiClient.get<MyProgressResponse[]>('/api/enrollments/progress').then((r) => r.data),
  })
  const activeCourses = (progressQuery.data ?? []).filter((p) => p.status === 'ACTIVE')

  const quizQueries = useQueries({
    queries: activeCourses.map((c) => ({
      queryKey: ['student', 'quizzes', c.courseId],
      queryFn: () => apiClient.get<StudentQuizResponse[]>('/api/quizzes', { params: { courseId: c.courseId } }).then((r) => r.data),
    })),
  })
  const assignmentQueries = useQueries({
    queries: activeCourses.map((c) => ({
      queryKey: ['student', 'assignments', c.courseId],
      queryFn: () =>
        apiClient.get<StudentAssignmentResponse[]>('/api/assignments', { params: { courseId: c.courseId } }).then((r) => r.data),
    })),
  })

  const now = Date.now()
  const tasks: CourseTask[] = []
  activeCourses.forEach((course, i) => {
    for (const q of quizQueries[i]?.data ?? []) {
      const expired = q.deadline != null && new Date(q.deadline).getTime() < now
      tasks.push({
        key: `quiz-${q.id}`,
        kind: 'QUIZ',
        courseId: course.courseId,
        courseTitle: course.courseTitle,
        title: q.title,
        deadline: q.deadline,
        status: q.attemptsUsed > 0 ? 'DONE' : expired || q.locked ? 'OVERDUE' : 'TODO',
        score: q.bestScore,
        maxScore: null,
      })
    }
    for (const a of assignmentQueries[i]?.data ?? []) {
      const expired = a.deadline != null && new Date(a.deadline).getTime() < now
      tasks.push({
        key: `assignment-${a.id}`,
        kind: 'ASSIGNMENT',
        courseId: course.courseId,
        courseTitle: course.courseTitle,
        title: a.title,
        deadline: a.deadline,
        status: a.graded ? 'GRADED' : a.submissionCount > 0 ? 'DONE' : expired && !a.canSubmit ? 'OVERDUE' : 'TODO',
        score: a.score,
        maxScore: a.maxScore,
      })
    }
  })

  const loading = progressQuery.isLoading || quizQueries.some((q) => q.isLoading) || assignmentQueries.some((q) => q.isLoading)
  return { tasks, progress: progressQuery.data ?? [], loading }
}

export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: 'Chưa làm',
  DONE: 'Đã nộp / đã làm',
  GRADED: 'Đã chấm',
  OVERDUE: 'Quá hạn / đã khóa',
}
