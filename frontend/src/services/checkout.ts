import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import type { Order } from '@/services/order'
import type { Booking } from '@/services/booking'

// 對齊後端 CheckoutDto.MixedCheckoutRequest（POST /v2/checkout/mixed，Sprint 126/127，
// DEF-048 擴大範圍：購物車同時有 PRODUCT+ROOM 時一次結清）。
// roomListingId／checkInDate／checkOutDate 取自購物車的 ROOM 項目，不由前端重複帶入
// （後端直接讀購物車，避免與購物車實際內容不一致）。
export interface MixedCheckoutRequest {
  // PRODUCT 側收件資訊
  shippingAddress?: string
  shippingRecipientName?: string
  shippingPhone?: string
  addressId?: string
  notes?: string
  // ROOM 側訂房資訊
  guestCount: number
  guestName: string
  guestPhone?: string
  guestEmail?: string
  specialRequests?: string
  // 共用
  promoCode?: string
}

// 對齊後端 CheckoutDto.MixedCheckoutResponse
export interface MixedCheckoutResponse {
  order: Order
  booking: Booking
  promoCode: string | null
  totalDiscountAmount: number
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

// 合併結帳的錯誤碼 → 可讀訊息。與 order/booking 結帳共用 PRD §9.5.1／Sprint 124 建立的
// 促銷碼錯誤碼慣例（比照 bookingErrorMessage），另加庫存/日期衝突訊息（比照 product 結帳頁）。
const CHECKOUT_ERROR_MESSAGES: Record<string, string> = {
  'E-5004': '購物車項目不足，請確認同時有商品與房型項目',
  'E-3004': '部分商品庫存不足，請減少數量或稍後再試',
  'E-3002': '部分商品或房型已下架，請重新確認購物車',
  'E-4001': '所選日期已被預訂，請返回修改入住／退房日期',
  'E-4003': '日期範圍無效，請確認退房日晚於入住日',
  'E-4005': '入住人數超過房型容量，請減少人數',
  'E-8007': '無權使用此收件地址',
  'E-5007': '促銷碼不存在或已停用',
  'E-5008': '促銷碼已過期',
  'E-5009': '促銷碼已達使用上限，請移除後重新結帳',
  'E-6005': '結帳處理中，請稍候再試',
  'E-9004': '請求格式錯誤，請重新嘗試',
}

export function checkoutErrorMessage(code?: string | null): string {
  return (code && CHECKOUT_ERROR_MESSAGES[code]) || '合併結帳失敗，請稍後再試'
}

class CheckoutService {
  // 合併結帳（POST /v2/checkout/mixed）；idempotencyKey 供後端去重（比照 bookingService.createBooking）。
  async checkoutMixed(
    request: MixedCheckoutRequest,
    idempotencyKey: string
  ): Promise<MixedCheckoutResponse> {
    const response = await apiClient.post<ApiResponse<MixedCheckoutResponse>>(
      API_ENDPOINTS.checkout.mixed,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey } }
    )
    return response.data.data
  }
}

const checkoutService = new CheckoutService()
export default checkoutService
