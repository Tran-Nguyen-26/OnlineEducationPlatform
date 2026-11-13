// oxlint-disable react/only-export-components -- router config file legitimately re-exports lazy components
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from '@/layouts/RootLayout'
import { RegisterLayout } from '@/layouts/RegisterLayout'
import { AdminLayout } from '@/layouts/AdminLayout'

// Lazy-loaded feature pages
import { lazy, Suspense } from 'react'

const LoginPage = lazy(() => import('@/features/auth/loginPage/LoginPage.tsx'))
const RegisterPage = lazy(() => import('@/features/auth/registerStudent/RegisterStudentPage'))
const ForgotPasswordPage = lazy(
  () => import('@/features/auth/forgotPassword/ForgotPasswordPage.tsx')
)
const CourseDetailPage = lazy(() => import('@/features/courses/CourseDetailPage'))
const EnrollmentPage = lazy(() => import('@/features/enrollment/EnrollmentPage'))
const AssessmentPage = lazy(() => import('@/features/assessment/AssessmentPage'))
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'))
const AdminUsersPage = lazy(() => import('@/features/admin/users/AdminUsersPage'))
const InstructorApplicationsPage = lazy(() => import('@/features/admin/instructorApplications/InstructorApplicationsPage'))
const AdminDashboardPage = lazy(() => import('@/features/admin/AdminPlaceholders').then(m => ({ default: m.AdminDashboardPage })))
const AdminCoursesPage = lazy(() => import('@/features/admin/AdminCoursesPage'))
const AdminQuizzesPage = lazy(() => import('@/features/admin/quizzes/AdminQuizzesPage'))
const AccessCodesPage = lazy(() => import('@/features/admin/AccessCodesPage'))
const AdminSettingsPage = lazy(() => import('@/features/admin/AdminPlaceholders').then(m => ({ default: m.AdminSettingsPage })))

const PageLoader = () => (
  <div className="flex min-h-screen items-center justify-center">
    <div className="border-primary h-8 w-8 animate-spin rounded-full border-4 border-t-transparent" />
  </div>
)

const withSuspense = (Component: React.ComponentType) => (
  <Suspense fallback={<PageLoader />}>
    <Component />
  </Suspense>
)

export const router = createBrowserRouter([
  { path: '/verify/:id', element: withSuspense(lazy(() => import('@/features/courses/VerifyCredentialPage'))) },
  {
    path: '/',
    element: <Navigate to="/login" replace />,
  },
  {
    element: <RootLayout />,
    children: [
      { path: 'dashboard', element: withSuspense(DashboardPage) },
      { path: 'dashboard/analytics', element: withSuspense(lazy(() => import('@/features/dashboard/CourseAnalyticsPage'))) },
      { path: 'courses', element: <Navigate to="/enrollment" replace /> },
      { path: 'courses/:id', element: withSuspense(CourseDetailPage) },
      { path: 'enrollment', element: withSuspense(EnrollmentPage) },
      { path: 'assessment', element: withSuspense(AssessmentPage) },
      { path: 'assessment/grading', element: withSuspense(lazy(() => import('@/features/assessment/BlindGradingPage'))) },
    ],
  },
  {
    // LoginPage is self-contained (full-page layout) – no AuthLayout wrapper needed
    path: '/login',
    element: withSuspense(LoginPage),
  },
  {
    path: '/choose-role',
    element: <Navigate to="/register" replace />,
  },
  {
    // ForgotPasswordPage is self-contained (full-page layout)
    path: '/forgot-password',
    element: withSuspense(ForgotPasswordPage),
  },
  {
    path: '/',
    element: <RegisterLayout />,
    children: [{ path: 'register', element: withSuspense(RegisterPage) }],
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate to="/admin/users" replace /> },
      { path: 'users', element: withSuspense(AdminUsersPage) },
      { path: 'instructor-applications', element: withSuspense(InstructorApplicationsPage) },
      { path: 'dashboard', element: withSuspense(AdminDashboardPage) },
      { path: 'courses', element: withSuspense(AdminCoursesPage) },
      { path: 'quizzes', element: withSuspense(AdminQuizzesPage) },
      { path: 'access-codes', element: withSuspense(AccessCodesPage) },
      { path: 'settings', element: withSuspense(AdminSettingsPage) },
    ],
  },
  {
    path: '/register-instructor',
    element: <Navigate to="/login" replace />,
  },
])
