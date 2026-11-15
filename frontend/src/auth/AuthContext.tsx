import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { apiClient } from '../lib/apiClient'
import { tokenStore } from '../lib/tokenStore'
import type { AuthResponse, MeResponse } from '../types/api'

interface AuthContextValue {
  user: MeResponse | null
  loading: boolean
  login: (email: string, password: string) => Promise<MeResponse>
  register: (email: string, password: string, fullName: string) => Promise<MeResponse>
  logout: () => void
  refreshMe: () => Promise<MeResponse>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null)
  const [loading, setLoading] = useState(true)

  async function refreshMe(): Promise<MeResponse> {
    const { data } = await apiClient.get<MeResponse>('/api/auth/me')
    setUser(data)
    return data
  }

  useEffect(() => {
    if (!tokenStore.getAccessToken()) {
      setLoading(false)
      return
    }
    refreshMe()
      .catch(() => {
        tokenStore.clear()
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  async function login(email: string, password: string): Promise<MeResponse> {
    const { data } = await apiClient.post<AuthResponse>('/api/auth/login', { email, password })
    tokenStore.setTokens(data.accessToken, data.refreshToken)
    return refreshMe()
  }

  async function register(email: string, password: string, fullName: string): Promise<MeResponse> {
    const { data } = await apiClient.post<AuthResponse>('/api/auth/register', { email, password, fullName })
    tokenStore.setTokens(data.accessToken, data.refreshToken)
    return refreshMe()
  }

  function logout() {
    const refreshToken = tokenStore.getRefreshToken()
    if (refreshToken) {
      apiClient.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
    }
    tokenStore.clear()
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, login, register, logout, refreshMe }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
