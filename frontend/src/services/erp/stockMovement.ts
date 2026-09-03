import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

/**
 * 庫存異動類型，值域即 PRD §6.7.4 異動類型表（後端 StockMovement.MovementType 與此一致）。
 *
 * 其中 INBOUND / OUTBOUND / RESERVE / RELEASE 由採購單與訂單流程自動產生，手動異動 API 會拒絕；
 * RETURN 的庫存語意 PRD 未定義（DEF-044），後端同樣拒絕新建，僅既有資料可能出現。
 * 可手動建立的子集見 MANUAL_MOVEMENT_TYPES。
 */
export type StockMovementType =
  | 'INBOUND'
  | 'OUTBOUND'
  | 'RESERVE'
  | 'RELEASE'
  | 'ADJUST_PLUS'
  | 'ADJUST_MINUS'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'SCRAP'
  | 'RETURN'

/**
 * 手動異動表單可選的型別，與後端 StockMovementService 的允許集合一一對應。
 *
 * 🔴 這份清單與後端是一組耦合：DEF-063 就是兩邊各自演化、中間沒有轉換層所造成
 * （下拉 7 個選項有 5 個含預設值必定 E_7005）。動這裡務必同步後端，
 * 後端 M16ErpE2ETest 的 E2E-M16-005b 與 StockMovementServiceTest 的 DEF-063 守衛會驗證這條耦合。
 */
export const MANUAL_MOVEMENT_TYPES = [
  'ADJUST_PLUS',
  'ADJUST_MINUS',
  'TRANSFER_IN',
  'TRANSFER_OUT',
  'SCRAP',
] as const

export type ManualStockMovementType = (typeof MANUAL_MOVEMENT_TYPES)[number]

/**
 * 列表數量顯示為 +N 的型別：PRD §6.7.4 方向欄為正向者。
 * 含 RESERVE——它加的是 reserved_qty 而非 total_qty，但方向同為正。
 */
export const INBOUND_MOVEMENT_TYPES: readonly StockMovementType[] = [
  'INBOUND',
  'RESERVE',
  'ADJUST_PLUS',
  'TRANSFER_IN',
  'RETURN',
]

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
  movementType: ManualStockMovementType
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