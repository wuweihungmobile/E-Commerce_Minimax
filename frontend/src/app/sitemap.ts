import type { MetadataRoute } from 'next'
import { buildSitemap, loadSitemapSources } from '@/lib/sitemap'

// sitemap.xml（DEF-500，Sprint 341）。內容見 lib/sitemap.ts。
//
// force-dynamic 是必要的：sitemap.ts 預設會在 `next build` 時執行並把結果烤進建置產物（官方文件：special Route Handler，預設快取）——
// 建置時後端不在，烤進去的會是一份沒有任何商品與文章的 sitemap，而且之後不會更新。
// 改成每次請求才產生，並在記憶體裡留一小時（爬蟲一天只來幾次，不必每次都把全部商品與文章再讀一遍）。
// 只有「全部讀到了」才留存：後端暫時壞了時回傳只有固定頁面的 sitemap，但不留存，下一次請求會重試。
export const dynamic = 'force-dynamic'

const TTL_MS = 60 * 60 * 1000
let cached: { at: number; entries: MetadataRoute.Sitemap } | null = null

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  if (cached && Date.now() - cached.at < TTL_MS) return cached.entries
  const { listings, posts, complete } = await loadSitemapSources()
  const entries = buildSitemap(listings, posts)
  if (complete) cached = { at: Date.now(), entries }
  return entries
}
