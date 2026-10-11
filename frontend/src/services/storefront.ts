import apiClient from "@/lib/axios"
import { API_ENDPOINTS } from "@/lib/api"
import type { Listing, Page } from "@/services/listing"

// 店鋪前台公開資料（DEF-321(b)，Sprint 252）：GET /v2/public/stores/{slug}，匿名訪客可呼叫，
// 只回傳 ACTIVE 店鋪（SUSPENDED/待審核/slug 不存在皆回 404，不可枚舉）。
export interface StoreProfile {
  tenantId: string
  slug: string
  name: string
  description?: string
  logoUrl?: string
}

export interface StoreListingQuery {
  type?: "PRODUCT" | "ROOM"
  featured?: boolean
  page?: number
  size?: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

class StorefrontService {
  async getStoreProfile(slug: string): Promise<StoreProfile> {
    const response = await apiClient.get<ApiResponse<StoreProfile>>(
      API_ENDPOINTS.storefront.profile(slug)
    )
    return response.data.data
  }

  async getStoreListings(slug: string, query: StoreListingQuery = {}): Promise<Page<Listing>> {
    const params = new URLSearchParams()
    if (query.type) params.append("type", query.type)
    if (query.featured !== undefined) params.append("featured", String(query.featured))
    if (query.page !== undefined) params.append("page", String(query.page))
    if (query.size !== undefined) params.append("size", String(query.size))

    const response = await apiClient.get<ApiResponse<Page<Listing>>>(
      API_ENDPOINTS.storefront.listings(slug) + "?" + params.toString()
    )
    return response.data.data
  }
}

const storefrontService = new StorefrontService()
export default storefrontService
