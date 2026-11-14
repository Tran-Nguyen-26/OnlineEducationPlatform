export interface CourseIssue {
  id: string
  courseId: string
  courseTitle: string
  instructorEmail: string
  category: 'CONTENT' | 'RUBRIC'
  detail: string
  status: 'OPEN' | 'RESOLVED' | 'REJECTED'
  resolution: string | null
  createdAt: string
  updatedAt: string
}

const auth = () => {
  const token = localStorage.getItem('access_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function fetchCourseIssues(status: string): Promise<CourseIssue[]> {
  const response = await fetch(`/api/admin/course-issues?status=${encodeURIComponent(status)}`, { headers: auth() })
  const data = await response.json().catch(() => null) as (CourseIssue[] & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Không tải được hàng đợi báo lỗi.')
  return data as CourseIssue[]
}

export async function decideCourseIssue(id: string, decision: 'RESOLVED' | 'REJECTED', resolution: string): Promise<CourseIssue> {
  const response = await fetch(`/api/admin/course-issues/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...auth() },
    body: JSON.stringify({ decision, resolution }),
  })
  const data = await response.json().catch(() => null) as (CourseIssue & { message?: string }) | null
  if (!response.ok) throw new Error(data?.message || 'Không cập nhật được báo lỗi.')
  return data as CourseIssue
}
