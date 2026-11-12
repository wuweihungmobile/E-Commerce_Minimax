// 公開頁的標題與分享資訊（DEF-475，Sprint 335）：動態路由頁原本只有靜態的頁型標題（「店鋪首頁」「商品／房源詳情」），
// 搜尋結果與社群分享看到的每一頁都一樣。這裡在伺服器端用後端的公開 API 取實體名稱，組成 generateMetadata 的回傳值。
//
// 範圍：店鋪前台（/stores/[slug] 與 products／rooms／blog 子頁）、商品／房源詳情（/listings/[id]），
// 以及文章頁（/stores/[slug]/blog/[postSlug]，Sprint 336，DEF-483）。
// 文章頁原本不在範圍內：舊網址 /blog/[slug] 沒有店鋪，後端取單篇文章要 tenantId，而 tenantId 只存在瀏覽器的 localStorage，
// 伺服器端查不到（見 SPRINT_335_PLAN §3）。網址改成帶店鋪之後，伺服器端先查店鋪（得到 tenantId）再查文章。
//
// 失敗一律退回呼叫端傳進來的靜態標題（只有 title、沒有殘缺的 Open Graph）：標題請求不能讓頁面壞掉。

import type { Metadata } from 'next'
import { fetchPublicData } from './server-api'
import { listingPath, postBySlugPath, storeProfilePath, UUID_PATTERN } from './public-api'
import { SITE_NAME, SITE_URL } from './site'

const DESCRIPTION_MAX = 160

interface StoreProfile {
  name?: unknown
  description?: string | null
  logoUrl?: string | null
}

interface StoreIdentity extends StoreProfile {
  tenantId?: unknown
}

interface PostDetail {
  title?: unknown
  excerpt?: string | null
  content?: string | null
  featuredImageUrl?: string | null
  publishedAt?: string | null
}

interface ListingDetail {
  title?: unknown
  description?: string | null
  coverImageUrl?: string | null
}

/** 文章內文裡的商品嵌入語法 {{embed:listing:<id>}}：不是人讀的文字，不能出現在 description（語法的定義見 lib/article-embeds）。 */
const EMBED_SYNTAX = /\{\{embed:listing:[^}]*\}\}/gi

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

