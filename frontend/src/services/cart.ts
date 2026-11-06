import apiClient, { type RequestOptions } from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 購物車（對齊後端 CartDto）。Sprint 331（DEF-455）：購物車頁、三個結帳頁與商品詳情頁原本各自 import apiClient 與 API_ENDPOINTS
// 直接發請求、各自宣告回應型別；請求形狀與型別集中到這裡。
// 注意：只有 cart.count 還在 services/listing.ts（既有位置，這輪不動）。

export interface CartItem {
  cartItemKey: string
  listingId: string
  listingName: string
  coverImageUrl: string | null
  quantity: number
  unitPrice: number
  subtotal: number
  listingType?: string
  startDate: string | null
  endDate: string | null
  // 動態定價調整（AI-2403 折扣 / AI-2406c 漲價）：規則生效時填入（unitPrice/subtotal 已為調整後）。
  // discountAmount 為有號差額（正=折扣、負=加價）；priceAdjustmentType 明示方向。
  originalUnitPrice?: number | null
  discountAmount?: number | null
  appliedRuleName?: string | null
  priceAdjustmentType?: string | null
  // Sprint 237（DEF-319 同店結帳）：商品／房源所屬的店鋪；房源已不存在時為 null（該項目無法結帳）
  storeId?: string | null
  storeName?: string | null
  // Sprint 239：店鋪是否營業中（非 ACTIVE 的店鋪不能下單，結帳會回 E-2010）
  storeActive?: boolean | null
}

// Sprint 237：後端 CartDto.StoreCartSummary——每家店鋪各自的小計、運費（該店鋪的運費模板）、已套用的促銷碼與折扣
export interface StoreSummary {
  storeId: string
  storeName?: string | null
  /** 店鋪是否營業中（Sprint 239）；false 時標示「暫停營業」並停用結帳。舊版回應沒有此欄位時視為營業中。 */
  storeActive?: boolean | null
  itemCount: number
  totalAmount: number
  shippingFee: number
  appliedPromoCode?: string | null
  discountAmount: number
  finalAmount: number
}

export interface CartResponse {
  cartId: string
  userId: string
  items: CartItem[]
  totalAmount: number
  // Sprint 146：後端 CartDto.CartResponse 以 @JsonProperty("totalItems") 序列化此欄位，
  // 先前前端宣告為 itemCount，導致「共 __ 項商品」永遠讀到 undefined
  totalItems: number
  // Sprint 101：後端一律回傳預估運費（PRODUCT 項目小計為基數），並讓 finalAmount 含運費
  shippingFee?: number
  appliedPromoCode?: string
  discountAmount?: number
  finalAmount?: number
  // Sprint 237：依店鋪分組的結帳摘要。購物車可以放多家店鋪的項目，但結帳、促銷碼、運費都以「一家店鋪」為單位
  stores?: StoreSummary[]
}

export interface CartItemUpdateResult {
  cartItemKey: string
  quantity: number
  subtotal: number
}

export interface PromoValidation {
  valid: boolean
  invalidReason?: string
}

export interface PromoApplyResult {
  storeId?: string
  appliedPromoCode: string
  shippingFee: number
  discountAmount: number
  finalAmount: number
}

/** 加入購物車：商品只要 listingId＋quantity；房源另帶入住／退房日（quantity 固定 1）。 */
export interface AddCartItemRequest {
  listingId: string
  quantity: number
  startDate?: string
  endDate?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

class CartService {
  async getCart(options?: RequestOptions): Promise<CartResponse> {
    const response = await apiClient.get<ApiResponse<CartResponse>>(API_ENDPOINTS.cart.get, options)
    return response.data.data
  }

  async addItem(request: AddCartItemRequest): Promise<void> {
    await apiClient.post(API_ENDPOINTS.cart.add, request)
  }

  async updateQuantity(cartItemKey: string, quantity: number): Promise<CartItemUpdateResult> {
    const response = await apiClient.put<ApiResponse<CartItemUpdateResult>>(
      API_ENDPOINTS.cart.update(cartItemKey),
      { quantity }
    )
    return response.data.data
  }

  async removeItem(cartItemKey: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.cart.remove(cartItemKey))
  }

  /** 促銷碼在店鋪驗證：多家店鋪時要指定是哪一家（storeId）。 */
  async validatePromo(code: string, storeId?: string): Promise<PromoValidation> {
    const response = await apiClient.get<ApiResponse<PromoValidation>>(
      `${API_ENDPOINTS.cart.validatePromo}?code=${encodeURIComponent(code)}${storeId ? `&storeId=${storeId}` : ''}`
    )
    return response.data.data
  }

  /** 沒有 storeId 時請求本文只有 promoCode（舊版單一店鋪購物車）；與搬出頁面之前的線上格式完全相同。 */
  async applyPromo(promoCode: string, storeId?: string): Promise<PromoApplyResult> {
    const response = await apiClient.post<ApiResponse<PromoApplyResult>>(
      API_ENDPOINTS.cart.applyPromo,
      storeId ? { promoCode, storeId } : { promoCode }
    )
    return response.data.data
  }

  async removePromo(storeId?: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.cart.removePromo, storeId ? { params: { storeId } } : undefined)
  }
}

const cartService = new CartService()
export default cartService
