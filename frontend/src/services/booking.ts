import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 BookingDto.BookingResponse / BookingListResponse
// 與 domain/model/order/Booking.BookingStatus enum

export type BookingStatus =
  | 'CREATED'
  | 'PAID'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'CHECKED_OUT'
  | 'COMPLETED'
  | 'CANCELLED'

export type CanceledBy = 'CUSTOMER' | 'MERCHANT' | 'SYSTEM'
export type RefundStatus = 'NONE' | 'PENDING' | 'COMPLETED'

/** 取消預訂的結果（POST /v2/bookings/{id}/cancel，PRD §15.2.5）：這次取消有沒有退款、退多少 */
export interface CancelBookingResult {
  bookingId: string
  status: BookingStatus
  canceledAt: string
  canceledBy: CanceledBy
  refundStatus: RefundStatus
  refundAmount: number | null
}

export interface Booking {
  id: string
  tenantId: string
  userId: string
  roomListingId: string
  roomTitle: string | null
  coverImageUrl: string | null
  checkInDate: string
  checkOutDate: string
  guestCount: number
  status: BookingStatus
  totalAmount: number
  /** 付款期限（Sprint 225，DEF-311）；逾時仍未付款的訂房會被自動取消。null＝歷史訂房，不會逾時 */
  paymentDueAt: string | null
  /** 取消時間（Sprint 227，DEF-312）；未取消（或歷史取消）為 null。欄位名沿用 PRD §15.2.5 的拼法 */
  canceledAt: string | null
  /** 取消方：CUSTOMER 買家本人／MERCHANT 商家或管理員代為取消／SYSTEM 系統（逾時）；未取消為 null */
  canceledBy: CanceledBy | null
  /** 退款進度（PRD §15.2.5）：NONE 不需退款（未付款，或依 Q14 不退）／PENDING 等待自動退款／COMPLETED 已退回 */
  refundStatus: RefundStatus
  /** 應退金額（PRD Q14）；refundStatus 為 NONE 時為 null */
  refundAmount: number | null
  /** 下單當下套用的促銷碼（Sprint 124，DEF-047／PRD US-010）；null 表示未使用優惠券 */
  promoCode: string | null
  /** 下單當下的折扣金額（Sprint 124）；totalAmount 已扣除本欄位 */
  discountAmount: number
  currency: string
  guestName: string | null
  guestPhone: string | null
  guestEmail: string | null
  specialRequests: string | null
  nightsCount: number
  checkInTime: string | null
  checkOutTime: string | null
  createdAt: string
  updatedAt: string
}

// 注意：後端 BookingListResponse.roomTitle 目前未填充（回 null），前端須降級處理
export interface BookingListItem {
  id: string
  roomListingId: string
  roomTitle: string | null
  checkInDate: string
  checkOutDate: string
  guestCount: number
  status: BookingStatus
  totalAmount: number
  currency: string
  nightsCount: number
  createdAt: string
  /** 訂房人姓名（Sprint 231，DEF-316）：商家端列表用來識別是誰訂的；買家查自己的列表也會看到自己填的姓名 */
  guestName: string | null
}

// 對齊後端 BookingDto.CreateRequest（POST /v2/bookings）
export interface CreateBookingRequest {
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  guestCount: number
  guestName: string
  guestPhone?: string
  guestEmail?: string
  specialRequests?: string
  /** 選用：結帳時套用的促銷碼（Sprint 124，DEF-047／PRD US-010） */
  promoCode?: string
}

// 對齊後端 BookingDto.AvailabilityResponse（GET /v2/bookings/availability）
export interface AvailabilityResponse {
  available: boolean
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  nightsCount: number | null
  totalPrice: number | null
  currency: string | null
  unavailableReason: string | null
  // 動態定價調整（AI-2402 / AI-2406b）：有規則生效時填入，否則為 null。
  // discountAmount = 有號差額（正=折扣、負=加價）；priceAdjustmentType 明示方向（DISCOUNT/MARKUP/NONE）。
  originalTotalPrice?: number | null
  discountAmount?: number | null
  appliedRuleName?: string | null
  priceAdjustmentType?: string | null
}

// 對齊後端 BookingDto.CalendarResponse（GET /v2/bookings/calendar 每日一筆）
// NOT_OPEN（AI-2202e）：超過房源開放窗（open_until_date / booking_window_days）之未開放日，
// 由後端 getCalendar 計算補入（非持久化狀態）；前端灰底禁選、不刪除線（區別於已訂/封鎖）。
export type RoomCalendarStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED' | 'MAINTENANCE' | 'NOT_OPEN'

