import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { AdminLayout } from './layouts/AdminLayout'
import { DashboardPage } from './pages/admin/DashboardPage'
import { SubjectsPage } from './pages/admin/SubjectsPage'
import { CoursesPage } from './pages/admin/CoursesPage'
import { CourseDetailPage } from './pages/admin/CourseDetailPage'
import { TeachersPage } from './pages/admin/TeachersPage'
import { UsersPage } from './pages/admin/UsersPage'
import { AuditLogPage } from './pages/admin/AuditLogPage'
import { StudentLayout } from './layouts/StudentLayout'
import { CataloguePage } from './pages/student/CataloguePage'
import { CourseViewerPage } from './pages/student/CourseViewerPage'
import { MyProgressPage } from './pages/student/MyProgressPage'
import { TeacherLayout } from './layouts/TeacherLayout'
import { MyCoursesPage } from './pages/teacher/MyCoursesPage'
import { CourseViewPage } from './pages/teacher/CourseViewPage'

function RoleHome() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role === 'ADMIN') return <Navigate to="/admin" replace />
  if (user.role === 'STUDENT') return <Navigate to="/student" replace />
  if (user.role === 'INSTRUCTOR') return <Navigate to="/teacher" replace />
  return <Navigate to="/login" replace />
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

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
          <Route path="audit-logs" element={<AuditLogPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['STUDENT']} />}>
        <Route path="/student" element={<StudentLayout />}>
          <Route index element={<CataloguePage />} />
          <Route path="progress" element={<MyProgressPage />} />
          <Route path="courses/:courseId" element={<CourseViewerPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['INSTRUCTOR']} />}>
        <Route path="/teacher" element={<TeacherLayout />}>
          <Route index element={<MyCoursesPage />} />
          <Route path="courses/:courseId" element={<CourseViewPage />} />
        </Route>
      </Route>

      <Route path="/" element={<RoleHome />} />
      <Route path="*" element={<RoleHome />} />
    </Routes>
  )
}

export default App
