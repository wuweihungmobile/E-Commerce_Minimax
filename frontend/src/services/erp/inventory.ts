import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

/**
 * 庫存台帳一列（PRD §6.7.2）。
 *
 * Sprint 116（DEF-066）：後端資料來源由孤兒的 `inventory` 表改為真正有數字的 `product_inventory`。
 * 隨之移除 `location`——新來源沒有儲位資料，留著只會是永遠顯示「-」的欄位。
 */
export interface InventoryLedgerDto {
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  lowStockThreshold: number
  lastInboundDate: string | null
  lastOutboundDate: string | null
  updatedAt: string
}

export interface InventoryDetailDto {
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  lowStockThreshold: number
  lastInboundDate: string | null
  lastOutboundDate: string | null
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

/**
 * 低庫存預警。Sprint 116（DEF-066）：`product_inventory` 只有單一的低庫存門檻，
 * 沒有「補貨點」與「安全庫存」之分——硬填兩欄會讓畫面看起來有兩種門檻、實際是同一個數字。
 */
export interface LowStockAlertDto {
  skuId: string
  skuCode: string
  productName: string
  currentQuantity: number
  lowStockThreshold: number
  severity: 'LOW' | 'CRITICAL'
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