export interface CalendarDay {
  date: string // YYYY-MM-DD
  status: RoomCalendarStatus
  price: number | null
  bookingId: string | null
  // 動態定價每日調整（AI-2405b / AI-2406b）：可訂日有規則生效時填入，price 為調整後、originalPrice 為調整前。
  // 調整可為折扣（price < originalPrice）或加價（price > originalPrice）；priceAdjustmentType 明示方向。
  originalPrice?: number | null
  appliedRuleName?: string | null
  priceAdjustmentType?: string | null
}

// booking 建立錯誤碼 → 可讀訊息。
// 注意：後端 ErrorCode 的 wire code 為「連字號」格式（Java 常數 E_4001 → JSON code "E-4001"）。
const BOOKING_ERROR_MESSAGES: Record<string, string> = {
  'E-4001': '所選日期已被預訂，請返回修改入住／退房日期',
  'E-4000': '找不到此房型，可能已下架',
  'E-4003': '日期範圍無效，請確認退房日晚於入住日',
  'E-4005': '入住人數超過房型容量，請減少人數',
  'E-3002': '此房型目前未開放預訂',
  'E-2010': '此店鋪目前暫停營業，無法下單或付款',
  'E-6005': '預訂處理中，請稍候再試',
  'E-9004': '請求格式錯誤，請重新嘗試',
  // 促銷碼相關（Sprint 124，DEF-047），對齊 BookingService.resolveValidPromoForCheckout
  'E-5007': '促銷碼不存在或已停用',
  'E-5008': '促銷碼已過期',
  'E-5009': '促銷碼已達使用上限，請移除後重新預訂',
}

export function bookingErrorMessage(code?: string | null): string {
  return (code && BOOKING_ERROR_MESSAGES[code]) || '預訂失敗，請稍後再試'
}

// 對齊後端 BookingDto.StateLogResponse（GET /v2/dashboard/bookings/{id}/state-log，PRD §9.7，Sprint 246 DEF-350）
export interface BookingStateLogEntry {
  id: string
  bookingId: string
  action: string
  fromStatus: string | null
  toStatus: string | null
  changedBy: string | null
  reason: string | null
  createdAt: string
}

export interface BookingQuery {
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

// 前端顯示用：可取消狀態（對齊後端 OrderStateMachine.canCancel）。後端為最終權威。
const CANCELLABLE_STATUSES: ReadonlySet<BookingStatus> = new Set<BookingStatus>([
  'CREATED',
  'PAID',
  'CONFIRMED',
])

export function isBookingCancellable(status: BookingStatus): boolean {
  return CANCELLABLE_STATUSES.has(status)
}

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  CREATED: '待付款',
  PAID: '已付款',
  CONFIRMED: '已確認',
  CHECKED_IN: '已入住',
  CHECKED_OUT: '已退房',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export type BookingStatusBadgeVariant =
  | 'default'
  | 'secondary'
  | 'success'
  | 'destructive'
  | 'warning'
  | 'outline'

export function bookingStatusBadgeVariant(status: BookingStatus): BookingStatusBadgeVariant {
  switch (status) {
    case 'COMPLETED':
    case 'CHECKED_OUT':
      return 'success'
    case 'CANCELLED':
      return 'destructive'
    case 'CREATED':
      return 'warning'
    default:
      return 'default'
  }
}

/** 取消政策摘要（PRD §15.2.5／Q14）：確認預訂前與取消時都顯示（PRD US-012 驗收標準）。 */
export const CANCELLATION_POLICY_SUMMARY =
  '取消政策：入住前 24 小時（含）以上取消可全額退款；入住前不足 24 小時取消不退款；商家取消一律全額退款。'

/**
 * 取消結果的說明文字：告訴買家這次取消有沒有退款、退多少。
 * 回應的 NONE 分不出「沒付過款」與「付過款但依 Q14 不退」，所以由呼叫端告知取消前是否已付款；金額格式也由呼叫端提供。
 */
export function cancelResultMessage(
  result: CancelBookingResult,
  wasPaid: boolean,
  formatMoney: (amount: number) => string
): string {
  if (result.refundStatus === 'PENDING' && result.refundAmount != null) {
    return `預訂已取消，將退款 ${formatMoney(result.refundAmount)}，系統會自動退回原付款方式。`
  }
  return wasPaid ? '預訂已取消。入住前不足 24 小時，依取消政策本次不退款。' : '預訂已取消。'
}

class BookingService {
  async getBookings(query: BookingQuery = {}): Promise<PaginatedResponse<BookingListItem>> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append('page', String(query.page))
    if (query.size !== undefined) params.append('size', String(query.size))
    if (query.sortBy) params.append('sortBy', query.sortBy)
    if (query.sortDir) params.append('sortDir', query.sortDir)
    const qs = params.toString()
    const response = await apiClient.get<ApiResponse<PaginatedResponse<BookingListItem>>>(
      API_ENDPOINTS.bookings.list + (qs ? '?' + qs : '')
    )
    return response.data.data
  }

