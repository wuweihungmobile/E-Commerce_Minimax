import apiClient from "@/lib/axios"
import { API_ENDPOINTS } from "@/lib/api"

// 買家商品/房型（GET /v2/listings，回傳 Listing entity）
export interface Listing {
  id: string
  tenantId: string
  listingType: "PRODUCT" | "ROOM"
  title: string
  description?: string
  coverImageUrl?: string
  status: string
  basePrice: number
  currency: string
  tags?: string[]
  createdAt: string
  updatedAt: string
}

// ROOM 動態計價回應（GET /v2/listings/{id}/price?checkIn&checkOut）
export interface PriceBreakdown {
  date: string
  basePrice: number
  adjustedPrice: number
  appliedRuleName: string
  adjustmentType: 'PERCENTAGE' | 'FIXED_AMOUNT'
  adjustmentValue: number
}
export interface CalculatePriceResponse {
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  nights: number
  baseTotal: number
  adjustedTotal: number
  discount: number
  currency: string
  breakdown?: PriceBreakdown[]
}

// Spring Data Page 結構
export interface Page<T> {
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
}

export interface ListingQuery {
  page?: number
  size?: number
  type?: "product" | "room"
  keyword?: string
  sortBy?: string
  sortDir?: "ASC" | "DESC"
}

class ListingService {
  async getListings(query: ListingQuery = {}): Promise<Page<Listing>> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append("page", String(query.page))
    if (query.size !== undefined) params.append("size", String(query.size))
    if (query.type) params.append("type", query.type)
    if (query.keyword) params.append("keyword", query.keyword)
    if (query.sortBy) params.append("sortBy", query.sortBy)
    if (query.sortDir) params.append("sortDir", query.sortDir)

    const response = await apiClient.get<ApiResponse<Page<Listing>>>(
      API_ENDPOINTS.listings.list + "?" + params.toString()
    )
    return response.data.data
  }

  // 商品詳情（GET /v2/listings/{id}，需 product:read/room:read）
  async getListingById(id: string): Promise<Listing> {
    const response = await apiClient.get<ApiResponse<Listing>>(
      API_ENDPOINTS.listings.detail(id)
    )
    return response.data.data
  }

  // ROOM 動態計價（GET /v2/listings/{id}/price?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD）
  async getListingPrice(
    id: string,
    checkIn: string,
    checkOut: string
  ): Promise<CalculatePriceResponse> {
    const params = new URLSearchParams({ checkIn, checkOut })
    const response = await apiClient.get<ApiResponse<CalculatePriceResponse>>(
      API_ENDPOINTS.listings.detail(id) + "/price?" + params.toString()
    )
    return response.data.data
  }

  async getCartCount(): Promise<number> {
    const response = await apiClient.get<ApiResponse<number | { count: number }>>(
      API_ENDPOINTS.cart.count
    )
    const d = response.data.data
    return typeof d === "number" ? d : d?.count ?? 0
  }
}

const listingService = new ListingService()
export default listingService
