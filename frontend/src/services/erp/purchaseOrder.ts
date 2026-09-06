import apiClient from '@/lib/axios'
import { API_ENDPOINTS_ERP } from '@/lib/api_erp'

// ========== Types ==========

export type POStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED'

export interface PurchaseOrderItemDto {
  id: string
  listingId: string
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  receivedQuantity: number
  unitPrice: number
  subtotal: number
}

export interface PurchaseOrderDto {
  id: string
  poNumber: string
  supplierId: string
  supplierName: string
  status: POStatus
  totalAmount: number
  currency: string
  expectedDeliveryDate: string
  notes: string
  items: PurchaseOrderItemDto[]
  createdAt: string
  updatedAt: string
  reviewedBy?: string
  reviewedAt?: string
  rejectionReason?: string
}

export interface ListingOption {
  id: string
  title: string
  basePrice: number
  currency: string
}

export interface PurchaseOrderCreateRequest {
  supplierId: string
  // DEF-078（Sprint 129）：選填，後端 PurchaseOrderCreateRequest 亦為選填欄位
  expectedDeliveryDate?: string
  notes?: string
  items: Array<{
    // DEF-076（Sprint 129）：後端 @NotNull 要求 listingId，原本前端型別中完全沒有此欄位
    listingId: string
    quantity: number
    // DEF-077（Sprint 129）：後端欄位名為 unitCost，原本前端送 unitPrice 導致必定 400
    unitCost: number
  }>
}

export interface PurchaseOrderUpdateRequest {
  // 後端 PurchaseOrderUpdateRequest.java 僅支援 notes：items 需取消後重新建立，
  // expectedDeliveryDate 不允許在 update 時修改
  notes?: string
}

export interface PurchaseOrderReceiveRequest {
  items: Array<{
    itemId: string
    receivedQuantity: number
  }>
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

class PurchaseOrderService {
  async listPurchaseOrders(
    status?: POStatus,
    page: number = 0,
    size: number = 20
  ): Promise<PaginatedResponse<PurchaseOrderDto>> {
    const params = new URLSearchParams()
    if (status) params.append('status', status)
    params.append('page', String(page))
    params.append('size', String(size))

    const response = await apiClient.get<ApiResponse<PaginatedResponse<PurchaseOrderDto>>>(
      API_ENDPOINTS_ERP.purchaseOrders.list + '?' + params.toString()
    )
    return response.data.data
  }

  async listListingOptions(): Promise<ListingOption[]> {
    const response = await apiClient.get<ApiResponse<ListingOption[]>>(
      API_ENDPOINTS_ERP.purchaseOrders.listingOptions
    )
    return response.data.data
  }

  async getPurchaseOrder(id: string): Promise<PurchaseOrderDto> {
    const response = await apiClient.get<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.detail(id)
    )
    return response.data.data
  }

  async createPurchaseOrder(request: PurchaseOrderCreateRequest): Promise<PurchaseOrderDto> {
    const response = await apiClient.post<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.create,
      request
    )
    return response.data.data
  }

  async updatePurchaseOrder(id: string, request: PurchaseOrderUpdateRequest): Promise<PurchaseOrderDto> {
    const response = await apiClient.put<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.update(id),
      request
    )
    return response.data.data
  }

  async submitPurchaseOrder(id: string): Promise<PurchaseOrderDto> {
    const response = await apiClient.put<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.submit(id),
      {}
    )
    return response.data.data
  }

  async receivePurchaseOrder(id: string, request: PurchaseOrderReceiveRequest): Promise<PurchaseOrderDto> {
    const response = await apiClient.put<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.receive(id),
      request
    )
    return response.data.data
  }

  async cancelPurchaseOrder(id: string): Promise<PurchaseOrderDto> {
    const response = await apiClient.put<ApiResponse<PurchaseOrderDto>>(
      API_ENDPOINTS_ERP.purchaseOrders.cancel(id),
      {}
    )
    return response.data.data
  }
}

export default new PurchaseOrderService()