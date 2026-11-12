import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { ListingDetail } from "@/components/storefront/ListingDetail"
import { listingPath, UUID_PATTERN } from "@/lib/public-api"
import { JsonLd } from "@/components/seo/JsonLd"
import { listingJsonLd, listingMetadata } from "@/lib/seo"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { Listing } from "@/services/listing"

// 標題用商品／房源名稱（DEF-475，Sprint 335）；id 不是 UUID 或取不到就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params
  return listingMetadata(id, '商品／房源詳情')
}

// 買家商品詳情頁（S38 AI-2103）：置於 (storefront) group，沿用共用 Header/Footer + StorefrontShell。
// 公開路由；未登入/401 由 ListingDetail 顯示登入引導。Next 16：params 為 Promise。
//
// SEO（DEF-500，Sprint 341）：商品在「伺服器端」取、隨第一份 HTML 送出（名稱、價格、說明、圖片，搜尋引擎看得到）。
//   - id 不是 UUID（不可能存在）或後端明確回 404：notFound()＝真正的 404（listings/[id]/not-found.tsx，與備援畫面共用 ListingNotFound）；
//   - 後端暫時不可用（逾時、429、5xx）或需要登入（401／403，舊行為）：不帶資料，由瀏覽器自己載入並照舊處理，絕不回 404。
export default async function ListingDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  if (!UUID_PATTERN.test(id)) notFound()
  const result = await fetchPublic<Listing>(listingPath(id))
  if (result.status === "notfound") notFound()
  const listing = dataOrUndefined(result)
  return (
    <StorefrontShell>
      {/* 商品／房源的結構化資料（DEF-500）：名稱、說明、圖片與價格，內容與頁面上看到的相同 */}
      <JsonLd data={listing ? listingJsonLd(id, listing) : null} />
      <ListingDetail id={id} initialListing={listing} />
    </StorefrontShell>
  )
}