  async getBooking(id: string): Promise<Booking> {
    const response = await apiClient.get<ApiResponse<Booking>>(
      API_ENDPOINTS.bookings.detail(id)
    )
    return response.data.data
  }

  async cancelBooking(id: string, reason?: string): Promise<CancelBookingResult> {
    const url = reason
      ? API_ENDPOINTS.bookings.cancel(id) + '?reason=' + encodeURIComponent(reason)
      : API_ENDPOINTS.bookings.cancel(id)
    const response = await apiClient.post<ApiResponse<CancelBookingResult>>(url)
    return response.data.data
  }

  // ========== 店家層（Sprint 231，DEF-316） ==========

  // 店鋪訂房列表（GET /v2/dashboard/bookings，PRD §9.16）；取消沿用上面的 cancelBooking，
  // 後端 checkBookingOwnership 依租戶放行同一個端點。
  async listTenantBookings(query: BookingQuery = {}): Promise<PaginatedResponse<BookingListItem>> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append('page', String(query.page))
    if (query.size !== undefined) params.append('size', String(query.size))
    if (query.sortBy) params.append('sortBy', query.sortBy)
    if (query.sortDir) params.append('sortDir', query.sortDir)
    const qs = params.toString()
    const response = await apiClient.get<ApiResponse<PaginatedResponse<BookingListItem>>>(
      API_ENDPOINTS.dashboardBookings.list + (qs ? '?' + qs : '')
    )
    return response.data.data
  }

  // 店家標記入住（POST /v2/dashboard/bookings/{id}/check-in，Sprint 245 後端／246 前端，DEF-345／DEF-350）
  async checkIn(bookingId: string): Promise<Booking> {
    const response = await apiClient.post<ApiResponse<Booking>>(API_ENDPOINTS.dashboardBookings.checkIn(bookingId))
    return response.data.data
  }

  // 店家標記退房（同一交易內自動轉為 COMPLETED）
  async checkOut(bookingId: string): Promise<Booking> {
    const response = await apiClient.post<ApiResponse<Booking>>(API_ENDPOINTS.dashboardBookings.checkOut(bookingId))
    return response.data.data
  }

  // 訂房狀態機日誌（PRD §9.7，Sprint 246 DEF-350）
  async getStateLog(bookingId: string): Promise<BookingStateLogEntry[]> {
    const response = await apiClient.get<ApiResponse<BookingStateLogEntry[]>>(
      API_ENDPOINTS.dashboardBookings.stateLog(bookingId)
    )
    return response.data.data
  }

  // ROOM 日期可用性查詢（GET /v2/bookings/availability，S40 端點改 @RequestParam 後可用）。
  async checkAvailability(
    roomListingId: string,
    checkInDate: string,
    checkOutDate: string
  ): Promise<AvailabilityResponse> {
    const params = new URLSearchParams({ roomListingId, checkInDate, checkOutDate })
    const response = await apiClient.get<ApiResponse<AvailabilityResponse>>(
      API_ENDPOINTS.bookings.availability + '?' + params.toString()
    )
    return response.data.data
  }

  // ROOM 整月日曆查詢（GET /v2/bookings/calendar）。回傳區間內「已有記錄」的日期狀態；
  // 未回傳之日期由前端視為可預訂（AVAILABLE）。（Sprint 41 US-004 / AI-2202b）
  async getCalendar(
    roomListingId: string,
    startDate: string,
    endDate: string
  ): Promise<CalendarDay[]> {
    const params = new URLSearchParams({ roomListingId, startDate, endDate })
    const response = await apiClient.get<ApiResponse<CalendarDay[]>>(
      API_ENDPOINTS.bookings.calendar + '?' + params.toString()
    )
    return response.data.data
  }

  // 建立預訂（POST /v2/bookings）；idempotencyKey 供後端去重（避免重複送出）。
  async createBooking(request: CreateBookingRequest, idempotencyKey: string): Promise<Booking> {
    const response = await apiClient.post<ApiResponse<Booking>>(
      API_ENDPOINTS.bookings.create,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey } }
    )
    return response.data.data
  }
}

const bookingService = new BookingService()
export default bookingService
