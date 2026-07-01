import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 OrderDto.OrderResponse / OrderListResponse / OrderItemResponse / StateLogResponse
// 與 domain/model/order/Order.OrderStatus enum

export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'CONFIRMED'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDING'
  | 'REFUNDED'

export type OrderType = 'PRODUCT' | 'ROOM'

export interface OrderItem {
  id: string
  listingId: string
  listingTitle: string
  coverImageUrl: string | null
  skuId: string | null
  skuCode: string | null
  specName: string | null
  quantity: number
  unitPrice: number
  subtotal: number
}

export interface Order {
  id: string
  tenantId: string
  userId: string
  orderType: OrderType
  status: OrderStatus
  totalAmount: number
  shippingFee: number
  currency: string
  shippingAddress: string | null
  shippingRecipientName: string | null
  shippingPhone: string | null
  notes: string | null
  guestCount: number | null
  guestName: string | null
  guestPhone: string | null
  guestEmail: string | null
  items: OrderItem[]
  createdAt: string
  updatedAt: string
}

export interface OrderListItem {
  id: string
  orderType: OrderType
  status: OrderStatus
  totalAmount: number
  currency: string
  itemCount: number
  createdAt: string
}

export interface OrderStateLog {
  id: string
  orderId: string
  sequence: number
  fromStatus: string | null
  toStatus: string
  changedBy: string | null
  reason: string | null
  createdAt: string
}

export interface OrderQuery {
  page?: number
  size?: number
  sortBy?: string
  sortDir?: 'ASC' | 'DESC'
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

// 前端顯示用：可取消狀態（出貨前）。後端 OrderStateMachine 為最終權威，
// 送出後若後端拒絕則以錯誤訊息呈現。
const CANCELLABLE_STATUSES: ReadonlySet<OrderStatus> = new Set<OrderStatus>([
  'CREATED',
  'PAID',
  'CONFIRMED',
])

export function isCancellable(status: OrderStatus): boolean {
  return CANCELLABLE_STATUSES.has(status)
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: '待付款',
  PAID: '已付款',
  CONFIRMED: '已確認',
  SHIPPING: '運送中',
  DELIVERED: '已送達',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

export type OrderStatusBadgeVariant =
  | 'default'
  | 'secondary'
  | 'success'
  | 'destructive'
  | 'warning'
  | 'outline'

export function orderStatusBadgeVariant(status: OrderStatus): OrderStatusBadgeVariant {
  switch (status) {
    case 'COMPLETED':
    case 'DELIVERED':
      return 'success'
    case 'CANCELLED':
    case 'REFUNDED':
      return 'destructive'
    case 'CREATED':
    case 'REFUNDING':
      return 'warning'
    default:
      return 'default'
  }
}

class OrderService {
  async getOrders(query: OrderQuery = {}): Promise<PaginatedResponse<OrderListItem>> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append('page', String(query.page))
    if (query.size !== undefined) params.append('size', String(query.size))
    if (query.sortBy) params.append('sortBy', query.sortBy)
    if (query.sortDir) params.append('sortDir', query.sortDir)
    const qs = params.toString()
    const response = await apiClient.get<ApiResponse<PaginatedResponse<OrderListItem>>>(
      API_ENDPOINTS.orders.list + (qs ? '?' + qs : '')
    )
    return response.data.data
  }

  async getOrder(id: string): Promise<Order> {
    const response = await apiClient.get<ApiResponse<Order>>(
      API_ENDPOINTS.orders.detail(id)
    )
    return response.data.data
  }

  async cancelOrder(id: string, reason?: string): Promise<Order> {
    const url = reason
      ? API_ENDPOINTS.orders.cancel(id) + '?reason=' + encodeURIComponent(reason)
      : API_ENDPOINTS.orders.cancel(id)
    const response = await apiClient.post<ApiResponse<Order>>(url)
    return response.data.data
  }

  async getOrderLogs(id: string): Promise<OrderStateLog[]> {
    const response = await apiClient.get<ApiResponse<OrderStateLog[]>>(
      API_ENDPOINTS.orders.logs(id)
    )
    return response.data.data
  }
}

export default new OrderService()
