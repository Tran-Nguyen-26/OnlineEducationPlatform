// oxlint-disable react/only-export-components -- router config file legitimately re-exports lazy components
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from '@/layouts/RootLayout'
import { AuthLayout } from '@/layouts/AuthLayout'

// Lazy-loaded feature pages
import { lazy, Suspense } from 'react'

const LoginPage = lazy(() => import('@/features/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'))
const CoursesPage = lazy(() => import('@/features/courses/CoursesPage'))
const CourseDetailPage = lazy(() => import('@/features/courses/CourseDetailPage'))
const EnrollmentPage = lazy(() => import('@/features/enrollment/EnrollmentPage'))
const AssessmentPage = lazy(() => import('@/features/assessment/AssessmentPage'))
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'))

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
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: withSuspense(DashboardPage) },
      { path: 'courses', element: withSuspense(CoursesPage) },
      { path: 'courses/:id', element: withSuspense(CourseDetailPage) },
      { path: 'enrollment', element: withSuspense(EnrollmentPage) },
      { path: 'assessment', element: withSuspense(AssessmentPage) },
    ],
  },
  {
    path: '/',
    element: <AuthLayout />,
    children: [
      { path: 'login', element: withSuspense(LoginPage) },
      { path: 'register', element: withSuspense(RegisterPage) },
    ],
  },
])
