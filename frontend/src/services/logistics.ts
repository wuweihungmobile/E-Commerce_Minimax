import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 LogisticsDto（M11 物流追蹤）

export type LogisticsProvider = 'HCT' | 'TCAT'

export type LogisticsStatus =
  | 'PENDING'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED'

export interface CreateLogisticsRequest {
  orderId: string
  logisticsProvider: LogisticsProvider
  receiverName?: string
  receiverPhone?: string
  shippingAddress?: string
}

export interface LogisticsResponse {
  logisticsId: string
  orderId: string
  logisticsProvider: LogisticsProvider
  trackingNumber: string
  status: LogisticsStatus
  pickupTime: string | null
  deliveryTime: string | null
  shippingAddress: string | null
  receiverName: string | null
  receiverPhone: string | null
  createdAt: string
  updatedAt: string
}

export interface TrackingEvent {
  status: LogisticsStatus
  description: string
  location: string
  eventTime: string
}

export interface TrackingShippingAddress {
  receiverName: string | null
  phone: string | null
  address: string | null
}

export interface TrackingDetail {
  logisticsId: string
  trackingNumber: string
  logisticsProvider: LogisticsProvider
  currentStatus: LogisticsStatus
  shippingAddress: TrackingShippingAddress | null
  events: TrackingEvent[]
  updatedAt: string
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

export const LOGISTICS_STATUS_LABELS: Record<LogisticsStatus, string> = {
  PENDING: '待取件',
  PICKED_UP: '已取件',
  IN_TRANSIT: '配送中',
  OUT_FOR_DELIVERY: '配送員已出發',
  DELIVERED: '已簽收',
  FAILED: '配送失敗',
  RETURNED: '已退貨',
}

export const LOGISTICS_PROVIDER_LABELS: Record<LogisticsProvider, string> = {
  HCT: '黑貓宅急便',
  TCAT: '新竹物流',
}

class LogisticsService {
  /** 賣家/店主：建立物流單（出貨），訂單須為 CONFIRMED，成功後訂單自動轉為 SHIPPING */
  async createLogistics(request: CreateLogisticsRequest): Promise<LogisticsResponse> {
    const response = await apiClient.post<ApiResponse<LogisticsResponse>>(
      API_ENDPOINTS.logistics.create,
      request
    )
    return response.data.data
  }

  async getByOrder(orderId: string): Promise<LogisticsResponse[]> {
    const response = await apiClient.get<ApiResponse<LogisticsResponse[]>>(
      API_ENDPOINTS.logistics.byOrder(orderId)
    )
    return response.data.data ?? []
  }

  async getTrackingDetail(logisticsId: string): Promise<TrackingDetail> {
    const response = await apiClient.get<ApiResponse<TrackingDetail>>(
      API_ENDPOINTS.logistics.trackingDetail(logisticsId)
    )
    return response.data.data
  }
}

export default new LogisticsService()
