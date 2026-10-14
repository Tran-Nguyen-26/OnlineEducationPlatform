import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'

export const apiClient = createClient<paths>({
  baseUrl: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

function isPublicAuthEndpoint(url: string, schemaPath?: string): boolean {
  if (schemaPath === '/auth/change-password' || url.includes('/auth/change-password')) {
    return false
  }
  return (
    Boolean(schemaPath && schemaPath.startsWith('/auth/')) ||
    url.includes('/api/auth/') ||
    url.includes('/auth/')
  )
}

// Attach auth token from localStorage on every request (except public auth endpoints)
apiClient.use({
  onRequest({ request, schemaPath }) {
    if (!isPublicAuthEndpoint(request.url, schemaPath)) {
      const token = localStorage.getItem('access_token')
      if (token) {
        request.headers.set('Authorization', `Bearer ${token}`)
      }
    }
    return request
  },
  onResponse({ request, response, schemaPath }) {
    if (response.status === 401) {
      if (!isPublicAuthEndpoint(request.url, schemaPath) && window.location.pathname !== '/login') {
        localStorage.removeItem('access_token')
        window.location.href = '/login'
      }
    }
    return response
  },
})

export default apiClient
