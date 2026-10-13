"use client"

import { useCallback, useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { SortToolbar } from "@/components/storefront/SortToolbar"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import listingService, { type Listing, type Page } from "@/services/listing"

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
  const [data, setData] = useState<Page<Listing> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    // 注意：不在 effect 內同步 setState（React 19 嚴格 hooks 禁令），比照 HomeContent 既有寫法。
    let cancelled = false
    const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
    listingService
      .getPublicListings({
        page,
        size: PAGE_SIZE,
        type: type === "PRODUCT" ? "product" : "room",
        keyword: keyword || undefined,
        sortBy,
        sortDir,
      })
      .then((res) => {
        if (cancelled) return
        setData(res)
        setError(false)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setError(true)
        setData(null)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [type, sortTab, keyword, page, reloadKey])

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
    setError(false)
    setLoading(true)
    setReloadKey((k) => k + 1)
  }

  return (
    <StorefrontShell>
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
        <div
          data-testid="listing-type-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <p className="text-sm text-rs-ink-muted">請確認網路連線後重試</p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-2 inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
          >
            重新載入
          </button>
        </div>
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
        <div
          data-testid="listing-type-empty"
          className="flex flex-col items-center justify-center py-24 text-center gap-2"
        >
          <p className="text-lg text-rs-ink">{emptyMessage}</p>
        </div>
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
