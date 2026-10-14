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
        throw new Error('Không thể kết nối đến máy chủ backend (502 Bad Gateway). Vui lòng đảm bảo backend đang chạy.')
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
      if (err.message.includes('fetch') || err.message.includes('NetworkError') || err.message.includes('Failed to fetch')) {
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
        throw new Error('Không thể kết nối đến máy chủ backend (502 Bad Gateway). Vui lòng đảm bảo backend đang chạy.')
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
      if (err.message.includes('fetch') || err.message.includes('NetworkError') || err.message.includes('Failed to fetch')) {
        throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend.')
      }
      throw err
    }
    throw new Error('Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.')
  }
}

