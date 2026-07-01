import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 訂單付款（對齊後端 OrderPaymentController + OrderPaymentStateDto，對 Mock）
// 使用訂單層端點（狀態機驅動），付款成功自動 CREATED → PAID

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'

export interface OrderPaymentState {
  orderId: string
  orderStatus: string
  paymentId: string | null
  paymentStatus: string | null
  transactionId: string | null
  nextValidStates: string | null
  canPay: boolean
  canCancel: boolean
  canRefund: boolean
  paidAt: string | null
  updatedAt: string | null
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: '待付款',
  SUCCESS: '付款成功',
  FAILED: '付款失敗',
  REFUNDED: '已退款',
}

class OrderPaymentService {
  async getPaymentState(orderId: string): Promise<OrderPaymentState> {
    const response = await apiClient.get<ApiResponse<OrderPaymentState>>(
      API_ENDPOINTS.orders.payment(orderId)
    )
    return response.data.data
  }

  // Mock 付款成功：訂單 CREATED → PAID
  async pay(orderId: string): Promise<OrderPaymentState> {
    const response = await apiClient.post<ApiResponse<OrderPaymentState>>(
      API_ENDPOINTS.orders.pay(orderId)
    )
    return response.data.data
  }

  // Mock 付款失敗：訂單維持 CREATED
  async payFail(orderId: string, reason?: string): Promise<OrderPaymentState> {
    const url = reason
      ? API_ENDPOINTS.orders.payFail(orderId) + '?reason=' + encodeURIComponent(reason)
      : API_ENDPOINTS.orders.payFail(orderId)
    const response = await apiClient.post<ApiResponse<OrderPaymentState>>(url)
    return response.data.data
  }
}

export default new OrderPaymentService()