function build(
  title: string,
  description: string | undefined,
  images: string[] | undefined,
  url: string,
  article?: { publishedTime?: string }
): Metadata {
  const shared = {
    title,
    ...(description ? { description } : {}),
    siteName: SITE_NAME,
    url,
    ...(images ? { images } : {}),
  }
  return {
    title,
    ...(description ? { description } : {}),
    // 標準網址（DEF-500，Sprint 341）：帶追蹤參數（?utm_source=…）或其他多餘查詢字串進來的網址，搜尋引擎要收斂成同一個
    alternates: { canonical: url },
    openGraph: article
      ? { ...shared, type: 'article', ...(article.publishedTime ? { publishedTime: article.publishedTime } : {}) }
      : { ...shared, type: 'website' },
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
  const encoded = encodeURIComponent(slug) // 頁面網址（og:url）用；伺服器端請求的路徑由 storeProfilePath 組（同樣編碼）
  const store = await fetchPublicData<StoreProfile>(storeProfilePath(slug))
  if (!store || !isNonEmptyString(store.name)) return { title: options.fallbackTitle }
  const name = store.name.trim()
  const title = options.section ? `${name}｜${options.section}` : name
  return build(title, toDescription(store.description), toImages(store.logoUrl), `/stores/${encoded}${options.path}`)
}

export async function listingMetadata(id: string, fallbackTitle: string): Promise<Metadata> {
  if (!UUID_PATTERN.test(id)) return { title: fallbackTitle } // 不是 UUID 的一定查不到，不必打後端
  const listing = await fetchPublicData<ListingDetail>(listingPath(id))
  if (!listing || !isNonEmptyString(listing.title)) return { title: fallbackTitle }
  return build(listing.title.trim(), toDescription(listing.description), toImages(listing.coverImageUrl), `/listings/${id}`)
}

/**
 * 文章頁（/stores/[slug]/blog/[postSlug]）的標題與分享資訊：「文章標題｜店名」。
 *
 * 要打兩次後端：先查店鋪（公開店鋪資料給 tenantId，也順便擋掉不存在或非 ACTIVE 的店鋪——停權店鋪的文章標題不會出現在 HTML），
 * 再用 tenantId 查文章。查文章帶 countView=false：標題請求不是一次瀏覽，瀏覽器載入頁面時才計一次
 * （否則每篇文章的瀏覽數會多算一倍，搜尋引擎與連結預覽機器人也會灌水；見後端 PostService.getPublishedPostBySlug）。
 * 任何一步取不到都退回靜態標題。
 */
export async function postMetadata(storeSlug: string, postSlug: string, fallbackTitle: string): Promise<Metadata> {
  const encodedStore = encodeURIComponent(storeSlug) // 頁面網址（og:url）用；伺服器端請求的路徑由 public-api 的函式組（同樣編碼）
  const encodedPost = encodeURIComponent(postSlug)
  const store = await fetchPublicData<StoreIdentity>(storeProfilePath(storeSlug))
  if (!store || !isNonEmptyString(store.name)) return { title: fallbackTitle }
  // tenantId 要放進查詢字串，先確認長得像 UUID，不信任後端回傳的任意字串
  if (typeof store.tenantId !== 'string' || !UUID_PATTERN.test(store.tenantId)) return { title: fallbackTitle }
  const post = await fetchPublicData<PostDetail>(postBySlugPath(postSlug, store.tenantId))
  if (!post || !isNonEmptyString(post.title)) return { title: fallbackTitle }
  const title = `${post.title.trim()}｜${store.name.trim()}`
  const description = toDescription(post.excerpt) ?? toDescription((post.content ?? '').replace(EMBED_SYNTAX, ' '))
  const publishedTime =
    isNonEmptyString(post.publishedAt) && !Number.isNaN(Date.parse(post.publishedAt)) ? post.publishedAt : undefined
  return build(title, description, toImages(post.featuredImageUrl), `/stores/${encodedStore}/blog/${encodedPost}`, {
    publishedTime,
  })
}

// ---------------------------------------------------------------------------------------------------------------------
// 結構化資料（JSON-LD，schema.org；DEF-500，Sprint 341）：讓搜尋引擎「理解」頁面是什麼（商品＋價格、文章＋發布日期、店鋪、網站與站內搜尋），
// 可能顯示成豐富搜尋結果。內容一律取自伺服器端已取好的資料（與頁面上看到的相同）——結構化資料與頁面內容不一致是違反搜尋引擎規範的。
// 輸出時由 components/seo/JsonLd 負責跳脫 `<`（資料裡有店家與使用者輸入的文字，不能讓 </script> 提前結束標籤）。
// ---------------------------------------------------------------------------------------------------------------------

type JsonLdObject = Record<string, unknown>
const SCHEMA_ORG = 'https://schema.org'

/** 站內相對路徑 → 絕對網址（結構化資料的 url 必須是絕對網址）。 */
const absoluteUrl = (path: string): string => new URL(path, SITE_URL).toString()

/** 整站：網站名稱與站內搜尋（搜尋引擎可顯示站內搜尋框，搜尋走首頁的 ?keyword=）。 */
export function websiteJsonLd(): JsonLdObject {
  return {
    '@context': SCHEMA_ORG,
    '@type': 'WebSite',
    name: SITE_NAME,
    url: absoluteUrl('/'),
    potentialAction: {
      '@type': 'SearchAction',
      target: { '@type': 'EntryPoint', urlTemplate: `${absoluteUrl('/')}?keyword={search_term_string}` },
      'query-input': 'required name=search_term_string',
    },
  }
}

/** 店鋪首頁：店名、說明、Logo。 */
export function storeJsonLd(slug: string, store: StoreProfile): JsonLdObject | null {
  if (!isNonEmptyString(store.name)) return null
  const description = toDescription(store.description)
  const logo = toImages(store.logoUrl)?.[0]
  return {
    '@context': SCHEMA_ORG,
    '@type': 'Store',
    name: store.name.trim(),
    url: absoluteUrl(`/stores/${encodeURIComponent(slug)}`),
    ...(description ? { description } : {}),
    ...(logo ? { logo, image: logo } : {}),
  }
}

export interface ListingForJsonLd extends ListingDetail {
  listingType?: unknown
  basePrice?: unknown
  currency?: unknown
}

/** 商品／房源詳情：名稱、說明、圖片與價格。房源用 HotelRoom、商品用 Product；沒有合法價格就不帶 offers（寧可少，不寫錯）。 */
export function listingJsonLd(id: string, listing: ListingForJsonLd): JsonLdObject | null {
  if (!isNonEmptyString(listing.title)) return null
  const description = toDescription(listing.description)
  const image = toImages(listing.coverImageUrl)?.[0]
  const url = absoluteUrl(`/listings/${id}`)
  const hasPrice = typeof listing.basePrice === 'number' && Number.isFinite(listing.basePrice) && listing.basePrice >= 0
  return {
    '@context': SCHEMA_ORG,
    '@type': listing.listingType === 'ROOM' ? 'HotelRoom' : 'Product',
    name: listing.title.trim(),
    url,
    ...(description ? { description } : {}),
    ...(image ? { image } : {}),
    ...(hasPrice && isNonEmptyString(listing.currency)
      ? {
          offers: {
            '@type': 'Offer',
            url,
            price: listing.basePrice,
            priceCurrency: listing.currency,
            availability: `${SCHEMA_ORG}/InStock`,
          },
        }
      : {}),
  }
}

export interface PostForJsonLd extends PostDetail {
  authorName?: unknown
}

/** 單篇文章：標題、說明、發布日期、作者、封面圖，以及它所屬的店鋪部落格。 */
export function articleJsonLd(
  storeSlug: string,
  postSlug: string,
  storeName: string,
  post: PostForJsonLd
): JsonLdObject | null {
  if (!isNonEmptyString(post.title)) return null
  const url = absoluteUrl(`/stores/${encodeURIComponent(storeSlug)}/blog/${encodeURIComponent(postSlug)}`)
  const description = toDescription(post.excerpt) ?? toDescription((post.content ?? '').replace(EMBED_SYNTAX, ' '))
  const image = toImages(post.featuredImageUrl)?.[0]
  const published = isNonEmptyString(post.publishedAt) && !Number.isNaN(Date.parse(post.publishedAt)) ? post.publishedAt : undefined
  return {
    '@context': SCHEMA_ORG,
    '@type': 'BlogPosting',
    headline: post.title.trim().slice(0, 110), // schema.org／Google 對 headline 的建議上限
    url,
    mainEntityOfPage: { '@type': 'WebPage', '@id': url },
    ...(description ? { description } : {}),
    ...(image ? { image } : {}),
    ...(published ? { datePublished: published } : {}),
    ...(isNonEmptyString(post.authorName) ? { author: { '@type': 'Person', name: post.authorName.trim() } } : {}),
    publisher: { '@type': 'Organization', name: SITE_NAME },
    isPartOf: {
      '@type': 'Blog',
      name: `${storeName.trim()}｜部落格`,
      url: absoluteUrl(`/stores/${encodeURIComponent(storeSlug)}/blog`),
    },
  }
}
