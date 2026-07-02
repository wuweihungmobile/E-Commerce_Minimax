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
}

// booking 建立錯誤碼 → 可讀訊息。
// 注意：後端 ErrorCode 的 wire code 為「連字號」格式（Java 常數 E_4001 → JSON code "E-4001"）。
const BOOKING_ERROR_MESSAGES: Record<string, string> = {
  'E-4001': '所選日期已被預訂，請返回修改入住／退房日期',
  'E-4000': '找不到此房型，可能已下架',
  'E-4003': '日期範圍無效，請確認退房日晚於入住日',
  'E-4005': '入住人數超過房型容量，請減少人數',
  'E-3002': '此房型目前未開放預訂',
  'E-6005': '預訂處理中，請稍候再試',
  'E-9004': '請求格式錯誤，請重新嘗試',
}

export function bookingErrorMessage(code?: string | null): string {
  return (code && BOOKING_ERROR_MESSAGES[code]) || '預訂失敗，請稍後再試'
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

  async cancelBooking(id: string, reason?: string): Promise<void> {
    const url = reason
      ? API_ENDPOINTS.bookings.cancel(id) + '?reason=' + encodeURIComponent(reason)
      : API_ENDPOINTS.bookings.cancel(id)
    await apiClient.post(url)
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
