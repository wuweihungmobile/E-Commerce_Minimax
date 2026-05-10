import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

export type StockMovementType =
  | 'INBOUND'
  | 'OUTBOUND'
  | 'ADJUST_PLUS'
  | 'ADJUST_MINUS'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'SCRAP'

export interface StockMovementDto {
  id: string
  skuId: string
  skuCode: string
  productName: string
  movementType: StockMovementType
  quantity: number
  referenceNumber: string
  notes: string
  createdAt: string
  createdBy: string
}

export interface StockMovementRequest {
  skuId: string
  movementType: StockMovementType
  quantity: number
  referenceNumber?: string
  notes?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  errors?: Array<{
    field: string
    message: string
    rejectedValue?: unknown
  }>
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

class StockMovementService {
  async getStockMovements(
    page: number = 0,
    size: number = 50
  ): Promise<PaginatedResponse<StockMovementDto>> {
    const params = new URLSearchParams()
    params.append('page', String(page))
    params.append('size', String(size))

    const response = await apiClient.get<ApiResponse<PaginatedResponse<StockMovementDto>>>(
      API_ENDPOINTS_ERP.stockMovements.list + '?' + params.toString()
    )
    return response.data.data
  }

  async createStockMovement(request: StockMovementRequest): Promise<StockMovementDto> {
    const response = await apiClient.post<ApiResponse<StockMovementDto>>(
      API_ENDPOINTS_ERP.stockMovements.create,
      request
    )
    return response.data.data
  }
}

export default new StockMovementService()