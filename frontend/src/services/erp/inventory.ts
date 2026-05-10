import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

export interface InventoryLedgerDto {
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  location: string
  lastInboundDate: string
  lastOutboundDate: string
}

export interface InventoryDetailDto {
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  location: string
  reorderPoint: number
  safetyStock: number
  lastInboundDate: string
  lastOutboundDate: string
  movements: StockMovementSummary[]
}

export interface StockMovementSummary {
  id: string
  movementType: string
  quantity: number
  referenceNumber: string
  notes: string
  createdAt: string
}

export interface LowStockAlertDto {
  skuId: string
  skuCode: string
  productName: string
  currentQuantity: number
  reorderPoint: number
  safetyStock: number
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

class InventoryService {
  async getInventoryLedger(page: number = 0, size: number = 50): Promise<PaginatedResponse<InventoryLedgerDto>> {
    const params = new URLSearchParams()
    params.append('page', String(page))
    params.append('size', String(size))

    const response = await apiClient.get<ApiResponse<PaginatedResponse<InventoryLedgerDto>>>(
      API_ENDPOINTS_ERP.inventory.list + '?' + params.toString()
    )
    return response.data.data
  }

  async getInventoryDetail(skuId: string): Promise<InventoryDetailDto> {
    const response = await apiClient.get<ApiResponse<InventoryDetailDto>>(
      API_ENDPOINTS_ERP.inventory.detail(skuId)
    )
    return response.data.data
  }

  async getLowStockAlerts(): Promise<LowStockAlertDto[]> {
    const response = await apiClient.get<ApiResponse<LowStockAlertDto[]>>(
      API_ENDPOINTS_ERP.inventory.alerts
    )
    return response.data.data
  }
}

export default new InventoryService()