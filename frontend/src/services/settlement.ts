import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

export type SettlementStatus =
  | 'PENDING'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
  | 'FAILED'
  | 'REVERSAL_PENDING'
  | 'REVERSED'

export interface SettlementStatementDto {
  id: string
  statementNumber: string
  periodStart: string
  periodEnd: string
  totalOrders: number
  totalGmv: number
  totalRefunds: number
  commissionAmount: number
  netSettlementAmount: number
  currency: string
  status: SettlementStatus
  generatedAt: string
  reviewedAt?: string
  rejectionReason?: string
  approvedAt?: string
  paidAt?: string
  notes?: string
  adjustmentAmount?: number
  reversalInitiatedBy?: string
  reversalInitiatedByRole?: string
  reversalRequestedAt?: string
  reversalReason?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface SettlementStatementListResponse {
  statements: SettlementStatementDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

class SettlementService {
  async getReversalCandidates(
    tenantId?: string,
    page: number = 0,
    size: number = 20
  ): Promise<SettlementStatementListResponse> {
    const params: Record<string, string | number> = { page, size }
    if (tenantId) params.tenantId = tenantId

    const response = await apiClient.get<ApiResponse<SettlementStatementListResponse>>(
      API_ENDPOINTS.admin.settlements.reversalCandidates,
      { params }
    )
    return response.data.data
  }

  async initiateReversal(statementId: string, reason: string): Promise<SettlementStatementDto> {
    const response = await apiClient.post<ApiResponse<SettlementStatementDto>>(
      API_ENDPOINTS.admin.settlements.reverseInitiate(statementId),
      null,
      { params: { reason } }
    )
    return response.data.data
  }

  async confirmReversal(statementId: string): Promise<SettlementStatementDto> {
    const response = await apiClient.post<ApiResponse<SettlementStatementDto>>(
      API_ENDPOINTS.admin.settlements.reverseConfirm(statementId)
    )
    return response.data.data
  }
}

export default new SettlementService()
