// sitemap.xml 的內容（DEF-500，Sprint 341）：告訴搜尋引擎「這個網站有哪些公開頁面」，比只靠連結爬行更完整也更快。
// 純函式（buildSitemap）與取資料（loadSitemapSources）分開：前者可以單元測試；後者打後端公開 API（伺服器端，見 lib/server-api）。
//
// 收錄範圍：首頁與四個列表入口、服務條款與隱私權政策、每間店鋪（首頁、商品、房源、部落格，有內容才列）、每個商品／房源詳情、
// 每篇文章。分頁（?page=2）、排序、搜尋結果不收錄——它們是同一批內容的不同切法，搜尋引擎沿著頁面上的連結就找得到。
// 店鋪從商品與文章的 tenantSlug 推得：沒有任何商品與文章的店鋪沒有可收錄的內容。

import type { MetadataRoute } from 'next'
import type { PublicPostFeed, PublicPostSummary } from '@/services/cms'
import type { Listing, Page } from '@/services/listing'
import { articlePath } from './article-path'
import { publicListingsPath, publicPostFeedPath } from './public-api'
import { fetchPublic } from './server-api'
import { SITE_URL } from './site'

/** 後端公開列表單頁最多回 100 筆（PageableUtils.of 的上限）。 */
const SOURCE_PAGE_SIZE = 100
/** 每一種來源最多讀幾頁（2 萬筆）：sitemap 單檔上限是 5 萬個網址，也不讓一次請求為了超大型目錄打爆後端。超過要改用 generateSitemaps 切檔。 */
const MAX_PAGES = 200
/** 一次同時讀幾頁（分批，不一口氣全打出去）。 */
const BATCH = 4

const STATIC_PATHS = ['/', '/products', '/rooms', '/blog', '/privacy', '/terms']

type SitemapListing = Pick<Listing, 'id' | 'listingType'> & { tenantSlug?: string; updatedAt?: string }
type SitemapPost = Pick<PublicPostSummary, 'slug' | 'tenantSlug' | 'publishedAt'>

const absolute = (path: string) => new URL(path, SITE_URL).toString()

/** 把取到的商品／房源與文章組成 sitemap 條目。 */
export function buildSitemap(listings: SitemapListing[], posts: SitemapPost[]): MetadataRoute.Sitemap {
  const entries: MetadataRoute.Sitemap = STATIC_PATHS.map((path) => ({
    url: absolute(path),
    changeFrequency: path === '/' || path === '/blog' ? 'daily' : 'monthly',
    priority: path === '/' ? 1 : path === '/privacy' || path === '/terms' ? 0.2 : 0.8,
  }))

  // 每間店鋪：有商品才列商品頁、有房源才列房源頁、有文章才列部落格頁
  const stores = new Map<string, { products: boolean; rooms: boolean; blog: boolean }>()
  const store = (slug: string) => {
    let flags = stores.get(slug)
    if (!flags) stores.set(slug, (flags = { products: false, rooms: false, blog: false }))
    return flags
  }
  for (const listing of listings) {
    if (!listing.tenantSlug) continue
    const flags = store(listing.tenantSlug)
    if (listing.listingType === 'ROOM') flags.rooms = true
    else flags.products = true
  }
  for (const post of posts) {
    if (post.tenantSlug) store(post.tenantSlug).blog = true
  }
  for (const [slug, flags] of stores) {
    const base = `/stores/${encodeURIComponent(slug)}`
    entries.push({ url: absolute(base), changeFrequency: 'weekly', priority: 0.7 })
    if (flags.products) entries.push({ url: absolute(`${base}/products`), changeFrequency: 'weekly', priority: 0.6 })
    if (flags.rooms) entries.push({ url: absolute(`${base}/rooms`), changeFrequency: 'weekly', priority: 0.6 })
    if (flags.blog) entries.push({ url: absolute(`${base}/blog`), changeFrequency: 'weekly', priority: 0.6 })
  }

  for (const listing of listings) {
    const modified = listing.updatedAt && !Number.isNaN(Date.parse(listing.updatedAt)) ? new Date(listing.updatedAt) : undefined
    entries.push({
      url: absolute(`/listings/${listing.id}`),
      ...(modified ? { lastModified: modified } : {}),
      changeFrequency: 'weekly',
      priority: 0.7,
    })
  }
  for (const post of posts) {
    if (!post.tenantSlug) continue
    const published = post.publishedAt && !Number.isNaN(Date.parse(post.publishedAt)) ? new Date(post.publishedAt) : undefined
    entries.push({
      url: absolute(articlePath(post.tenantSlug, post.slug)),
      ...(published ? { lastModified: published } : {}),
      changeFrequency: 'monthly',
      priority: 0.6,
    })
  }
  return entries
}

interface Loaded<T> {
  items: T[]
  /** 全部讀到了（沒有任何一頁失敗、也沒有因為頁數上限而截斷） */
  complete: boolean
}

/** 讀完一個分頁列表的所有頁：先讀第 0 頁拿到總頁數，其餘分批同時讀。任何一頁失敗就標記為不完整、保留已經讀到的。 */
async function loadAllPages<T>(
  pathFor: (page: number) => string,
  read: (data: unknown) => { items: T[]; totalPages: number } | null
): Promise<Loaded<T>> {
  const first = await fetchPublic<unknown>(pathFor(0))
  const firstPage = first.status === 'ok' ? read(first.data) : null
  if (!firstPage) return { items: [], complete: false }

  const items = [...firstPage.items]
  const lastPage = Math.min(firstPage.totalPages, MAX_PAGES)
  let complete = firstPage.totalPages <= MAX_PAGES
  for (let start = 1; start < lastPage; start += BATCH) {
    const pages = Array.from({ length: Math.min(BATCH, lastPage - start) }, (_, i) => start + i)
    const results = await Promise.all(pages.map((page) => fetchPublic<unknown>(pathFor(page))))
    for (const result of results) {
      const parsed = result.status === 'ok' ? read(result.data) : null
      if (parsed) items.push(...parsed.items)
      else complete = false
    }
  }
  return { items, complete }
}

/** 從後端取 sitemap 要的商品／房源與文章（公開端點，只含營業中店鋪的上架項目與已發布文章）。 */
export async function loadSitemapSources(): Promise<{ listings: SitemapListing[]; posts: SitemapPost[]; complete: boolean }> {
  const [listings, posts] = await Promise.all([
    loadAllPages<SitemapListing>(
      (page) => publicListingsPath({ page, size: SOURCE_PAGE_SIZE }),
      (data) => {
        const result = data as Page<Listing>
        return Array.isArray(result?.content) ? { items: result.content, totalPages: result.totalPages ?? 1 } : null
      }
    ),
    loadAllPages<SitemapPost>(
      (page) => publicPostFeedPath(page, SOURCE_PAGE_SIZE),
      (data) => {
        const result = data as PublicPostFeed
        return Array.isArray(result?.posts) ? { items: result.posts, totalPages: result.totalPages ?? 1 } : null
      }
    ),
  ])
  return { listings: listings.items, posts: posts.items, complete: listings.complete && posts.complete }
}
