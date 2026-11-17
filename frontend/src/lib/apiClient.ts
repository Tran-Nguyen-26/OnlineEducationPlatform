import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { tokenStore } from './tokenStore'
import type { ApiErrorBody, AuthResponse } from '../types/api'

// Production build: VITE_API_BASE_URL is '' (same origin, nginx proxies /api) or a full URL.
// Development (variable unset): same host the page was opened from, so 127.0.0.1 / LAN IPs work too.
const configuredBase = import.meta.env.VITE_API_BASE_URL as string | undefined
export const API_BASE_URL =
  configuredBase !== undefined
    ? configuredBase
    : typeof window === 'undefined'
      ? 'http://localhost:8080'
      : `http://${window.location.hostname}:8080`

export const apiClient = axios.create({ baseURL: API_BASE_URL })

const MUTATING = new Set(['post', 'put', 'patch'])
const NO_KEY = ['/api/auth/'] // credentials flows are never replayed

apiClient.interceptors.request.use((config) => {
  // One Idempotency-Key per logical request: set once, so every automatic retry of this request reuses it and the
  // gateway executes the action at most once (a double click / flaky network cannot create duplicates).
  if (MUTATING.has((config.method ?? '').toLowerCase()) && !config.headers['Idempotency-Key'] && !NO_KEY.some((p) => config.url?.includes(p))) {
    config.headers['Idempotency-Key'] = crypto.randomUUID()
  }
  const token = tokenStore.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
  _attempt?: number
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** Transient failures worth retrying: no response (network), 429 (honour Retry-After), 502/503/504. */
function isTransient(error: AxiosError): boolean {
  const status = error.response?.status
  return !error.response || status === 429 || status === 502 || status === 503 || status === 504
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

    // Automatic retry with exponential backoff + jitter. Safe for reads, and for writes because they carry an Idempotency-Key.
    if (original && isTransient(error) && !original.url?.includes('/api/auth/')) {
      const method = (original.method ?? 'get').toLowerCase()
      const safe = method === 'get' || (MUTATING.has(method) && !!original.headers['Idempotency-Key'])
      const attempt = original._attempt ?? 0
      if (safe && attempt < 3) {
        original._attempt = attempt + 1
        const retryAfter = Number(error.response?.headers?.['retry-after'])
        const delay = retryAfter > 0 ? Math.min(retryAfter, 10) * 1000 : Math.min(4000, 300 * 2 ** attempt) + Math.random() * 200
        await sleep(delay)
        return apiClient(original)
      }
    }

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
