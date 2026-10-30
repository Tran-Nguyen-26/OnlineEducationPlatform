import { apiClient } from './client'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string | null
  dateOfBirth?: string | null // 'YYYY-MM-DD'
  organization?: string | null
  bio?: string | null
}

export interface AuthUserSummary {
  id: string
  email: string
  fullName: string
  role: string
  mustChangePassword: boolean
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUserSummary
}

export interface ApiErrorResponse {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  details?: Record<string, string>
}

/**
 * Register a new student account.
 * Calls POST /api/auth/register
 */
export async function registerUser(payload: RegisterRequest): Promise<AuthResponse> {
  const body = {
    email: payload.email.trim(),
    password: payload.password,
    fullName: payload.fullName.trim(),
    phone: payload.phone?.trim() || null,
    dateOfBirth: payload.dateOfBirth?.trim() || null,
    organization: payload.organization?.trim() || null,
    bio: payload.bio?.trim() || null,
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/register', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error(
          'Không thể kết nối đến máy chủ backend (502 Bad Gateway). Vui lòng đảm bảo backend đang chạy.'
        )
      }
      const errData = error as ApiErrorResponse | undefined
      if (errData?.error === 'EMAIL_TAKEN' || response.status === 409) {
        throw new Error('Email này đã được đăng ký. Vui lòng sử dụng email khác hoặc đăng nhập.')
      }
      if (errData?.details && Object.keys(errData.details).length > 0) {
        const messages = Object.values(errData.details).join('. ')
        throw new Error(messages)
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(`Đăng ký không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`)
    }

    return data as unknown as AuthResponse
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (
        err.message.includes('fetch') ||
        err.message.includes('NetworkError') ||
        err.message.includes('Failed to fetch')
      ) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

/**
 * Authenticate a user with email and password.
 * Calls POST /api/auth/login
 */
export async function loginUser(payload: LoginRequest): Promise<AuthResponse> {
  const body = {
    email: payload.email.trim(),
    password: payload.password,
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/login', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error(
          'Không thể kết nối đến máy chủ backend (502 Bad Gateway). Vui lòng đảm bảo backend đang chạy.'
        )
      }
      const errData = error as ApiErrorResponse | undefined
      if (errData?.error === 'BAD_CREDENTIALS' || response.status === 401) {
        throw new Error('Sai email hoặc mật khẩu. Vui lòng kiểm tra lại thông tin đăng nhập.')
      }
      if (errData?.error === 'ACCOUNT_DISABLED' || response.status === 403) {
        throw new Error('Tài khoản của bạn đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.')
      }
      if (errData?.details && Object.keys(errData.details).length > 0) {
        const messages = Object.values(errData.details).join('. ')
        throw new Error(messages)
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(`Đăng nhập không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`)
    }

    return data as unknown as AuthResponse
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (
        err.message.includes('fetch') ||
        err.message.includes('NetworkError') ||
        err.message.includes('Failed to fetch')
      ) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ForgotPasswordResponse {
  message: string
  emailMasked: string
  cooldownSeconds: number
  expiresInSeconds: number
}

export interface ForgotPasswordVerifyOtpRequest {
  email: string
  otpCode: string
}

export interface ForgotPasswordVerifyOtpResponse {
  resetToken: string
  expiresInSeconds: number
  message: string
}

export interface ResetPasswordRequest {
  resetToken: string
  newPassword: string
  confirmPassword: string
}

/**
 * Step 1: Request an OTP code sent to user email.
 * Calls POST /api/auth/forgot-password
 */
