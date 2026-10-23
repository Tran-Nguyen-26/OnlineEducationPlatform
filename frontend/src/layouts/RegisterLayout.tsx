import { Link, Outlet } from 'react-router-dom'
import '@/features/auth/registerStudent/register-student.css'

export function RegisterLayout() {
  return (
    <div className="register-layout">
      {/* ── Left panel ─────────────────────────────────────────── */}
      <aside className="register-left-panel">
        {/* Logo */}
        <div className="reg-logo">
          <div className="reg-logo-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 14l9-5-9-5-9 5 9 5z" />
              <path d="M12 14l6.16-3.422A12.083 12.083 0 0112 21.93a12.083 12.083 0 01-6.16-11.352L12 14z" />
            </svg>
          </div>
          <Link to="/" className="reg-logo-name">
            EduPlatform
          </Link>
        </div>

        {/* Hero text */}
        <div className="reg-hero">
          <span className="reg-badge">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              className="reg-badge-icon"
            >
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v6l4 2" />
            </svg>
            Học. Thực hành. Phát triển.
          </span>
          <h1 className="reg-hero-title">Bắt Đầu Hành Trình Học Tập</h1>
          <p className="reg-hero-desc">
            Tạo tài khoản, khám phá khóa học và phát triển kỹ năng mới cùng EduPlatform.
          </p>
        </div>

        {/* Featured card */}
        <div className="reg-feature-card">
          <div className="reg-feature-card-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 14l9-5-9-5-9 5 9 5z" />
              <path d="M12 14l6.16-3.422A12.083 12.083 0 0112 21.93a12.083 12.083 0 01-6.16-11.352L12 14z" />
            </svg>
          </div>
          <div>
            <div className="reg-feature-card-title">Cổng Học Viên Chính Thức</div>
            <div className="reg-feature-card-desc">
              Tham gia cùng hàng nghìn học viên tiếp cận chương trình công nghệ được kiểm chứng, môi
              trường thực hành đám mây và lộ trình học cá nhân hóa.
            </div>
          </div>
        </div>

        {/* Feature list */}
        <ul className="reg-feature-list">
          <li className="reg-feature-item">
            <span className="reg-feature-item-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
              </svg>
            </span>
            <div>
              <div className="reg-feature-item-title">Chương Trình Công Nghệ Chọn Lọc</div>
              <div className="reg-feature-item-desc">
                Các lộ trình khoa học máy tính và phát triển phần mềm được kiểm duyệt liên tục.
              </div>
            </div>
          </li>
          <li className="reg-feature-item">
            <span className="reg-feature-item-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="3" width="20" height="14" rx="2" />
                <path d="M8 21h8M12 17v4" />
              </svg>
            </span>
            <div>
              <div className="reg-feature-item-title">Dự Án Thực Hành Lập Trình</div>
              <div className="reg-feature-item-desc">
                Triển khai dự án thực tế trực tiếp lên các cụm đám mây vi mô được xác minh.
              </div>
            </div>
          </li>
          <li className="reg-feature-item">
            <span className="reg-feature-item-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="8" r="4" />
                <path d="M20 21a8 8 0 10-16 0" />
              </svg>
            </span>
            <div>
              <div className="reg-feature-item-title">Chứng Chỉ Kỹ Năng Xác Thực</div>
              <div className="reg-feature-item-desc">
                Chứng chỉ số chống giả mạo được các đối tác tuyển dụng công nghệ toàn cầu công nhận.
              </div>
            </div>
          </li>
        </ul>
      </aside>

      {/* ── Right panel ────────────────────────────────────────── */}
      <main className="register-right-panel">
        <div className="register-form-scroll">
          <div className="register-form-card">
            <Outlet />
          </div>

          {/* Footer */}
          <footer className="reg-footer">
            <span>© 2024 EduPlatform Inc. Bảo lưu mọi quyền.</span>
            <span className="reg-footer-dot">•</span>
            <a href="#" className="reg-footer-link">
              Chính sách bảo mật
            </a>
            <span className="reg-footer-dot">•</span>
            <a href="#" className="reg-footer-link">
              Điều khoản dịch vụ
            </a>
            <span className="reg-footer-dot">•</span>
            <a href="#" className="reg-footer-link">
              Trung tâm hỗ trợ
            </a>
          </footer>
        </div>
      </main>
    </div>
  )
}
