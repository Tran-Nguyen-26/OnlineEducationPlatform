import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { tokenStore } from './tokenStore'
import type { ApiErrorBody, AuthResponse } from '../types/api'

export const API_BASE_URL = 'http://localhost:8080'

export const apiClient = axios.create({ baseURL: API_BASE_URL })

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.getRefreshToken()
  if (!refreshToken) return null
  try {
    const { data } = await axios.post<AuthResponse>(`${API_BASE_URL}/api/auth/refresh`, { refreshToken })
    tokenStore.setTokens(data.accessToken, data.refreshToken)
    return data.accessToken
  } catch {
    tokenStore.clear()
    return null
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableRequestConfig | undefined
    const isRefreshCall = original?.url?.includes('/api/auth/refresh')

    if (error.response?.status === 401 && original && !original._retried && !isRefreshCall) {
      original._retried = true
      refreshPromise ??= refreshAccessToken().finally(() => {
        refreshPromise = null
      })
      const newAccessToken = await refreshPromise
      if (newAccessToken) {
        original.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(original)
      }
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorBody>(error) && error.response?.data?.message) {
    return error.response.data.message
  }
  return 'Đã có lỗi xảy ra, vui lòng thử lại.'
}
