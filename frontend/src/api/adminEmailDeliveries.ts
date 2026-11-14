import { request } from './quiz'

export interface EmailDelivery {
  id: string
  eventType: string | null
  recipientEmail: string | null
  subject: string | null
  status: 'PENDING' | 'SENT' | 'FAILED'
  retryCount: number
  lastError: string | null
  scheduledAt: string | null
  sentAt: string | null
  dedupeKey: string | null
}

export interface EmailDeliveryPage {
  items: EmailDelivery[]
  page: number
  pageSize: number
  total: number
  hasNext: boolean
}

export function fetchFailedEmailDeliveries(page: number) {
  return request<EmailDeliveryPage>(`/api/admin/email-deliveries?page=${page}&page_size=20`)
}

export function replayFailedEmailDelivery(id: string) {
  return request<EmailDelivery>(`/api/admin/email-deliveries/${encodeURIComponent(id)}/replay`, {
    method: 'POST',
  })
}
