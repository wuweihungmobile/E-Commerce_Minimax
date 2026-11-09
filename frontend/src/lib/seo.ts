// 公開頁的標題與分享資訊（DEF-475，Sprint 335）：動態路由頁原本只有靜態的頁型標題（「店鋪首頁」「商品／房源詳情」），
// 搜尋結果與社群分享看到的每一頁都一樣。這裡在伺服器端用後端的公開 API 取實體名稱，組成 generateMetadata 的回傳值。
//
// 範圍：店鋪前台（/stores/[slug] 與 products／rooms／blog 子頁）與商品／房源詳情（/listings/[id]）。
// 不含 /blog/[slug]（文章頁）：後端取單篇文章要 tenantId，而目前 tenantId 只存在瀏覽器的 localStorage，伺服器端查不到（見 SPRINT_335_PLAN §3）。
//
// 失敗一律退回呼叫端傳進來的靜態標題（只有 title、沒有殘缺的 Open Graph）：標題請求不能讓頁面壞掉。

import type { Metadata } from 'next'
import { fetchPublicData } from './server-api'
import { SITE_NAME } from './site'

const DESCRIPTION_MAX = 160
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

interface StoreProfile {
  name?: unknown
  description?: string | null
  logoUrl?: string | null
}

interface ListingDetail {
  title?: unknown
  description?: string | null
  coverImageUrl?: string | null
}

const isNonEmptyString = (value: unknown): value is string => typeof value === 'string' && value.trim() !== ''

/** 說明文字：壓掉多餘空白、截成 160 字（搜尋結果與分享卡片顯示的長度上限附近）；空的回 undefined（不帶 description）。 */
function toDescription(text: string | null | undefined): string | undefined {
  const collapsed = (text ?? '').replace(/\s+/g, ' ').trim()
  if (collapsed === '') return undefined
  return collapsed.length > DESCRIPTION_MAX ? `${collapsed.slice(0, DESCRIPTION_MAX - 1)}…` : collapsed
}

/** 只收絕對的 http(s) 網址：相對路徑可能是需要登入的內部網址（例如 /api/v2/media/files/...），社群爬蟲抓不到。 */
const toImages = (url: string | null | undefined): string[] | undefined =>
  typeof url === 'string' && /^https?:\/\//i.test(url) ? [url] : undefined

function build(title: string, description: string | undefined, images: string[] | undefined, url: string): Metadata {
  return {
    title,
    ...(description ? { description } : {}),
    openGraph: {
      title,
      ...(description ? { description } : {}),
      siteName: SITE_NAME,
      type: 'website',
      url,
      ...(images ? { images } : {}),
    },
  }
}

export interface StoreMetadataOptions {
  /** 子頁的區段名稱（「商品」「房源」「部落格」）；首頁不帶 */
  section?: string
  /** 取不到店名時用的靜態標題（原本的頁型標題） */
  fallbackTitle: string
  /** 頁面在 /stores/<slug> 之後的路徑：'' 、'/products'、'/rooms'、'/blog' */
  path: string
}

export async function storeMetadata(slug: string, options: StoreMetadataOptions): Promise<Metadata> {
  const encoded = encodeURIComponent(slug) // slug 來自網址，編碼後才放進伺服器端請求的路徑
  const store = await fetchPublicData<StoreProfile>(`/v2/public/stores/${encoded}`)
  if (!store || !isNonEmptyString(store.name)) return { title: options.fallbackTitle }
  const name = store.name.trim()
  const title = options.section ? `${name}｜${options.section}` : name
  return build(title, toDescription(store.description), toImages(store.logoUrl), `/stores/${encoded}${options.path}`)
}

export async function listingMetadata(id: string, fallbackTitle: string): Promise<Metadata> {
  if (!UUID_PATTERN.test(id)) return { title: fallbackTitle } // 不是 UUID 的一定查不到，不必打後端
  const listing = await fetchPublicData<ListingDetail>(`/v2/listings/${id}`)
  if (!listing || !isNonEmptyString(listing.title)) return { title: fallbackTitle }
  return build(listing.title.trim(), toDescription(listing.description), toImages(listing.coverImageUrl), `/listings/${id}`)
}
