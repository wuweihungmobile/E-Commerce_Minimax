import { ListingTypeBrowser } from "@/components/storefront/ListingTypeBrowser"

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

  return (
    <ListingTypeBrowser
      key={`${sortTab}|${keyword}|${page}`}
      type="PRODUCT"
      basePath="/products"
      emptyMessage="目前沒有符合條件的商品"
      sortTab={sortTab}
      keyword={keyword}
      page={page}
    />
  )
}
