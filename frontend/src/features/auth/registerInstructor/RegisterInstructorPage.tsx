import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerInstructor } from '@/api/auth.ts'
import './register-instructor.css'

const FEATURES = [
  {
    title: 'Cổng Giảng viên Chính thức',
    desc: 'Nền tảng chuyên biệt dành cho nhà giáo dục công nghệ, cung cấp bộ công cụ quản lý khóa học và đo lường tiến độ học viên theo thời gian thực.',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
    ),
  },
  {
    title: 'Xây dựng và quản lý khóa học',
    desc: 'Tổ chức giáo trình theo từng mô-đun bài bản, tích hợp video chuẩn HD và học liệu tài liệu phong phú.',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <path d="M3 9h18M9 21V9" />
      </svg>
    ),
  },
  {
    title: 'Tạo bài kiểm tra và bài tập',
    desc: 'Thiết lập bài trắc nghiệm tự động chấm điểm, chấm bài thực hành lập trình và tiêu chí đánh giá chuẩn hóa.',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M9 11l3 3L22 4" />
        <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
      </svg>
    ),
  },
  {
    title: 'Theo dõi tiến độ học tập của học viên',
    desc: 'Phân tích trực quan mức độ hoàn thành bài học, tương tác diễn đàn và giải đáp thắc mắc kịp thời cho học viên.',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
    ),
  },
]