export async function requestForgotPasswordOtp(
  payload: ForgotPasswordRequest
): Promise<ForgotPasswordResponse> {
  const body = {
    email: payload.email.trim().toLowerCase(),
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/forgot-password', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error('Không thể kết nối đến máy chủ backend. Vui lòng kiểm tra lại dịch vụ.')
      }
      const errData = error as ApiErrorResponse | undefined
      if (response.status === 429) {
        throw new Error(
          errData?.message || 'Bạn đã yêu cầu gửi mã quá nhiều lần. Vui lòng thử lại sau 60 phút.'
        )
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(
        `Yêu cầu gửi mã không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`
      )
    }

    return data as ForgotPasswordResponse
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (
        err.message.includes('fetch') ||
        err.message.includes('NetworkError') ||
        err.message.includes('Failed to fetch')
      ) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

/**
 * Step 2: Verify the 6-digit OTP code and retrieve the one-time resetToken.
 * Calls POST /api/auth/forgot-password/verify-otp
 */
export async function verifyForgotPasswordOtp(
  payload: ForgotPasswordVerifyOtpRequest
): Promise<ForgotPasswordVerifyOtpResponse> {
  const body = {
    email: payload.email.trim().toLowerCase(),
    otpCode: payload.otpCode.trim(),
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/forgot-password/verify-otp', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error('Không thể kết nối đến máy chủ backend. Vui lòng kiểm tra lại dịch vụ.')
      }
      const errData = error as ApiErrorResponse | undefined
      if (errData?.error === 'OTP_EXPIRED') {
        throw new Error('Mã xác thực đã hết hạn hoặc không tồn tại. Vui lòng bấm Gửi lại mã.')
      }
      if (errData?.error === 'OTP_MAX_ATTEMPTS') {
        throw new Error('Bạn đã nhập sai mã quá 5 lần. Phiên xác thực đã bị hủy vì lý do an toàn.')
      }
      if (errData?.error === 'INVALID_OTP') {
        throw new Error(errData.message || 'Mã xác thực không chính xác. Vui lòng kiểm tra lại.')
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(
        `Xác thực mã không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`
      )
    }

    return data as ForgotPasswordVerifyOtpResponse
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (
        err.message.includes('fetch') ||
        err.message.includes('NetworkError') ||
        err.message.includes('Failed to fetch')
      ) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

/**
 * Step 3: Set a new password using the resetToken.
 * Calls POST /api/auth/reset-password
 */
export async function submitResetPassword(
  payload: ResetPasswordRequest
): Promise<{ message: string }> {
  const body = {
    resetToken: payload.resetToken,
    newPassword: payload.newPassword,
    confirmPassword: payload.confirmPassword,
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/reset-password', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error('Không thể kết nối đến máy chủ backend. Vui lòng kiểm tra lại dịch vụ.')
      }
      const errData = error as ApiErrorResponse | undefined
      if (errData?.error === 'INVALID_RESET_TOKEN') {
        throw new Error(
          'Phiên đặt lại mật khẩu đã hết hạn hoặc không hợp lệ. Vui lòng thực hiện lại từ đầu.'
        )
      }
      if (errData?.error === 'PASSWORD_CONFIRMATION_MISMATCH') {
        throw new Error('Mật khẩu xác nhận không khớp.')
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(
        `Đặt lại mật khẩu không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`
      )
    }

    return data as { message: string }
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (
        err.message.includes('fetch') ||
        err.message.includes('NetworkError') ||
        err.message.includes('Failed to fetch')
      ) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

export interface InstructorApplicationRequest {
  email: string
  fullName: string
  phone?: string | null
  organization?: string | null
  jobTitle?: string | null
  expertise?: string | null
  yearsOfExperience?: number | null
  professionalBio?: string | null
  motivation?: string | null
}

/**
 * Submit an instructor registration application.
 * Calls POST /api/auth/register-instructor
 */
export async function registerInstructor(
  payload: InstructorApplicationRequest
): Promise<{ success: boolean; message: string }> {
  const body = {
    email: payload.email.trim(),
    fullName: payload.fullName.trim(),
    phone: payload.phone?.trim() || null,
    organization: payload.organization?.trim() || null,
    jobTitle: payload.jobTitle?.trim() || null,
    expertise: payload.expertise?.trim() || null,
    yearsOfExperience: payload.yearsOfExperience ?? null,
    professionalBio: payload.professionalBio?.trim() || null,
    motivation: payload.motivation?.trim() || null,
  }

  try {
    const { data, error, response } = await apiClient.POST('/auth/register-instructor', {
      body,
    })

    if (!response.ok || error) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        throw new Error('Không thể kết nối đến máy chủ backend. Vui lòng đảm bảo backend đang chạy.')
      }
      const errData = error as ApiErrorResponse | undefined
      if (errData?.error === 'APPLICATION_ALREADY_PENDING') {
        throw new Error(errData.message || 'Email này hiện đang có hồ sơ chờ xét duyệt. Quản trị viên đang xử lý.')
      }
      if (errData?.error === 'EMAIL_TAKEN' || response.status === 409) {
        throw new Error(errData?.message || 'Email này đã được đăng ký hoặc đã có đơn đăng ký giảng viên.')
      }
      if (errData?.details && Object.keys(errData.details).length > 0) {
        const messages = Object.values(errData.details).join('. ')
        throw new Error(messages)
      }
      if (errData?.message) {
        throw new Error(errData.message)
      }
      throw new Error(`Gửi đơn đăng ký không thành công (Mã lỗi: ${response.status}). Vui lòng thử lại.`)
    }

    return data
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (err.message.includes('fetch') || err.message.includes('NetworkError') || err.message.includes('Failed to fetch')) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}
