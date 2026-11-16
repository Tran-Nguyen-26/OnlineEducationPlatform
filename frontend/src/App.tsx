import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { VerifyCertificatePage } from './pages/VerifyCertificatePage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { AdminLayout } from './layouts/AdminLayout'
import { DashboardPage } from './pages/admin/DashboardPage'
import { SubjectsPage } from './pages/admin/SubjectsPage'
import { CoursesPage } from './pages/admin/CoursesPage'
import { CourseDetailPage } from './pages/admin/CourseDetailPage'
import { TeachersPage } from './pages/admin/TeachersPage'
import { UsersPage } from './pages/admin/UsersPage'
import { AuditLogPage } from './pages/admin/AuditLogPage'
import { FinancePage } from './pages/admin/FinancePage'
import { ReportsPage } from './pages/admin/ReportsPage'
import { BroadcastPage } from './pages/admin/BroadcastPage'
import { UserDetailPage } from './pages/admin/UserDetailPage'
import { StudentLayout } from './layouts/StudentLayout'
import { CataloguePage } from './pages/student/CataloguePage'
import { CourseViewerPage } from './pages/student/CourseViewerPage'
import { MyProgressPage } from './pages/student/MyProgressPage'
import { CertificatePage } from './pages/student/CertificatePage'
import { DashboardPage as StudentDashboardPage } from './pages/student/DashboardPage'
import { TasksPage } from './pages/student/TasksPage'
import { GradesPage } from './pages/student/GradesPage'
import { CertificatesPage } from './pages/student/CertificatesPage'
import { TuitionPage } from './pages/student/TuitionPage'
import { NotificationsPage } from './pages/shared/NotificationsPage'
import { ProfilePage } from './pages/shared/ProfilePage'
import { TeacherLayout } from './layouts/TeacherLayout'
import { MyCoursesPage } from './pages/teacher/MyCoursesPage'
import { LandingPage } from './pages/landing/LandingPage'
import { TeacherDashboardPage } from './pages/teacher/DashboardPage'
import { PendingGradingPage } from './pages/teacher/PendingGradingPage'
import { CourseViewPage } from './pages/teacher/CourseViewPage'

function RoleHome() {
  const { user, loading } = useAuth()
  if (loading) return null
  if (!user) return <Navigate to="/login" replace />
  if (user.role === 'ADMIN') return <Navigate to="/admin" replace />
  if (user.role === 'STUDENT') return <Navigate to="/student" replace />
  if (user.role === 'INSTRUCTOR') return <Navigate to="/teacher" replace />
  return <Navigate to="/login" replace />
}

function HomeRoute() {
  const { user, loading } = useAuth()
  if (loading) return null
  return user ? <RoleHome /> : <LandingPage />
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-certificate" element={<VerifyCertificatePage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="subjects" element={<SubjectsPage />} />
          <Route path="courses" element={<CoursesPage />} />
          <Route path="courses/:courseId" element={<CourseDetailPage />} />
          <Route path="teachers" element={<TeachersPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="users/:userId" element={<UserDetailPage />} />
          <Route path="finance" element={<FinancePage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="broadcast" element={<BroadcastPage />} />
          <Route path="audit-logs" element={<AuditLogPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['STUDENT']} />}>
        <Route path="/student/courses/:courseId" element={<CourseViewerPage />} />
        <Route path="/student" element={<StudentLayout />}>
          <Route index element={<StudentDashboardPage />} />
          <Route path="my-courses" element={<MyProgressPage />} />
          <Route path="progress" element={<Navigate to="/student/my-courses" replace />} />
          <Route path="explore" element={<CataloguePage />} />
          <Route path="tasks" element={<TasksPage />} />
          <Route path="grades" element={<GradesPage />} />
          <Route path="certificates" element={<CertificatesPage />} />
          <Route path="certificates/:enrollmentId" element={<CertificatePage />} />
          <Route path="tuition" element={<TuitionPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['INSTRUCTOR']} />}>
        <Route path="/teacher" element={<TeacherLayout />}>
          <Route index element={<TeacherDashboardPage />} />
          <Route path="courses" element={<MyCoursesPage />} />
          <Route path="courses/:courseId" element={<CourseViewPage />} />
          <Route path="grading" element={<PendingGradingPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="/" element={<HomeRoute />} />
      <Route path="*" element={<RoleHome />} />
    </Routes>
  )
}

export default App