export default function RegisterInstructorPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // ── Section 1: Thông tin cá nhân & liên lạc ──
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')

  // ── Section 2: Thông tin chuyên môn ──
  const [organization, setOrganization] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [expertise, setExpertise] = useState('')
  const [yearsOfExperience, setYearsOfExperience] = useState<string>('')

  // ── Section 3: Hồ sơ năng lực & Mục tiêu ──
  const [professionalBio, setProfessionalBio] = useState('')
  const [motivation, setMotivation] = useState('')

  // Terms
  const [agreed, setAgreed] = useState(true)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!agreed) {
      setError('Vui lòng đồng ý với Tiêu chuẩn Giảng viên và Chính sách liêm chính học thuật để tiếp tục.')
      return
    }
    if (!fullName.trim() || fullName.trim().length < 2) {
      setError('Vui lòng nhập họ và tên hợp lệ (tối thiểu 2 ký tự).')
      return
    }

    setLoading(true)
    try {
      await registerInstructor({
        email,
        fullName,
        phone: phone || null,
        organization: organization || null,
        jobTitle: jobTitle || null,
        expertise: expertise || null,
        yearsOfExperience: yearsOfExperience !== '' ? Number(yearsOfExperience) : null,
        professionalBio: professionalBio || null,
        motivation: motivation || null,
      })

      navigate('/login', {
        state: {
          registeredEmail: email,
          message:
            'Đơn đăng ký Giảng viên đã được tiếp nhận thành công! Quản trị viên sẽ xét duyệt và gửi thông tin tài khoản qua email của bạn.',
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
    <div className="ri-page">
      {/* ════ LEFT PANEL ════ */}
      <aside className="ri-left">
        {/* Top bar with Logo & Sidebar Back button */}
        <div className="ri-left-top">
          <Link to="/" className="ri-logo">
            <div className="ri-logo-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 14l9-5-9-5-9 5 9 5z" />
                <path d="M12 14l6.16-3.422A12.083 12.083 0 0112 21.93a12.083 12.083 0 01-6.16-11.352L12 14z" />
              </svg>
            </div>
            <span className="ri-logo-name">EduPlatform</span>
          </Link>

          {/* Prominent Back Button in Left Sidebar */}
          <Link to="/choose-role" className="ri-sidebar-back-btn" title="Quay lại chọn loại tài khoản">
            <span className="ri-back-icon-circle">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
            </span>
            <span>Quay lại chọn tài khoản</span>
          </Link>
        </div>

        {/* Hero */}
        <h1 className="ri-hero-title">Chia sẻ kiến thức của bạn</h1>
        <p className="ri-hero-desc">
          Tạo khóa học, xây dựng nội dung giảng dạy và đồng hành cùng học viên trên hành trình phát triển toàn diện.
        </p>

        {/* Features */}
        <div className="ri-feature-list">
          {FEATURES.map((f) => (
            <div key={f.title} className="ri-feature-card">
              <div className="ri-feature-card-icon">{f.icon}</div>
              <div>
                <div className="ri-feature-card-title">{f.title}</div>
                <div className="ri-feature-card-desc">{f.desc}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Testimonial */}
        <div className="ri-testimonial">
          <div className="ri-testimonial-header">
            <div className="ri-testimonial-avatar">NHĐ</div>
            <div>
              <div className="ri-testimonial-name">TS. Nguyễn Hải Đăng</div>
              <div className="ri-testimonial-title">Trưởng bộ môn Kỹ thuật Phần mềm, ĐH Bách Khoa</div>
            </div>
          </div>
          <p className="ri-testimonial-quote">
            <span className="ri-testimonial-quote-icon">"</span>
            EduPlatform giúp tôi truyền tải trọn vẹn kiến thức thực chiến đến hàng nghìn học viên một cách trực quan,
            khoa học và đạt hiệu quả tối ưu nhất.
            <span className="ri-testimonial-quote-icon">"</span>
          </p>
        </div>
      </aside>

      {/* ════ RIGHT PANEL ════ */}
      <main className="ri-right">
        <div className="ri-right-scroll">
          <div className="ri-form-card">
            {/* Form Top Navigation Bar */}
            <div className="ri-card-top-nav">
              <Link to="/choose-role" className="ri-card-back-btn" title="Quay lại chọn loại tài khoản">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <path d="M19 12H5M12 19l-7-7 7-7" />
                </svg>
                <span>Quay lại chọn tài khoản</span>
              </Link>
              <div className="ri-role-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M23 21v-2a4 4 0 00-3-3.87" />
                  <path d="M16 3.13a4 4 0 010 7.75" />
                </svg>
                Tài khoản Giảng viên
              </div>
            </div>

            {/* Header Titles */}
            <div className="ri-form-header-box">
              <h2 className="ri-form-title">Đăng ký tài khoản Giảng viên</h2>
              <p className="ri-form-subtitle">
                Vui lòng hoàn tất thông tin bên dưới để gửi hồ sơ xét duyệt tài khoản Giảng viên trên EduPlatform.
              </p>
            </div>

            {/* Error alert */}
            {error && (
              <div className="ri-alert-error" role="alert">
                <svg className="ri-alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="ri-form-body" noValidate>

              {/* ═══════════════════════════════════════
                  SECTION 1 – THÔNG TIN CÁ NHÂN & LIÊN LẠC
              ═══════════════════════════════════════ */}
              <div className="ri-section-card">
                <div className="ri-section-header">
                  <span className="ri-section-badge">1</span>
                  <div className="ri-section-info">
                    <h3 className="ri-section-title">THÔNG TIN CÁ NHÂN & LIÊN LẠC</h3>
                    <p className="ri-section-desc">Thông tin cơ bản để xác thực hồ sơ và liên hệ với bạn</p>
                  </div>
                </div>

                <div className="ri-section-fields">
                  {/* Full Name + Phone */}
                  <div className="ri-two-col">
                    <div className="ri-field">
                      <label htmlFor="ri-fullname" className="ri-label">
                        Họ và tên <span className="ri-required">*</span>
                      </label>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
                            <circle cx="12" cy="7" r="4" />
                          </svg>
                        </span>
                        <input
                          id="ri-fullname"
                          type="text"
                          required
                          minLength={2}
                          maxLength={255}
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          placeholder="Ví dụ: Nguyễn Văn A"
                          className="ri-input ri-input-icon-left"
                          autoComplete="name"
                        />
                      </div>
                    </div>

                    <div className="ri-field">
                      <div className="ri-label-row">
                        <label htmlFor="ri-phone" className="ri-label">Số điện thoại</label>
                        <span className="ri-optional">Tùy chọn</span>
                      </div>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 9.81a19.79 19.79 0 01-3.07-8.63A2 2 0 012 .18h3a2 2 0 012 1.72 12.84 12.84 0 00.7 2.81 2 2 0 01-.45 2.11L6.09 7.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 14z" />
                          </svg>
                        </span>
                        <input
                          id="ri-phone"
                          type="tel"
                          maxLength={50}
                          value={phone}
                          onChange={(e) => setPhone(e.target.value)}
                          placeholder="VD: 0912 345 678"
                          className="ri-input ri-input-icon-left"
                          autoComplete="tel"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Email */}
                  <div className="ri-field">
                    <label htmlFor="ri-email" className="ri-label">
                      Địa chỉ email liên hệ <span className="ri-required">*</span>
                    </label>
                    <div className="ri-input-wrap">
                      <span className="ri-input-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                          <polyline points="22,6 12,13 2,6" />
                        </svg>
                      </span>
                      <input
                        id="ri-email"
                        type="email"
                        required
                        maxLength={255}
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="giangvien@eduplatform.vn"
                        className="ri-input ri-input-icon-left"
                        autoComplete="email"
                      />
                    </div>
                    <span className="ri-field-hint">Email sẽ dùng để nhận thông báo xét duyệt và tài khoản đăng nhập.</span>
                  </div>
                </div>
              </div>

              {/* ═══════════════════════════════════════
                  SECTION 2 – THÔNG TIN CHUYÊN MÔN
              ═══════════════════════════════════════ */}
              <div className="ri-section-card">
                <div className="ri-section-header">
                  <span className="ri-section-badge">2</span>
                  <div className="ri-section-info">
                    <h3 className="ri-section-title">THÔNG TIN CHUYÊN MÔN & KINH NGHIỆM</h3>
                    <p className="ri-section-desc">Kinh nghiệm công tác và chuyên ngành dự kiến giảng dạy</p>
                  </div>
                </div>

                <div className="ri-section-fields">
                  {/* Organization + Job Title */}
                  <div className="ri-two-col">
                    <div className="ri-field">
                      <div className="ri-label-row">
                        <label htmlFor="ri-org" className="ri-label">Trường học / Đơn vị công tác</label>
                        <span className="ri-optional">Tùy chọn</span>
                      </div>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <rect x="2" y="7" width="20" height="14" rx="2" />
                            <path d="M16 7V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v2" />
                            <line x1="12" y1="12" x2="12" y2="16" />
                            <line x1="10" y1="14" x2="14" y2="14" />
                          </svg>
                        </span>
                        <input
                          id="ri-org"
                          type="text"
                          maxLength={255}
                          value={organization}
                          onChange={(e) => setOrganization(e.target.value)}
                          placeholder="VD: ĐH Bách Khoa, FPT..."
                          className="ri-input ri-input-icon-left"
                        />
                      </div>
                    </div>

                    <div className="ri-field">
                      <div className="ri-label-row">
                        <label htmlFor="ri-jobtitle" className="ri-label">Chức danh / Vị trí</label>
                        <span className="ri-optional">Tùy chọn</span>
                      </div>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2z" />
                            <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16" />
                          </svg>
                        </span>
                        <input
                          id="ri-jobtitle"
                          type="text"
                          maxLength={255}
                          value={jobTitle}
                          onChange={(e) => setJobTitle(e.target.value)}
                          placeholder="VD: Giảng viên, Kỹ sư phần mềm"
                          className="ri-input ri-input-icon-left"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Expertise + Years of Experience */}
                  <div className="ri-two-col">
                    <div className="ri-field">
                      <div className="ri-label-row">
                        <label htmlFor="ri-expertise" className="ri-label">Lĩnh vực chuyên môn chính</label>
                        <span className="ri-optional">Tùy chọn</span>
                      </div>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                          </svg>
                        </span>
                        <input
                          id="ri-expertise"
                          type="text"
                          maxLength={255}
                          value={expertise}
                          onChange={(e) => setExpertise(e.target.value)}
                          placeholder="VD: Lập trình Web, AI, An toàn mạng..."
                          className="ri-input ri-input-icon-left"
                        />
                      </div>
                    </div>

                    <div className="ri-field">
                      <div className="ri-label-row">
                        <label htmlFor="ri-years" className="ri-label">Kinh nghiệm thực tế</label>
                        <span className="ri-optional">0 – 60 năm</span>
                      </div>
                      <div className="ri-input-wrap">
                        <span className="ri-input-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                            <line x1="16" y1="2" x2="16" y2="6" />
                            <line x1="8" y1="2" x2="8" y2="6" />
                            <line x1="3" y1="10" x2="21" y2="10" />
                          </svg>
                        </span>
                        <input
                          id="ri-years"
                          type="number"
                          min={0}
                          max={60}
                          value={yearsOfExperience}
                          onChange={(e) => setYearsOfExperience(e.target.value)}
                          placeholder="VD: 5"
                          className="ri-input ri-input-icon-left"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* ═══════════════════════════════════════
                  SECTION 3 – HỒ SƠ NĂNG LỰC & MỤC TIÊU
              ═══════════════════════════════════════ */}
              <div className="ri-section-card">
                <div className="ri-section-header">
                  <span className="ri-section-badge">3</span>
                  <div className="ri-section-info">
                    <h3 className="ri-section-title">HỒ SƠ NĂNG LỰC & MỤC TIÊU GIẢNG DẠY</h3>
                    <p className="ri-section-desc">Cung cấp thêm chi tiết để hội đồng xét duyệt hồ sơ nhanh hơn</p>
                  </div>
                </div>

                <div className="ri-section-fields">
                  {/* Professional Bio */}
                  <div className="ri-field">
                    <div className="ri-label-row">
                      <label htmlFor="ri-bio" className="ri-label">Giới thiệu năng lực chuyên môn</label>
                      <span className="ri-optional">Tùy chọn</span>
                    </div>
                    <textarea
                      id="ri-bio"
                      rows={3}
                      maxLength={5000}
                      value={professionalBio}
                      onChange={(e) => setProfessionalBio(e.target.value)}
                      placeholder="Chia sẻ kinh nghiệm giảng dạy, chứng chỉ chuyên môn hoặc các dự án công nghệ tiêu biểu bạn từng thực hiện…"
                      className="ri-input ri-textarea"
                    />
                  </div>

                  {/* Motivation */}
                  <div className="ri-field">
                    <div className="ri-label-row">
                      <label htmlFor="ri-motivation" className="ri-label">Lý do & mục tiêu tham gia giảng dạy</label>
                      <span className="ri-optional">Tùy chọn</span>
                    </div>
                    <textarea
                      id="ri-motivation"
                      rows={3}
                      maxLength={5000}
                      value={motivation}
                      onChange={(e) => setMotivation(e.target.value)}
                      placeholder="Chia sẻ lý do bạn muốn trở thành giảng viên, những giá trị bạn muốn mang lại cho học viên và mục tiêu trên EduPlatform…"
                      className="ri-input ri-textarea"
                    />
                  </div>
                </div>
              </div>

              {/* Agree checkbox */}
              <div className="ri-agree-box">
                <label className="ri-agree" htmlFor="ri-agree">
                  <input
                    id="ri-agree"
                    type="checkbox"
                    className="ri-agree-input"
                    checked={agreed}
                    onChange={(e) => setAgreed(e.target.checked)}
                  />
                  <span className="ri-agree-text">
                    Tôi cam đoan thông tin khai báo là trung thực và đồng ý với{' '}
                    <a href="#">Tiêu chuẩn Giảng viên</a> cùng{' '}
                    <a href="#">Chính sách liêm chính học thuật</a> của EduPlatform.
                  </span>
                </label>
              </div>

              {/* Submit & Secondary Back Action */}
              <div className="ri-actions-container">
                <button
                  id="ri-submit"
                  type="submit"
                  disabled={loading}
                  className="ri-submit-btn"
                >
                  {loading ? (
                    <>
                      <svg className="ri-spinner" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.25" />
                        <path d="M12 2a10 10 0 0110 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                      </svg>
                      Đang gửi đơn đăng ký…
                    </>
                  ) : (
                    <>
                      <span>Gửi hồ sơ đăng ký Giảng viên</span>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <line x1="5" y1="12" x2="19" y2="12" />
                        <polyline points="12 5 19 12 12 19" />
                      </svg>
                    </>
                  )}
                </button>

                {/* Prominent Back Button at Form Bottom */}
                <Link to="/choose-role" className="ri-back-bottom-btn" title="Quay lại chọn loại tài khoản">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M19 12H5M12 19l-7-7 7-7" />
                  </svg>
                  <span>Quay lại chọn loại tài khoản</span>
                </Link>
              </div>

              {/* Signin prompt */}
              <div className="ri-signin-prompt">
                <span>Đã có tài khoản trên hệ thống?</span>{' '}
                <Link to="/login" className="ri-signin-link">Đăng nhập ngay</Link>
              </div>

              {/* Privacy note */}
              <div className="ri-privacy-note">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
                <span>
                  Thông tin của bạn được lưu trữ bảo mật và chỉ sử dụng cho mục đích thẩm định năng lực giảng dạy theo đúng chính sách bảo vệ dữ liệu của EduPlatform.
                </span>
              </div>
            </form>
          </div>

          {/* Footer */}
          <footer className="ri-footer">
            <span>© 2026 EduPlatform Inc. All rights reserved.</span>
            <span className="ri-footer-dot">•</span>
            <a href="#" className="ri-footer-link">Privacy Policy</a>
            <span className="ri-footer-dot">•</span>
            <a href="#" className="ri-footer-link">Terms of Service</a>
            <span className="ri-footer-dot">•</span>
            <a href="#" className="ri-footer-link">Security</a>
          </footer>
        </div>
      </main>
    </div>
  )
}
