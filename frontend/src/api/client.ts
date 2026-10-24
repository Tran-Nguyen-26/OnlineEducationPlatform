import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'

export const apiClient = createClient<paths>({
  baseUrl: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach auth token from localStorage on every request
apiClient.use({
  onRequest({ request }) {
    const token = localStorage.getItem('access_token')
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
  onResponse({ response }) {
    if (response.status === 401) {
      localStorage.removeItem('access_token')
      window.location.href = '/login'
    }
    return response
  },
})

export default apiClient
