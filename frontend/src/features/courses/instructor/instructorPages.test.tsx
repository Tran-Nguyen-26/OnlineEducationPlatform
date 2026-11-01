import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import InstructorCoursesPage from './InstructorCoursesPage'
import CourseBuilderPage from './CourseBuilderPage'
import LessonContentPage from './LessonContentPage'
import UploadManagerPage from './UploadManagerPage'
import PublishCoursePage from './PublishCoursePage'

const mocks = vi.hoisted(() => ({
  list: vi.fn(), create: vi.fn(), update: vi.fn(), modules: vi.fn(), createModule: vi.fn(),
  reorderModules: vi.fn(), lessons: vi.fn(), createLesson: vi.fn(), reorderLessons: vi.fn(),
  contents: vi.fn(), createContent: vi.fn(), updateContent: vi.fn(), reorderContents: vi.fn(),
  publish: vi.fn(), uploadFile: vi.fn(),
}))

vi.mock('./courseApi', () => ({ courseApi: mocks, uploadFile: mocks.uploadFile }))

const course = {
  id: 'course-1', title: 'Sinh học', description: 'Mô tả', visibility: 'PUBLIC',
  lifecycle_status: 'DRAFT', learning_mode: 'SELF_PACED', created_at: '', updated_at: '',
}
const moduleItem = { id: 'module-1', course_id: 'course-1', title: 'Chương 1', description: null, position: 0, is_default: true }
const lesson = { id: 'lesson-1', module_id: 'module-1', title: 'Bài 1', description: null, position: 0, is_required: true }
const textContent = {
  id: 'content-1', lesson_id: 'lesson-1', content_type: 'TEXT', title: 'Ghi chú', text_content: 'Nội dung',
  external_url: null, file_name: null, mime_type: null, file_size: null, duration_seconds: null,
  upload_status: 'AVAILABLE', position: 0,
}
const page = <T,>(items: T[]) => ({ items, pagination: { page: 1, page_size: 100, total: items.length, has_next: false } })

function renderAt(path: string, element: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/instructor/courses" element={<InstructorCoursesPage />} />
    <Route path="/instructor/courses/:courseId/builder" element={<CourseBuilderPage />} />
    <Route path="/instructor/courses/:courseId/lessons/:lessonId/content" element={<LessonContentPage />} />
    <Route path="/instructor/courses/:courseId/lessons/:lessonId/upload" element={<UploadManagerPage />} />
    <Route path="/instructor/courses/:courseId/publish" element={<PublishCoursePage />} />
    <Route path="/placeholder" element={element} />
  </Routes></MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.list.mockResolvedValue(page([course]))
  mocks.modules.mockResolvedValue(page([moduleItem]))
  mocks.lessons.mockResolvedValue(page([lesson]))
  mocks.contents.mockResolvedValue(page([textContent]))
  mocks.create.mockResolvedValue(course)
  mocks.createModule.mockResolvedValue(moduleItem)
  mocks.createLesson.mockResolvedValue(lesson)
  mocks.update.mockResolvedValue(course)
  mocks.createContent.mockResolvedValue(textContent)
  mocks.updateContent.mockResolvedValue(textContent)
  mocks.reorderModules.mockResolvedValue({ success: true })
  mocks.reorderLessons.mockResolvedValue({ success: true })
  mocks.reorderContents.mockResolvedValue({ success: true })
  mocks.publish.mockResolvedValue(course)
})

describe('instructor course builder', () => {
  it('shows the instructor course list and its create action', async () => {
    renderAt('/instructor/courses', null)
    expect(await screen.findByText('Khóa học của tôi')).toBeInTheDocument()
    expect(await screen.findByText('Sinh học')).toBeInTheDocument()
  })

  it('creates a course from the list dialog', async () => {
    renderAt('/instructor/courses', null)
    fireEvent.click(await screen.findByRole('button', { name: /tạo khóa học/i }))
    fireEvent.change(screen.getByLabelText(/tên khóa học/i), { target: { value: 'Vật lý' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo nháp' }))
    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'Vật lý' })))
  })

  it('renders the course builder and module lesson structure', async () => {
    renderAt('/instructor/courses/course-1/builder', null)
    expect(await screen.findByText('Course builder')).toBeInTheDocument()
    expect(await screen.findByText('Chương 1')).toBeInTheDocument()
    expect(await screen.findByText('Bài 1')).toBeInTheDocument()
  })

  it('sends one complete lesson order after moving a lesson', async () => {
    mocks.lessons.mockResolvedValue(page([lesson, { ...lesson, id: 'lesson-2', title: 'Bài 2', position: 1 }]))
    renderAt('/instructor/courses/course-1/builder', null)
    fireEvent.click(await screen.findByRole('button', { name: 'Đưa bài Bài 2 lên' }))
    await waitFor(() => expect(mocks.reorderLessons).toHaveBeenCalledWith('course-1', 'module-1', ['lesson-2', 'lesson-1']))
  })

  it('loads lesson content and offers the upload screen', async () => {
    renderAt('/instructor/courses/course-1/lessons/lesson-1/content', null)
    expect(await screen.findByText('Soạn nội dung')).toBeInTheDocument()
    expect(await screen.findByText('Ghi chú')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /quản lý upload/i })).toHaveAttribute('href', '/instructor/courses/course-1/lessons/lesson-1/upload')
  })

  it('saves edited lesson text', async () => {
    renderAt('/instructor/courses/course-1/lessons/lesson-1/content', null)
    fireEvent.click(await screen.findByText('Ghi chú'))
    fireEvent.change(await screen.findByLabelText('Nội dung'), { target: { value: 'Bài giảng đã sửa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(mocks.updateContent).toHaveBeenCalledWith('course-1', 'module-1', 'lesson-1', 'content-1', expect.objectContaining({ text_content: 'Bài giảng đã sửa' })))
  })

  it('shows the upload manager with a file progress area', async () => {
    renderAt('/instructor/courses/course-1/lessons/lesson-1/upload', null)
    expect(await screen.findByRole('heading', { name: 'Tải tài liệu lên' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /chọn pdf/i })).toBeInTheDocument()
  })

  it('publishes after the readiness checklist passes', async () => {
    mocks.contents.mockResolvedValue(page([textContent]))
    renderAt('/instructor/courses/course-1/publish', null)
    fireEvent.click(await screen.findByRole('button', { name: 'Xuất bản khóa học' }))
    await waitFor(() => expect(mocks.publish).toHaveBeenCalledWith('course-1'))
  })
})
