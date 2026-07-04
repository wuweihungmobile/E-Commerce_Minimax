import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

export interface Room {
  listingId: string
  tenantId: string
  title: string
  description: string
  location: string
  latitude: number
  longitude: number
  basePrice: number
  currency: string
  coverImageUrl: string
  status: string
  tags: string[]
  maxGuests: number
  amenities: string[]
  checkInTime: string
  checkOutTime: string
  roomCount: number
  // 開放窗（AI-2202e）：null = 無限制
  openUntilDate?: string | null
  bookingWindowDays?: number | null
  createdAt: string
  updatedAt: string
}

export interface RoomListItem {
  listingId: string
  title: string
  location: string
  basePrice: number
  currency: string
  coverImageUrl: string
  status: string
  maxGuests: number
  roomCount: number
  createdAt: string
}

export interface RoomFilters {
  maxGuests?: number
  location?: string
  keyword?: string
  page?: number
  size?: number
  sortBy?: string
  sortDir?: 'ASC' | 'DESC'
}

export interface CreateRoomRequest {
  title: string
  description?: string
  location: string
  latitude?: number
  longitude?: number
  basePrice: number
  coverImageUrl?: string
  tags?: string[]
  maxGuests?: number
  amenities?: string[]
  checkInTime?: string
  checkOutTime?: string
  roomCount?: number
  // 開放窗（AI-2202e）：開放至某固定日（YYYY-MM-DD）；未填 = 無限制
  openUntilDate?: string | null
  // 開放窗（AI-2202e）：開放未來 N 天（滾動）；未填 = 無限制。兩者取最早生效
  bookingWindowDays?: number | null
}

export interface UpdateRoomRequest extends CreateRoomRequest {
  status?: string
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
  data: T
  message?: string
  errors?: Array<{
    field: string
    message: string
    rejectedValue?: unknown
  }>
}

class RoomService {
  async getRooms(filters: RoomFilters = {}): Promise<PaginatedResponse<RoomListItem>> {
    const params = new URLSearchParams()
    if (filters.maxGuests) params.append('maxGuests', String(filters.maxGuests))
    if (filters.location) params.append('location', filters.location)
    if (filters.keyword) params.append('keyword', filters.keyword)
    if (filters.page !== undefined) params.append('page', String(filters.page))
    if (filters.size !== undefined) params.append('size', String(filters.size ?? 20))
    if (filters.sortBy) params.append('sortBy', filters.sortBy)
    if (filters.sortDir) params.append('sortDir', filters.sortDir)

    const response = await apiClient.get<ApiResponse<PaginatedResponse<RoomListItem>>>(
      API_ENDPOINTS.rooms.list + '?' + params.toString()
    )
    return response.data.data
  }

  async getRoom(id: string): Promise<Room> {
    const response = await apiClient.get<ApiResponse<Room>>(
      API_ENDPOINTS.rooms.detail(id)
    )
    return response.data.data
  }

  async createRoom(request: CreateRoomRequest): Promise<Room> {
    const response = await apiClient.post<ApiResponse<Room>>(
      API_ENDPOINTS.rooms.create,
      request
    )
    return response.data.data
  }

  async updateRoom(id: string, request: UpdateRoomRequest): Promise<Room> {
    const response = await apiClient.put<ApiResponse<Room>>(
      API_ENDPOINTS.rooms.update(id),
      request
    )
    return response.data.data
  }

  async deleteRoom(id: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.rooms.delete(id))
  }

  /** 清除開放窗（Sprint 57 AI-2202f）：將 openUntilDate/bookingWindowDays 皆清回無限制。 */
  async clearOpenWindow(id: string): Promise<Room> {
    const response = await apiClient.delete<ApiResponse<Room>>(
      API_ENDPOINTS.rooms.clearOpenWindow(id)
    )
    return response.data.data
  }
}

export default new RoomService()