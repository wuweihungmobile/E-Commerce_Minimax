import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 SupportTicketDto（Sprint 91，PRD §6.10 M18 Phase 2-B）

export type TicketCategory = 'PRODUCT' | 'BOOKING' | 'PAYMENT' | 'TECHNICAL' | 'OTHER'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type SenderType = 'CUSTOMER' | 'STAFF' | 'SYSTEM'

export interface SupportMessage {
  id: string
  ticketId: string
  senderId: string
  senderType: SenderType
  message: string
  attachments?: string[]
  createdAt: string
}

export interface SupportTicket {
  id: string
  tenantId: string | null
  ticketNumber: string
  category: TicketCategory
  subject: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  customerId: string
  assignedTo: string | null
  orderId: string | null
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
  messages?: SupportMessage[]
}

export interface CreateTicketInput {
  category: TicketCategory
  subject: string
  description: string
  orderId?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface TicketListResponse {
  tickets: SupportTicket[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

class SupportService {
  async listMyTickets(page: number = 0, size: number = 20): Promise<TicketListResponse> {
    const response = await apiClient.get<ApiResponse<TicketListResponse>>(
      API_ENDPOINTS.support.tickets.list,
      { params: { page, size } }
    )
    return response.data.data
  }

  async createTicket(input: CreateTicketInput): Promise<SupportTicket> {
    const response = await apiClient.post<ApiResponse<SupportTicket>>(
      API_ENDPOINTS.support.tickets.create,
      input
    )
    return response.data.data
  }

  async getTicket(id: string): Promise<SupportTicket> {
    const response = await apiClient.get<ApiResponse<SupportTicket>>(
      API_ENDPOINTS.support.tickets.detail(id)
    )
    return response.data.data
  }

  async postMessage(ticketId: string, message: string): Promise<SupportMessage> {
    const response = await apiClient.post<ApiResponse<SupportMessage>>(
      API_ENDPOINTS.support.tickets.messages(ticketId),
      { message }
    )
    return response.data.data
  }
}

export default new SupportService()
