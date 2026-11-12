// 公開頁 SEO 的伺服器端渲染（DEF-500，Sprint 341）要打的後端路徑、每頁筆數與排序對照。
//
// 為什麼獨立一個檔案：伺服器端（Server Component、generateMetadata）不能用 services／axios（那是給瀏覽器用的，見 lib/server-api.ts），
// 但它取的資料必須和瀏覽器端取的是「同一批端點、同一組查詢參數」——否則伺服器端渲染出來的第一頁與瀏覽器之後載入的不一致
// （換頁後內容跳掉，或爬蟲看到的與使用者看到的不同）。兩邊請求網址一致由 lib/public-api.test.ts 的契約測試守門。
//
// 伺服器端路徑一律對來自網址的值編碼（slug、id、關鍵字）；tenantId 與 id 先確認長得像 UUID 才放進路徑／查詢字串，
// 不信任任何來自網址或後端回應的任意字串（與 lib/seo.ts 同一原則）。
//
// 路徑字串必須「逐字相同」才能被 server-api 的請求內快取合併：generateMetadata 與頁面本體取同一份資料時只打一次後端，
// 所以 seo.ts 的標題請求也一律用這裡的函式組路徑。

import { API_ENDPOINTS } from './api'

export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** 首頁、商品、房源、店鋪商品／房源、貼文列表每頁的筆數（原本各元件各自宣告 PAGE_SIZE = 12，伺服器端與瀏覽器端必須一致）。 */
export const LIST_PAGE_SIZE = 12
/** 店鋪首頁「精選商品／房源」區塊的筆數 */
export const FEATURED_LIMIT = 8

/** 排序頁籤 → 後端的排序欄位與方向（首頁與商品／房源列表共用；沒有對到的頁籤一律當「最新」）。 */
export const SORT_MAP: Record<string, { sortBy: string; sortDir: 'ASC' | 'DESC' }> = {
  newest: { sortBy: 'createdAt', sortDir: 'DESC' },
  priceAsc: { sortBy: 'basePrice', sortDir: 'ASC' },
  priceDesc: { sortBy: 'basePrice', sortDir: 'DESC' },
}

export interface PublicListingsQuery {
  page: number
  size: number
  type?: 'product' | 'room'
  keyword?: string
  sortBy?: string
  sortDir?: 'ASC' | 'DESC'
}

/** 跨店鋪公開商品／房源列表（首頁、/products、/rooms）。參數順序與 services/listing.ts 的 getPublicListings 相同。 */
export function publicListingsPath(query: PublicListingsQuery): string {
  const params = new URLSearchParams()
  params.append('page', String(query.page))
  params.append('size', String(query.size))
  if (query.type) params.append('type', query.type)
  if (query.keyword) params.append('keyword', query.keyword)
  if (query.sortBy) params.append('sortBy', query.sortBy)
  if (query.sortDir) params.append('sortDir', query.sortDir)
  return `${API_ENDPOINTS.publicListings.list}?${params.toString()}`
}

/** 店鋪的公開資料（店名、tenantId…）。與 lib/seo.ts 的標題請求是同一條路徑。 */
export function storeProfilePath(slug: string): string {
  return API_ENDPOINTS.storefront.profile(encodeURIComponent(slug))
}

export interface StoreListingsQuery {
  type?: 'PRODUCT' | 'ROOM'
  featured?: boolean
  page?: number
  size?: number
}

/** 單一店鋪的商品／房源列表。參數順序與 services/storefront.ts 的 getStoreListings 相同。 */
export function storeListingsPath(slug: string, query: StoreListingsQuery = {}): string {
  const params = new URLSearchParams()
  if (query.type) params.append('type', query.type)
  if (query.featured !== undefined) params.append('featured', String(query.featured))
  if (query.page !== undefined) params.append('page', String(query.page))
  if (query.size !== undefined) params.append('size', String(query.size))
  return `${API_ENDPOINTS.storefront.listings(encodeURIComponent(slug))}?${params.toString()}`
}

/** 跨店鋪公開貼文列表（/blog）。 */
export function publicPostFeedPath(page: number, size: number): string {
  return `/v2/public/posts?page=${page}&size=${size}`
}

/** 單一店鋪的已發布貼文列表；tenantId 必須是 UUID（呼叫端先檢查）。 */
export function storePostsPath(tenantId: string, query: { page: number; size: number; categoryId?: string }): string {
  const category = query.categoryId ? `&categoryId=${encodeURIComponent(query.categoryId)}` : ''
  return `/v2/posts?tenantId=${tenantId}&page=${query.page}&size=${query.size}${category}`
}

/** 單一店鋪啟用中的貼文分類；tenantId 必須是 UUID。 */
export function storeCategoriesPath(tenantId: string): string {
  return `/v2/posts/categories?tenantId=${tenantId}`
}

/**
 * 依 slug 取單篇已發布文章；tenantId 必須是 UUID。
 * 伺服器端一律帶 countView=false：伺服器端渲染（含爬蟲、連結預覽）不是一次瀏覽，瀏覽器載入頁面時才計一次
 * （見 components/storefront/StoreArticle 與後端 PostService.getPublishedPostBySlug）。
 */
export function postBySlugPath(postSlug: string, tenantId: string): string {
  return `/v2/posts/${encodeURIComponent(postSlug)}?tenantId=${tenantId}&countView=false`
}

/** 商品／房源詳情；id 必須是 UUID。 */
export function listingPath(id: string): string {
  return API_ENDPOINTS.listings.detail(id)
}

/** 文章內嵌的商品／房源卡片；id 必須是 UUID。 */
export function listingCardPath(id: string): string {
  return `${API_ENDPOINTS.listings.detail(id)}/card`
}
