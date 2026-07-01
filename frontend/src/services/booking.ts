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
}

export default new BookingService()
