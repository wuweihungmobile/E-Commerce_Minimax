import type { Metadata } from "next"
import { SITE_TITLE } from "@/lib/site"
import { HomeContent } from "@/components/storefront/HomeContent"
import { JsonLd } from "@/components/seo/JsonLd"
import { websiteJsonLd } from "@/lib/seo"
import { LIST_PAGE_SIZE, publicListingsPath, SORT_MAP } from "@/lib/public-api"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { Listing, Page } from "@/services/listing"

export const metadata: Metadata = {
  title: { absolute: SITE_TITLE },
}

const VALID_CATS = ["all", "product", "room"]
const VALID_SORTS = ["newest", "priceAsc", "priceDesc"]

// 首頁（server component）：依 URL searchParams 解析篩選條件，以 props 傳給 client HomeContent
// （DEF-020，官方建議做法：避免 useSearchParams + Suspense）。非法值退回預設。
export default async function HomePage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const sp = await searchParams
  const typeParam = typeof sp.type === "string" ? sp.type : "all"
  const cat = VALID_CATS.includes(typeParam) ? typeParam : "all"
  const sortParam = typeof sp.sort === "string" ? sp.sort : "newest"
  const sortTab = VALID_SORTS.includes(sortParam) ? sortParam : "newest"
  const keyword = typeof sp.keyword === "string" ? sp.keyword : ""
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  // SEO（DEF-500，Sprint 341）：這一頁的商品／房源在「伺服器端」取（GET /v2/public/listings）、隨第一份 HTML 送出——
  // 首頁是整站最重要的入口，搜尋引擎與不執行 JavaScript 的爬蟲要看得到商品卡片與連結。取不到（逾時、後端壞了）就不帶資料，
  // 元件退回由瀏覽器自己載入。
  const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
  const result = await fetchPublic<Page<Listing>>(
    publicListingsPath({
      page,
      size: LIST_PAGE_SIZE,
      type: cat === "all" ? undefined : (cat as "product" | "room"),
      keyword: keyword || undefined,
      sortBy,
      sortDir,
    })
  )

  // key 使篩選/排序/搜尋/分頁變更時 remount HomeContent，直接用這一組條件的伺服器資料
  // （避免在 effect 內同步 setState，符合 React 19 嚴格 hooks 規則）。
  return (
    <>
      {/* 網站名稱與站內搜尋（DEF-500）：搜尋引擎可顯示站內搜尋框 */}
      <JsonLd data={websiteJsonLd()} />
      <HomeContent
        key={`${cat}|${sortTab}|${keyword}|${page}`}
        cat={cat}
        sortTab={sortTab}
        keyword={keyword}
        page={page}
        initialResult={dataOrUndefined(result)}
      />
    </>
  )
}
