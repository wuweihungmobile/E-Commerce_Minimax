import type { Metadata } from "next"
import { StoreHomeView } from "@/components/storefront/StoreHomeView"
import { FEATURED_LIMIT, storeListingsPath } from "@/lib/public-api"
import { peekStoreProfile } from "@/lib/public-data"
import { JsonLd } from "@/components/seo/JsonLd"
import { storeJsonLd, storeMetadata } from "@/lib/seo"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { Listing, Page } from "@/services/listing"

// 標題用店名（DEF-475，Sprint 335）；取不到（店鋪不存在、後端慢或壞）就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  return storeMetadata(slug, { fallbackTitle: '店鋪首頁', path: '' })
}

// 店鋪前台首頁（PRD §10 /stores/[slug]；§12.7 Guest/Buyer 瀏覽店鋪前台流程；
// DEF-321(b)，Sprint 252）。公開路由，無需登入。
//
// SEO（DEF-500，Sprint 341）：店鋪資料與精選清單在「伺服器端」取、隨第一份 HTML 送出，搜尋引擎與不執行 JavaScript 的爬蟲
// 看得到店名、導覽與商品卡片連結。店鋪不存在由 stores/[slug]/layout.tsx 回真正的 404；後端暫時不可用就不帶資料，
// 元件退回由瀏覽器自己載入。店鋪資料沒取到時不再打精選清單（後端壞了就不要再多等一個逾時）。
export default async function StorePage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  const profile = await peekStoreProfile(slug)
  const featured = profile
    ? await fetchPublic<Page<Listing>>(storeListingsPath(slug, { featured: true, size: FEATURED_LIMIT }))
    : undefined
  return (
    <>
      {/* 店鋪的結構化資料（DEF-500）：店名、說明、Logo */}
      <JsonLd data={profile ? storeJsonLd(slug, profile) : null} />
      <StoreHomeView slug={slug} initialProfile={profile} initialFeatured={featured && dataOrUndefined(featured)} />
    </>
  )
}
