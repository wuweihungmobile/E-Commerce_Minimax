import { HomeContent } from "@/components/storefront/HomeContent"

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

  // key 使篩選/排序/搜尋/分頁變更時 remount HomeContent → 回到 loading 初值顯示 skeleton
  // （避免在 effect 內同步 setState，符合 React 19 嚴格 hooks 規則）。
  return (
    <HomeContent
      key={`${cat}|${sortTab}|${keyword}|${page}`}
      cat={cat}
      sortTab={sortTab}
      keyword={keyword}
      page={page}
    />
  )
}
