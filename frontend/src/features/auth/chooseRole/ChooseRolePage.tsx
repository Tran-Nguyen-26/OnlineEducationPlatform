import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import './choose-role.css'

export default function ChooseRolePage() {
  const navigate = useNavigate()
  const [selectedRole, setSelectedRole] = useState<'STUDENT' | 'TEACHER'>('STUDENT')

  const handleContinue = () => {
    if (selectedRole === 'STUDENT') {
      navigate('/register')
    } else {
      navigate('/register-instructor')
    }
  }

  return (
    <div className="cr-page">
      {/* ── Logo EduPlatform ── */}
      <Link to="/" className="cr-logo">
        <div className="cr-logo-box">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="cr-cap-icon">
            <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
            <path d="M6 12v5c3 3 9 3 12 0v-5" />
          </svg>
        </div>
        <span className="cr-logo-name">EDUPLATFORM</span>
      </Link>

      {/* ── Header ── */}
      <div className="cr-header">
        <h1 className="cr-title">BẠN SỬ DỤNG EDUPLATFORM VỚI VAI TRÒ NÀO?</h1>
        <p className="cr-subtitle">
          Chọn vai trò chính để thiết lập không gian học tập và bộ công cụ giảng dạy phù hợp với bạn.
        </p>
      </div>

      {/* ── 2 Role Cards Grid ── */}
      <div className="cr-cards">
        {/* Student Card */}
        <div
          className={`cr-card ${selectedRole === 'STUDENT' ? 'cr-card--selected' : ''}`}
          onClick={() => setSelectedRole('STUDENT')}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSelectedRole('STUDENT')
            }
          }}
          aria-pressed={selectedRole === 'STUDENT'}
        >
          {/* Top row with icon & radio check */}
          <div className="cr-card-top-row">
            <div className="cr-role-icon cr-role-icon--student">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 3L1 9l11 6 9-4.91V17h2V9L12 3zM3.45 9L12 4.34 20.55 9 12 13.66 3.45 9zM5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82z" />
              </svg>
            </div>
            <div className={`cr-radio-check ${selectedRole === 'STUDENT' ? 'cr-radio-check--active' : ''}`}>
              {selectedRole === 'STUDENT' && (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </div>
          </div>

          <span className="cr-tier-badge">DÀNH CHO HỌC VIÊN</span>
          <h2 className="cr-card-title">TÔI LÀ HỌC VIÊN</h2>
          <p className="cr-card-desc">
            Tham gia lộ trình chuẩn hóa, thực hành tương tác và xây dựng hồ sơ năng lực được chứng nhận.
          </p>

          {/* Subcard with features & watermark */}
          <div className="cr-subcard">
            <ul className="cr-subcard-features">
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                  </svg>
                </span>
                <span>Truy cập hơn 3.400 khóa học chuẩn hóa</span>
              </li>
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="3" y="11" width="18" height="10" rx="2" />
                    <circle cx="12" cy="5" r="2" />
                    <path d="M12 7v4" />
                    <line x1="8" y1="16" x2="8" y2="16" />
                    <line x1="16" y1="16" x2="16" y2="16" />
                  </svg>
                </span>
                <span>Bài kiểm tra tương thích & Trợ lý học tập AI</span>
              </li>
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  </svg>
                </span>
                <span>Chứng chỉ số chống làm giả</span>
              </li>
            </ul>
            <div className="cr-subcard-watermark">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
              </svg>
            </div>
          </div>
        </div>

        {/* Instructor Card */}
        <div
          className={`cr-card ${selectedRole === 'TEACHER' ? 'cr-card--selected' : ''}`}
          onClick={() => setSelectedRole('TEACHER')}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSelectedRole('TEACHER')
            }
          }}
          aria-pressed={selectedRole === 'TEACHER'}
        >
          {/* Top row with icon & radio check */}
          <div className="cr-card-top-row">
            <div className="cr-role-icon cr-role-icon--teacher">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58v.23z" />
              </svg>
            </div>
            <div className={`cr-radio-check ${selectedRole === 'TEACHER' ? 'cr-radio-check--active' : ''}`}>
              {selectedRole === 'TEACHER' && (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </div>
          </div>

          <span className="cr-tier-badge">DÀNH CHO GIẢNG VIÊN</span>
          <h2 className="cr-card-title">TÔI LÀ GIẢNG VIÊN</h2>
          <p className="cr-card-desc">
            Xây dựng giáo trình linh hoạt, quản lý lớp học và cấp chứng chỉ chuẩn mực toàn cầu.
          </p>

          {/* Subcard with features & watermark */}
          <div className="cr-subcard">
            <ul className="cr-subcard-features">
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polygon points="5 3 19 12 5 21 5 3" />
                  </svg>
                </span>
                <span>Bộ công cụ xây dựng bài giảng & đa phương tiện</span>
              </li>
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M12 2v20M2 12h20M4.93 4.93l14.14 14.14M19.07 4.93L4.93 19.07" />
                  </svg>
                </span>
                <span>Tiêu chí chấm tự động & chấm bài hàng loạt</span>
              </li>
              <li className="cr-subcard-item">
                <span className="cr-feature-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="20" x2="18" y2="10" />
                    <line x1="12" y1="20" x2="12" y2="4" />
                    <line x1="6" y1="20" x2="6" y2="14" />
                  </svg>
                </span>
                <span>Báo cáo tiến độ học viên theo thời gian thực</span>
              </li>
            </ul>
            <div className="cr-subcard-watermark">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      {/* ── Primary Action Button ── */}
      <button
        id="cr-continue-btn"
        type="button"
        className="cr-cta-btn"
        onClick={handleContinue}
      >
        <span>
          {selectedRole === 'STUDENT'
            ? 'TIẾP TỤC VỚI VAI TRÒ HỌC VIÊN'
            : 'TIẾP TỤC VỚI VAI TRÒ GIẢNG VIÊN'}
        </span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="cr-arrow-icon">
          <line x1="5" y1="12" x2="19" y2="12" />
          <polyline points="12 5 19 12 12 19" />
        </svg>
      </button>

      {/* ── Sign In Link ── */}
      <p className="cr-signin-row">
        Đã có tài khoản?{' '}
        <Link to="/login" className="cr-signin-link">
          Đăng nhập ngay
        </Link>
      </p>

      {/* ── Compliance & Certifications ── */}
      <div className="cr-certifications">
        <div className="cr-cert-item">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <polyline points="9 12 11 14 15 10" />
          </svg>
          <span>Xác thực SSO Học thuật</span>
        </div>
        <span className="cr-cert-dot">·</span>
        <div className="cr-cert-item">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
          <span>Chứng nhận ISO-IEC 27001</span>
        </div>
        <span className="cr-cert-dot">·</span>
        <div className="cr-cert-item">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
          <span>Tuân thủ bảo mật dữ liệu giáo dục (FERPA)</span>
        </div>
      </div>

      {/* ── Footer ── */}
      <footer className="cr-footer">
        <span>&copy; {new Date().getFullYear()} EduPlatform Inc. All rights reserved.</span>
      </footer>
    </div>
  )
}
