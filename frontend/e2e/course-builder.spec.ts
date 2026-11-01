import { expect, test } from '@playwright/test'

test('create a course, add a lesson, upload a PDF, and publish it', async ({ page }) => {
  const course = {
    id: 'course-e2e', title: 'Vật lý cơ bản', description: '', visibility: 'PUBLIC',
    lifecycle_status: 'DRAFT', capacity: null, learning_mode: 'FREE_NAVIGATION',
    start_at: null, end_at: null, created_by: 'instructor-1', created_at: '', updated_at: '',
  }
  const module = { id: 'module-e2e', course_id: course.id, title: 'Chương 1', position: 0, is_default: true }
  const lesson = { id: 'lesson-e2e', module_id: module.id, title: 'Bài 1', position: 0, is_required: true }
  const content = {
    id: 'content-e2e', lesson_id: lesson.id, content_type: 'PDF', title: 'Bài giảng PDF',
    text_content: null, external_url: null, file_name: 'bai-giang.pdf', mime_type: 'application/pdf',
    file_size: 1024, duration_seconds: null, upload_status: 'PENDING', position: 0,
  }
  const empty = { items: [], pagination: { page: 1, page_size: 100, total: 0, has_next: false } }
  const pageOf = (items: unknown[]) => ({ items, pagination: { page: 1, page_size: 100, total: items.length, has_next: false } })
  let courseCreated = false
  let lessonCreated = false
  let contentAvailable = false

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    if (path === '/my/courses' && request.method() === 'GET') {
      return route.fulfill({ json: pageOf(courseCreated ? [course] : []) })
    }
    if (path === '/courses' && request.method() === 'POST') {
      courseCreated = true
      return route.fulfill({ status: 201, json: course })
    }
    if (path === `/courses/${course.id}/modules` && request.method() === 'GET') return route.fulfill({ json: pageOf([module]) })
    if (path === `/courses/${course.id}/modules/${module.id}/lessons` && request.method() === 'GET') {
      return route.fulfill({ json: pageOf(lessonCreated ? [lesson] : []) })
    }
    if (path === `/courses/${course.id}/modules/${module.id}/lessons` && request.method() === 'POST') {
      lessonCreated = true
      return route.fulfill({ status: 201, json: lesson })
    }
    if (path === `/courses/${course.id}/modules/${module.id}/lessons/${lesson.id}/contents` && request.method() === 'GET') {
      return route.fulfill({ json: pageOf(contentAvailable ? [{ ...content, upload_status: 'AVAILABLE' }] : (contentAvailable ? [content] : [])) })
    }
    if (path === `/courses/${course.id}/modules/${module.id}/lessons/${lesson.id}/contents` && request.method() === 'POST') {
      return route.fulfill({ status: 201, json: content })
    }
    if (path === `/contents/${content.id}/upload-url` && request.method() === 'POST') {
      return route.fulfill({ json: { upload_url: 'http://storage.test/upload', method: 'PUT', required_headers: { 'Content-Type': 'application/pdf' }, expires_at: new Date(Date.now() + 900_000).toISOString() } })
    }
    if (path === `/contents/${content.id}/complete-upload` && request.method() === 'POST') {
      contentAvailable = true
      return route.fulfill({ json: { ...content, upload_status: 'AVAILABLE' } })
    }
    if (path === `/courses/${course.id}/publish` && request.method() === 'POST') {
      return route.fulfill({ json: { ...course, lifecycle_status: 'PUBLISHED' } })
    }
    return route.fulfill({ status: 404, json: { error: { message: `Unexpected API call: ${request.method()} ${path}` } } })
  })
  await page.route('http://storage.test/upload', (route) => route.fulfill({ status: 200 }))

  await page.goto('/instructor/courses')
  await page.getByRole('button', { name: 'Tạo khóa học' }).click()
  await page.getByLabel('Tên khóa học').fill('Vật lý cơ bản')
  await page.getByRole('button', { name: 'Tạo nháp' }).click()
  await expect(page).toHaveURL(new RegExp(`/instructor/courses/${course.id}/builder$`))

  await page.getByRole('button', { name: /thêm bài/i }).click()
  await page.getByRole('link', { name: /soạn nội dung bài 1/i }).click()
  await page.getByRole('link', { name: /quản lý upload/i }).click()
  await page.getByLabel('Chọn tệp tải lên').setInputFiles({ name: 'bai-giang.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') })
  await page.getByRole('button', { name: 'Bắt đầu tải lên' }).click()
  await expect(page.getByText('Tải lên và xác minh thành công')).toBeVisible()
  await page.getByRole('link', { name: /soạn nội dung/i }).click()
  await page.getByRole('link', { name: /course builder/i }).click()
  await page.getByRole('link', { name: /xuất bản/i }).click()
  await page.getByRole('button', { name: 'Xuất bản khóa học' }).click()
  await expect(page.getByText('Khóa học đã được xuất bản.')).toBeVisible()
})
