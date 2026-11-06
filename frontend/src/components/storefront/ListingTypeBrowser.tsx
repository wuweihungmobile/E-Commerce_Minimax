"use client"

import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { SortToolbar } from "@/components/storefront/SortToolbar"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import listingService from "@/services/listing"
import { useAsyncData } from "@/hooks/useAsyncData"
import { Button } from "@/components/ui/button"
import { EmptyState } from '@/components/ui/states'
import { PageTitle } from "@/components/ui/page-title"

const SORT_TABS = [
  { id: "newest", label: "最新" },
  { id: "priceAsc", label: "價格低到高" },
  { id: "priceDesc", label: "價格高到低" },
]

const SORT_MAP: Record<string, { sortBy: string; sortDir: "ASC" | "DESC" }> = {
  newest: { sortBy: "createdAt", sortDir: "DESC" },
  priceAsc: { sortBy: "basePrice", sortDir: "ASC" },
  priceDesc: { sortBy: "basePrice", sortDir: "DESC" },
}

const PAGE_SIZE = 12

export interface ListingTypeBrowserProps {
  /** 固定瀏覽類型——與首頁 HomeContent 不同，本頁沒有「全部商品」切換，類型由路由本身決定 */
  type: "PRODUCT" | "ROOM"
  /** 導覽時使用的基礎路徑（/products 或 /rooms），取代 HomeContent 寫死的 "/" */
  basePath: "/products" | "/rooms"
  emptyMessage: string
  /** 排序頁籤（newest/priceAsc/priceDesc），由 server page 依 URL `sort` 解析 */
  sortTab: string
  /** 關鍵字，由 server page 依 URL `keyword` 解析 */
  keyword: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析 */
  page: number
}

// 跨店鋪公開商品/房源瀏覽（PRD §10.1.1 `/products`、`/rooms`，DEF-358(a)，Sprint 259）。
// 與 HomeContent.tsx 共用同一套 URL-driven 篩選 + 四態處理骨架，差異僅在：固定 type（無分類
// 切換側欄）、呼叫端自訂 basePath（HomeContent 寫死導回 "/"）。資料來源同為公開端點
// getPublicListings，匿名可用，不需要 needsAuth 分支。
export function ListingTypeBrowser({ type, basePath, emptyMessage, sortTab, keyword, page }: ListingTypeBrowserProps) {
  const router = useRouter()
  const { data: result, error, loading, reload } = useAsyncData(
    () => {
      const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
      return listingService.getPublicListings({
        page,
        size: PAGE_SIZE,
        type: type === "PRODUCT" ? "product" : "room",
        keyword: keyword || undefined,
        sortBy,
        sortDir,
      })
    },
    [type, sortTab, keyword, page],
    { errorMessage: "載入失敗，請稍後再試" }
  )
  // 失敗時不顯示舊資料（與原本失敗後清成 null 相同）
  const data = error === null ? result : undefined

  const navigate = useCallback(
    (next: { sort?: string; page?: number }) => {
      const s = next.sort ?? sortTab
      const p = next.page ?? 0
      const params = new URLSearchParams()
      if (s !== "newest") params.set("sort", s)
      if (keyword) params.set("keyword", keyword)
      if (p > 0) params.set("page", String(p + 1)) // URL 為 1-based
      const qs = params.toString()
      router.push(qs ? `${basePath}?${qs}` : basePath)
    },
    [sortTab, keyword, basePath, router]
  )

  const handleSort = (id: string) => navigate({ sort: id, page: 0 })
  const handlePageChange = (oneBased: number) => navigate({ page: oneBased - 1 })
  const handleRetry = () => {
    reload()
  }

  return (
    <StorefrontShell>
      <PageTitle className="sr-only">{type === "PRODUCT" ? "商品" : "房源"}</PageTitle>
      <SortToolbar
        tabs={SORT_TABS}
        activeTab={sortTab}
        onTabChange={handleSort}
        right={
          data ? (
            <span data-testid="result-count">共 {data.totalElements} 件</span>
          ) : null
        }
      />

      {loading ? (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6">
          {Array.from({ length: PAGE_SIZE }).map((_, i) => (
            <div key={i} className="flex flex-col gap-3">
              <Skeleton className="aspect-square w-full rounded-lg" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-5 w-1/3" />
            </div>
          ))}
        </div>
      ) : error ? (
        <EmptyState
          data-testid="listing-type-error"
          className="py-24"
          title="載入失敗，請稍後再試"
          description="請確認網路連線後重試"
          action={
            <Button variant="default" type="button" onClick={handleRetry}>
              重新載入
            </Button>
          }
        />
      ) : data && data.content.length > 0 ? (
        <div
          data-testid="listing-type-grid"
          className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6"
        >
          {data.content.map((item) => (
            <ProductCard
              key={item.id}
              href={`/listings/${item.id}`}
              image={item.coverImageUrl}
              title={item.title}
              price={item.basePrice}
              currency={item.currency === "TWD" ? "NT$" : item.currency}
              features={item.tags?.slice(0, 2)}
              logistics={item.listingType === "ROOM" ? ["旅宿"] : ["賣家宅配"]}
            />
          ))}
        </div>
      ) : (
        <EmptyState data-testid="listing-type-empty" className="py-24" title={emptyMessage} />
      )}

      {data && data.totalPages > 1 && (
        <Pagination
          current={page + 1}
          total={data.totalPages}
          onChange={handlePageChange}
        />
      )}
    </StorefrontShell>
  )
}
