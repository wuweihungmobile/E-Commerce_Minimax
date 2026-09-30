import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import type { OrderPaymentState } from '@/services/payment'

// 訂房付款（對齊後端 BookingPaymentController，Sprint 221，DEF-303 (1)）
// 付款狀態沿用既有的 GET /v2/orders/bookings/{id}/payment，回應與訂單付款同型
// （OrderPaymentStateDto：orderId／orderStatus 兩個欄位裝的是訂房 id／訂房狀態）。

// 對齊後端 CheckoutSessionResponse（訂房為 bookingId，訂單為 orderId）
export interface BookingCheckoutSessionResponse {
  bookingId: string
  sessionId: string
  sessionUrl: string
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

class BookingPaymentService {
  async getPaymentState(bookingId: string): Promise<OrderPaymentState> {
    const response = await apiClient.get<ApiResponse<OrderPaymentState>>(
      API_ENDPOINTS.bookings.payment(bookingId)
    )
    return response.data.data
  }

  // Mock 付款成功：訂房 CREATED → PAID（啟用 Stripe 時後端拒絕，前端不會顯示此按鈕）
  async pay(bookingId: string): Promise<OrderPaymentState> {
    const response = await apiClient.post<ApiResponse<OrderPaymentState>>(
      API_ENDPOINTS.bookings.pay(bookingId)
    )
    return response.data.data
  }

  // 真實金流：建立 Stripe Checkout Session，回前端重導 URL
  async createCheckoutSession(bookingId: string): Promise<BookingCheckoutSessionResponse> {
    const response = await apiClient.post<ApiResponse<BookingCheckoutSessionResponse>>(
      API_ENDPOINTS.bookings.payCheckout(bookingId)
    )
    return response.data.data
  }

  // 真實金流：Checkout 回跳後以 sessionId 確認狀態
  async confirmCheckoutReturn(bookingId: string, sessionId: string): Promise<OrderPaymentState> {
    const response = await apiClient.get<ApiResponse<OrderPaymentState>>(
      API_ENDPOINTS.bookings.payCheckoutReturn(bookingId) + '?sessionId=' + encodeURIComponent(sessionId)
    )
    return response.data.data
  }
}

const bookingPaymentService = new BookingPaymentService()
export default bookingPaymentService
