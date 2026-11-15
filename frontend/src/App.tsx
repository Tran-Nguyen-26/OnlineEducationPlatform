import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { AdminLayout } from './layouts/AdminLayout'
import { DashboardPage } from './pages/admin/DashboardPage'
import { SubjectsPage } from './pages/admin/SubjectsPage'
import { CoursesPage } from './pages/admin/CoursesPage'
import { CourseDetailPage } from './pages/admin/CourseDetailPage'
import { TeachersPage } from './pages/admin/TeachersPage'
import { UsersPage } from './pages/admin/UsersPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

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
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/admin" replace />} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  )
}

export default App
