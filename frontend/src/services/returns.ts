import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 ReturnDto（Sprint 118，PRD §8.2.12/8.2.13，DEF-044）

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED' | 'CANCELLED'

export interface ReturnItem {
  id: string
  orderItemId: string
  skuId: string | null
  requestedQty: number
  /** 收貨確認後才有值 */
  sellableQty: number | null
  unsellableQty: number | null
}

export interface ReturnRequest {
  id: string
  returnNumber: string
  orderId: string
  customerId: string
  tenantId: string
  status: ReturnStatus
  reason: string | null
  rejectionReason: string | null
  reviewedAt: string | null
  receivedAt: string | null
  createdAt: string
  items: ReturnItem[]
}

export interface CreateReturnItem {
  orderItemId: string
  quantity: number
}

export interface CreateReturnInput {
  orderId: string
  reason?: string
  items: CreateReturnItem[]
}

export interface ReceiveReturnItem {
  itemId: string
  sellableQty: number
  unsellableQty: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const RETURN_STATUS_LABELS: Record<ReturnStatus, string> = {
  REQUESTED: '待審核',
  APPROVED: '已核准，待收貨',
  REJECTED: '已駁回',
  RECEIVED: '已完成',
  CANCELLED: '已撤回',
}

export type ReturnStatusBadgeVariant = 'default' | 'success' | 'destructive' | 'outline' | 'warning'

export function returnStatusBadgeVariant(status: ReturnStatus): ReturnStatusBadgeVariant {
  switch (status) {
    case 'RECEIVED':
      return 'success'
    case 'REJECTED':
      return 'destructive'
    case 'CANCELLED':
      return 'outline'
    case 'APPROVED':
      return 'default'
    default:
      return 'warning'
  }
}

class ReturnService {
  // ========== 買家層 ==========

  async createReturn(input: CreateReturnInput): Promise<ReturnRequest> {
    const response = await apiClient.post<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.returns.create,
      input
    )
    return response.data.data
  }

  async listMyReturns(page: number = 0, size: number = 20): Promise<PaginatedResponse<ReturnRequest>> {
    const response = await apiClient.get<ApiResponse<PaginatedResponse<ReturnRequest>>>(
      API_ENDPOINTS.returns.list,
      { params: { page, size } }
    )
    return response.data.data
  }

  async getReturn(id: string): Promise<ReturnRequest> {
    const response = await apiClient.get<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.returns.detail(id)
    )
    return response.data.data
  }

  async cancelReturn(id: string): Promise<ReturnRequest> {
    const response = await apiClient.post<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.returns.cancel(id)
    )
    return response.data.data
  }

  // ========== 店家層 ==========

  async listTenantReturns(page: number = 0, size: number = 20): Promise<PaginatedResponse<ReturnRequest>> {
    const response = await apiClient.get<ApiResponse<PaginatedResponse<ReturnRequest>>>(
      API_ENDPOINTS.dashboardReturns.list,
      { params: { page, size } }
    )
    return response.data.data
  }

  async getTenantReturn(id: string): Promise<ReturnRequest> {
    const response = await apiClient.get<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.dashboardReturns.detail(id)
    )
    return response.data.data
  }

  async approveReturn(id: string): Promise<ReturnRequest> {
    const response = await apiClient.post<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.dashboardReturns.approve(id)
    )
    return response.data.data
  }

  async rejectReturn(id: string, rejectionReason?: string): Promise<ReturnRequest> {
    const response = await apiClient.post<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.dashboardReturns.reject(id),
      rejectionReason ? { rejectionReason } : undefined
    )
    return response.data.data
  }

  async receiveReturn(id: string, items: ReceiveReturnItem[]): Promise<ReturnRequest> {
    const response = await apiClient.post<ApiResponse<ReturnRequest>>(
      API_ENDPOINTS.dashboardReturns.receive(id),
      { items }
    )
    return response.data.data
  }
}

export default new ReturnService()
