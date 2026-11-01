import { Link, useNavigate } from 'react-router-dom'
import './choose-role.css'

const STUDENT_FEATURES = [
  'Khám phá và tham gia các khóa học tương tác',
  'Xem bài giảng và tài liệu học tập',
  'Làm bài kiểm tra và nộp bài tập trực tuyến',
  'Theo dõi lộ trình học cá nhân hóa',
]

const TEACHER_FEATURES = [
  'Xây dựng và phát hành khóa học tương tác',
  'Tổ chức học phần, tài liệu đa phương tiện và bài tập',
  'Tạo bài tập trắc nghiệm và tự động chấm điểm',
  'Phân tích số liệu lớp học và tương tác học viên',
]

export default function ChooseRolePage() {
  const navigate = useNavigate()

  return (
    <div className="cr-page">
      {/* Logo */}
      <Link to="/" className="cr-logo">
        <div className="cr-logo-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 14l9-5-9-5-9 5 9 5z" />
            <path d="M12 14l6.16-3.422A12.083 12.083 0 0112 21.93a12.083 12.083 0 01-6.16-11.352L12 14z" />
          </svg>
        </div>
        <span className="cr-logo-name">EduPlatform</span>
      </Link>

      {/* Header */}
      <div className="cr-header">
        <h1 className="cr-title">Bạn muốn đăng ký với vai trò?</h1>
        <p className="cr-subtitle">
          Chọn loại tài khoản phù hợp để bắt đầu hành trình cùng EduPlatform.
        </p>
      </div>

      {/* Cards */}
      <div className="cr-cards">
        {/* Student card */}
        <div className="cr-card cr-card--student" onClick={() => navigate('/register')}>
          <div className="cr-card-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
              <path d="M6 12v5c3 3 9 3 12 0v-5" />
            </svg>
          </div>
          <div className="cr-card-body">
            <div className="cr-card-title">Học Viên</div>
            <div className="cr-card-desc">
              Tham gia hàng nghìn học viên truy cập chương trình công nghệ được kiểm chứng, môi
              trường thực hành và lộ trình học cá nhân hóa.
            </div>
            <ul className="cr-card-features">
              {STUDENT_FEATURES.map((f) => (
                <li key={f} className="cr-card-feature">
                  <span className="cr-card-feature-dot">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  </span>
                  {f}
                </li>
              ))}
            </ul>
          </div>

          <button
            id="cr-btn-student"
            type="button"
            className="cr-btn cr-btn--student"
            onClick={(e) => {
              e.stopPropagation()
              navigate('/register')
            }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
              <path d="M6 12v5c3 3 9 3 12 0v-5" />
            </svg>
            Đăng ký làm Học Viên
          </button>
        </div>

        {/* Teacher card */}
        <div className="cr-card cr-card--teacher" onClick={() => navigate('/register-instructor')}>
          <div className="cr-card-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 00-3-3.87" />
              <path d="M16 3.13a4 4 0 010 7.75" />
            </svg>
          </div>
          <div className="cr-card-body">
            <div className="cr-card-title">Giảng Viên</div>
            <div className="cr-card-desc">
              Tạo khóa học, thiết kế bài kiểm tra và theo dõi hiệu suất học tập của học viên trong
              thời gian thực.
            </div>
            <ul className="cr-card-features">
              {TEACHER_FEATURES.map((f) => (
                <li key={f} className="cr-card-feature">
                  <span className="cr-card-feature-dot">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  </span>
                  {f}
                </li>
              ))}
            </ul>
          </div>

          <button
            id="cr-btn-teacher"
            type="button"
            className="cr-btn cr-btn--teacher"
            onClick={(e) => {
              e.stopPropagation()
              navigate('/register-instructor')
            }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 00-3-3.87" />
              <path d="M16 3.13a4 4 0 010 7.75" />
            </svg>
            Đăng ký làm Giảng viên
          </button>
        </div>
      </div>

      <hr className="cr-divider" />

      <p className="cr-login-link">
        Đã có tài khoản? <Link to="/login">Đăng nhập ngay</Link>
      </p>

      <footer className="cr-footer">
        <span>© 2026 EduPlatform Inc. Bảo lưu mọi quyền.</span>
        <span className="cr-footer-dot">•</span>
        <a href="#" className="cr-footer-link">
          Chính sách bảo mật
        </a>
        <span className="cr-footer-dot">•</span>
        <a href="#" className="cr-footer-link">
          Điều khoản dịch vụ
        </a>
        <span className="cr-footer-dot">•</span>
        <a href="#" className="cr-footer-link">
          Trung tâm hỗ trợ
        </a>
      </footer>
    </div>
  )
}
