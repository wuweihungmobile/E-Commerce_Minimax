"use client"

import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { SidebarNav } from "@/components/storefront/SidebarNav"
import { SortToolbar } from "@/components/storefront/SortToolbar"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import listingService from "@/services/listing"
import { useAsyncData } from "@/hooks/useAsyncData"
import { Button } from "@/components/ui/button"
import { PageTitle } from '@/components/ui/page-title'

const CATEGORIES = [
  { id: "all", label: "全部商品" },
  { id: "product", label: "嚴選商品" },
  { id: "room", label: "旅宿房型" },
]

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

export interface HomeContentProps {
  /** 分類（all/product/room），由 server page 依 URL `type` 解析 */
  cat: string
  /** 排序頁籤（newest/priceAsc/priceDesc），由 server page 依 URL `sort` 解析 */
  sortTab: string
  /** 關鍵字，由 server page 依 URL `keyword` 解析 */
  keyword: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析 */
  page: number
}

// 首頁內容區（DEF-020：URL-driven 篩選）。篩選/排序/分頁改由 router.push 更新 URL query，
// server page 依 searchParams 重新以 props 傳入 → 本元件 effect 依 props 重新取資料
// （不再用 page-scoped state + nonce）。
//
// DEF-358(a)（Sprint 259）：改呼叫跨店鋪公開端點 getPublicListings（匿名可用，比照 PRD §12.7
// 「Guest 可瀏覽所有商品與房源」），取代先前需登入的 getListings——先前 401/403 時顯示
// 「登入後即可瀏覽精選商品」的 needsAuth 分支因此移除，不再是必要狀態。
export function HomeContent({ cat, sortTab, keyword, page }: HomeContentProps) {
  const router = useRouter()
  const { data: result, error, loading, reload } = useAsyncData(
    () => {
      const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
      return listingService.getPublicListings({
        page,
        size: PAGE_SIZE,
        type: cat === "all" ? undefined : (cat as "product" | "room"),
        keyword: keyword || undefined,
        sortBy,
        sortDir,
      })
    },
    [cat, sortTab, keyword, page],
    { errorMessage: "載入失敗，請稍後再試" }
  )
  // 失敗時不顯示舊資料（與原本失敗後清成 null 相同）
  const data = error === null ? result : undefined

  // 更新 URL query（保留其他篩選）。篩選變更預設回第 1 頁。
  const navigate = useCallback(
    (next: { cat?: string; sort?: string; page?: number }) => {
      const c = next.cat ?? cat
      const s = next.sort ?? sortTab
      const p = next.page ?? 0
      const params = new URLSearchParams()
      if (c !== "all") params.set("type", c)
      if (s !== "newest") params.set("sort", s)
      if (keyword) params.set("keyword", keyword)
      if (p > 0) params.set("page", String(p + 1)) // URL 為 1-based
      const qs = params.toString()
      router.push(qs ? `/?${qs}` : "/")
    },
    [cat, sortTab, keyword, router]
  )

  const handleSelectCat = (id: string) => navigate({ cat: id, page: 0 })
  const handleSort = (id: string) => navigate({ sort: id, page: 0 })
  const handlePageChange = (oneBased: number) => navigate({ page: oneBased - 1 })
  const handleRetry = () => {
    reload()
  }

  return (
    <StorefrontShell
      sidebar={
        <SidebarNav items={CATEGORIES} active={cat} onSelect={handleSelectCat} />
      }
    >
      <PageTitle className="sr-only">意象若水 RUOSHUI</PageTitle>
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
          data-testid="home-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <p className="text-sm text-rs-ink-muted">請確認網路連線後重試</p>
          <Button
            variant="default"
            type="button"
            onClick={handleRetry}
            className="mt-2"
          >
            重新載入
          </Button>
        </div>
      ) : data && data.content.length > 0 ? (
        <div
          data-testid="product-grid"
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
          data-testid="home-empty"
          className="flex flex-col items-center justify-center py-24 text-center gap-2"
        >
          <p className="text-lg text-rs-ink">目前沒有符合條件的商品</p>
          <p className="text-sm text-rs-ink-muted">換個分類或關鍵字再逛逛吧</p>
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
