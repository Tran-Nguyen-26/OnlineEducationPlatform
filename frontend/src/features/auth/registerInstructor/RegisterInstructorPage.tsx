import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  GraduationCap,
  User,
  Mail,
  Phone,
  Building2,
  Briefcase,
  Award,
  Clock,
  ShieldCheck,
  BookOpen,
  CheckSquare,
  BarChart3,
  ArrowRight,
  Lock,
  AlertCircle,
} from 'lucide-react'
import { registerInstructor } from '@/api/auth.ts'
import './register-instructor.css'

export default function RegisterInstructorPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Form states
  // ── Section 1: Thông tin cá nhân & liên hệ ──
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')

  // ── Section 2: Hồ sơ chuyên môn & năng lực ──
  const [organization, setOrganization] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [expertise, setExpertise] = useState('')
  const [yearsOfExperience, setYearsOfExperience] = useState<string>('')
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

    if (!email.trim()) {
      setError('Vui lòng nhập địa chỉ email liên hệ.')
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
    <div className="instructor-reg-container">
      {/* ── Left Brand & Features Panel ── */}
      <aside className="instructor-reg-left">
        <div className="instructor-reg-left-content">
          {/* Logo & Brand */}
          <Link to="/" className="instructor-reg-brand">
            <div className="instructor-reg-logo-box">
              <GraduationCap className="h-5 w-5" strokeWidth={2.5} />
            </div>
            <div className="instructor-reg-brand-text">
              <span className="instructor-reg-brand-name">EduPlatform</span>
              <span className="instructor-reg-brand-sub">Cổng Giảng Viên</span>
            </div>
          </Link>

          {/* Slogan Pill */}
          <div className="instructor-reg-pill">
            <span className="instructor-reg-pill-dot" />
            <span>Giảng Dạy · Kết Nối · Phát Triển</span>
          </div>

          {/* Hero Typography */}
          <h1 className="instructor-reg-hero-title">
            Chia Sẻ Tri Thức Cùng EduPlatform
          </h1>
          <p className="instructor-reg-hero-desc">
            Tạo khóa học, xây dựng nội dung giảng dạy chuẩn học thuật và đồng hành cùng hàng nghìn học viên trên hành trình phát triển toàn diện.
          </p>

          {/* 4 Feature Cards */}
          <div className="instructor-reg-features">
            {/* Card 1 */}
            <div className="instructor-reg-card">
              <div className="instructor-reg-card-icon">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <div>
                <div className="instructor-reg-card-title">Cổng Giảng Viên Chính Thức</div>
                <div className="instructor-reg-card-desc">
                  Nền tảng chuyên biệt dành cho nhà giáo dục công nghệ, cung cấp bộ công cụ quản lý khóa học và đo lường tiến độ học viên theo thời gian thực.
                </div>
              </div>
            </div>

            {/* Card 2 */}
            <div className="instructor-reg-card">
              <div className="instructor-reg-card-icon">
                <BookOpen className="h-4 w-4" />
              </div>
              <div>
                <div className="instructor-reg-card-title">Xây Dựng & Quản Lý Khóa Học</div>
                <div className="instructor-reg-card-desc">
                  Tổ chức giáo trình theo từng mô-đun bài bản, tích hợp video chuẩn HD và kho học liệu tài liệu phong phú.
                </div>
              </div>
            </div>

            {/* Card 3 */}
            <div className="instructor-reg-card">
              <div className="instructor-reg-card-icon">
                <CheckSquare className="h-4 w-4" />
              </div>
              <div>
                <div className="instructor-reg-card-title">Tạo Bài Kiểm Tra & Bài Tập</div>
                <div className="instructor-reg-card-desc">
                  Thiết lập bài trắc nghiệm tự động chấm điểm, chấm bài thực hành lập trình và tiêu chí đánh giá chuẩn hóa.
                </div>
              </div>
            </div>

            {/* Card 4 */}
            <div className="instructor-reg-card">
              <div className="instructor-reg-card-icon">
                <BarChart3 className="h-4 w-4" />
              </div>
              <div>
                <div className="instructor-reg-card-title">Theo Dõi & Phân Tích Học Viên</div>
                <div className="instructor-reg-card-desc">
                  Báo cáo phân tích chuyên sâu mức độ hoàn thành bài học, tương tác diễn đàn và giải đáp thắc mắc kịp thời cho học viên.
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Left Footer */}
        <div className="instructor-reg-left-footer">
          <div className="instructor-reg-ssl-badge">
            <Lock className="h-3.5 w-3.5 text-slate-400" />
            <span>Bảo mật SSL 256-bit</span>
          </div>
          <div>© 2026 EduPlatform</div>
        </div>
      </aside>

      {/* ── Right Form Card Panel ── */}
      <main className="instructor-reg-right">
        <div className="instructor-reg-form-card">
          {/* Card Header */}
          <div className="instructor-reg-header-row">
            <div>
              <h2 className="instructor-reg-header-title">Đăng Ký Tài Khoản Giảng Viên</h2>
              <p className="instructor-reg-header-sub">
                Vui lòng hoàn tất thông tin bên dưới để gửi hồ sơ xét duyệt tài khoản Giảng viên trên EduPlatform.
              </p>
            </div>
          </div>

          {/* Error Alert */}
          {error && (
            <div className="instructor-reg-alert" role="alert">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit}>
            {/* ── Section 1: Thông tin cá nhân & liên hệ ── */}
            <div className="instructor-reg-section-bar">
              <span className="instructor-reg-section-num">1</span>
              <span className="instructor-reg-section-title">THÔNG TIN CÁ NHÂN & LIÊN HỆ</span>
            </div>

            {/* Full Name */}
            <div className="instructor-reg-field">
              <label htmlFor="riFullName" className="instructor-reg-label">
                HỌ VÀ TÊN <span className="instructor-reg-required">*</span>
              </label>
              <div className="instructor-reg-input-wrap">
                <span className="instructor-reg-input-icon">
                  <User className="h-4 w-4" />
                </span>
                <input
                  id="riFullName"
                  type="text"
                  required
                  minLength={2}
                  maxLength={255}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Ví dụ: TS. Nguyễn Hải Đăng"
                  className="instructor-reg-input instructor-reg-input-with-icon"
                />
              </div>
            </div>

            {/* Email & Phone (2 Columns) */}
            <div className="instructor-reg-two-col">
              {/* Email */}
              <div className="instructor-reg-field">
                <label htmlFor="riEmail" className="instructor-reg-label">
                  ĐỊA CHỈ EMAIL LIÊN HỆ <span className="instructor-reg-required">*</span>
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Mail className="h-4 w-4" />
                  </span>
                  <input
                    id="riEmail"
                    type="email"
                    required
                    maxLength={255}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="giangvien@eduplatform.vn"
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>

              {/* Phone */}
              <div className="instructor-reg-field">
                <label htmlFor="riPhone" className="instructor-reg-label">
                  SỐ ĐIỆN THOẠI
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Phone className="h-4 w-4" />
                  </span>
                  <input
                    id="riPhone"
                    type="tel"
                    maxLength={50}
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="0912 345 678"
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>
            </div>

            {/* ── Section 2: Hồ sơ chuyên môn & năng lực giảng dạy ── */}
            <div className="instructor-reg-section-bar">
              <span className="instructor-reg-section-num">2</span>
              <span className="instructor-reg-section-title">HỒ SƠ CHUYÊN MÔN & NĂNG LỰC GIẢNG DẠY</span>
            </div>

            {/* Organization & Job Title (2 Columns) */}
            <div className="instructor-reg-two-col">
              {/* Organization */}
              <div className="instructor-reg-field">
                <label htmlFor="riOrg" className="instructor-reg-label">
                  TRƯỜNG HỌC / ĐƠN VỊ CÔNG TÁC
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Building2 className="h-4 w-4" />
                  </span>
                  <input
                    id="riOrg"
                    type="text"
                    maxLength={255}
                    value={organization}
                    onChange={(e) => setOrganization(e.target.value)}
                    placeholder="Ví dụ: ĐH Bách Khoa, FPT..."
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>

              {/* Job Title */}
              <div className="instructor-reg-field">
                <label htmlFor="riJobTitle" className="instructor-reg-label">
                  CHỨC DANH / VỊ TRÍ
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Briefcase className="h-4 w-4" />
                  </span>
                  <input
                    id="riJobTitle"
                    type="text"
                    maxLength={255}
                    value={jobTitle}
                    onChange={(e) => setJobTitle(e.target.value)}
                    placeholder="Ví dụ: Giảng viên chính, Kỹ sư phần mềm"
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>
            </div>

            {/* Expertise & Years of Experience (2 Columns) */}
            <div className="instructor-reg-two-col">
              {/* Expertise */}
              <div className="instructor-reg-field">
                <label htmlFor="riExpertise" className="instructor-reg-label">
                  LĨNH VỰC CHUYÊN MÔN CHÍNH
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Award className="h-4 w-4" />
                  </span>
                  <input
                    id="riExpertise"
                    type="text"
                    maxLength={255}
                    value={expertise}
                    onChange={(e) => setExpertise(e.target.value)}
                    placeholder="Ví dụ: Lập trình Web, AI, An toàn mạng..."
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>

              {/* Years of Experience */}
              <div className="instructor-reg-field">
                <label htmlFor="riYears" className="instructor-reg-label">
                  KINH NGHIỆM THỰC TẾ (SỐ NĂM)
                </label>
                <div className="instructor-reg-input-wrap">
                  <span className="instructor-reg-input-icon">
                    <Clock className="h-4 w-4" />
                  </span>
                  <input
                    id="riYears"
                    type="number"
                    min={0}
                    max={60}
                    value={yearsOfExperience}
                    onChange={(e) => setYearsOfExperience(e.target.value)}
                    placeholder="Ví dụ: 5"
                    className="instructor-reg-input instructor-reg-input-with-icon"
                  />
                </div>
              </div>
            </div>

            {/* Professional Bio */}
            <div className="instructor-reg-field">
              <label htmlFor="riBio" className="instructor-reg-label">
                GIỚI THIỆU NĂNG LỰC CHUYÊN MÔN
              </label>
              <textarea
                id="riBio"
                rows={3}
                maxLength={5000}
                value={professionalBio}
                onChange={(e) => setProfessionalBio(e.target.value)}
                placeholder="Chia sẻ kinh nghiệm giảng dạy, chứng chỉ chuyên môn hoặc các dự án công nghệ tiêu biểu bạn từng thực hiện…"
                className="instructor-reg-textarea"
              />
            </div>

            {/* Motivation */}
            <div className="instructor-reg-field">
              <label htmlFor="riMotivation" className="instructor-reg-label">
                LÝ DO & MỤC TIÊU THAM GIA GIẢNG DẠY
              </label>
              <textarea
                id="riMotivation"
                rows={3}
                maxLength={5000}
                value={motivation}
                onChange={(e) => setMotivation(e.target.value)}
                placeholder="Chia sẻ lý do bạn muốn trở thành giảng viên, những giá trị bạn muốn mang lại cho học viên và mục tiêu trên EduPlatform…"
                className="instructor-reg-textarea"
              />
            </div>

            {/* Agreement Checkbox */}
            <div className="instructor-reg-agree">
              <input
                id="riAgree"
                type="checkbox"
                checked={agreed}
                onChange={(e) => setAgreed(e.target.checked)}
              />
              <label htmlFor="riAgree">
                Tôi cam đoan thông tin khai báo là trung thực và đồng ý với{' '}
                <a href="#standards">Tiêu chuẩn Giảng viên</a> cùng{' '}
                <a href="#integrity">Chính sách liêm chính học thuật</a> của EduPlatform.
              </label>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="instructor-reg-submit"
            >
              {loading ? (
                <span>Đang gửi hồ sơ đăng ký…</span>
              ) : (
                <>
                  <span>GỬI HỒ SƠ ĐĂNG KÝ GIẢNG VIÊN</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>

            {/* Sign in Link */}
            <div className="instructor-reg-signin">
              Đã có tài khoản trên hệ thống?
              <Link to="/login">Đăng nhập ngay</Link>
            </div>
          </form>
        </div>
      </main>
    </div>
  )
}
