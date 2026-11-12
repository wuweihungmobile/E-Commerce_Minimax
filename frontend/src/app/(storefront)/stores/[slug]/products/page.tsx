import type { Metadata } from "next"
import { StoreProductsView } from "@/components/storefront/StoreProductsView"
import { LIST_PAGE_SIZE, storeListingsPath } from "@/lib/public-api"
import { peekStoreProfile } from "@/lib/public-data"
import { storeMetadata } from "@/lib/seo"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { Listing, Page } from "@/services/listing"

// 標題用「店名｜商品」（DEF-475，Sprint 335）；取不到店名就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  return storeMetadata(slug, { section: '商品', fallbackTitle: '店鋪商品', path: '/products' })
}

// 店鋪前台商品列表（PRD §10 /stores/[slug]/products；DEF-321(b)，Sprint 252）。
export default async function StoreProductsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const { slug } = await params
  const sp = await searchParams
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  // SEO（DEF-500，Sprint 341）：店鋪資料與這一頁的清單在伺服器端取、隨第一份 HTML 送出（做法同 stores/[slug]/page.tsx）
  const profile = await peekStoreProfile(slug)
  const listings = profile
    ? await fetchPublic<Page<Listing>>(storeListingsPath(slug, { type: "PRODUCT", page, size: LIST_PAGE_SIZE }))
    : undefined

  return (
    <StoreProductsView
      slug={slug}
      page={page}
      initialProfile={profile}
      initialListings={listings && dataOrUndefined(listings)}
    />
  )
}
