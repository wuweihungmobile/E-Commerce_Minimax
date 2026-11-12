// 公開頁伺服器端渲染（SEO，DEF-500，Sprint 341）共用的取資料步驟：頁面與 layout 都呼叫，同一個請求內相同路徑只會打一次後端
// （server-api 的請求內快取）。呼叫端是 Server Component，所以可以直接用 notFound()。

import { notFound } from 'next/navigation'
import type { CategoryListResponse, ListingCardResponse, PostListResponse } from '@/services/cms'
import type { StoreProfile } from '@/services/storefront'
import { embeddedListingIds } from './article-embeds'
import {
  LIST_PAGE_SIZE,
  listingCardPath,
  storeCategoriesPath,
  storePostsPath,
  storeProfilePath,
  UUID_PATTERN,
} from './public-api'
import { dataOrUndefined, fetchPublic } from './server-api'

/**
 * 店鋪的公開資料，給 stores/[slug]/layout.tsx 用——「店鋪存在嗎」只由 layout 這一處判定。
 * 店鋪不存在（對匿名訪客，停權、未審核與不存在刻意不分，後端一律 404）→ notFound()：回真正的 HTTP 404，
 * 搜尋引擎不會把「找不到店鋪」畫面當成內容收錄（soft 404），Next 也會加上 noindex。
 * 後端暫時不可用（逾時、429、5xx）→ undefined，絕不是 404：好好的店鋪不能因為後端一時壞了就被搜尋引擎除名。
 */
export async function loadStoreProfile(slug: string): Promise<StoreProfile | undefined> {
  const result = await fetchPublic<StoreProfile>(storeProfilePath(slug))
  if (result.status === 'notfound') notFound()
  return dataOrUndefined(result)
}

/**
 * 店鋪的公開資料，給店鋪底下各頁（首頁、商品、房源、貼文、文章）用：不存在或不可用都回 undefined，不丟 notFound()。
 * 為什麼頁面不自己判定店鋪不存在：layout 已經判定了；頁面再拋一次，會被離頁面最近的 not-found 邊界接走——
 * 文章頁就會把「店鋪不存在」說成「找不到文章」（兩個畫面的文字與 E2E 預期不同）。同一個請求內與 layout 取的是同一份（請求內快取）。
 * 取不到時頁面不帶初始資料，也不再打其餘請求（店鋪不存在或後端壞了，多打也沒有用）。
 */
export async function peekStoreProfile(slug: string): Promise<StoreProfile | undefined> {
  return dataOrUndefined(await fetchPublic<StoreProfile>(storeProfilePath(slug)))
}

export interface StoreBlogData {
  categories?: CategoryListResponse
  posts?: PostListResponse
}

/**
 * 店鋪貼文列表頁的伺服器端資料：啟用中的分類，與這一頁的貼文（可帶分類篩選）。取不到的部分是 undefined，元件會自己由瀏覽器載入。
 * 規則與 StoreBlogList 相同：指定了分類，要先確認它存在且啟用才取貼文——網址亂填、已停用的分類編號不會送出貼文請求，
 * 元件顯示「此分類不存在或已停用」。沒指定分類時分類與貼文互不依賴，一起取。tenantId 來自後端回應，先確認是 UUID 才放進請求。
 */
export async function loadStoreBlog(tenantId: string, page: number, categoryId?: string): Promise<StoreBlogData> {
  if (!UUID_PATTERN.test(tenantId)) return {}
  const categoriesRequest = fetchPublic<CategoryListResponse>(storeCategoriesPath(tenantId))
  if (!categoryId) {
    const [categories, posts] = await Promise.all([
      categoriesRequest,
      fetchPublic<PostListResponse>(storePostsPath(tenantId, { page, size: LIST_PAGE_SIZE })),
    ])
    return { categories: dataOrUndefined(categories), posts: dataOrUndefined(posts) }
  }
  const categories = dataOrUndefined(await categoriesRequest)
  if (!categories?.categories.some((category) => category.id === categoryId)) return { categories }
  const posts = await fetchPublic<PostListResponse>(storePostsPath(tenantId, { page, size: LIST_PAGE_SIZE, categoryId }))
  return { categories, posts: dataOrUndefined(posts) }
}

/** 一篇文章最多先取幾張嵌入卡片：多出來的由瀏覽器自己載入（文章內文是店家寫的，數量沒有上限，不能讓一篇文章拖住整個頁面）。 */
const MAX_EMBEDDED_CARDS = 12

/**
 * 文章內嵌的商品／房源卡片，先取好給第一份 HTML 用（卡片是文章內容的一部分，搜尋引擎應該看得到）。
 * 一次全部同時取；取不到的（商品已下架、後端暫時不可用）不放進結果，元件會自己載入並顯示「此商品已下架」或錯誤。
 */
export async function loadEmbeddedCards(content: string): Promise<Record<string, ListingCardResponse>> {
  const ids = embeddedListingIds(content)
    .filter((id) => UUID_PATTERN.test(id))
    .slice(0, MAX_EMBEDDED_CARDS)
  const results = await Promise.all(ids.map((id) => fetchPublic<ListingCardResponse>(listingCardPath(id))))
  const cards: Record<string, ListingCardResponse> = {}
  ids.forEach((id, index) => {
    const card = dataOrUndefined(results[index])
    if (card) cards[id] = card
  })
  return cards
}
