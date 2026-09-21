import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 商品規格（SKU）管理（Sprint 178）。對齊後端 SkuDto。
// 背景：product_skus/product_inventory 先前在正式環境完全無法產生資料——沒有任何程式碼會建立
// ProductSku，導致依賴 SKU 的既有 ERP 庫存子系統（低庫存預警、採購單收貨入庫）實質上從未真正
// 運作過。本檔是唯一缺少的建立入口。

export type SkuStatus = 'ACTIVE' | 'INACTIVE'

export interface Sku {
  id: string
  listingId: string
  skuCode: string
  specName: string | null
  priceOverride: number | null
  status: SkuStatus
  totalQty: number
  reservedQty: number
  availableQty: number
  createdAt: string
  updatedAt: string
}

export interface CreateSkuRequest {
  skuCode: string
  specName?: string
  priceOverride?: number
}

// 後端 UpdateRequest 不含 skuCode——建立後代碼不可變更（比照 ShippingTemplateDto 既有慣例）
export interface UpdateSkuRequest {
  specName?: string
  priceOverride?: number
  status?: SkuStatus
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

class SkuService {
  async list(listingId: string): Promise<Sku[]> {
    const response = await apiClient.get<ApiResponse<Sku[]>>(API_ENDPOINTS.products.skus(listingId))
    return response.data.data
  }

  async create(listingId: string, request: CreateSkuRequest): Promise<Sku> {
    const response = await apiClient.post<ApiResponse<Sku>>(
      API_ENDPOINTS.products.skus(listingId),
      request
    )
    return response.data.data
  }

  async update(listingId: string, skuId: string, request: UpdateSkuRequest): Promise<Sku> {
    const response = await apiClient.put<ApiResponse<Sku>>(
      API_ENDPOINTS.products.updateSku(listingId, skuId),
      request
    )
    return response.data.data
  }
}

export default new SkuService()
