import type { Metadata } from "next"
import { ListingTypeBrowser } from "@/components/storefront/ListingTypeBrowser"
import { LIST_PAGE_SIZE, publicListingsPath, SORT_MAP } from "@/lib/public-api"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { Listing, Page } from "@/services/listing"

export const metadata: Metadata = {
  title: '商品',
}

const VALID_SORTS = ["newest", "priceAsc", "priceDesc"]

// 跨店鋪商品列表（PRD §10.1.1 `/products`，DEF-358(a)，Sprint 259）：server component，
// 依 URL searchParams 解析篩選條件，以 props 傳給 client ListingTypeBrowser（比照既有
// `/`（HomeContent）慣例，DEF-020：避免 useSearchParams + Suspense）。非法值退回預設。
export default async function ProductsPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const sp = await searchParams
  const sortParam = typeof sp.sort === "string" ? sp.sort : "newest"
  const sortTab = VALID_SORTS.includes(sortParam) ? sortParam : "newest"
  const keyword = typeof sp.keyword === "string" ? sp.keyword : ""
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  // SEO（DEF-500，Sprint 341）：這一頁在「伺服器端」取、隨第一份 HTML 送出（做法同首頁）；取不到就不帶資料，由瀏覽器自己載入。
  const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
  const result = await fetchPublic<Page<Listing>>(
    publicListingsPath({ page, size: LIST_PAGE_SIZE, type: "product", keyword: keyword || undefined, sortBy, sortDir })
  )

  return (
    <ListingTypeBrowser
      key={`${sortTab}|${keyword}|${page}`}
      type="PRODUCT"
      basePath="/products"
      emptyMessage="目前沒有符合條件的商品"
      sortTab={sortTab}
      keyword={keyword}
      page={page}
      initialResult={dataOrUndefined(result)}
    />
  )
}
