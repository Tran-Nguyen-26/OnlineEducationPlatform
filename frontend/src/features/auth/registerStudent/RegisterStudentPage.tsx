import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  GraduationCap,
  Mail,
  Lock,
  User,
  Phone,
  Calendar,
  Building2,
  Eye,
  EyeOff,
  ShieldCheck,
  Code2,
  Compass,
  Laptop,
  ArrowRight,
  AlertCircle,
} from 'lucide-react'
import { registerUser } from '@/api/auth.ts'
import './register-student.css'

function getPasswordStrength(password: string): { level: number; label: string; color: string } {
  if (!password) return { level: 0, label: '', color: '' }
  let score = 0
  if (password.length >= 6) score++
  if (password.length >= 10) score++
  if (/[A-Z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++

  if (score <= 1) return { level: 1, label: 'Yếu', color: '#ef4444' }
  if (score <= 2) return { level: 2, label: 'Trung bình', color: '#f97316' }
  if (score <= 3) return { level: 3, label: 'Khá', color: '#eab308' }
  return { level: 4, label: 'Mạnh', color: '#10b981' }
}

export default function RegisterStudentPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Form states
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [dob, setDob] = useState('')
  const [school, setSchool] = useState('')
  const [about, setAbout] = useState('')
  const [agreeTerms, setAgreeTerms] = useState(true)

  const strength = getPasswordStrength(password)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!agreeTerms) {
      setError('Vui lòng đồng ý với Điều khoản dịch vụ và Chính sách bảo mật.')
      return
    }

    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không trùng khớp.')
      return
    }

    if (password.length < 6) {
      setError('Mật khẩu phải có độ dài từ 6 ký tự trở lên.')
      return
    }

    if (!fullName.trim()) {
      setError('Vui lòng nhập họ và tên của bạn.')
      return
    }

    setLoading(true)
    try {
      await registerUser({
        email,
        password,
        fullName,
        phone,
        dateOfBirth: dob,
        organization: school,
        bio: about,
      })

      navigate('/login', {
        state: {
          registeredEmail: email,
          message: 'Đăng ký tài khoản học viên thành công! Vui lòng đăng nhập.',
        },
      })
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Đã xảy ra lỗi trong quá trình đăng ký. Vui lòng thử lại.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="student-reg-container">
      {/* ── Left Brand & Features Panel ── */}
      <aside className="student-reg-left">
        <div className="student-reg-left-content">
          {/* Logo & Brand */}
          <Link to="/" className="student-reg-brand">
            <div className="student-reg-logo-box">
              <GraduationCap className="h-5 w-5" strokeWidth={2.5} />
            </div>
            <div className="student-reg-brand-text">
              <span className="student-reg-brand-name">EduPlatform</span>
              <span className="student-reg-brand-sub">Cổng Học Viện</span>
            </div>
          </Link>

          {/* Slogan Pill */}
          <div className="student-reg-pill">
            <span className="student-reg-pill-dot" />
            <span>Học Tập · Thực Hành · Phát Triển</span>
          </div>

          {/* Hero Typography */}
          <h1 className="student-reg-hero-title">
            Bắt Đầu Hành Trình Học Tập
          </h1>
          <p className="student-reg-hero-desc">
            Tạo tài khoản, khám phá chương trình học chuẩn mực và phát triển năng lực chuyên môn cùng EduPlatform.
          </p>

          {/* 4 Feature Cards */}
          <div className="student-reg-features">
            {/* Card 1 */}
            <div className="student-reg-card">
              <div className="student-reg-card-icon">
                <Laptop className="h-4 w-4" />
              </div>
              <div>
                <div className="student-reg-card-title">Cổng Học Viên Chính Thức</div>
                <div className="student-reg-card-desc">
                  Truy cập toàn diện vào môi trường thực hành đám mây, kho bài giảng và tài nguyên mã nguồn.
                </div>
              </div>
            </div>

            {/* Card 2 */}
            <div className="student-reg-card">
              <div className="student-reg-card-icon">
                <Compass className="h-4 w-4" />
              </div>
              <div>
                <div className="student-reg-card-title">Lộ Trình Công Nghệ Tuyển Chọn</div>
                <div className="student-reg-card-desc">
                  Các chuyên ngành thiết kế chuẩn kỹ sư cho Điện toán đám mây, Fullstack và Trí tuệ nhân tạo.
                </div>
              </div>
            </div>

            {/* Card 3 */}
            <div className="student-reg-card">
              <div className="student-reg-card-icon">
                <Code2 className="h-4 w-4" />
              </div>
              <div>
                <div className="student-reg-card-title">Dự Án Thực Hành Lập Trình</div>
                <div className="student-reg-card-desc">
                  Xây dựng dự án thực tế đưa vào portfolio với đánh giá chi tiết từ đội ngũ trợ giảng.
                </div>
              </div>
            </div>

            {/* Card 4 */}
            <div className="student-reg-card">
              <div className="student-reg-card-icon">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <div>
                <div className="student-reg-card-title">Chứng Chỉ Kỹ Năng Xác Thực</div>
                <div className="student-reg-card-desc">
                  Chứng chỉ số bảo mật được doanh nghiệp công nhận, liên kết trực tiếp vào hồ sơ tuyển dụng.
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Left Footer */}
        <div className="student-reg-left-footer">
          <div className="student-reg-ssl-badge">
            <Lock className="h-3.5 w-3.5 text-slate-400" />
            <span>Bảo mật SSL 256-bit</span>
          </div>
          <div>© 2026 EduPlatform</div>
        </div>
      </aside>

      {/* ── Right Form Card Panel ── */}
      <main className="student-reg-right">
        <div className="student-reg-form-card">
          {/* Card Header */}
          <div className="student-reg-header-row">
            <div>
              <h2 className="student-reg-header-title">Tạo Tài Khoản Học Viên</h2>
              <p className="student-reg-header-sub">
                Hoàn tất thông tin đăng ký để truy cập không gian học tập và thực hành.
              </p>
            </div>
          </div>

          {/* Error Alert */}
          {error && (
            <div className="student-reg-alert" role="alert">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit}>
            {/* ── Section 1: Thông tin tài khoản ── */}
            <div className="student-reg-section-bar">
              <span className="student-reg-section-num">1</span>
              <span className="student-reg-section-title">THÔNG TIN TÀI KHOẢN</span>
            </div>

            {/* Email */}
            <div className="student-reg-field">
              <label htmlFor="regEmail" className="student-reg-label">
                ĐỊA CHỈ EMAIL <span className="student-reg-required">*</span>
              </label>
              <div className="student-reg-input-wrap">
                <span className="student-reg-input-icon">
                  <Mail className="h-4 w-4" />
                </span>
                <input
                  id="regEmail"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  className="student-reg-input student-reg-input-with-icon"
                />
              </div>
            </div>

            {/* Password & Confirm Password (2 Columns) */}
            <div className="student-reg-two-col">
              {/* Password */}
              <div className="student-reg-field">
                <label htmlFor="regPassword" className="student-reg-label">
                  MẬT KHẨU <span className="student-reg-required">*</span>
                </label>
                <div className="student-reg-input-wrap">
                  <span className="student-reg-input-icon">
                    <Lock className="h-4 w-4" />
                  </span>
                  <input
                    id="regPassword"
                    type={showPassword ? 'text' : 'password'}
                    required
                    minLength={6}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Tối thiểu 6 ký tự"
                    className="student-reg-input student-reg-input-with-icon student-reg-input-with-toggle"
                  />
                  <button
                    type="button"
                    className="student-reg-toggle-btn"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {/* Password strength indicator */}
                {password && (
                  <div className="student-reg-strength-box">
                    <div className="student-reg-strength-bars">
                      {[1, 2, 3, 4].map((i) => (
                        <div
                          key={i}
                          className="student-reg-strength-bar"
                          style={{
                            backgroundColor: i <= strength.level ? strength.color : '#e2e8f0',
                          }}
                        />
                      ))}
                    </div>
                    <span className="student-reg-strength-text" style={{ color: strength.color }}>
                      {strength.label}
                    </span>
                  </div>
                )}
              </div>

              {/* Confirm Password */}
              <div className="student-reg-field">
                <label htmlFor="regConfirmPassword" className="student-reg-label">
                  XÁC NHẬN MẬT KHẨU <span className="student-reg-required">*</span>
                </label>
                <div className="student-reg-input-wrap">
                  <span className="student-reg-input-icon">
                    <Lock className="h-4 w-4" />
                  </span>
                  <input
                    id="regConfirmPassword"
                    type={showConfirm ? 'text' : 'password'}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Nhập lại mật khẩu"
                    className="student-reg-input student-reg-input-with-icon student-reg-input-with-toggle"
                  />
                  <button
                    type="button"
                    className="student-reg-toggle-btn"
                    onClick={() => setShowConfirm(!showConfirm)}
                    aria-label={showConfirm ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
            </div>

            {/* ── Section 2: Hồ sơ cá nhân ── */}
            <div className="student-reg-section-bar">
              <span className="student-reg-section-num">2</span>
              <span className="student-reg-section-title">HỒ SƠ CÁ NHÂN</span>
            </div>

            {/* Full Name */}
            <div className="student-reg-field">
              <label htmlFor="regFullName" className="student-reg-label">
                HỌ VÀ TÊN <span className="student-reg-required">*</span>
              </label>
              <div className="student-reg-input-wrap">
                <span className="student-reg-input-icon">
                  <User className="h-4 w-4" />
                </span>
                <input
                  id="regFullName"
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Ví dụ: Nguyễn Văn An"
                  className="student-reg-input student-reg-input-with-icon"
                />
              </div>
            </div>

            {/* Phone & Date of Birth (2 Columns) */}
            <div className="student-reg-two-col">
              {/* Phone */}
              <div className="student-reg-field">
                <label htmlFor="regPhone" className="student-reg-label">
                  SỐ ĐIỆN THOẠI
                </label>
                <div className="student-reg-input-wrap">
                  <span className="student-reg-input-icon">
                    <Phone className="h-4 w-4" />
                  </span>
                  <input
                    id="regPhone"
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="0901234567"
                    className="student-reg-input student-reg-input-with-icon"
                  />
                </div>
              </div>

              {/* Date of Birth */}
              <div className="student-reg-field">
                <label htmlFor="regDob" className="student-reg-label">
                  NGÀY SINH
                </label>
                <div className="student-reg-input-wrap">
                  <span className="student-reg-input-icon">
                    <Calendar className="h-4 w-4" />
                  </span>
                  <input
                    id="regDob"
                    type="date"
                    value={dob}
                    onChange={(e) => setDob(e.target.value)}
                    className="student-reg-input student-reg-input-with-icon"
                  />
                </div>
              </div>
            </div>

            {/* University / Organization */}
            <div className="student-reg-field">
              <label htmlFor="regSchool" className="student-reg-label">
                TRƯỜNG ĐẠI HỌC / TỔ CHỨC
              </label>
              <div className="student-reg-input-wrap">
                <span className="student-reg-input-icon">
                  <Building2 className="h-4 w-4" />
                </span>
                <input
                  id="regSchool"
                  type="text"
                  value={school}
                  onChange={(e) => setSchool(e.target.value)}
                  placeholder="Ví dụ: Đại học Bách Khoa, FPT..."
                  className="student-reg-input student-reg-input-with-icon"
                />
              </div>
            </div>

            {/* About Me */}
            <div className="student-reg-field">
              <label htmlFor="regAbout" className="student-reg-label">
                GIỚI THIỆU BẢN THÂN
              </label>
              <textarea
                id="regAbout"
                rows={3}
                value={about}
                onChange={(e) => setAbout(e.target.value)}
                placeholder="Chia sẻ mục tiêu nghề nghiệp, nền tảng học vấn hoặc công nghệ bạn muốn chinh phục..."
                className="student-reg-textarea"
              />
            </div>

            {/* Agreement Checkbox */}
            <div className="student-reg-agree">
              <input
                id="regAgree"
                type="checkbox"
                checked={agreeTerms}
                onChange={(e) => setAgreeTerms(e.target.checked)}
              />
              <label htmlFor="regAgree">
                Tôi đồng ý với <Link to="/terms">Điều khoản dịch vụ</Link> và{' '}
                <Link to="/privacy">Chính sách bảo mật</Link> của EduPlatform.
              </label>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="student-reg-submit"
            >
              {loading ? (
                <span>Đang xử lý đăng ký...</span>
              ) : (
                <>
                  <span>TẠO TÀI KHOẢN</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>

            {/* Sign in Link */}
            <div className="student-reg-signin">
              Đã có tài khoản?
              <Link to="/login">Đăng nhập ngay</Link>
            </div>
          </form>
        </div>
      </main>
    </div>
  )
}
